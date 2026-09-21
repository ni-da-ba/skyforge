from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.effects import EffectStatus
from v2.managed_pr_lifecycle import (
    ManagedLifecycleDisposition,
    ManagedLifecycleEffectAdapter,
    ManagedLifecycleMutationValidator,
    advance_managed_pr_lifecycle,
)
from v2.ordinary_effect_executor import (
    OrdinaryEffectExecutionDisposition,
    advance_remote_effect,
)
from v2.ordinary_effects import OrdinaryEffectStore, OrdinaryMutationScope
from v2.ordinary_service import ManagedOrdinaryHandoff


HEAD = "a" * 40
BASE = "b" * 40
ATTEMPT = "c" * 64
SPEC = "d" * 64


def handoff(*, auto=True):
    return ManagedOrdinaryHandoff(
        task_id="github-issue-874-comment-1",
        authority_key="github-task-authority:" + "e" * 64,
        task_spec_hash=SPEC,
        lane="Implementation",
        scope=OrdinaryMutationScope(
            attempt_id=ATTEMPT,
            repo="ni-da-ba/skyforge",
            base_sha=BASE,
            branch="codex/implementation-r5c12",
            expected_head_sha=HEAD,
            pr_title="R5C12 exact lifecycle",
            pr_body="Frozen lifecycle body.",
            issue_number=874,
        ),
        pr_number=904,
        changed_paths=("docs/operations/r5c12.md",),
        auto_merge_eligible=auto,
    )


class FakeResult:
    def __init__(self, stdout=""):
        self.stdout = stdout
        self.stderr = ""


class FakeGitHub:
    def __init__(self):
        self.state = "OPEN"
        self.merged_at = None
        self.draft = True
        self.merge_state = "CLEAN"
        self.review = ""
        self.checks = [
            {"name": "build", "status": "COMPLETED", "conclusion": "SUCCESS"}
        ]
        self.head = HEAD
        self.branch = "codex/implementation-r5c12"
        self.base = "main"
        self.title = "R5C12 exact lifecycle"
        self.body = "Frozen lifecycle body."
        self.paths = ["docs/operations/r5c12.md"]
        self.ready_calls = 0
        self.merge_calls = 0
        self.view_calls = 0
        self.commands = []
        self.flip_review_after_views = None
        self.current_base = BASE
        self.fail_base_truth = False

    def _pr(self):
        self.view_calls += 1
        if (
            self.flip_review_after_views is not None
            and self.view_calls > self.flip_review_after_views
        ):
            self.review = "REVIEW_REQUIRED"
        return {
            "number": 904,
            "state": self.state,
            "mergedAt": self.merged_at,
            "isDraft": self.draft,
            "mergeStateStatus": self.merge_state,
            "statusCheckRollup": self.checks,
            "headRefName": self.branch,
            "headRefOid": self.head,
            "baseRefName": self.base,
            "title": self.title,
            "body": self.body,
            "reviewDecision": self.review,
        }

    def __call__(self, args, **kwargs):
        command = tuple(args)
        self.commands.append(command)
        if command == (
            "gh", "api", "repos/ni-da-ba/skyforge/commits/main", "--jq", ".sha"
        ):
            if self.fail_base_truth:
                raise subprocess.CalledProcessError(1, args, stderr="network unavailable")
            return FakeResult(self.current_base + "\n")
        if args[:3] == ["gh", "pr", "view"]:
            return FakeResult(json.dumps(self._pr()))
        if args[:3] == ["gh", "pr", "diff"]:
            return FakeResult("\n".join(self.paths) + "\n")
        if args[:3] == ["gh", "pr", "ready"]:
            self.ready_calls += 1
            if self.state != "OPEN" or not self.draft:
                raise subprocess.CalledProcessError(
                    1, args, stderr="not an open draft"
                )
            self.draft = False
            return FakeResult("")
        if args[:3] == ["gh", "pr", "merge"]:
            self.merge_calls += 1
            if self.state != "OPEN" or self.draft or self.head != HEAD:
                raise subprocess.CalledProcessError(
                    1, args, stderr="merge precondition failed"
                )
            self.state = "MERGED"
            self.merged_at = "2026-09-18T04:10:00Z"
            return FakeResult("")
        raise AssertionError(command)


