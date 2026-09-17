from __future__ import annotations

import unittest

from v2.decision import SourcePRState
from v2.worker import (
    WorkerLifecycleState,
    WorkerRecoveryDisposition,
    WorkerRecoveryObservation,
    classify_worker_recovery,
)


BRANCH = "codex/implementation-task-284-test"


def managed_worker(*, stage: str = "handoff") -> WorkerLifecycleState:
    return WorkerLifecycleState.from_legacy_mapping(
        {
            "lane": "Implementation",
            "branch": BRANCH,
            "stage": stage,
            "managed_pr": 485,
            "worktree": "/tmp/worker",
            "authority_key": "task:284",
        }
    )


def managed_observation(
    *,
    state: SourcePRState = SourcePRState.OPEN,
    merged: bool = False,
    branch: str = BRANCH,
) -> WorkerRecoveryObservation:
    return WorkerRecoveryObservation(
        controller_paused=True,
        worktree_available=True,
        managed_owner_present=True,
        managed_branch=BRANCH,
        remote_pr_state=state,
        remote_pr_merged_at_present=merged,
        remote_pr_branch=branch,
    )


class WorkerProjectionTest(unittest.TestCase):
    def test_projection_is_deterministic(self) -> None:
        first = WorkerLifecycleState.from_legacy_mapping(
            {
                "branch": BRANCH,
                "stage": "editing",
                "managed_pr": None,
                "worktree": "/tmp/worker",
                "start_head": "start-head",
                "worker_retry_circuit_open": True,
            }
        )
        second = WorkerLifecycleState.from_legacy_mapping(
            {
                "worker_retry_circuit_open": True,
                "start_head": "start-head",
                "worktree": "/tmp/worker",
                "managed_pr": None,
                "stage": "editing",
                "branch": BRANCH,
            }
        )
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_malformed_worker_state_fails_closed(self) -> None:
        cases = [
            [],
            {"branch": "", "stage": "editing"},
            {"branch": BRANCH, "stage": ""},
            {"branch": BRANCH, "stage": "editing", "managed_pr": 0},
            {
                "branch": BRANCH,
                "stage": "editing",
                "worker_retry_circuit_open": "yes",
            },
        ]
        for raw in cases:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    WorkerLifecycleState.from_legacy_mapping(raw)

    def test_malformed_recovery_observation_fails_closed(self) -> None:
        with self.assertRaises(ValueError):
            WorkerRecoveryObservation(
                controller_paused=True,
                changed_paths=["not-a-tuple"],  # type: ignore[arg-type]
            )
        with self.assertRaises(ValueError):
            WorkerRecoveryObservation(
                controller_paused=True,
                remote_pr_state="OPEN",  # type: ignore[arg-type]
            )


class ManagedHandoffRecoveryParityTest(unittest.TestCase):
    # Mirrors test_open_handoff_detach_preserves_pr_ownership_and_clears_worker.
    def test_open_handoff_is_detachable_with_exact_ownership(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="handoff"),
            managed_observation(state=SourcePRState.OPEN),
        )
        self.assertEqual(
            decision.disposition,
            WorkerRecoveryDisposition.DETACH_OPEN_HANDOFF,
        )

    # Mirrors test_merged_handoff_is_archived_and_detached_without_original_guard.
    def test_merged_handoff_is_detachable_with_archive_semantics(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="handoff"),
            managed_observation(state=SourcePRState.MERGED, merged=True),
        )
        self.assertEqual(
            decision.disposition,
            WorkerRecoveryDisposition.DETACH_MERGED_HANDOFF,
        )

    # Mirrors test_closed_unmerged_pr_is_not_detached.
    def test_closed_unmerged_handoff_remains_protected(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="handoff"),
            managed_observation(state=SourcePRState.CLOSED),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    # Mirrors test_merged_editing_worker_is_archived_and_detached.
    def test_merged_editing_worker_is_detachable(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="editing"),
            managed_observation(state=SourcePRState.MERGED, merged=True),
        )
        self.assertEqual(
            decision.disposition,
            WorkerRecoveryDisposition.DETACH_MERGED_EDITING,
        )

    # Mirrors test_open_editing_worker_remains_protected.
    def test_open_editing_worker_remains_protected(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="editing"),
            managed_observation(state=SourcePRState.OPEN),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    # Mirrors test_merged_editing_worker_branch_drift_remains_protected.
    def test_live_branch_drift_blocks_even_merged_recovery(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="editing"),
            managed_observation(
                state=SourcePRState.MERGED,
                merged=True,
                branch="codex/some-other-branch",
            ),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)
        self.assertIn("branch identity drift", decision.reason)

    def test_missing_local_managed_ownership_blocks(self) -> None:
        observation = WorkerRecoveryObservation(
            controller_paused=True,
            worktree_available=True,
            managed_owner_present=False,
            managed_branch="",
            remote_pr_state=SourcePRState.OPEN,
            remote_pr_branch=BRANCH,
        )
        decision = classify_worker_recovery(
            managed_worker(stage="handoff"),
            observation,
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)


