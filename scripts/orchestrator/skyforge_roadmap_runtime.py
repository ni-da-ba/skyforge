#!/usr/bin/env python3
"""Hosted Skyforge runtime with bounded roadmap continuation.

This layer sits above the replay-safe/task-owned/auto-merge runtime. It does not invent work. When the
ordinary authorized queue is genuinely idle, it may synthesize exactly one task authority from the
machine-readable ORCHESTRATOR_ROADMAP manifest. Every v1 roadmap task must already be issue-backed,
bounded, and explicitly listed in that manifest. Human gates, unmerged handoffs, missing authority,
quota/controller blocks, and manifest exhaustion all fail closed.
"""

from __future__ import annotations

import os
from typing import Any

import roadmap_policy
import skyforge_control_replay_runtime as replay_runtime


core = replay_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_roadmap_runtime.py"
POLICY_PATH = "scripts/orchestrator/roadmap_policy.py"
MANIFEST_PATH = "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
PROGRAM_ROADMAP_PATH = "docs/agent-state/PROGRAM_ROADMAP.md"

core.CONTROLLER_RUNTIME_PATHS.update({RUNTIME_PATH, POLICY_PATH})
core.PROTECTED_WORKER_PATHS.update({MANIFEST_PATH, PROGRAM_ROADMAP_PATH})

_ORIGINAL_RESUME_PENDING = core.Orchestrator.resume_pending
_ORIGINAL_DRAIN_AND_DISPATCH = core.Orchestrator._drain_and_dispatch
_ORIGINAL_PERIODIC_RECONCILE = core.Orchestrator._run_periodic_reconcile
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def _roadmap_enabled() -> bool:
    return os.environ.get("SKYFORGE_ORCHESTRATOR_ROADMAP", "1") == "1"


def _roadmap_manifest(self: core.Orchestrator) -> roadmap_policy.RoadmapManifest:
    return roadmap_policy.load_manifest(self.root / MANIFEST_PATH)


def _roadmap_state_locked(
    self: core.Orchestrator,
    manifest: roadmap_policy.RoadmapManifest,
) -> dict[str, Any]:
    state = self.state.data.get("roadmap")
    if not isinstance(state, dict) or state.get("roadmap_id") != manifest.roadmap_id:
        state = {
            "roadmap_id": manifest.roadmap_id,
            "manifest_fingerprint": manifest.fingerprint,
            "completed_runs": {},
            "blocked_nodes": {},
            "active": None,
            "claims_day": core._utc_day(),
            "claims_today": 0,
            "last_seeded_at": None,
            "last_completed_at": None,
            "last_blocked_at": None,
            "last_exhausted_at": None,
            "last_error": None,
            "last_trigger": None,
        }
        self.state.data["roadmap"] = state
        self.state.save()
        return state

    # Protected manifest edits may add/reorder future nodes without erasing durable progress.
    state["manifest_fingerprint"] = manifest.fingerprint
    if state.get("claims_day") != core._utc_day():
        state["claims_day"] = core._utc_day()
        state["claims_today"] = 0
    state.setdefault("completed_runs", {})
    state.setdefault("blocked_nodes", {})
    state.setdefault("active", None)
    state.setdefault("claims_today", 0)
    state.setdefault("last_error", None)
    state.setdefault("last_trigger", None)
    self.state.save()
    return state


def _roadmap_event(
    manifest: roadmap_policy.RoadmapManifest,
    node: roadmap_policy.RoadmapNode,
    *,
    run_number: int,
) -> core.EventDecision:
    source_id = f"roadmap:{manifest.roadmap_id}:{node.node_id}:run:{run_number}"
    signal = (
        f"AUDIT — ROADMAP TASK AUTHORITY: {node.node_id}\n"
        f"Canonical roadmap: {PROGRAM_ROADMAP_PATH}\n"
        f"Machine manifest: {MANIFEST_PATH}\n"
        f"Authorized issue: #{node.issue_number}\n"
        f"Lane hint: {node.lane}\n"
        f"Bounded objective hint: {node.objective_hint}\n"
        f"Stop boundary: {node.stop_boundary}\n"
        "This authority exists only for the smallest still-unaccepted tranche consistent with current "
        "main, lane ownership, validation policy, and the issue body. Do not recreate accepted work. "
        "If the remaining uncertainty is human/product/manual/external-evidence-only, return HUMAN_GATE "
        "or NOOP rather than inventing another implementation tranche."
    )
    return core.EventDecision(
        actionable=True,
        reason="explicit bounded roadmap successor authority",
        event="roadmap",
        action="advance",
        pr_number=node.issue_number,
        observed_at=core._utc_now(),
        source_id=source_id,
        signal_kind="task",
        signal_text=signal,
    )


