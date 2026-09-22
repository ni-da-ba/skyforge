from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from test_platform_v2_objective_ingress import objective_payload, prepare_root
from v2.objective_command import (
    ObjectiveCommandPhase,
    ObjectiveCommandStore,
    advance_objective_command,
    prepare_objective_command,
    reconcile_pending_objective_commands,
)
from v2.objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalStore,
    parse_objective_comment,
)


class ObjectiveCommandTest(unittest.TestCase):
    def source(
        self,
        *,
        request_id: str = "objective-request-0001",
        objective_text: str = "Continue DR-70",
    ) -> DevelopmentApiObjectiveSource:
        return DevelopmentApiObjectiveSource(
            repo="ni-da-ba/skyforge",
            request_id=request_id,
            actor="ni-da-ba",
            client="operations-console",
            submitted_at="2026-09-21T00:10:00Z",
            objective_text=objective_text,
        )

    def test_command_persists_non_authoritative_proposal_exactly_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            commands = ObjectiveCommandStore.for_root(root)
            proposals = ObjectiveProposalStore.for_root(root)
            source = self.source()

            prepared = prepare_objective_command(
                store=commands,
                source=source,
                root=root,
            )
            self.assertEqual(prepared.phase, ObjectiveCommandPhase.PREPARED)
            self.assertEqual(len(proposals.load().records), 0)
            self.assertFalse(
                prepared.proposal.compiled.as_dict()["executable_task_authority"]
            )

            complete = advance_objective_command(
                command_store=commands,
                proposal_store=proposals,
                request_id=source.request_id,
            )
            self.assertEqual(complete.phase, ObjectiveCommandPhase.RECONCILED)
            self.assertEqual(len(proposals.load().records), 1)
            self.assertEqual(
                proposals.load().records[0].proposal_id,
                complete.proposal.proposal_id,
            )
            self.assertFalse(
                proposals.load().records[0].compiled.as_dict()["executable_task_authority"]
            )

    def test_same_request_is_idempotent_but_conflicting_payload_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            store = ObjectiveCommandStore.for_root(root)
            source = self.source()
            first = prepare_objective_command(store=store, source=source, root=root)
            replay = prepare_objective_command(store=store, source=source, root=root)
            self.assertEqual(first, replay)

            conflict = self.source(objective_text="Show DR-70")
            with self.assertRaisesRegex(ValueError, "conflicting payload"):
                prepare_objective_command(store=store, source=conflict, root=root)

    def test_identical_replay_uses_frozen_compile_even_if_compile_inputs_disappear(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            store = ObjectiveCommandStore.for_root(root)
            source = self.source()
            first = prepare_objective_command(store=store, source=source, root=root)

            (root / "docs" / "agent-state" / "ORCHESTRATOR_ROADMAP.json").unlink()
            replay = prepare_objective_command(store=store, source=source, root=root)

            self.assertEqual(replay, first)
            self.assertEqual(replay.proposal.compiled.digest, first.proposal.compiled.digest)

    def test_command_phase_cannot_regress(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            store = ObjectiveCommandStore.for_root(root)
            source = self.source()
            prepared = prepare_objective_command(store=store, source=source, root=root)
            reconciled = store.put(
                prepared.with_phase(ObjectiveCommandPhase.RECONCILED)
            )
            self.assertEqual(reconciled.phase, ObjectiveCommandPhase.RECONCILED)
            with self.assertRaisesRegex(ValueError, "phase regression"):
                store.put(prepared)

    def test_restart_after_proposal_persist_completes_command_without_duplicate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            commands = ObjectiveCommandStore.for_root(root)
            proposals = ObjectiveProposalStore.for_root(root)
            source = self.source()
            prepared = prepare_objective_command(store=commands, source=source, root=root)

            proposals.capture_record(prepared.proposal)
            commands.put(prepared.with_phase(ObjectiveCommandPhase.PROPOSAL_PERSISTED))

            recovered = reconcile_pending_objective_commands(
                command_store=ObjectiveCommandStore.for_root(root),
                proposal_store=ObjectiveProposalStore.for_root(root),
            )
            self.assertEqual(len(recovered), 1)
            self.assertEqual(recovered[0].phase, ObjectiveCommandPhase.RECONCILED)
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 1)

    def test_restart_from_prepared_reconciles_existing_exact_proposal(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            commands = ObjectiveCommandStore.for_root(root)
            proposals = ObjectiveProposalStore.for_root(root)
            source = self.source()
            prepared = prepare_objective_command(store=commands, source=source, root=root)
            proposals.capture_record(prepared.proposal)

            recovered = reconcile_pending_objective_commands(
                command_store=ObjectiveCommandStore.for_root(root),
                proposal_store=ObjectiveProposalStore.for_root(root),
            )
            self.assertEqual(recovered[0].phase, ObjectiveCommandPhase.RECONCILED)
            self.assertEqual(len(proposals.load().records), 1)

    def test_multiple_pending_commands_reconcile_independently(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            commands = ObjectiveCommandStore.for_root(root)
            proposals = ObjectiveProposalStore.for_root(root)
            prepare_objective_command(
                store=commands,
                source=self.source(request_id="objective-request-0001"),
                root=root,
            )
            prepare_objective_command(
                store=commands,
                source=self.source(
                    request_id="objective-request-0002",
                    objective_text="Show DR-70",
                ),
                root=root,
            )
            recovered = reconcile_pending_objective_commands(
                command_store=commands,
                proposal_store=proposals,
            )
            self.assertEqual(len(recovered), 2)
            self.assertTrue(
                all(item.phase is ObjectiveCommandPhase.RECONCILED for item in recovered)
            )
            self.assertEqual(len(proposals.load().records), 2)

    def test_legacy_github_source_identity_remains_exact_under_current_compile(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            payload = json.loads(objective_payload().decode("utf-8"))
            source = parse_objective_comment(
                event_name="issue_comment",
                payload=payload,
                repo="ni-da-ba/skyforge",
                trusted_actors=("ni-da-ba",),
            )
            self.assertEqual(
                source.digest,
                "d04e0b66c2682a580df80a75a22e480cb6ba22d34266fa6bad799f7a69529a16",
            )
            record = ObjectiveProposalStore.for_root(root).capture(
                source=source,
                delivery_id="objective-1",
                root=root,
            ).record
            self.assertEqual(record.source.digest, source.digest)
            self.assertEqual(
                record.compiled.disposition.value,
                "HUMAN_GATE",
            )
            self.assertEqual(
                record.compiled.human_gate.node_id,
                "dr-human-exploration-rereview",
            )


if __name__ == "__main__":
    unittest.main()
