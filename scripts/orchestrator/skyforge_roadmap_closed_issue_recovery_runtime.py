#!/usr/bin/env python3
"""Recovery extension for active roadmap tasks whose issue closes while paused.

The bounded-roadmap selector already treats a closed issue as complete before seeding new work.
This extension closes the matching live-state gap: if a roadmap task is already active, has no
controller-managed PR, and its authoritative issue becomes CLOSED while the controller is paused
or otherwise between dispatch cycles, reconciliation must not replay that stale authority.

Open-PR work continues through the existing PR/merge guards. Issue lookup uncertainty still fails
closed.
"""

from __future__ import annotations

from typing import Any

import skyforge_roadmap_runtime as roadmap_runtime

core = roadmap_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_roadmap_closed_issue_recovery_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_RESOLVE_ACTIVE = roadmap_runtime._roadmap_resolve_active


def _manifest_node(
    manifest: roadmap_runtime.roadmap_policy.RoadmapManifest,
    node_id: str,
) -> roadmap_runtime.roadmap_policy.RoadmapNode | None:
    return next((node for node in manifest.nodes if node.node_id == node_id), None)


def _matching_managed_pr(self: core.Orchestrator, issue_number: int) -> int | None:
    """Return a durable task-owned PR before considering issue-closure retirement.

    The base roadmap runtime deliberately binds a newly created managed PR before applying the global
    open-task guard. Preserve that race-safety invariant here: once a PR exists, its lifecycle is
    authoritative even if the issue closes concurrently.
    """
    authority_key = f"task:{int(issue_number)}"
    for record in roadmap_runtime._task_managed_records(self):
        if (
            str(record.get("authority_key") or "") == authority_key
            and record.get("pr_number")
        ):
            return int(record["pr_number"])
    return None


def _resolve_active_closed_issue(
    self: core.Orchestrator,
    manifest: roadmap_runtime.roadmap_policy.RoadmapManifest,
    state: dict[str, Any],
) -> bool:
    active = state.get("active")
    if not isinstance(active, dict):
        return _ORIGINAL_RESOLVE_ACTIVE(self, manifest, state)

    node_id = str(active.get("node_id") or "")
    issue_number = active.get("issue_number")

    # A bound PR remains the durable work surface and must follow the normal PR lifecycle.
    if active.get("pr_number") or not issue_number:
        return _ORIGINAL_RESOLVE_ACTIVE(self, manifest, state)

    # A just-created managed PR may exist before active.pr_number has been persisted. Delegate first
    # so the accepted binding-race behavior can attach it and enforce the normal PR lifecycle.
    if _matching_managed_pr(self, int(issue_number)) is not None:
        return _ORIGINAL_RESOLVE_ACTIVE(self, manifest, state)

    issue_open = roadmap_runtime._roadmap_issue_open(self, int(issue_number))
    if issue_open is None:
        state["last_error"] = {
            "at": core._utc_now(),
            "kind": "RoadmapIssueLookupError",
            "summary": (
                f"could not verify active roadmap issue #{issue_number}; "
                "refusing stale-authority retirement"
            ),
        }
        self.state.save()
        self._metric("roadmap_issue_lookup_errors")
        return True

    if issue_open is True:
        return _ORIGINAL_RESOLVE_ACTIVE(self, manifest, state)

    node = _manifest_node(manifest, node_id)
    if node is None:
        state["last_error"] = {
            "at": core._utc_now(),
            "kind": "RoadmapActiveNodeMissing",
            "summary": f"active roadmap node {node_id!r} is absent from the protected manifest",
        }
        self.state.save()
        self._metric("roadmap_advance_failures")
        return True

    completed = state.setdefault("completed_runs", {})
    completed[node.node_id] = max(int(completed.get(node.node_id) or 0), node.max_runs)
    state["active"] = None
    state["last_completed_at"] = core._utc_now()
    state["last_error"] = None
    state["last_closed_active_issue"] = {
        "at": core._utc_now(),
        "node_id": node.node_id,
        "issue_number": int(issue_number),
        "reason": "authoritative issue closed while roadmap node was active without a bound PR",
    }
    self.state.save()
    self._metric("roadmap_closed_active_issues_skipped")
    return False


def install_extension() -> None:
    if getattr(roadmap_runtime, "_skyforge_closed_active_issue_recovery_installed", False):
        return
    roadmap_runtime._roadmap_resolve_active = _resolve_active_closed_issue
    roadmap_runtime._skyforge_closed_active_issue_recovery_installed = True


install_extension()
