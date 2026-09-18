from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from v2.core import HumanGateRecord
from v2.cutover import LegacyOperationalProjection
from v2.events import DurableEvent
from v2.roadmap_recovery import (
    ClosedActiveDisposition,
    RoadmapIssueState,
)
from v2.roadmap_service import (
    RoadmapApplyDisposition,
    RoadmapAuthorityLedger,
    RoadmapAuthorityStore,
    apply_roadmap_decision,
    bind_active_pr,
    record_gate_visibility,
    reconcile_active_issue,
)
from v2.roadmap_shadow import (
    RoadmapControlObservation,
    RoadmapIssueTruth,
    ShadowRoadmapManifest,
    evaluate_roadmap_shadow,
)
from v2.terminal_gate import (
    TerminalGateDisposition,
    classify_terminal_gate_quiescence,
)


def task(node_id, issue, priority=100, prereq=(), max_runs=1):
    return {
        "id": node_id,
        "kind": "task",
        "lane": "Implementation",
        "issue_number": issue,
        "priority": priority,
        "max_runs": max_runs,
        "prerequisites": list(prereq),
        "objective_hint": f"implement {node_id}",
        "stop_boundary": f"stop after {node_id}",
    }


def gate(node_id, priority=90, prereq=(), message=None):
    return {
        "id": node_id,
        "kind": "gate",
        "lane": "Implementation",
        "priority": priority,
        "max_runs": 1,
        "prerequisites": list(prereq),
        "human_message": message or f"review {node_id}; machines must not self-pass",
    }


def manifest(nodes, *, max_claims=6):
    return ShadowRoadmapManifest.from_mapping(
        {
            "schema_version": 1,
            "roadmap_id": "r5c14-test-roadmap",
            "enabled": True,
            "max_auto_claims_per_utc_day": max_claims,
            "nodes": nodes,
        }
    )


def legacy_projection(manifest, *, completed=None, blocked=None, claims_today=0):
    raw = {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {},
        "human_gate_records": {
            "issue:349:implementation": {
                "token": "legacy-visible-token",
                "target": "349",
                "seeded_from_github": False,
            }
        },
        "roadmap": {
            "roadmap_id": manifest.roadmap_id,
            "manifest_fingerprint": manifest.fingerprint,
            "completed_runs": completed or {},
            "blocked_nodes": {
                node: {"reason": reason}
                for node, reason in (blocked or {}).items()
            },
            "active": None,
            "claims_day": "2026-09-18",
            "claims_today": claims_today,
        },
    }
    return LegacyOperationalProjection.from_legacy_mapping(raw)


class ProjectionAndPersistenceTest(unittest.TestCase):
    def test_cutover_projection_import_preserves_semantic_authority(self):
        m = manifest(
            [
                task("repair", 754, max_runs=2),
                gate(
                    "rereview",
                    prereq=("repair",),
                    message="DR-70 repair complete; machines must not self-pass.",
                ),
            ]
        )
        projection = legacy_projection(
            m,
            completed={"repair": 2},
            blocked={"rereview": "Machines must not self-pass this re-review."},
            claims_today=4,
        )
        ledger = RoadmapAuthorityLedger.from_legacy_projection(projection, m)

        self.assertEqual(dict(ledger.completed_runs), {"repair": 2})
        self.assertEqual(ledger.block_for("rereview").reason, "Machines must not self-pass this re-review.")
        self.assertEqual(ledger.claims_day, "2026-09-18")
        self.assertEqual(ledger.claims_today, 4)
        self.assertEqual(ledger.human_gate_records, projection.human_gate_records)

    def test_restart_round_trip_is_exact(self):
        m = manifest([task("a", 1)])
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m),
            m,
        )
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = RoadmapAuthorityStore.for_root(root)
            store.save(ledger)
            restarted = RoadmapAuthorityStore.for_root(root).load()
            self.assertEqual(restarted, ledger)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_manifest_fingerprint_change_cannot_reinterpret_ledger(self):
        first = manifest([task("a", 1)])
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(first),
            first,
        )
        changed = manifest([task("a", 1), task("b", 2, priority=50)])
        with self.assertRaises(ValueError):
            ledger.validate_manifest(changed)


