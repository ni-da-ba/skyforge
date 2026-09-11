#!/usr/bin/env python3
"""Prevent unrelated event churn from starving an already-scheduled controller dispatch.

The base controller debounces every actionable event by cancelling and replacing the pending timer.
Under sustained unrelated workflow traffic, that can indefinitely postpone an older managed-PR merge
or reconciliation event. This extension preserves the earliest live due time: new events may pull a
pending dispatch earlier, but may never push an existing live dispatch later.

Backoff/quiescence safety remains intact because the eventual timer callback re-evaluates blocked
state and workflow quiescence before dispatching; if those gates still apply, the callback schedules
the required later retry after the earlier timer has fired and cleared itself.
"""

from __future__ import annotations

import threading
import time

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_pending_timer_fairness_runtime.py"

core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)


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


core.Orchestrator._schedule_pending = schedule_pending
