from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import subprocess
import tempfile
import unittest

from test_platform_v2_dormant_worker import (
    FakeProvider,
    admitted_record,
    budget,
    config,
)
from v2.dormant_handoff_commit import (
    DormantCommitDisposition,
    DormantCommitOutcome,
    DormantHandoffCommitLedger,
    DormantHandoffCommitStore,
    advance_dormant_handoff_commit,
)
from v2.dormant_worker import (
    DormantWorkerDisposition,
    advance_dormant_admitted_worker,
)
from v2.worker_provider import (
    FrozenWorkerSpec,
    WorkerProviderConfig,
    WorkerRunLedger,
    WorkerRunRecord,
    WorkerRunStatus,
    WorkerRunStore,
)
from v2.worker_workspace import WorkerWorkspaceManager
from v2.workspace_commit import ATTEMPT_TRAILER


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def ready_worker(root: Path, provider=None):
    admission = admitted_record(root)
    chosen = provider or FakeProvider()
    result = advance_dormant_admitted_worker(
        root=root,
        provider=chosen,
        provider_quota=None,
        local_budget=budget(),
        config=config(admission),
    )
    assert result.disposition is DormantWorkerDisposition.HANDOFF_READY
    return admission, chosen


class NoChangeProvider:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        return "completed without repository changes"


class OutsideScopeProvider:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        (Path(worktree) / "outside.txt").write_text(
            "outside frozen scope\n",
            encoding="utf-8",
        )
        return "attempted out-of-scope work"


class InjectedStateSaveCrash(BaseException):
    pass


class CrashOnSaveStore:
    def __init__(self, root: Path):
        self.inner = DormantHandoffCommitStore.for_root(root)

    def load(self):
        return self.inner.load()

    def save(self, ledger):
        raise InjectedStateSaveCrash("controller died after Git commit before state save")


