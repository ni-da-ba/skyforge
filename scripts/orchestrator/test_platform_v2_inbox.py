from __future__ import annotations

import json
import pathlib
import sys
import unittest

MODULE_DIR = pathlib.Path(__file__).resolve().parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

import skyforge_orchestrator as legacy
from v2.events import DurableEvent, normalize_legacy_event_key
from v2.inbox import InboxState, compact_events, enqueue_events, select_dispatch_batch


def legacy_event(**overrides):
    values = {
        "actionable": True,
        "reason": "repository state changed",
        "event": "push",
        "action": None,
        "head_sha": "a" * 40,
        "pr_number": None,
        "observed_at": "2026-09-17T21:00:00+00:00",
        "source_id": None,
        "signal_kind": None,
        "signal_text": None,
    }
    values.update(overrides)
    return legacy.EventDecision(**values)


def v2_event(event: legacy.EventDecision) -> DurableEvent:
    return DurableEvent.from_legacy_mapping(event.to_state())


class DurableEventIdentityParityTest(unittest.TestCase):
    def test_v2_event_id_matches_legacy_exactly(self) -> None:
        cases = [
            legacy_event(),
            legacy_event(
                event="pull_request",
                action="synchronize",
                pr_number=481,
                source_id="delivery-100",
            ),
            legacy_event(
                reason="Audit/watchdog orchestration signal",
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="comment-100",
                signal_kind="restart_recommended",
                signal_text="AUDIT — RESTART RECOMMENDED for exact head",
            ),
        ]
        for event in cases:
            with self.subTest(event=event.event, source_id=event.source_id):
                self.assertEqual(v2_event(event).event_id, legacy._event_key(event))

    def test_observation_time_is_not_identity(self) -> None:
        first = legacy_event(observed_at="2026-09-17T21:00:00+00:00")
        second = legacy_event(observed_at="2026-09-17T21:05:00+00:00")
        self.assertEqual(v2_event(first).event_id, v2_event(second).event_id)
        self.assertEqual(v2_event(first).event_id, legacy._event_key(first))

    def test_distinct_source_ids_remain_distinct_authority(self) -> None:
        first = legacy_event(
            reason="Audit/watchdog orchestration signal",
            event="issue_comment",
            action="audit_signal",
            pr_number=349,
            source_id="100",
            signal_kind="restart_recommended",
            signal_text="AUDIT — RESTART RECOMMENDED",
        )
        second = legacy_event(
            reason=first.reason,
            event=first.event,
            action=first.action,
            pr_number=first.pr_number,
            source_id="101",
            signal_kind=first.signal_kind,
            signal_text=first.signal_text,
        )
        self.assertNotEqual(v2_event(first).event_id, v2_event(second).event_id)

    def test_event_id_is_fixed_size_legacy_hash(self) -> None:
        event = v2_event(
            legacy_event(
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="100",
                signal_kind="human_gate",
                signal_text="A" * 5000,
            )
        )
        self.assertRegex(event.event_id, r"^sha256:[0-9a-f]{64}$")
        self.assertEqual(len(event.event_id), 71)

    def test_legacy_json_key_normalization_matches_current_identity(self) -> None:
        event = legacy_event(
            event="issue_comment",
            action="audit_signal",
            pr_number=349,
            source_id="100",
            signal_kind="human_gate",
            signal_text="legacy durable authority",
        )
        payload = event.to_state()
        payload.pop("observed_at", None)
        old_key = json.dumps(payload, sort_keys=True, separators=(",", ":"))
        self.assertEqual(normalize_legacy_event_key(old_key), legacy._event_key(event))

    def test_unknown_historical_key_is_preserved_fail_closed(self) -> None:
        self.assertEqual(normalize_legacy_event_key("opaque-old-key"), "opaque-old-key")


class InboxProjectionTest(unittest.TestCase):
    def test_projection_reads_current_legacy_fields(self) -> None:
        event = legacy_event()
        key = legacy._event_key(event)
        state = InboxState.from_legacy_mapping(
            {
                "pending_events": [event.to_state()],
                "retired_event_keys": [key, key],
                "completed_authority_event_keys": [],
                "pending_decision": {
                    "event_keys": [key],
                    "decision": {"decision": "NOOP"},
                },
            }
        )
        self.assertEqual(len(state.pending_events), 1)
        self.assertEqual(state.retired_event_keys, (key,))
        self.assertEqual(state.owned_event_keys, (key,))

    def test_malformed_persisted_inbox_structures_fail_closed(self) -> None:
        cases = [
            {"pending_events": {}, "retired_event_keys": [], "completed_authority_event_keys": []},
            {"pending_events": [], "retired_event_keys": {}, "completed_authority_event_keys": []},
            {"pending_events": [], "retired_event_keys": [], "completed_authority_event_keys": "bad"},
            {
                "pending_events": [],
                "retired_event_keys": [],
                "completed_authority_event_keys": [],
                "pending_decision": [],
            },
            {
                "pending_events": [],
                "retired_event_keys": [],
                "completed_authority_event_keys": [],
                "pending_decision": {"event_keys": "bad"},
            },
            {
                "pending_events": [{"actionable": "yes", "reason": "", "event": "push"}],
                "retired_event_keys": [],
                "completed_authority_event_keys": [],
            },
        ]
        for raw in cases:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    InboxState.from_legacy_mapping(raw)


