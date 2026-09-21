from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.concurrency_claims import ConcurrencyClaimDisposition, ConcurrencyClaimStore
from v2.decision import (
    ClassifierDecision,
    DecisionFreshnessObservation,
    PendingDecisionRecord,
)
from v2.dispatch_admission import RepositoryTaskAuthority, admit_dispatch
from v2.dormant_worker import (
    DormantWorkerDisposition,
    advance_dormant_admitted_worker,
)
from v2.hosted_admission import (
    HostedAdmissionLedger,
    HostedAdmissionOutcome,
    HostedAdmissionRecord,
    HostedAdmissionStore,
)
from v2.identity import canonical_digest
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision
from v2.worker_provider import (
    WorkerProviderConfig,
    WorkerProviderError,
    WorkerRunStatus,
    WorkerRunStore,
    WorkerTier,
)
from v2.worker_workspace import WorkerWorkspaceManager


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def make_repo(root: Path) -> str:
    git(root, "init", "-b", "main")
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge R5C18 Test")
    (root / ".gitignore").write_text(".skyforge-platform-v2/\n", encoding="utf-8")
    target = root / "docs" / "operations"
    target.mkdir(parents=True)
    (target / "base.txt").write_text("base\n", encoding="utf-8")
    git(root, "add", ".gitignore", "docs/operations/base.txt")
    git(root, "commit", "-m", "base")
    return git(root, "rev-parse", "HEAD")


def budget(*, used=0, limit=10):
    return LocalBudgetObservation(calls_used=used, daily_limit=limit)


def admitted_record(root: Path) -> HostedAdmissionRecord:
    base = make_repo(root)
    authority = RepositoryTaskAuthority(
        task_id="r5c18-fixture",
        authority_key="issue:900",
        issue_numbers=(900,),
        lane="Implementation",
        objective="create bounded worker artifact",
        stop_boundary="leave local uncommitted work and stop",
        allowed_paths=("docs/operations/**",),
        protected_paths=("scripts/orchestrator/**",),
        context_text="fixture authority",
        auto_merge_eligible=False,
    )
    decision = ClassifierDecision.from_legacy_mapping(
        {
            "decision": "DISPATCH",
            "lane": authority.lane,
            "objective": authority.objective,
            "stop_boundary": authority.stop_boundary,
            "worker_tier": "LUNA",
            "allowed_paths": ["docs/operations/**"],
            "reason": "exact fixture dispatch",
        }
    )
    pending = PendingDecisionRecord.from_legacy_mapping(
        {
            "decision": decision.as_dict(),
            "event_keys": ["sha256:" + "a" * 64],
            "authority_event_keys": ["sha256:" + "a" * 64],
            "ordinary_event_keys": [],
            "task_issue_numbers": [900],
            "snapshot_main": base,
        }
    )
    admission = admit_dispatch(
        authority=authority,
        record=pending,
        freshness=DecisionFreshnessObservation(current_main=base),
        current_main=base,
        active_external_claims=(),
        provider_quota=None,
        local_budget=budget(),
        attempt_number=1,
    )
    assert admission.disposition.value == "ADMIT"
    record = HostedAdmissionRecord(
        plan_id="1" * 64,
        event_id="sha256:" + "a" * 64,
        issue_number=900,
        classifier_request_id="2" * 64,
        classifier_run_id="3" * 64,
        classifier_decision_digest=canonical_digest(decision.as_dict()),
        hydration_digest="4" * 64,
        authority_digest=admission.authority_digest,
        current_main=base,
        attempt_number=1,
        outcome=HostedAdmissionOutcome.ADMITTED,
        reason=admission.reason,
        pending_decision_digest=admission.pending_decision_digest,
        quota_digest=admission.quota.digest,
        consume_attempt=admission.consume_attempt,
        admission_digest=admission.digest,
        frozen_task=admission.frozen_task,
        attempt=admission.attempt,
        worker_spec=admission.worker_spec,
    )
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger(record))
    claim = ConcurrencyClaimStore.for_root(root).acquire(record.worker_spec)
    assert claim.decision.disposition is ConcurrencyClaimDisposition.ADMIT
    return record