def _task_managed_records(self: core.Orchestrator) -> list[dict[str, Any]]:
    with self._state_lock:
        return [
            dict(value)
            for value in (self.state.data.get("managed") or {}).values()
            if isinstance(value, dict)
            and str(value.get("authority_key") or "").startswith("task:")
            and value.get("pr_number")
        ]


def _roadmap_live_task_prs(self: core.Orchestrator) -> list[int]:
    """Return open task-owned controller PRs.

    Legacy managed PRs without durable task ownership do not block the roadmap. Visibility uncertainty
    does block: inability to prove a task PR terminal is never permission to launch parallel work.
    """
    open_prs: list[int] = []
    for record in _task_managed_records(self):
        number = int(record["pr_number"])
        try:
            pr = core._json_cmd(
                [
                    "gh", "pr", "view", str(number), "--repo", self.repo,
                    "--json", "state,mergedAt",
                ],
                cwd=self.root,
                timeout=60,
            )
        except Exception:
            return [number]
        if str(pr.get("state") or "").upper() == "OPEN" and not pr.get("mergedAt"):
            open_prs.append(number)
    return open_prs


def _roadmap_mark_blocked_locked(
    self: core.Orchestrator,
    state: dict[str, Any],
    node_id: str,
    reason: str,
) -> None:
    state.setdefault("blocked_nodes", {})[node_id] = {
        "at": core._utc_now(),
        "reason": reason[:1000],
    }
    state["active"] = None
    state["last_blocked_at"] = core._utc_now()
    state["last_error"] = None
    self.state.save()
    self._metric("roadmap_blocks")


def _roadmap_mark_completed_locked(
    self: core.Orchestrator,
    state: dict[str, Any],
    node_id: str,
) -> None:
    completed = state.setdefault("completed_runs", {})
    completed[node_id] = int(completed.get(node_id) or 0) + 1
    state["active"] = None
    state["last_completed_at"] = core._utc_now()
    state["last_error"] = None
    self.state.save()
    self._metric("roadmap_runs_completed")


def _roadmap_resolve_active(
    self: core.Orchestrator,
    manifest: roadmap_policy.RoadmapManifest,
    state: dict[str, Any],
) -> bool:
    """Resolve active roadmap ownership; return True while that node still owns work."""
    active = state.get("active")
    if not isinstance(active, dict):
        return False

    node_id = str(active.get("node_id") or "")
    issue_number = active.get("issue_number")
    authority_key = f"task:{int(issue_number)}" if issue_number else None

    # Bind the managed PR as soon as handoff creates it. This happens before the global open-task-PR
    # guard so a very fast CI/auto-merge cycle cannot erase the only durable PR association.
    managed_record = next(
        (
            value
            for value in _task_managed_records(self)
            if authority_key and str(value.get("authority_key") or "") == authority_key
        ),
        None,
    )
    if managed_record and managed_record.get("pr_number"):
        active["pr_number"] = int(managed_record["pr_number"])
        self.state.save()

    pr_number = active.get("pr_number")
    if pr_number:
        try:
            pr = core._json_cmd(
                [
                    "gh", "pr", "view", str(int(pr_number)), "--repo", self.repo,
                    "--json", "state,mergedAt",
                ],
                cwd=self.root,
                timeout=60,
            )
        except Exception as exc:
            state["last_error"] = {
                "at": core._utc_now(),
                "kind": type(exc).__name__,
                "summary": "roadmap active-PR state could not be verified",
            }
            self.state.save()
            return True

        if pr.get("mergedAt") or str(pr.get("state") or "").upper() == "MERGED":
            _roadmap_mark_completed_locked(self, state, node_id)
            return False
        if str(pr.get("state") or "").upper() == "OPEN":
            return True
        _roadmap_mark_blocked_locked(
            self,
            state,
            node_id,
            f"controller-managed roadmap PR #{pr_number} closed without merge",
        )
        return False

    event_state = active.get("event")
    if isinstance(event_state, dict):
        event_key = core._event_key(event_state)
        with self._state_lock:
            completed_keys = set(self.state.data.get("completed_authority_event_keys") or [])
            retired_keys = set(self.state.data.get("retired_event_keys") or [])
        if event_key in completed_keys or event_key in retired_keys:
            _roadmap_mark_blocked_locked(
                self,
                state,
                node_id,
                (
                    "roadmap task authority completed without a merged controller-managed PR; "
                    "the classifier/worker likely reached NOOP, HUMAN_GATE, or a no-change boundary"
                ),
            )
            return False

        # Crash-safe replay: replay exactly the same authority only if it was not durably consumed.
        self.enqueue(core.EventDecision.from_state(event_state))
        self._metric("roadmap_active_replays")
        return True

    _roadmap_mark_blocked_locked(
        self,
        state,
        node_id,
        "roadmap active state lost its durable event identity",
    )
    return False