class ManagedLifecycleTest(unittest.TestCase):
    def advance(self, root, remote, **kwargs):
        return advance_managed_pr_lifecycle(
            root=root,
            handoff=handoff(),
            store=OrdinaryEffectStore.for_root(root),
            runner=remote,
            **kwargs,
        )

    def test_happy_path_ready_then_exact_merge_and_restart_is_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            first = self.advance(root, remote)

            self.assertEqual(first.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 1)
            self.assertEqual(remote.state, "MERGED")

            ledger = OrdinaryEffectStore.for_root(root).load()
            ready = ledger.get(handoff().scope.ready_identity(904))
            merge = ledger.get(handoff().scope.merge_identity(904))
            self.assertEqual(ready.status, EffectStatus.COMPLETE)
            self.assertEqual(merge.status, EffectStatus.COMPLETE)

            second = self.advance(root, remote)
            self.assertEqual(second.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 1)

    def test_crash_after_ready_reconciles_without_second_ready(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            with self.assertRaisesRegex(RuntimeError, "injected crash"):
                self.advance(root, remote, crash_after_ready_execute=True)

            self.assertFalse(remote.draft)
            self.assertEqual(remote.ready_calls, 1)
            store = OrdinaryEffectStore.for_root(root)
            pending = store.load().get(handoff().scope.ready_identity(904))
            self.assertEqual(pending.status, EffectStatus.PENDING)

            restarted = self.advance(root, remote)
            self.assertEqual(restarted.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 1)
            self.assertEqual(
                store.load().get(handoff().scope.ready_identity(904)).status,
                EffectStatus.COMPLETE,
            )

    def test_crash_after_merge_reconciles_without_second_merge(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            with self.assertRaisesRegex(RuntimeError, "injected crash"):
                self.advance(root, remote, crash_after_merge_execute=True)

            self.assertEqual(remote.state, "MERGED")
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 1)
            store = OrdinaryEffectStore.for_root(root)
            self.assertEqual(
                store.load().get(handoff().scope.merge_identity(904)).status,
                EffectStatus.PENDING,
            )

            restarted = self.advance(root, remote)
            self.assertEqual(restarted.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 1)
            self.assertEqual(
                store.load().get(handoff().scope.merge_identity(904)).status,
                EffectStatus.COMPLETE,
            )

    def test_exact_external_merge_is_reconciled_without_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            remote.draft = False
            remote.state = "MERGED"
            remote.merged_at = "2026-09-18T04:10:00Z"
            remote.current_base = "f" * 40

            result = self.advance(root, remote)
            self.assertEqual(result.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 0)
            self.assertEqual(remote.merge_calls, 0)
            self.assertNotIn(
                ("gh", "api", "repos/ni-da-ba/skyforge/commits/main", "--jq", ".sha"),
                remote.commands,
            )
            self.assertEqual(
                OrdinaryEffectStore.for_root(root)
                .load()
                .get(handoff().scope.merge_identity(904))
                .status,
                EffectStatus.COMPLETE,
            )

    def test_open_managed_pr_with_advanced_frozen_base_terminalizes_without_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            remote.current_base = "f" * 40

            result = self.advance(root, remote)

            self.assertEqual(
                result.disposition,
                ManagedLifecycleDisposition.STALE_MANAGED_BASE,
            )
            self.assertEqual(result.current_base_sha, "f" * 40)
            self.assertEqual(remote.ready_calls, 0)
            self.assertEqual(remote.merge_calls, 0)
            self.assertEqual(OrdinaryEffectStore.for_root(root).load().records, ())

    def test_managed_pr_base_truth_ambiguity_blocks_without_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            remote.fail_base_truth = True

            result = self.advance(root, remote)

            self.assertEqual(result.disposition, ManagedLifecycleDisposition.BLOCKED)
            self.assertIn("base truth unavailable", result.reason)
            self.assertEqual(remote.ready_calls, 0)
            self.assertEqual(remote.merge_calls, 0)
            self.assertEqual(OrdinaryEffectStore.for_root(root).load().records, ())

    def test_human_gate_pending_ci_failure_and_drift_do_not_mutate(self):
        cases = []

        human = FakeGitHub()
        human.review = "REVIEW_REQUIRED"
        cases.append(("review", human, handoff()))

        pending = FakeGitHub()
        pending.checks = [
            {"name": "build", "status": "IN_PROGRESS", "conclusion": ""}
        ]
        cases.append(("pending", pending, handoff()))

        failed = FakeGitHub()
        failed.checks = [
            {"name": "build", "status": "COMPLETED", "conclusion": "FAILURE"}
        ]
        cases.append(("failed", failed, handoff()))

        drift = FakeGitHub()
        drift.head = "f" * 40
        cases.append(("drift", drift, handoff()))

        non_auto = FakeGitHub()
        cases.append(("non_auto", non_auto, handoff(auto=False)))

        for name, remote, value in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                result = advance_managed_pr_lifecycle(
                    root=root,
                    handoff=value,
                    store=OrdinaryEffectStore.for_root(root),
                    runner=remote,
                )
                self.assertNotEqual(result.disposition, ManagedLifecycleDisposition.COMPLETE)
                self.assertEqual(remote.ready_calls, 0)
                self.assertEqual(remote.merge_calls, 0)

    def test_post_ready_recheck_can_stop_merge(self):
        class FlipAfterReady(FakeGitHub):
            def __call__(self, args, **kwargs):
                result = super().__call__(args, **kwargs)
                if args[:3] == ["gh", "pr", "ready"]:
                    self.review = "REVIEW_REQUIRED"
                return result

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FlipAfterReady()
            result = self.advance(root, remote)
            self.assertNotEqual(result.disposition, ManagedLifecycleDisposition.COMPLETE)
            self.assertEqual(remote.ready_calls, 1)
            self.assertEqual(remote.merge_calls, 0)

    def test_merge_adapter_rechecks_base_inside_mutation_boundary(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            remote.draft = False
            remote.current_base = "f" * 40
            identity = handoff().scope.merge_identity(904)
            result = advance_remote_effect(
                store=OrdinaryEffectStore.for_root(root),
                identity=identity,
                adapter=ManagedLifecycleEffectAdapter(
                    root=root,
                    handoff=handoff(),
                    identity=identity,
                    runner=remote,
                ),
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.STALE_BASE,
            )
            self.assertEqual(remote.merge_calls, 0)
            self.assertEqual(
                OrdinaryEffectStore.for_root(root).load().get(identity).status,
                EffectStatus.ABANDONED,
            )

    def test_merge_adapter_rechecks_full_truth_inside_execute_boundary(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            remote = FakeGitHub()
            remote.draft = False
            # advance_remote_effect calls adapter.observe twice before execute.
            # Each observe performs one PR view. The third view is execute's fresh guard.
            remote.flip_review_after_views = 2
            identity = handoff().scope.merge_identity(904)
            result = advance_remote_effect(
                store=OrdinaryEffectStore.for_root(root),
                identity=identity,
                adapter=ManagedLifecycleEffectAdapter(
                    root=root,
                    handoff=handoff(),
                    identity=identity,
                    runner=remote,
                ),
            )
            self.assertEqual(
                result.disposition,
                OrdinaryEffectExecutionDisposition.BLOCKED,
            )
            self.assertEqual(remote.merge_calls, 0)


class MutationAllowlistTest(unittest.TestCase):
    def test_only_exact_ready_and_merge_commands_are_allowed(self):
        validator = ManagedLifecycleMutationValidator(handoff())
        self.assertEqual(
            validator.validate(validator.ready_command()),
            validator.ready_command(),
        )
        self.assertEqual(
            validator.validate(validator.merge_command()),
            validator.merge_command(),
        )
        for command in (
            ["gh", "pr", "ready", "905", "--repo", "ni-da-ba/skyforge"],
            ["gh", "pr", "merge", "904", "--repo", "ni-da-ba/skyforge", "--merge"],
            ["gh", "issue", "comment", "874", "--body", "x"],
            ["git", "push", "origin", "main"],
        ):
            with self.subTest(command=command), self.assertRaises(ValueError):
                validator.validate(command)


if __name__ == "__main__":
    unittest.main()