def blocked_record(root: Path) -> HostedAdmissionRecord:
    base = make_repo(root)
    record = HostedAdmissionRecord(
        plan_id="1" * 64,
        event_id="sha256:" + "a" * 64,
        issue_number=900,
        classifier_request_id="2" * 64,
        classifier_run_id="3" * 64,
        classifier_decision_digest="4" * 64,
        hydration_digest="5" * 64,
        authority_digest="6" * 64,
        current_main=base,
        attempt_number=1,
        outcome=HostedAdmissionOutcome.BLOCKED,
        reason="fixture blocked",
    )
    HostedAdmissionStore.for_root(root).save(HostedAdmissionLedger(record))
    return record


class FakeProvider:
    def __init__(self, *, summary="bounded local work complete", error=None):
        self.calls = 0
        self.summary = summary
        self.error = error

    def run(self, *, spec, worktree, config):
        self.calls += 1
        if self.error is not None:
            raise self.error
        target = Path(worktree) / "docs" / "operations" / "worker-output.txt"
        target.write_text("worker output\n", encoding="utf-8")
        return self.summary


class InjectedCrash(BaseException):
    pass


class CrashProvider:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        target = Path(worktree) / "docs" / "operations" / "partial.txt"
        target.write_text("partial durable work\n", encoding="utf-8")
        raise InjectedCrash("controller died after provider edited workspace")


def config(record: HostedAdmissionRecord, *, model="fixture-model"):
    return WorkerProviderConfig(
        tier=record.worker_spec.tier,
        model=model,
        reasoning_effort="low",
    )