class RoadmapApplyTest(unittest.TestCase):
    def test_task_selection_freezes_exact_active_event_and_daily_claim(self):
        m = manifest([task("a", 10)])
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )

        self.assertEqual(result.disposition, RoadmapApplyDisposition.TASK_SEEDED)
        self.assertIsNotNone(result.event)
        self.assertTrue(result.event.protected_authority)
        self.assertEqual(result.event.task_issue_number, 10)
        self.assertEqual(result.ledger.active.event.event_id, result.event.event_id)
        self.assertEqual(
            result.ledger.active.event.source_id,
            "roadmap:r5c14-test-roadmap:a:run:1",
        )
        self.assertEqual(result.ledger.claims_today, 1)

    def test_daily_claim_ceiling_blocks_without_creating_authority(self):
        m = manifest([task("a", 10)], max_claims=1)
        projection = legacy_projection(m, claims_today=1)
        ledger = RoadmapAuthorityLedger.from_legacy_projection(projection, m)
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )
        self.assertEqual(result.disposition, RoadmapApplyDisposition.DAILY_BOUND)
        self.assertIsNone(result.ledger.active)
        self.assertIsNone(result.event)

    def test_new_utc_day_resets_claim_budget(self):
        m = manifest([task("a", 10)], max_claims=1)
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m, claims_today=1),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-19",
        )
        self.assertEqual(result.disposition, RoadmapApplyDisposition.TASK_SEEDED)
        self.assertEqual(result.ledger.claims_day, "2026-09-19")
        self.assertEqual(result.ledger.claims_today, 1)

    def test_repair_completion_selects_rereview_and_machine_only_blocks_it(self):
        message = (
            "DR-70 repair evidence is machine-complete. Reopen the repaired canonical "
            "specimen. Machines must not self-pass this re-review."
        )
        m = manifest(
            [
                task("repair", 754, max_runs=2),
                gate("rereview", prereq=("repair",), message=message),
            ]
        )
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m, completed={"repair": 2}),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={754: RoadmapIssueTruth.CLOSED},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )

        self.assertEqual(result.disposition, RoadmapApplyDisposition.GATE_BLOCKED)
        self.assertIsNone(result.ledger.active)
        self.assertIn("Machines must not self-pass", result.ledger.block_for("rereview").reason)
        self.assertTrue(result.gate_visibility_required)
        self.assertEqual(result.gate_record.key, "issue:349:implementation")

        visible = HumanGateRecord(
            key=result.gate_record.key,
            token=result.gate_record.token,
            target=result.gate_record.target,
            seeded_from_github=False,
        )
        recorded = record_gate_visibility(result.ledger, visible)
        self.assertEqual(recorded.gate_for(visible.key), visible)

        later = evaluate_roadmap_shadow(
            manifest=m,
            state=recorded.shadow_state(m),
            issue_truth={754: RoadmapIssueTruth.CLOSED},
            control=RoadmapControlObservation(),
        )
        self.assertEqual(later.disposition.value, "EXHAUSTED")
        self.assertIn("rereview", later.projected_blocked_nodes)

    def test_closed_blocked_task_projects_completion_before_gate(self):
        m = manifest(
            [
                task("repair", 10, max_runs=2),
                gate("review", prereq=("repair",)),
            ]
        )
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m, blocked={"repair": "prior no-change boundary"}),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.CLOSED},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )
        self.assertEqual(result.disposition, RoadmapApplyDisposition.GATE_BLOCKED)
        self.assertEqual(dict(result.ledger.completed_runs)["repair"], 2)
        self.assertIsNone(result.ledger.block_for("repair"))
        self.assertIsNotNone(result.ledger.block_for("review"))

    def test_stale_shadow_decision_cannot_mutate_newer_ledger(self):
        m = manifest([task("a", 10)])
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        changed = RoadmapAuthorityLedger(
            roadmap_id=ledger.roadmap_id,
            manifest_fingerprint=ledger.manifest_fingerprint,
            completed_runs=(("a", 1),),
            blocked_nodes=(),
            active=None,
            claims_day=ledger.claims_day,
            claims_today=ledger.claims_today,
            human_gate_records=ledger.human_gate_records,
        )
        result = apply_roadmap_decision(
            ledger=changed,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )
        self.assertEqual(result.disposition, RoadmapApplyDisposition.BLOCKED)
        self.assertEqual(result.ledger, changed)


