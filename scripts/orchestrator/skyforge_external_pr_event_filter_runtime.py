#!/usr/bin/env python3
"""Model-free suppression for workflow completions owned by manual/external producers.

GitHub workflow completions are useful wakes for controller-managed PRs because they can advance a
managed handoff toward merge/recovery.  A workflow completion on an open PR that the controller does
not own cannot authorize hosted work: the controller is explicitly forbidden to race healthy manual
or external producers.  Retire those ordinary workflow events after the existing current-truth
normalizer, before a Luna classifier turn is purchased.

Main pushes, PR lifecycle events, trusted task/Audit authority, reconciliations, and all
controller-managed PR workflow completions remain untouched.
"""

from __future__ import annotations

from typing import Any

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_external_pr_event_filter_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_NORMALIZE_CAPTURED_EVENTS = core.Orchestrator._normalize_captured_events_for_snapshot


def _controller_owned_pr_numbers(snapshot: dict[str, Any]) -> set[int]:
    owned: set[int] = set()
    managed = snapshot.get("controller_managed")
    if not isinstance(managed, dict):
        return owned
    for value in managed.values():
        if not isinstance(value, dict):
            continue
        try:
            number = int(value.get("pr_number") or 0)
        except (TypeError, ValueError):
            continue
        if number > 0:
            owned.add(number)
    return owned


def _retire_event_keys(self: core.Orchestrator, keys: list[str]) -> None:
    if not keys:
        return
    with self._state_lock:
        retired = [
            str(value)
            for value in (self.state.data.get("retired_event_keys") or [])
            if value
        ]
        seen = set(retired)
        for key in keys:
            if key not in seen:
                retired.append(key)
                seen.add(key)
        self.state.data["retired_event_keys"] = retired[-core.DEFAULT_MAX_RETIRED_EVENT_KEYS :]
        # Close the same redelivery race guarded by the base current-truth normalizer.
        self._purge_suppressed_pending_events_locked()
        metrics = self.state.data.setdefault("metrics", {})
        metrics["external_pr_workflow_events_filtered"] = int(
            metrics.get("external_pr_workflow_events_filtered") or 0
        ) + len(keys)
        self.state.data["last_external_pr_workflow_filter"] = {
            "at": core._utc_now(),
            "retired": len(keys),
        }
        self.state.save()


def _normalize_captured_events_for_snapshot(
    self: core.Orchestrator,
    events: list[core.EventDecision],
    snapshot: dict[str, Any],
) -> list[core.EventDecision]:
    normalized = _ORIGINAL_NORMALIZE_CAPTURED_EVENTS(self, events, snapshot)
    if not normalized:
        return normalized

    owned_prs = _controller_owned_pr_numbers(snapshot)
    kept: list[core.EventDecision] = []
    dropped: list[core.EventDecision] = []
    for event in normalized:
        # Only ordinary CI completions attached to a concrete PR are eligible.  Protected authority
        # is never a workflow_run event today, but retain the priority guard so future authority
        # types cannot be silently weakened by this extension.
        if (
            event.event == "workflow_run"
            and event.pr_number is not None
            and not self._pending_event_priority(event.to_state())
        ):
            try:
                pr_number = int(event.pr_number)
            except (TypeError, ValueError):
                pr_number = 0
            if pr_number > 0 and pr_number not in owned_prs:
                dropped.append(event)
                continue
        kept.append(event)

    if not dropped:
        return normalized

    # Replace only the captured batch, preserving newer concurrent events exactly as the base
    # normalizer does. No synthetic reconcile is required: the snapshot already contains current PR
    # truth, and an unowned PR's CI result cannot authorize controller work. A later main push or PR
    # lifecycle transition remains independently actionable.
    self._replace_captured_pending_events(normalized, kept)
    dropped_keys = [core._event_key(event) for event in dropped]
    _retire_event_keys(self, dropped_keys)
    print(
        f"[orchestrator] model-free filtered {len(dropped)} workflow completion(s) "
        "for non-controller-owned PRs",
        flush=True,
    )
    return kept


core.Orchestrator._normalize_captured_events_for_snapshot = _normalize_captured_events_for_snapshot
