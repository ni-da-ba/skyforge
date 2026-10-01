#!/usr/bin/env python3
"""Prevent unrelated event churn from starving older controller work.

The base controller originally had two ordinary-queue starvation modes under sustained unrelated
workflow traffic:

1. every actionable event cancelled/replaced the pending timer, so newer traffic could indefinitely
   postpone an older managed-PR merge or reconciliation event;
2. once the timer fired, one mixed ordinary batch required every observed workflow head to be
   quiescent before any of the batch could classify, so one unrelated active head could hold ready
   reconciliation/terminal-PR evidence behind it.

This extension keeps both fairness boundaries local and model-free:

* pending timers preserve the earliest live due time: newer events may pull dispatch earlier, but may
  never push an existing live dispatch later;
* protected task/restart/human/loop-risk authority keeps the accepted one-at-a-time precedence;
* an otherwise ordinary batch may split into currently quiescent and still-active workflow-head
  subsets. The quiescent subset may proceed while the active subset remains durably queued for the
  next pass. Headless ordinary wakes remain coupled to the ordinary batch unless no workflow head is
  blocking, so generic manual/Audit wakes do not bypass active-workflow safety on their own.

The eventual dispatch still re-evaluates workflow quiescence before classification. No event is
retired by this extension; ordinary event ownership remains with the base controller's durable
pending-decision/event-key machinery.
"""

from __future__ import annotations

import threading
import time

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_pending_timer_fairness_runtime.py"

core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_SELECT_DISPATCH_BATCH = core.Orchestrator._select_dispatch_batch


def schedule_pending(self: core.Orchestrator, delay_seconds: int | float) -> None:
    """Schedule the pending queue without allowing event churn to postpone existing work."""
    delay = max(0.1, float(delay_seconds))
    scheduled_at = core._utc_now()
    due_epoch = time.time() + delay
    suppressed_postpone = False

    with self._timer_lock:
        existing = self._timer
        existing_due = self._timer_due_epoch
        if (
            existing is not None
            and existing.is_alive()
            and existing_due is not None
            and existing_due <= due_epoch
        ):
            suppressed_postpone = True
        else:
            if existing is not None:
                existing.cancel()
            self._timer_scheduled_at = scheduled_at
            self._timer_due_epoch = due_epoch
            self._timer = threading.Timer(delay, self._pending_timer_callback)
            self._timer.daemon = True
            self._timer.start()

    if suppressed_postpone:
        self._metric("pending_timer_postpone_suppressed")


def select_dispatch_batch(
    self: core.Orchestrator,
    pending: list[core.EventDecision],
) -> list[core.EventDecision]:
    """Let ready ordinary heads drain without discarding still-active unrelated heads.

    Protected authority keeps the exact accepted selector semantics. For an ordinary mixed batch,
    evaluate each unique workflow head once. If at least one headed event is quiescent while another
    headed event is still active, return only the quiescent headed events. Headless ordinary wakes
    stay behind the active subset during that split; this prevents a generic manual/Audit wake from
    becoming an unintended bypass of workflow quiescence.

    When every headed event is active, return the original batch so the base dispatch path performs
    its normal retry/backoff. When every headed event is quiescent (or the batch is entirely
    headless), preserve the original coalesced-batch behavior.
    """
    selected = _ORIGINAL_SELECT_DISPATCH_BATCH(self, pending)

    # The accepted selector isolates protected task/restart/human/loop-risk authority to one event.
    # Never widen or repartition that boundary here.
    if len(selected) != len(pending) or any(
        event.signal_kind in core.PROTECTED_AUTHORITY_SIGNAL_KINDS
        for event in selected
    ):
        return selected

    if len(selected) <= 1:
        return selected

    quiescent_by_head: dict[str, bool] = {}
    ready_headed: list[core.EventDecision] = []
    blocked_headed: list[core.EventDecision] = []
    headless: list[core.EventDecision] = []

    for event in selected:
        head = str(event.head_sha or "").strip()
        if not head:
            headless.append(event)
            continue
        if head not in quiescent_by_head:
            quiescent_by_head[head] = bool(self.workflows_quiescent(head))
        if quiescent_by_head[head]:
            ready_headed.append(event)
        else:
            blocked_headed.append(event)

    if ready_headed and blocked_headed:
        self._metric("ordinary_quiescent_batch_splits")
        self._metric("ordinary_active_events_deferred", len(blocked_headed))
        return ready_headed

    # No safe split exists: either everything is ready, everything is blocked, or there are no
    # workflow heads at all. Preserve the base coalesced batch and its existing quiescence/backoff.
    return selected


core.Orchestrator._schedule_pending = schedule_pending
core.Orchestrator._select_dispatch_batch = select_dispatch_batch
