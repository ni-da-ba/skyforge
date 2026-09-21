from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from test_platform_v2_program_progression import parent_proposal, prepare_program_root
from v2.objective_ingress import ObjectiveProposalStore, ProgramObjectiveSource
from v2.objective_lifecycle import (
    ObjectiveLifecycleOperation,
    ObjectiveLifecycleState,
    ObjectiveLifecycleStore,
    effective_objective_progression,
)


class ObjectiveLifecycleTest(unittest.TestCase):
    def test_pause_is_durable_idempotent_and_resume_is_exact(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            first = parent_proposal(root, "lifecycle-parent-0001")
            second = parent_proposal(root, "lifecycle-parent-0002")
            store = ObjectiveLifecycleStore.for_root(root)

            command, status, created = store.apply(
                root=root,
                request_id="lifecycle-pause-0001",
                proposal_id=first.proposal_id,
                operation=ObjectiveLifecycleOperation.PAUSE,
                reason="operator hold",
                actor="ni-da-ba",
                client="test",
            )
            self.assertTrue(created)
            self.assertEqual(status.state, ObjectiveLifecycleState.PAUSED)

            replay, replay_status, replay_created = ObjectiveLifecycleStore.for_root(root).apply(
                root=root,
                request_id="lifecycle-pause-0001",
                proposal_id=first.proposal_id,
                operation=ObjectiveLifecycleOperation.PAUSE,
                reason="operator hold",
                actor="ni-da-ba",
                client="test",
            )
            self.assertFalse(replay_created)
            self.assertEqual(replay.command_id, command.command_id)
            self.assertEqual(replay_status.state, ObjectiveLifecycleState.PAUSED)
            self.assertTrue(effective_objective_progression(root, second.proposal_id).allowed)

    def test_all_operations_are_request_idempotent_and_cancel_is_terminal(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            proposal = parent_proposal(root, "lifecycle-operations-0001")
            store = ObjectiveLifecycleStore.for_root(root)
            cases = (
                ("pause", ObjectiveLifecycleOperation.PAUSE, ObjectiveLifecycleState.PAUSED),
                ("resume", ObjectiveLifecycleOperation.RESUME, ObjectiveLifecycleState.ACTIVE),
                ("reconcile", ObjectiveLifecycleOperation.RECONCILE, ObjectiveLifecycleState.ACTIVE),
                ("cancel", ObjectiveLifecycleOperation.CANCEL, ObjectiveLifecycleState.CANCELLED),
            )
            for name, operation, expected in cases:
                kwargs = dict(
                    root=root,
                    request_id="lifecycle-" + name + "-request",
                    proposal_id=proposal.proposal_id,
                    operation=operation,
                    reason=name + " exact objective",
                    actor="ni-da-ba",
                    client="test",
                )
                _command, status, created = store.apply(**kwargs)
                self.assertTrue(created)
                self.assertEqual(status.state, expected)
                _replay, replay_status, replay_created = ObjectiveLifecycleStore.for_root(root).apply(**kwargs)
                self.assertFalse(replay_created)
                self.assertEqual(replay_status.state, expected)

            with self.assertRaisesRegex(ValueError, "cancelled objective cannot be paused or resumed"):
                store.apply(
                    root=root,
                    request_id="lifecycle-resume-after-cancel",
                    proposal_id=proposal.proposal_id,
                    operation=ObjectiveLifecycleOperation.RESUME,
                    reason="invalid resurrection",
                    actor="ni-da-ba",
                    client="test",
                )

    def test_cancel_fences_transitive_program_descendants(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_program_root(root)
            parent = parent_proposal(root, "lifecycle-ancestor-0001")
            store = ObjectiveProposalStore.for_root(root)

            child = store.capture(
                source=ProgramObjectiveSource(
                    repo="ni-da-ba/skyforge",
                    parent_proposal_id=parent.proposal_id,
                    program_id="skyforge-mainline-v1",
                    node_id="child",
                    projection_digest="b" * 64,
                    objective_text="Investigate landing gear",
                ),
                delivery_id="program-child-1",
                root=root,
            ).record
            grandchild = store.capture(
                source=ProgramObjectiveSource(
                    repo="ni-da-ba/skyforge",
                    parent_proposal_id=child.proposal_id,
                    program_id="skyforge-mainline-v1",
                    node_id="grandchild",
                    projection_digest="c" * 64,
                    objective_text="Investigate landing gear",
                ),
                delivery_id="program-child-2",
                root=root,
            ).record

            ObjectiveLifecycleStore.for_root(root).apply(
                root=root,
                request_id="lifecycle-cancel-0001",
                proposal_id=parent.proposal_id,
                operation=ObjectiveLifecycleOperation.CANCEL,
                reason="stop remaining program work",
                actor="ni-da-ba",
                client="test",
            )
            decision = effective_objective_progression(root, grandchild.proposal_id)
            self.assertFalse(decision.allowed)
            self.assertEqual(decision.state, ObjectiveLifecycleState.CANCELLED)
            self.assertEqual(decision.controlled_by_proposal_id, parent.proposal_id)


if __name__ == "__main__":
    unittest.main()