class ActiveRecoveryTest(unittest.TestCase):
    def seeded(self):
        m = manifest([task("a", 10, max_runs=2)])
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(m),
            m,
        )
        decision = evaluate_roadmap_shadow(
            manifest=m,
            state=ledger.shadow_state(m),
            issue_truth={10: RoadmapIssueTruth.OPEN},
            control=RoadmapControlObservation(),
        )
        result = apply_roadmap_decision(
            ledger=ledger,
            manifest=m,
            decision=decision,
            utc_day="2026-09-18",
        )
        return m, result.ledger

    def test_closed_active_issue_without_pr_completes_full_node(self):
        m, ledger = self.seeded()
        recovered = reconcile_active_issue(
            ledger=ledger,
            manifest=m,
            issue_state=RoadmapIssueState.CLOSED,
        )
        self.assertEqual(recovered.disposition, ClosedActiveDisposition.COMPLETE_NODE)
        self.assertIsNone(recovered.ledger.active)
        self.assertEqual(dict(recovered.ledger.completed_runs)["a"], 2)

    def test_open_active_issue_delegates_without_changing_authority(self):
        m, ledger = self.seeded()
        recovered = reconcile_active_issue(
            ledger=ledger,
            manifest=m,
            issue_state=RoadmapIssueState.OPEN,
        )
        self.assertEqual(recovered.disposition, ClosedActiveDisposition.DELEGATE_ROADMAP)
        self.assertEqual(recovered.ledger, ledger)

    def test_bound_pr_delegates_to_managed_lifecycle(self):
        m, ledger = self.seeded()
        bound = bind_active_pr(ledger, issue_number=10, pr_number=900)
        recovered = reconcile_active_issue(
            ledger=bound,
            manifest=m,
            issue_state=RoadmapIssueState.CLOSED,
        )
        self.assertEqual(recovered.disposition, ClosedActiveDisposition.DELEGATE_PR)
        self.assertEqual(recovered.ledger, bound)

    def test_pr_binding_is_exact_issue_scoped(self):
        _, ledger = self.seeded()
        with self.assertRaises(ValueError):
            bind_active_pr(ledger, issue_number=11, pr_number=900)


class TerminalGateCompositionTest(unittest.TestCase):
    def test_durable_rereview_gate_quiesces_only_ordinary_noise(self):
        m = manifest(
            [
                task("repair", 754, max_runs=2),
                gate("rereview", prereq=("repair",)),
            ]
        )
        ledger = RoadmapAuthorityLedger.from_legacy_projection(
            legacy_projection(
                m,
                completed={"repair": 2},
                blocked={"rereview": "Machines must not self-pass."},
            ),
            m,
        )
        ordinary = DurableEvent(
            actionable=True,
            reason="workflow completed",
            event="workflow_run",
            action="completed",
            head_sha="a" * 40,
        )
        decision = classify_terminal_gate_quiescence(
            manifest=m,
            state=ledger.shadow_state(m),
            pending_events=(ordinary,),
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(decision.disposition, TerminalGateDisposition.QUIESCE_ORDINARY)
        self.assertEqual(decision.ordinary_event_ids, (ordinary.event_id,))

        protected = DurableEvent(
            actionable=True,
            reason="explicit task",
            event="issue_comment",
            action="created",
            pr_number=900,
            signal_kind="task",
            signal_text="bounded explicit authority",
        )
        protected_decision = classify_terminal_gate_quiescence(
            manifest=m,
            state=ledger.shadow_state(m),
            pending_events=(protected,),
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(protected_decision.disposition, TerminalGateDisposition.DELEGATE)
        self.assertEqual(protected_decision.protected_event_ids, (protected.event_id,))


if __name__ == "__main__":
    unittest.main()