class DormantWorkerExecutionTest(unittest.TestCase):
    def test_exact_admitted_worker_runs_once_in_isolated_worktree(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            record = admitted_record(root)
            provider = FakeProvider()
            cfg = config(record)

            first = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(first.disposition, DormantWorkerDisposition.HANDOFF_READY)
            self.assertEqual(provider.calls, 1)

            manager = WorkerWorkspaceManager(root=root)
            worktree = manager.expected_path(record.worker_spec)
            self.assertTrue(worktree.is_dir())
            self.assertNotEqual(worktree, root.resolve())
            self.assertEqual(git(worktree, "branch", "--show-current"), record.worker_spec.branch)
            self.assertEqual(git(worktree, "rev-parse", "HEAD"), record.current_main)
            self.assertEqual(
                (worktree / "docs/operations/worker-output.txt").read_text(encoding="utf-8"),
                "worker output\n",
            )
            self.assertFalse((root / "docs/operations/worker-output.txt").exists())
            self.assertTrue(git(worktree, "status", "--porcelain"))

            durable = WorkerRunStore.for_root(root).load().find_attempt(
                record.attempt.attempt_id
            )
            self.assertEqual(durable.status, WorkerRunStatus.HANDOFF_READY)
            self.assertEqual(Path(durable.worktree).resolve(), worktree)

            again = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(again.disposition, DormantWorkerDisposition.ALREADY_READY)
            self.assertEqual(provider.calls, 1)
            self.assertEqual(
                (worktree / "docs/operations/worker-output.txt").read_text(encoding="utf-8"),
                "worker output\n",
            )

    def test_quota_block_occurs_before_worktree_or_provider(self):
        cases = [
            (
                ProviderQuotaDecision(
                    authoritative=True,
                    allowed=False,
                    block_kind="quota",
                    retry_after_seconds=900,
                    reason="provider blocks worker",
                ),
                budget(),
            ),
            (None, budget(used=10, limit=10)),
        ]
        for provider_quota, local in cases:
            with self.subTest(provider_quota=provider_quota), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                record = admitted_record(root)
                provider = FakeProvider()
                manager = WorkerWorkspaceManager(root=root)
                result = advance_dormant_admitted_worker(
                    root=root,
                    provider=provider,
                    provider_quota=provider_quota,
                    local_budget=local,
                    config=config(record),
                )
                self.assertEqual(result.disposition, DormantWorkerDisposition.QUOTA_BLOCKED)
                self.assertEqual(provider.calls, 0)
                self.assertFalse(manager.expected_path(record.worker_spec).exists())
                self.assertEqual(
                    len(WorkerRunStore.for_root(root).load().records),
                    0,
                )

    def test_crash_preserves_dirty_worktree_and_never_recalls_provider(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            record = admitted_record(root)
            provider = CrashProvider()
            cfg = config(record)
            with self.assertRaises(InjectedCrash):
                advance_dormant_admitted_worker(
                    root=root,
                    provider=provider,
                    provider_quota=None,
                    local_budget=budget(),
                    config=cfg,
                )
            self.assertEqual(provider.calls, 1)

            manager = WorkerWorkspaceManager(root=root)
            worktree = manager.expected_path(record.worker_spec)
            partial = worktree / "docs/operations/partial.txt"
            self.assertEqual(partial.read_text(encoding="utf-8"), "partial durable work\n")
            running = WorkerRunStore.for_root(root).load().find_attempt(
                record.attempt.attempt_id
            )
            self.assertEqual(running.status, WorkerRunStatus.RUNNING)

            restarted = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(
                restarted.disposition,
                DormantWorkerDisposition.RECOVERY_REQUIRED,
            )
            self.assertEqual(provider.calls, 1)
            self.assertEqual(partial.read_text(encoding="utf-8"), "partial durable work\n")
            self.assertEqual(
                WorkerRunStore.for_root(root)
                .load()
                .find_attempt(record.attempt.attempt_id)
                .status,
                WorkerRunStatus.INTERRUPTED,
            )

            third = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(third.disposition, DormantWorkerDisposition.RECOVERY_REQUIRED)
            self.assertEqual(provider.calls, 1)

    def test_provider_failure_is_durable_and_not_retried(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            record = admitted_record(root)
            provider = FakeProvider(
                error=WorkerProviderError("provider_capacity", 900, "capacity")
            )
            cfg = config(record)
            first = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(first.disposition, DormantWorkerDisposition.FAILED)
            self.assertEqual(provider.calls, 1)
            second = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(second.disposition, DormantWorkerDisposition.FAILED)
            self.assertEqual(provider.calls, 1)

    def test_provider_config_drift_fails_closed_without_second_call(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            record = admitted_record(root)
            provider = FakeProvider()
            first = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=config(record, model="model-a"),
            )
            self.assertEqual(first.disposition, DormantWorkerDisposition.HANDOFF_READY)
            drift = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=config(record, model="model-b"),
            )
            self.assertEqual(drift.disposition, DormantWorkerDisposition.CONFLICT)
            self.assertEqual(provider.calls, 1)

    def test_recovery_rejects_branch_or_head_drift_without_provider(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            record = admitted_record(root)
            provider = FakeProvider()
            cfg = config(record)
            first = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=cfg,
            )
            self.assertEqual(first.disposition, DormantWorkerDisposition.HANDOFF_READY)
            worktree = WorkerWorkspaceManager(root=root).expected_path(record.worker_spec)
            git(worktree, "checkout", "--detach")
            with self.assertRaisesRegex(RuntimeError, "branch identity drifted"):
                advance_dormant_admitted_worker(
                    root=root,
                    provider=provider,
                    provider_quota=None,
                    local_budget=budget(),
                    config=cfg,
                )
            self.assertEqual(provider.calls, 1)


class AdmissionBoundaryTest(unittest.TestCase):
    def test_no_admission_and_blocked_admission_never_create_worker(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            provider = FakeProvider()
            result = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=WorkerProviderConfig(WorkerTier.LUNA, "fixture", "low"),
            )
            self.assertEqual(result.disposition, DormantWorkerDisposition.NO_ADMISSION)
            self.assertEqual(provider.calls, 0)

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            blocked_record(root)
            provider = FakeProvider()
            result = advance_dormant_admitted_worker(
                root=root,
                provider=provider,
                provider_quota=None,
                local_budget=budget(),
                config=WorkerProviderConfig(WorkerTier.LUNA, "fixture", "low"),
            )
            self.assertEqual(result.disposition, DormantWorkerDisposition.NOT_ADMITTED)
            self.assertEqual(provider.calls, 0)
            self.assertEqual(len(WorkerRunStore.for_root(root).load().records), 0)


class CapabilityIsolationTest(unittest.TestCase):
    def test_hosted_runtime_has_no_dormant_worker_execution_path(self):
        root = Path(__file__).resolve().parent
        hosted = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("dormant_worker", hosted)
        self.assertNotIn("advance_dormant_admitted_worker", hosted)
        self.assertNotIn("worker_provider", hosted)
        self.assertNotIn("worker_workspace", hosted)

    def test_dormant_worker_has_no_handoff_commit_or_remote_effect_surface(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/dormant_worker.py").read_text(encoding="utf-8")
        for token in (
            "WorkspaceCommitAdapter",
            "advance_prepared_handoff",
            "OrdinaryEffect",
            "GhGit",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
