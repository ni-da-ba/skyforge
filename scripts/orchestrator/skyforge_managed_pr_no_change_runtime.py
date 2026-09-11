#!/usr/bin/env python3
"""Hosted liveness guard for no-change repairs on failed controller-managed PRs.

A failed exact-head workflow is machine authority for one bounded repair dispatch. If that worker
returns no repository delta, the base controller historically consumed the workflow event and became
quiet because single-event no-change batches intentionally do not synthesize generic follow-ups.

This extension preserves that generic anti-loop rule while giving failed managed PRs exactly one
explicit retry. A second no-change result is surfaced as a path-local HUMAN_GATE on the PR rather than
silently leaving a red managed handoff idle.
"""

from __future__ import annotations

from typing import Iterable

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_managed_pr_no_change_runtime.py"
RETRY_SOURCE_PREFIX = "managed-pr-no-change-retry:"

core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_SCHEDULE_NO_CHANGE_FOLLOWUP = core.Orchestrator._schedule_no_change_followup


def _managed_lane_for_pr(self: core.Orchestrator, pr_number: int) -> str | None:
    with self._state_lock:
        for lane, record in (self.state.data.get("managed") or {}).items():
            if not isinstance(record, dict):
                continue
            try:
                number = int(record.get("pr_number") or 0)
            except (TypeError, ValueError):
                continue
            if number == int(pr_number):
                return str(lane)
    return None


def _failed_workflow_event(events: Iterable[core.EventDecision]) -> core.EventDecision | None:
    values = list(events)
    if len(values) != 1:
        return None
    event = values[0]
    if (
        event.event != "workflow_run"
        or event.pr_number is None
        or not event.head_sha
        or "conclusion=failure" not in str(event.reason or "").lower()
    ):
        return None
    return event


def _retry_event(event: core.EventDecision) -> core.EventDecision:
    pr_number = int(event.pr_number or 0)
    head_sha = str(event.head_sha or "").lower()
    return core.EventDecision(
        actionable=True,
        reason=(
            "managed PR failed-CI repair worker made no repository changes; "
            "one bounded exact-head retry remains"
        ),
        event="reconcile",
        action="audit_signal",
        head_sha=head_sha,
        pr_number=pr_number,
        observed_at=core._utc_now(),
        source_id=f"{RETRY_SOURCE_PREFIX}{pr_number}:{head_sha}",
        signal_kind="restart_recommended",
        signal_text=(
            "AUDIT — RESTART RECOMMENDED (managed PR failed-CI no-change recovery)\n\n"
            f"Preserve PR #{pr_number} at head `{head_sha}`.\n"
            "The first autonomous repair dispatch for this failed exact head returned no repository "
            "delta. Re-evaluate this same failed head once. Repair it within ordinary hosted worker "
            "authority when machine-fixable; otherwise surface a real human/product/permission gate. "
            "Do not silently NOOP while the exact head remains failed."
        ),
    )


def schedule_no_change_followup(
    self: core.Orchestrator,
    events: list[core.EventDecision],
) -> bool:
    """Retain generic behavior, plus one bounded failed-managed-PR repair retry."""
    if _ORIGINAL_SCHEDULE_NO_CHANGE_FOLLOWUP(self, events):
        return True
    if self._pending_events() or len(events) != 1:
        return False

    event = events[0]
    source_id = str(event.source_id or "")
    if source_id.startswith(RETRY_SOURCE_PREFIX):
        pr_number = int(event.pr_number or 0)
        if pr_number <= 0:
            return False
        lane = _managed_lane_for_pr(self, pr_number)
        gate = {
            "decision": "HUMAN_GATE",
            "lane": lane,
            "pr_number": pr_number,
            "objective": None,
            "stop_boundary": None,
            "reusable_evidence": event.signal_text,
            "reason": "managed PR failed-CI repair produced no repository delta after its bounded retry",
            "human_message": (
                f"Controller-managed PR #{pr_number} remains on failed exact head {event.head_sha} "
                "after two autonomous repair dispatches produced no repository delta. The controller "
                "will not silently idle or loop further on this head; inspect the machine failure or "
                "provide new repair authority/evidence."
            ),
        }
        if self._post_gate(gate):
            self._metric("managed_pr_no_change_escalations")
            return True
        return False

    failed = _failed_workflow_event(events)
    if failed is None:
        return False

    retry = _retry_event(failed)
    self._metric("managed_pr_no_change_retries")
    self.enqueue(retry)
    return True


core.Orchestrator._schedule_no_change_followup = schedule_no_change_followup