class RetryCircuitRecoveryParityTest(unittest.TestCase):
    def stalled_worker(self, **overrides) -> WorkerLifecycleState:
        raw = {
            "branch": "codex/implementation-stalled",
            "stage": "editing",
            "managed_pr": None,
            "worktree": "/tmp/worker",
            "start_head": "start-head",
            "worker_retry_circuit_open": True,
        }
        raw.update(overrides)
        return WorkerLifecycleState.from_legacy_mapping(raw)

    def observation(
        self,
        *,
        current_head: str = "start-head",
        changed_paths: tuple[str, ...] = (),
    ) -> WorkerRecoveryObservation:
        return WorkerRecoveryObservation(
            controller_paused=True,
            worktree_available=True,
            current_head=current_head,
            changed_paths=changed_paths,
        )

    # Mirrors test_retry_circuit_clean_editing_worker_can_be_discarded.
    def test_clean_unchanged_stalled_worker_may_be_discarded(self) -> None:
        decision = classify_worker_recovery(
            self.stalled_worker(),
            self.observation(),
        )
        self.assertEqual(
            decision.disposition,
            WorkerRecoveryDisposition.DISCARD_STALLED_CLEAN,
        )

    # Mirrors test_retry_circuit_editing_worker_with_changes_remains_protected.
    def test_stalled_worker_with_changes_remains_protected(self) -> None:
        decision = classify_worker_recovery(
            self.stalled_worker(),
            self.observation(changed_paths=("src/Work.java",)),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    # Mirrors test_retry_circuit_editing_worker_with_moved_head_remains_protected.
    def test_stalled_worker_with_head_movement_remains_protected(self) -> None:
        decision = classify_worker_recovery(
            self.stalled_worker(),
            self.observation(current_head="different-head"),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    def test_stalled_worker_without_start_head_remains_protected(self) -> None:
        decision = classify_worker_recovery(
            self.stalled_worker(start_head=None),
            self.observation(),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    def test_missing_or_controller_worktree_remains_protected(self) -> None:
        worker = self.stalled_worker()
        missing = WorkerRecoveryObservation(
            controller_paused=True,
            worktree_available=False,
            current_head="start-head",
        )
        controller = WorkerRecoveryObservation(
            controller_paused=True,
            worktree_available=True,
            worktree_is_controller=True,
            current_head="start-head",
        )
        self.assertEqual(
            classify_worker_recovery(worker, missing).disposition,
            WorkerRecoveryDisposition.BLOCK,
        )
        self.assertEqual(
            classify_worker_recovery(worker, controller).disposition,
            WorkerRecoveryDisposition.BLOCK,
        )


class RecoveryBoundaryTest(unittest.TestCase):
    def test_controller_must_be_paused(self) -> None:
        decision = classify_worker_recovery(
            managed_worker(stage="handoff"),
            WorkerRecoveryObservation(controller_paused=False),
        )
        self.assertEqual(decision.disposition, WorkerRecoveryDisposition.BLOCK)

    # Mirrors test_other_pending_worker_stage_delegates_to_existing_guard.
    def test_unsupported_worker_stage_does_not_invent_recovery(self) -> None:
        worker = WorkerLifecycleState.from_legacy_mapping(
            {
                "branch": "codex/example",
                "stage": "prepared",
                "managed_pr": 485,
                "worktree": "/tmp/worker",
            }
        )
        decision = classify_worker_recovery(
            worker,
            WorkerRecoveryObservation(controller_paused=True),
        )
        self.assertEqual(
            decision.disposition,
            WorkerRecoveryDisposition.DELEGATE_EXISTING_GUARD,
        )

    def test_recovery_decision_digest_is_stable(self) -> None:
        worker = managed_worker(stage="handoff")
        observation = managed_observation(state=SourcePRState.OPEN)
        first = classify_worker_recovery(worker, observation)
        second = classify_worker_recovery(worker, observation)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)


if __name__ == "__main__":
    unittest.main()
