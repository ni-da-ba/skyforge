from __future__ import annotations

from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.core import ControllerState, ManagedPRObservation, ManagedPRState
from v2.domain import CIState, PRClass
from v2.effects import RemoteEffectObservation, RemoteEffectPresence
from v2.ordinary_effect_executor import OrdinaryRemoteUnavailable
from v2.ordinary_effects import OrdinaryEffectStore
from v2.ordinary_remote import OrdinaryEffectBinding
from v2.ordinary_service import (
    OrdinaryServiceDisposition,
    PreparedWorkerTask,
    advance_managed_merge,
    advance_prepared_handoff,
)
from v2.workspace_commit import WorkspaceCommitAdapter, WorkspaceCommitScope


SPEC_HASH = "a" * 64
ATTEMPT = "b" * 64
BRANCH = "codex/r5c2-prepared-worker"
REPO = "ni-da-ba/skyforge"


class FakeBoundRemote:
    def __init__(self, factory, binding):
        self.factory = factory
        self.binding = binding

    def observe(self, identity):
        if identity != self.binding.identity:
            raise ValueError("identity mismatch")
        remote = self.factory.remote.get(identity.effect_id)
        if remote is None:
            return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
        if remote == "CONFLICT":
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
        return RemoteEffectObservation(
            RemoteEffectPresence.PRESENT_EXACT,
            remote_identity=remote,
        )

    def execute(self, identity):
        if identity != self.binding.identity:
            raise ValueError("identity mismatch")
        self.factory.execute_calls[identity.effect_id] = (
            self.factory.execute_calls.get(identity.effect_id, 0) + 1
        )
        kind = identity.kind.value
        scope = self.binding.scope
        if kind == "PUSH_BRANCH":
            remote = f"branch:{scope.branch}@{scope.expected_head_sha}"
        elif kind == "CREATE_PR":
            remote = f"pr:42:{self.factory.create_state}@{scope.expected_head_sha}"
        elif kind == "POST_COMMENT":
            remote = "comment:1234"
        elif kind == "MERGE_PR":
            remote = f"merge:pr:{self.binding.pr_number}@{scope.expected_head_sha}"
        else:
            raise AssertionError(kind)
        self.factory.remote[identity.effect_id] = remote
        return remote


class FakeRemoteFactory:
    def __init__(self):
        self.remote = {}
        self.execute_calls = {}
        self.create_state = "OPEN"
        self.bindings = []

    def __call__(self, binding: OrdinaryEffectBinding):
        self.bindings.append(binding)
        return FakeBoundRemote(self, binding)

    @property
    def total_executes(self):
        return sum(self.execute_calls.values())


