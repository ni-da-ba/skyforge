from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_terminal_gate_shadow as live
from v2.events import DurableEvent
from v2.roadmap_shadow import ShadowRoadmapManifest, ShadowRoadmapState
from v2.terminal_gate import (
    TerminalGateDisposition,
    classify_terminal_gate_quiescence,
)


def task(node_id="task", issue=1, priority=100, prereq=()):
    return {
        "id": node_id,
        "kind": "task",
        "lane": "Implementation",
        "issue_number": issue,
        "priority": priority,
        "max_runs": 1,
        "prerequisites": list(prereq),
        "objective_hint": "do it",
        "stop_boundary": "stop",
    }


def gate(node_id="gate", priority=90, prereq=("task",)):
    return {
        "id": node_id,
        "kind": "gate",
        "lane": "Implementation",
        "priority": priority,
        "max_runs": 1,
        "prerequisites": list(prereq),
        "human_message": "review it",
    }


def manifest_payload():
    return {
        "schema_version": 1,
        "roadmap_id": "terminal-test",
        "enabled": True,
        "max_auto_claims_per_utc_day": 2,
        "nodes": [task(), gate()],
    }


def roadmap_state(manifest, *, completed=None, blocked=None, active=None):
    return ShadowRoadmapState.from_legacy(
        {
            "roadmap_id": manifest.roadmap_id,
            "manifest_fingerprint": manifest.fingerprint,
            "completed_runs": completed or {},
            "blocked_nodes": {node: {"reason": "blocked"} for node in (blocked or [])},
            "active": active,
        },
        manifest,
    )


def ordinary_event():
    return DurableEvent(
        actionable=True,
        reason="CI completed",
        event="workflow_run",
        action="completed",
        head_sha="a" * 40,
    )


def protected_event():
    return DurableEvent(
        actionable=True,
        reason="explicit task",
        event="issue_comment",
        action="created",
        pr_number=10,
        signal_kind="task",
        signal_text="bounded task",
    )


class TerminalGatePolicyTest(unittest.TestCase):
    def test_terminal_gate_quiesces_ordinary_events(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(manifest_payload())
        state = roadmap_state(
            manifest,
            completed={"task": 1},
            blocked={"gate"},
        )
        event = ordinary_event()
        decision = classify_terminal_gate_quiescence(
            manifest=manifest,
            state=state,
            pending_events=[event],
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(
            decision.disposition,
            TerminalGateDisposition.QUIESCE_ORDINARY,
        )
        self.assertEqual(decision.ordinary_event_ids, (event.event_id,))
        self.assertEqual(decision.gate_ids, ("gate",))

    def test_protected_authority_delegates(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(manifest_payload())
        state = roadmap_state(
            manifest,
            completed={"task": 1},
            blocked={"gate"},
        )
        event = protected_event()
        decision = classify_terminal_gate_quiescence(
            manifest=manifest,
            state=state,
            pending_events=[event],
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(decision.disposition, TerminalGateDisposition.DELEGATE)
        self.assertEqual(decision.protected_event_ids, (event.event_id,))

    def test_blocked_task_delegates_for_roadmap_reconciliation(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(manifest_payload())
        state = roadmap_state(
            manifest,
            blocked={"task", "gate"},
        )
        decision = classify_terminal_gate_quiescence(
            manifest=manifest,
            state=state,
            pending_events=[ordinary_event()],
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(decision.disposition, TerminalGateDisposition.DELEGATE)
        self.assertIn("blocked roadmap task", decision.reason)

    def test_eligible_successor_delegates(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(manifest_payload())
        state = roadmap_state(manifest, blocked={"gate"})
        decision = classify_terminal_gate_quiescence(
            manifest=manifest,
            state=state,
            pending_events=[ordinary_event()],
            pending_decision=False,
            pending_worker=False,
            blocked_kind=False,
        )
        self.assertEqual(decision.disposition, TerminalGateDisposition.DELEGATE)
        self.assertIn("eligible roadmap node", decision.reason)

    def test_in_flight_controller_work_delegates(self) -> None:
        manifest = ShadowRoadmapManifest.from_mapping(manifest_payload())
        state = roadmap_state(
            manifest,
            completed={"task": 1},
            blocked={"gate"},
        )
        for kwargs in (
            {"pending_decision": True, "pending_worker": False, "blocked_kind": False},
            {"pending_decision": False, "pending_worker": True, "blocked_kind": False},
            {"pending_decision": False, "pending_worker": False, "blocked_kind": True},
        ):
            with self.subTest(kwargs=kwargs):
                decision = classify_terminal_gate_quiescence(
                    manifest=manifest,
                    state=state,
                    pending_events=[ordinary_event()],
                    **kwargs,
                )
                self.assertEqual(decision.disposition, TerminalGateDisposition.DELEGATE)


class LiveTerminalGateCollectorTest(unittest.TestCase):
    def test_live_collector_projects_pending_ordinary_event(self) -> None:
        payload = manifest_payload()
        manifest = ShadowRoadmapManifest.from_mapping(payload)
        with tempfile.TemporaryDirectory() as code_td, tempfile.TemporaryDirectory() as state_td:
            code_root = Path(code_td)
            state_root = Path(state_td)
            manifest_path = code_root / live.MANIFEST_RELATIVE_PATH
            manifest_path.parent.mkdir(parents=True)
            manifest_path.write_text(json.dumps(payload), encoding="utf-8")
            state_dir = state_root / ".skyforge-orchestrator"
            state_dir.mkdir()
            event = ordinary_event()
            state = {
                "roadmap": {
                    "roadmap_id": manifest.roadmap_id,
                    "manifest_fingerprint": manifest.fingerprint,
                    "completed_runs": {"task": 1},
                    "blocked_nodes": {"gate": {"reason": "review"}},
                    "active": None,
                },
                "pending_events": [event.as_dict()],
                "pending_decision": None,
                "pending_worker": None,
                "blocked_kind": None,
                "paused": True,
            }
            (state_dir / "state.json").write_text(json.dumps(state), encoding="utf-8")
            value = live.collect_terminal_gate_shadow(
                state_root=state_root,
                code_root=code_root,
            )
        self.assertTrue(value["paused"])
        self.assertEqual(value["pending_event_count"], 1)
        self.assertEqual(
            value["decision"]["disposition"],
            "QUIESCE_ORDINARY",
        )
        self.assertEqual(value["decision"]["ordinary_event_ids"], [event.event_id])

    def test_collector_source_has_no_network_or_mutation_surface(self) -> None:
        source = Path(live.__file__).read_text(encoding="utf-8")
        for token in (
            "subprocess",
            "gh ",
            "requests",
            "urllib",
            "write_text",
            "write_bytes",
            "openai_codex",
            "WriterFence",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