class DormantLocalCommitTest(unittest.TestCase):
    def test_exact_worker_delta_creates_one_local_attempt_commit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admission, provider = ready_worker(root)
            base = admission.current_main
            manager = WorkerWorkspaceManager(root=root)
            worktree = manager.expected_path(admission.worker_spec)

            first = advance_dormant_handoff_commit(root=root)
            self.assertEqual(first.disposition, DormantCommitDisposition.RECORDED)
            record = first.ledger.record
            self.assertEqual(record.outcome, DormantCommitOutcome.COMMITTED)
            self.assertNotEqual(record.head_sha, base)
            self.assertEqual(
                record.changed_paths,
                ("docs/operations/worker-output.txt",),
            )
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), record.head_sha)
            self.assertEqual(git(root, "rev-parse", "HEAD"), base)
            self.assertEqual(git(worktree, "status", "--porcelain"), "")
            body = git(worktree, "log", "-1", "--format=%B")
            self.assertIn(
                f"{ATTEMPT_TRAILER}: {admission.attempt.attempt_id}",
                body,
            )
            self.assertEqual(provider.calls, 1)

            count_before = git(worktree, "rev-list", "--count", "HEAD")
            second = advance_dormant_handoff_commit(root=root)
            self.assertEqual(
                second.disposition,
                DormantCommitDisposition.ALREADY_RECORDED,
            )
            self.assertEqual(second.ledger, first.ledger)
            self.assertEqual(git(worktree, "rev-list", "--count", "HEAD"), count_before)

            store = DormantHandoffCommitStore.for_root(root)
            restarted = store.load()
            self.assertEqual(restarted, first.ledger)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_no_change_records_terminal_no_change_without_commit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admission, provider = ready_worker(root, provider=NoChangeProvider())
            worktree = WorkerWorkspaceManager(root=root).expected_path(admission.worker_spec)
            before = git(worktree, "rev-parse", "HEAD")
            result = advance_dormant_handoff_commit(root=root)
            self.assertEqual(result.disposition, DormantCommitDisposition.RECORDED)
            self.assertEqual(result.ledger.record.outcome, DormantCommitOutcome.NO_CHANGE)
            self.assertEqual(result.ledger.record.head_sha, admission.current_main)
            self.assertEqual(result.ledger.record.changed_paths, ())
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), before)
            self.assertEqual(provider.calls, 1)

    def test_out_of_scope_delta_is_durably_blocked_and_preserved_uncommitted(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admission, provider = ready_worker(root, provider=OutsideScopeProvider())
            worktree = WorkerWorkspaceManager(root=root).expected_path(admission.worker_spec)
            before = git(worktree, "rev-parse", "HEAD")
            result = advance_dormant_handoff_commit(root=root)
            self.assertEqual(result.disposition, DormantCommitDisposition.RECORDED)
            self.assertEqual(result.ledger.record.outcome, DormantCommitOutcome.BLOCKED)
            self.assertIn("outside frozen scope", result.ledger.record.reason)
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), before)
            self.assertIn("outside.txt", git(worktree, "status", "--porcelain"))
            self.assertEqual(
                (worktree / "outside.txt").read_text(encoding="utf-8"),
                "outside frozen scope\n",
            )
            self.assertEqual(provider.calls, 1)

            again = advance_dormant_handoff_commit(root=root)
            self.assertEqual(
                again.disposition,
                DormantCommitDisposition.ALREADY_RECORDED,
            )
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), before)

    def test_crash_after_git_commit_before_state_save_reconciles_same_logical_commit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admission, provider = ready_worker(root)
            worktree = WorkerWorkspaceManager(root=root).expected_path(admission.worker_spec)
            with self.assertRaises(InjectedStateSaveCrash):
                advance_dormant_handoff_commit(
                    root=root,
                    store=CrashOnSaveStore(root),
                )

            committed_head = git(worktree, "rev-parse", "HEAD")
            self.assertNotEqual(committed_head, admission.current_main)
            self.assertIsNone(DormantHandoffCommitStore.for_root(root).load().record)

            recovered = advance_dormant_handoff_commit(root=root)
            self.assertEqual(recovered.disposition, DormantCommitDisposition.RECORDED)
            record = recovered.ledger.record
            self.assertEqual(record.outcome, DormantCommitOutcome.COMMITTED)
            self.assertEqual(record.head_sha, committed_head)
            self.assertEqual(
                record.changed_paths,
                ("docs/operations/worker-output.txt",),
            )
            self.assertIsNotNone(recovered.workspace)
            self.assertFalse(recovered.workspace.commit_created)
            self.assertEqual(provider.calls, 1)
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), committed_head)

    def test_worker_must_be_handoff_ready(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admitted_record(root)
            result = advance_dormant_handoff_commit(root=root)
            self.assertEqual(
                result.disposition,
                DormantCommitDisposition.WORKER_NOT_READY,
            )
            self.assertIsNone(result.ledger.record)

    def test_worker_identity_drift_blocks_before_workspace_commit(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            admission = admitted_record(root)
            spec = admission.worker_spec
            drifted = FrozenWorkerSpec(
                task_id=spec.task_id,
                authority_key=spec.authority_key,
                task_spec_hash="e" * 64,
                attempt_id=spec.attempt_id,
                lane=spec.lane,
                objective=spec.objective,
                stop_boundary=spec.stop_boundary,
                base_sha=spec.base_sha,
                allowed_paths=spec.allowed_paths,
                protected_paths=spec.protected_paths,
                tier=spec.tier,
                context_text=spec.context_text,
            )
            fake_worktree = root / ".skyforge-platform-v2" / "worktrees" / "drift"
            fake_worktree.mkdir(parents=True)
            run = WorkerRunRecord(
                spec=drifted,
                worktree=str(fake_worktree),
                config=WorkerProviderConfig(
                    tier=drifted.tier,
                    model="fixture",
                    reasoning_effort="low",
                ),
                status=WorkerRunStatus.HANDOFF_READY,
                summary="drifted fixture",
            )
            WorkerRunStore.for_root(root).save(WorkerRunLedger((run,)))
            result = advance_dormant_handoff_commit(root=root)
            self.assertEqual(result.disposition, DormantCommitDisposition.CONFLICT)
            self.assertIsNone(result.ledger.record)

    def test_tampered_commit_ledger_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            ready_worker(root)
            result = advance_dormant_handoff_commit(root=root)
            raw = result.ledger.as_dict()

            bad_head = {
                "schema_version": 1,
                "record": dict(raw["record"]),
            }
            bad_head["record"]["head_sha"] = "0" * 40
            with self.assertRaises(ValueError):
                DormantHandoffCommitLedger.from_mapping(bad_head)

            bad_id = {
                "schema_version": 1,
                "record": dict(raw["record"]),
            }
            bad_id["record"]["record_id"] = "f" * 64
            with self.assertRaises(ValueError):
                DormantHandoffCommitLedger.from_mapping(bad_id)


class DormantCommitIsolationTest(unittest.TestCase):
    def test_hosted_runtime_has_no_local_commit_path(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("dormant_handoff_commit", source)
        self.assertNotIn("advance_dormant_handoff_commit", source)
        self.assertNotIn("workspace_commit", source)

    def test_local_commit_service_has_no_remote_effect_or_push_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/dormant_handoff_commit.py").read_text(encoding="utf-8")
        for token in (
            "ordinary_service",
            "OrdinaryEffect",
            "GhGit",
            "remote_factory",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