def _roadmap_issue_open(self: core.Orchestrator, issue_number: int) -> bool | None:
    try:
        issue = core._json_cmd(
            [
                "gh", "issue", "view", str(issue_number), "--repo", self.repo,
                "--json", "state,title",
            ],
            cwd=self.root,
            timeout=60,
        )
    except Exception:
        return None
    return str(issue.get("state") or "").upper() == "OPEN"


def _roadmap_record_error(
    self: core.Orchestrator,
    *,
    trigger: str,
    exc: Exception,
) -> None:
    with self._state_lock:
        state = self.state.data.setdefault("roadmap", {})
        state["last_error"] = {
            "at": core._utc_now(),
            "kind": type(exc).__name__,
            "summary": str(exc)[:1000],
        }
        state["last_trigger"] = trigger
        self.state.save()
    self._metric("roadmap_advance_failures")


def _roadmap_maybe_advance(self: core.Orchestrator, *, trigger: str) -> bool:
    """Seed at most one explicit roadmap authority when the ordinary controller is truly idle."""
    if not _roadmap_enabled() or self.is_paused():
        return False
    with self._state_lock:
        if (
            self.state.data.get("pending_events")
            or self.state.data.get("pending_decision")
            or isinstance(self.state.data.get("pending_worker"), dict)
            or self.state.data.get("blocked_kind")
        ):
            return False

    try:
        manifest = _roadmap_manifest(self)
    except Exception as exc:
        _roadmap_record_error(self, trigger=trigger, exc=exc)
        self._metric("roadmap_manifest_errors")
        return False
    if not manifest.enabled:
        return False

    with self._dispatch_lock:
        with self._state_lock:
            if (
                self.state.data.get("pending_events")
                or self.state.data.get("pending_decision")
                or isinstance(self.state.data.get("pending_worker"), dict)
                or self.state.data.get("blocked_kind")
                or self.state.data.get("paused")
            ):
                return False
            state = _roadmap_state_locked(self, manifest)
            state["last_trigger"] = trigger
            self.state.save()

        # Resolve/bind this roadmap's current PR before the global task-PR guard. This closes the
        # handoff/auto-merge race while still preventing unrelated parallel task work.
        if _roadmap_resolve_active(self, manifest, state):
            return False
        if _roadmap_live_task_prs(self):
            return False

        while True:
            with self._state_lock:
                completed_runs = {
                    str(key): int(value or 0)
                    for key, value in (state.get("completed_runs") or {}).items()
                }
                blocked_nodes = set((state.get("blocked_nodes") or {}).keys())
            node = roadmap_policy.select_next_node(
                manifest,
                completed_runs=completed_runs,
                blocked_nodes=blocked_nodes,
            )
            if node is None:
                with self._state_lock:
                    state["last_exhausted_at"] = core._utc_now()
                    state["last_error"] = None
                    self.state.save()
                self._metric("roadmap_idle_exhausted")
                return False

            if node.kind == "gate":
                gate = {
                    "decision": "HUMAN_GATE",
                    "lane": node.lane,
                    "pr_number": None,
                    "reason": f"bounded roadmap gate {node.node_id}",
                    "human_message": node.human_message,
                }
                if not self._post_gate(gate):
                    with self._state_lock:
                        state["last_error"] = {
                            "at": core._utc_now(),
                            "kind": "RoadmapGateVisibilityError",
                            "summary": f"could not surface roadmap gate {node.node_id}",
                        }
                        self.state.save()
                    return False
                with self._state_lock:
                    _roadmap_mark_blocked_locked(
                        self,
                        state,
                        node.node_id,
                        node.human_message or "roadmap human gate",
                    )
                return False

            issue_state = _roadmap_issue_open(self, int(node.issue_number))
            if issue_state is None:
                with self._state_lock:
                    state["last_error"] = {
                        "at": core._utc_now(),
                        "kind": "RoadmapIssueLookupError",
                        "summary": f"could not verify roadmap issue #{node.issue_number}",
                    }
                    self.state.save()
                self._metric("roadmap_issue_lookup_errors")
                return False
            if issue_state is False:
                with self._state_lock:
                    completed = state.setdefault("completed_runs", {})
                    completed[node.node_id] = node.max_runs
                    state["last_completed_at"] = core._utc_now()
                    self.state.save()
                self._metric("roadmap_closed_issues_skipped")
                continue
            break

        with self._state_lock:
            if state.get("claims_day") != core._utc_day():
                state["claims_day"] = core._utc_day()
                state["claims_today"] = 0
            if int(state.get("claims_today") or 0) >= manifest.max_auto_claims_per_utc_day:
                state["last_error"] = {
                    "at": core._utc_now(),
                    "kind": "RoadmapDailyBound",
                    "summary": (
                        "bounded roadmap daily claim ceiling reached; waiting for next UTC day "
                        "or explicit task authority"
                    ),
                }
                self.state.save()
                self._metric("roadmap_daily_bound_hits")
                return False

            run_number = int((state.get("completed_runs") or {}).get(node.node_id) or 0) + 1
            event = _roadmap_event(manifest, node, run_number=run_number)
            state["active"] = {
                "node_id": node.node_id,
                "issue_number": node.issue_number,
                "lane": node.lane,
                "run_number": run_number,
                "seeded_at": core._utc_now(),
                "source_id": event.source_id,
                "event": event.to_state(),
                "pr_number": None,
            }
            state["claims_today"] = int(state.get("claims_today") or 0) + 1
            state["last_seeded_at"] = core._utc_now()
            state["last_error"] = None
            self.state.save()

        self.enqueue(event)
        self._metric("roadmap_tasks_seeded")
        print(
            f"[orchestrator] roadmap seeded {node.node_id} issue=#{node.issue_number} "
            f"run={run_number} trigger={trigger}",
            flush=True,
        )
        return True