class OrdinaryServiceIntegrationTest(unittest.TestCase):
    def git(self, root: Path, *args: str) -> str:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=True,
            text=True,
            capture_output=True,
        )
        return result.stdout.strip()

    def make_worker(self, root: Path, *, changed: bool = True):
        self.git(root, "init", "-b", "main")
        self.git(root, "config", "user.email", "test@example.invalid")
        self.git(root, "config", "user.name", "Skyforge R5C2 Test")
        (root / "allowed.txt").write_text("base\n", encoding="utf-8")
        self.git(root, "add", "allowed.txt")
        self.git(root, "commit", "-m", "base")
        start = self.git(root, "rev-parse", "HEAD")
        self.git(root, "checkout", "-b", BRANCH)
        if changed:
            (root / "allowed.txt").write_text("changed\n", encoding="utf-8")

        workspace = WorkspaceCommitAdapter(
            worktree=root,
            scope=WorkspaceCommitScope(
                attempt_id=ATTEMPT,
                branch=BRANCH,
                start_head=start,
                allowed_paths=("allowed.txt",),
            ),
        )
        task = PreparedWorkerTask(
            task_id="r5c2-test",
            authority_key="issue:854",
            task_spec_hash=SPEC_HASH,
            attempt_id=ATTEMPT,
            repo=REPO,
            base_sha=start,
            branch=BRANCH,
            lane="Implementation",
            objective="prepared worker handoff",
            pr_title="CODEX Implementation: prepared worker handoff",
            pr_body="R5C2 bounded integration fixture.",
            issue_number=854,
            comment_body="Prepared worker handoff is ready.",
            auto_merge_eligible=True,
        )
        return start, workspace, task

    def managed_state(self, handoff):
        return ControllerState(
            managed=(
                ManagedPRState(
                    lane=handoff.lane,
                    pr_number=handoff.pr_number,
                    branch=handoff.scope.branch,
                    authority_key=handoff.authority_key,
                    expected_head=handoff.scope.expected_head_sha,
                    auto_merge_eligible=handoff.auto_merge_eligible,
                    changed_paths=handoff.changed_paths,
                ),
            )
        )

    def exact_observation(self, handoff, **overrides):
        values = dict(
            lane=handoff.lane,
            pr_number=handoff.pr_number,
            active_pr=True,
            base_branch="main",
            head_branch=handoff.scope.branch,
            current_head_sha=handoff.scope.expected_head_sha,
            evidence_sha=handoff.scope.expected_head_sha,
            reviewed_sha=handoff.scope.expected_head_sha,
            task_spec_hash=handoff.task_spec_hash,
            accepted_task_spec_hash=handoff.task_spec_hash,
            ci_state=CIState.PASS,
            pr_class=PRClass.DELIVERY,
            human_gate_pending=False,
            review_required=False,
        )
        values.update(overrides)
        return ManagedPRObservation(**values)

    def test_prepared_handoff_completes_and_restart_is_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()

            first = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(first.disposition, OrdinaryServiceDisposition.COMPLETE)
            self.assertIsNotNone(first.handoff)
            self.assertEqual(first.handoff.pr_number, 42)
            self.assertEqual(first.handoff.changed_paths, ("allowed.txt",))
            self.assertEqual(remote.total_executes, 3)

            second = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(second.disposition, OrdinaryServiceDisposition.COMPLETE)
            self.assertEqual(second.handoff.digest, first.handoff.digest)
            self.assertEqual(remote.total_executes, 3)
            self.assertEqual(store.load().records[-1].status.value, "COMPLETE")

    def test_no_change_never_creates_remote_effect(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root, changed=False)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            result = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(result.disposition, OrdinaryServiceDisposition.NOT_ELIGIBLE)
            self.assertEqual(result.stage, "WORKSPACE_NO_CHANGE")
            self.assertEqual(remote.total_executes, 0)
            self.assertEqual(store.load().records, ())

    def test_closed_pr_after_create_is_not_promoted_to_managed_handoff(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            remote.create_state = "CLOSED"

            result = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(result.disposition, OrdinaryServiceDisposition.BLOCKED)
            self.assertEqual(result.stage, "CREATE_PR_REOBSERVE")
            self.assertIn("open PR", result.reason)

    def test_exact_managed_acceptance_allows_one_merge(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            handoff_result = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            )
            handoff = handoff_result.handoff
            self.assertIsNotNone(handoff)

            state = self.managed_state(handoff)
            observation = self.exact_observation(handoff)
            merged = advance_managed_merge(
                state=state,
                observation=observation,
                handoff=handoff,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(merged.disposition, OrdinaryServiceDisposition.COMPLETE)
            self.assertEqual(merged.transition.kind.value, "MERGE_ELIGIBLE")
            after_first = remote.total_executes

            merged_again = advance_managed_merge(
                state=state,
                observation=observation,
                handoff=handoff,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(merged_again.disposition, OrdinaryServiceDisposition.COMPLETE)
            self.assertEqual(remote.total_executes, after_first)

    def test_human_gate_never_calls_merge_adapter(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            handoff = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            ).handoff
            self.assertIsNotNone(handoff)
            before = remote.total_executes

            result = advance_managed_merge(
                state=self.managed_state(handoff),
                observation=self.exact_observation(
                    handoff,
                    human_gate_pending=True,
                    pr_class=PRClass.HUMAN_GATE,
                ),
                handoff=handoff,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(result.disposition, OrdinaryServiceDisposition.NOT_ELIGIBLE)
            self.assertEqual(result.transition.kind.value, "HUMAN_GATE")
            self.assertEqual(remote.total_executes, before)

    def test_head_or_task_evidence_drift_never_calls_merge(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            handoff = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            ).handoff
            self.assertIsNotNone(handoff)
            before = remote.total_executes

            for observation in (
                self.exact_observation(handoff, evidence_sha="d" * 40),
                self.exact_observation(handoff, accepted_task_spec_hash="e" * 64),
            ):
                with self.subTest(observation=observation):
                    result = advance_managed_merge(
                        state=self.managed_state(handoff),
                        observation=observation,
                        handoff=handoff,
                        store=store,
                        remote_factory=remote,
                    )
                    self.assertNotEqual(
                        result.disposition,
                        OrdinaryServiceDisposition.COMPLETE,
                    )
            self.assertEqual(remote.total_executes, before)

    def test_managed_identity_mismatch_blocks_before_policy_or_merge(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, workspace, task = self.make_worker(root)
            store = OrdinaryEffectStore.for_root(root)
            remote = FakeRemoteFactory()
            handoff = advance_prepared_handoff(
                task=task,
                workspace=workspace,
                store=store,
                remote_factory=remote,
            ).handoff
            self.assertIsNotNone(handoff)
            bad_state = ControllerState(
                managed=(
                    ManagedPRState(
                        lane=handoff.lane,
                        pr_number=handoff.pr_number,
                        branch=handoff.scope.branch,
                        authority_key="issue:wrong",
                        expected_head=handoff.scope.expected_head_sha,
                        auto_merge_eligible=True,
                    ),
                )
            )
            before = remote.total_executes
            result = advance_managed_merge(
                state=bad_state,
                observation=self.exact_observation(handoff),
                handoff=handoff,
                store=store,
                remote_factory=remote,
            )
            self.assertEqual(result.disposition, OrdinaryServiceDisposition.BLOCKED)
            self.assertEqual(remote.total_executes, before)

    def test_hosted_runtime_does_not_import_r5c2_service(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("ordinary_service", source)


if __name__ == "__main__":
    unittest.main()
