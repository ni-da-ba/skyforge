from __future__ import annotations

import unittest

from v2.core import ManagedPRState
from v2.decision import SourcePRState
from v2.rebase import (
    ManagedMergeState,
    ManagedRebaseObservation,
    RebaseDisposition,
    RebaseResultDisposition,
    classify_managed_rebase,
    classify_rebase_result,
)


def managed() -> ManagedPRState:
    return ManagedPRState(
        lane="Implementation",
        pr_number=600,
        branch="codex/implementation-task-492",
        expected_head="a" * 40,
        authority_key="task:492",
        auto_merge_eligible=True,
        changed_paths=("skyforge-neoforge-1211/src/main/java/Example.java",),
    )


def observed(merge_state: ManagedMergeState, **overrides) -> ManagedRebaseObservation:
    values = {
        "remote_pr_state": SourcePRState.OPEN,
        "base_branch": "main",
        "head_branch": "codex/implementation-task-492",
        "head_sha": "a" * 40,
        "merge_state": merge_state,
    }
    values.update(overrides)
    return ManagedRebaseObservation(**values)


class ManagedRebasePreflightParityTest(unittest.TestCase):
    # Mirrors test_pending_worker_prevents_managed_base_mutation.
    def test_pending_worker_blocks_base_mutation(self) -> None:
        decision = classify_managed_rebase(
            managed(),
            observed(ManagedMergeState.BEHIND),
            pending_worker=True,
        )
        self.assertEqual(
            decision.disposition,
            RebaseDisposition.BLOCK_PENDING_WORKER,
        )
        self.assertIsNone(decision.plan)

    # Mirrors test_clean_managed_pr_needs_no_rebase.
    def test_clean_exact_managed_pr_is_noop(self) -> None:
        decision = classify_managed_rebase(
            managed(),
            observed(ManagedMergeState.CLEAN),
            pending_worker=False,
        )
        self.assertEqual(decision.disposition, RebaseDisposition.NOOP_CLEAN)

    # Mirrors the bounded rebase setup in test_clean_rebase_updates_expected_head_with_force_lease.
    def test_behind_exact_managed_pr_yields_force_lease_plan(self) -> None:
        decision = classify_managed_rebase(
            managed(),
            observed(ManagedMergeState.BEHIND),
            pending_worker=False,
        )
        self.assertEqual(decision.disposition, RebaseDisposition.REBASE_REQUIRED)
        self.assertIsNotNone(decision.plan)
        assert decision.plan is not None
        self.assertEqual(decision.plan.expected_old_head, "a" * 40)
        self.assertEqual(
            decision.plan.force_with_lease,
            f"refs/heads/{managed().branch}:{'a' * 40}",
        )

    def test_dirty_exact_managed_pr_also_requires_rebase(self) -> None:
        decision = classify_managed_rebase(
            managed(),
            observed(ManagedMergeState.DIRTY),
            pending_worker=False,
        )
        self.assertEqual(decision.disposition, RebaseDisposition.REBASE_REQUIRED)

    def test_identity_drift_fails_closed(self) -> None:
        cases = [
            observed(ManagedMergeState.BEHIND, base_branch="release"),
            observed(ManagedMergeState.BEHIND, head_branch="other"),
            observed(ManagedMergeState.BEHIND, head_sha="b" * 40),
            observed(ManagedMergeState.BEHIND, remote_pr_state=SourcePRState.CLOSED),
            observed(ManagedMergeState.UNKNOWN),
        ]
        for observation in cases:
            with self.subTest(observation=observation):
                decision = classify_managed_rebase(
                    managed(), observation, pending_worker=False
                )
                self.assertEqual(decision.disposition, RebaseDisposition.RECONCILE)
                self.assertIsNone(decision.plan)


class ManagedRebaseResultParityTest(unittest.TestCase):
    def plan(self):
        decision = classify_managed_rebase(
            managed(),
            observed(ManagedMergeState.BEHIND),
            pending_worker=False,
        )
        assert decision.plan is not None
        return decision.plan

    # Mirrors test_clean_rebase_updates_expected_head_with_force_lease and
    # test_dispatch_defers_classifier_when_rebase_advanced_head.
    def test_success_advances_expected_head_and_defers_reclassification(self) -> None:
        result = classify_rebase_result(
            self.plan(),
            rebase_succeeded=True,
            conflict=False,
            observed_old_head="a" * 40,
            new_head="b" * 40,
        )
        self.assertEqual(
            result.disposition,
            RebaseResultDisposition.ADVANCE_HEAD_AND_DEFER,
        )
        self.assertEqual(result.new_expected_head, "b" * 40)
        self.assertEqual(result.dispatch_defer_seconds, 30)

    # Mirrors test_conflicting_rebase_aborts_and_pauses_before_model_spend.
    def test_conflict_is_human_gate_not_retry_or_model_work(self) -> None:
        result = classify_rebase_result(
            self.plan(),
            rebase_succeeded=False,
            conflict=True,
            observed_old_head="a" * 40,
        )
        self.assertEqual(
            result.disposition,
            RebaseResultDisposition.PAUSE_HUMAN_GATE,
        )
        self.assertEqual(result.dispatch_defer_seconds, 0)

    def test_wrong_start_head_fails_closed(self) -> None:
        result = classify_rebase_result(
            self.plan(),
            rebase_succeeded=True,
            conflict=False,
            observed_old_head="c" * 40,
            new_head="b" * 40,
        )
        self.assertEqual(result.disposition, RebaseResultDisposition.BLOCK)

    def test_success_without_new_head_fails_closed(self) -> None:
        result = classify_rebase_result(
            self.plan(),
            rebase_succeeded=True,
            conflict=False,
            observed_old_head="a" * 40,
            new_head="",
        )
        self.assertEqual(result.disposition, RebaseResultDisposition.BLOCK)

    def test_decisions_are_deterministic(self) -> None:
        plan = self.plan()
        first = classify_rebase_result(
            plan,
            rebase_succeeded=True,
            conflict=False,
            observed_old_head="a" * 40,
            new_head="b" * 40,
        )
        second = classify_rebase_result(
            plan,
            rebase_succeeded=True,
            conflict=False,
            observed_old_head="a" * 40,
            new_head="b" * 40,
        )
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


if __name__ == "__main__":
    unittest.main()