class PureInboxPolicyTest(unittest.TestCase):
    def test_enqueue_deduplicates_pending_and_observed_time_redelivery(self) -> None:
        first = v2_event(legacy_event(observed_at="2026-09-17T21:00:00+00:00"))
        redelivery = v2_event(legacy_event(observed_at="2026-09-17T21:10:00+00:00"))
        state = InboxState(pending_events=(first,))

        result = enqueue_events(state, [redelivery])

        self.assertEqual(result.suppressed_replays, 1)
        self.assertEqual(result.after.pending_events, (first,))

    def test_retired_and_completed_keys_suppress_replay(self) -> None:
        retired = v2_event(legacy_event(head_sha="a" * 40))
        completed = v2_event(
            legacy_event(
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="task-1",
                signal_kind="task",
                signal_text="AUDIT — NEW TASK",
            )
        )
        state = InboxState(
            retired_event_keys=(retired.event_id,),
            completed_authority_event_keys=(completed.event_id,),
        )

        result = enqueue_events(state, [retired, completed])

        self.assertEqual(result.suppressed_replays, 2)
        self.assertEqual(result.after.pending_events, ())

    def test_ordinary_semantic_wakes_coalesce_latest_wins(self) -> None:
        events = [
            DurableEvent(
                actionable=True,
                reason="Audit/watchdog orchestration signal",
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id=f"audit-{index}",
                signal_kind="audit",
                signal_text=f"AUDIT — synchronization {index}",
            )
            for index in range(4)
        ]
        events += [
            DurableEvent(
                actionable=True,
                reason="workflow completed; controller will require head quiescence",
                event="workflow_run",
                action="completed",
                head_sha="same-head",
                pr_number=447,
                source_id=f"workflow-{index}",
            )
            for index in range(3)
        ]

        compacted, report = compact_events(events)

        self.assertEqual(len(compacted), 2)
        self.assertEqual(
            {event.source_id for event in compacted},
            {"audit-3", "workflow-2"},
        )
        self.assertEqual(report.coalesced, 5)
        self.assertFalse(report.reconcile_inserted)

    def test_queue_pressure_compacts_ordinary_history_to_reconcile(self) -> None:
        events = [
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=f"head-{index}",
                pr_number=index,
            )
            for index in range(12)
        ]

        compacted, report = compact_events(events, limit=10)

        self.assertEqual(len(compacted), 10)
        self.assertTrue(
            any(
                event.event == "reconcile" and event.action == "queue_compaction"
                for event in compacted
            )
        )
        self.assertEqual(report.before, 12)
        self.assertEqual(report.after, 10)
        self.assertEqual(report.dropped_to_reconcile, 3)
        self.assertTrue(report.reconcile_inserted)

    def test_queue_pressure_never_drops_protected_authority(self) -> None:
        ordinary = [
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=f"head-{index}",
                pr_number=index,
            )
            for index in range(12)
        ]
        signals = [
            DurableEvent(
                actionable=True,
                reason="Audit/watchdog orchestration signal",
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id=f"signal-{index}",
                signal_kind="restart_recommended",
                signal_text=f"AUDIT — RESTART RECOMMENDED {index}",
            )
            for index in range(2)
        ]

        compacted, report = compact_events(ordinary + signals, limit=10)

        self.assertEqual(len(compacted), 10)
        source_ids = {event.source_id for event in compacted}
        self.assertIn("signal-0", source_ids)
        self.assertIn("signal-1", source_ids)
        self.assertTrue(report.reconcile_inserted)

    def test_owned_event_keys_are_protected_from_compaction(self) -> None:
        owned = [
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=f"owned-{index}",
                pr_number=index,
            )
            for index in range(2)
        ]
        later = [
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=f"later-{index}",
                pr_number=100 + index,
            )
            for index in range(12)
        ]

        compacted, _ = compact_events(
            owned + later,
            owned_event_keys=[event.event_id for event in owned],
            limit=10,
        )

        compacted_ids = {event.event_id for event in compacted}
        self.assertTrue({event.event_id for event in owned}.issubset(compacted_ids))
        self.assertEqual(len(compacted), 10)

    def test_protected_authority_may_exceed_soft_cap_without_loss(self) -> None:
        signals = [
            DurableEvent(
                actionable=True,
                reason="Audit/watchdog orchestration signal",
                event="issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id=f"signal-{index}",
                signal_kind="restart_recommended",
                signal_text=f"AUDIT — RESTART RECOMMENDED {index}",
            )
            for index in range(11)
        ]

        compacted, report = compact_events(signals, limit=10)

        self.assertEqual(len(compacted), 11)
        self.assertEqual(
            {event.source_id for event in compacted},
            {f"signal-{index}" for index in range(11)},
        )
        self.assertEqual(report.protected_overflow, 1)
        self.assertFalse(report.reconcile_inserted)

    def test_dispatch_batch_preserves_legacy_protected_priority(self) -> None:
        events = (
            DurableEvent(True, "ordinary", "push"),
            DurableEvent(
                True,
                "gate",
                "issue_comment",
                action="audit_signal",
                signal_kind="human_gate",
                source_id="gate",
            ),
            DurableEvent(
                True,
                "restart",
                "issue_comment",
                action="audit_signal",
                signal_kind="restart_recommended",
                source_id="restart",
            ),
            DurableEvent(
                True,
                "task",
                "issue_comment",
                action="audit_signal",
                signal_kind="task",
                source_id="task",
            ),
        )

        selected = select_dispatch_batch(events)

        self.assertEqual(len(selected), 1)
        self.assertEqual(selected[0].source_id, "task")

    def test_dispatch_batch_coalesces_ordinary_as_one_batch(self) -> None:
        events = (
            DurableEvent(True, "one", "push", head_sha="a"),
            DurableEvent(True, "two", "workflow_run", head_sha="a"),
        )
        self.assertEqual(select_dispatch_batch(events), events)


if __name__ == "__main__":
    unittest.main()