def resume_pending(self: core.Orchestrator) -> None:
    _ORIGINAL_RESUME_PENDING(self)
    _roadmap_maybe_advance(self, trigger="startup-idle")


def _drain_and_dispatch(self: core.Orchestrator) -> None:
    try:
        _ORIGINAL_DRAIN_AND_DISPATCH(self)
    finally:
        try:
            _roadmap_maybe_advance(self, trigger="queue-drained")
        except Exception as exc:
            _roadmap_record_error(self, trigger="queue-drained", exc=exc)
            print(
                f"[orchestrator] bounded roadmap advance failed closed: "
                f"{type(exc).__name__}: {exc}",
                flush=True,
            )


def _run_periodic_reconcile(self: core.Orchestrator) -> None:
    _ORIGINAL_PERIODIC_RECONCILE(self)
    try:
        _roadmap_maybe_advance(self, trigger="periodic-idle")
    except Exception as exc:
        _roadmap_record_error(self, trigger="periodic-idle", exc=exc)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    with self._state_lock:
        state = self.state.data.get("roadmap")
        state = dict(state) if isinstance(state, dict) else {}
    snapshot["bounded_roadmap"] = {
        "enabled": _roadmap_enabled(),
        "manifest_path": MANIFEST_PATH,
        "roadmap_id": state.get("roadmap_id"),
        "manifest_fingerprint": state.get("manifest_fingerprint"),
        "active": state.get("active"),
        "completed_runs": state.get("completed_runs") or {},
        "blocked_nodes": state.get("blocked_nodes") or {},
        "claims_day": state.get("claims_day"),
        "claims_today": int(state.get("claims_today") or 0),
        "last_seeded_at": state.get("last_seeded_at"),
        "last_completed_at": state.get("last_completed_at"),
        "last_blocked_at": state.get("last_blocked_at"),
        "last_exhausted_at": state.get("last_exhausted_at"),
        "last_error": state.get("last_error"),
        "last_trigger": state.get("last_trigger"),
    }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_bounded_roadmap_extension_installed", False):
        return
    core.Orchestrator.resume_pending = resume_pending
    core.Orchestrator._drain_and_dispatch = _drain_and_dispatch
    core.Orchestrator._run_periodic_reconcile = _run_periodic_reconcile
    core.Orchestrator.health_snapshot = health_snapshot
    core.Orchestrator._roadmap_maybe_advance = _roadmap_maybe_advance
    core._skyforge_bounded_roadmap_extension_installed = True


install_extension()


def main() -> int:
    return replay_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
