from __future__ import annotations

import unittest

from v2.events import DurableEvent
from v2.fairness import (
    PendingTimerObservation,
    TimerDisposition,
    classify_pending_timer,
    select_quiescent_dispatch_batch,
)


def ordinary(event: str, *, head: str | None = None, pr: int | None = None) -> DurableEvent:
    return DurableEvent(
        actionable=True,
        reason="ordinary",
        event=event,
        action="completed",
        head_sha=head,
        pr_number=pr,
    )


class PendingTimerFairnessParityTest(unittest.TestCase):
    def test_later_request_cannot_postpone_earlier_live_timer(self) -> None:
        decision = classify_pending_timer(
            PendingTimerObservation(101.0, 40.0, 125.0, True)
        )
        self.assertEqual(decision.disposition, TimerDisposition.KEEP_EARLIER)
        self.assertEqual(decision.due_epoch, 125.0)

    def test_earlier_request_pulls_forward(self) -> None:
        decision = classify_pending_timer(
            PendingTimerObservation(102.0, 5.0, 125.0, True)
        )
        self.assertEqual(decision.disposition, TimerDisposition.REPLACE_EARLIER)
        self.assertEqual(decision.due_epoch, 107.0)

    def test_dead_timer_is_replaceable(self) -> None:
        decision = classify_pending_timer(
            PendingTimerObservation(150.0, 40.0, 125.0, False)
        )
        self.assertEqual(decision.disposition, TimerDisposition.REPLACE_DEAD)
        self.assertEqual(decision.due_epoch, 190.0)


class QuiescentBatchParityTest(unittest.TestCase):
    def test_mixed_heads_select_only_ready_head_events(self) -> None:
        active = "a" * 40
        ready = "b" * 40
        events = [
            ordinary("workflow_run", head=active, pr=489),
            ordinary("reconcile", head=ready),
            ordinary("workflow_run", head=ready, pr=507),
            ordinary("issue_comment"),
        ]
        decision = select_quiescent_dispatch_batch(
            events, {active: False, ready: True}
        )
        self.assertTrue(decision.split)
        self.assertEqual(
            decision.selected_event_ids,
            (events[1].event_id, events[2].event_id),
        )

    def test_each_unique_head_needs_one_mapping_entry(self) -> None:
        active = "a" * 40
        ready = "b" * 40
        events = [
            ordinary("workflow_run", head=active, pr=489),
            ordinary("pull_request", head=active, pr=489),
            ordinary("workflow_run", head=ready, pr=507),
            ordinary("pull_request", head=ready, pr=507),
        ]
        decision = select_quiescent_dispatch_batch(
            events, {active: False, ready: True}
        )
        self.assertEqual(
            decision.selected_event_ids,
            (events[2].event_id, events[3].event_id),
        )
        with self.assertRaises(ValueError):
            select_quiescent_dispatch_batch(events, {active: False})

    def test_all_ready_or_all_blocked_preserves_original_batch(self) -> None:
        a = "a" * 40
        b = "b" * 40
        events = [
            ordinary("workflow_run", head=a, pr=516),
            ordinary("workflow_run", head=b, pr=517),
            ordinary("issue_comment"),
        ]
        for mapping in ({a: True, b: True}, {a: False, b: False}):
            with self.subTest(mapping=mapping):
                decision = select_quiescent_dispatch_batch(events, mapping)
                self.assertFalse(decision.split)
                self.assertEqual(
                    decision.selected_event_ids,
                    tuple(event.event_id for event in events),
                )

    def test_protected_authority_precedes_quiescence_split(self) -> None:
        ordinary_event = ordinary("workflow_run", head="a" * 40, pr=489)
        task = DurableEvent(
            actionable=True,
            reason="explicit task",
            event="roadmap",
            action="advance",
            pr_number=491,
            source_id="roadmap:test:run:3",
            signal_kind="task",
            signal_text="bounded task authority",
        )
        decision = select_quiescent_dispatch_batch(
            [ordinary_event, task], {"a" * 40: True}
        )
        self.assertFalse(decision.split)
        self.assertEqual(decision.selected_event_ids, (task.event_id,))

    def test_deterministic_decisions(self) -> None:
        obs = PendingTimerObservation(100.0, 25.0)
        self.assertEqual(
            classify_pending_timer(obs).digest,
            classify_pending_timer(obs).digest,
        )


if __name__ == "__main__":
    unittest.main()
