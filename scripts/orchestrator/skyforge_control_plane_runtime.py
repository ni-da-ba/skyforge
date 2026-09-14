#!/usr/bin/env python3
"""Skyforge hosted control-plane runtime.

The DigitalOcean host is orchestration infrastructure only.  It may classify,
reconcile, journal, coordinate GitHub state, and resume a *completed* legacy
handoff, but it must never create or resume a project-editing worker/worktree.

Project development and automated validation belong to GitHub/GitHub Actions.
Manual/interactive verification belongs to Nicholas' local workstation.  See
``docs/agent-state/EXECUTION_BOUNDARIES.md``.
"""

from __future__ import annotations

from typing import Any

import skyforge_roadmap_runtime as roadmap_runtime


core = roadmap_runtime.core
_ORIGINAL_RESUME_OR_PREPARE_WORKER = core.Orchestrator._resume_or_prepare_worker
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def _control_plane_only_resume_or_prepare_worker(
    self: core.Orchestrator,
    lane: str,
    source_pr: int | None,
    objective: str,
    worker_tier: str,
    allowed_paths: list[str] | None,
):
    """Fail closed before any hosted project worker/worktree can be created.

    A pre-existing worker that already reached durable ``handoff`` is permitted
    to pass through only so the controller can finish commit/push/PR transport.
    That stage performs no project development and is needed for crash-safe
    recovery.  An ``editing`` worker is never resumed under this runtime.
    """

    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        pending = dict(pending) if isinstance(pending, dict) else None

    if pending and pending.get("stage") == "handoff":
        return _ORIGINAL_RESUME_OR_PREPARE_WORKER(
            self,
            lane,
            source_pr,
            objective,
            worker_tier,
            allowed_paths,
        )

    task_issue = None
    with self._state_lock:
        decision_record = self.state.data.get("pending_decision")
        if isinstance(decision_record, dict):
            values = decision_record.get("task_issue_numbers") or []
            if values:
                try:
                    task_issue = int(values[0])
                except (TypeError, ValueError):
                    task_issue = None

    target = task_issue or source_pr
    message = (
        "EXECUTION BOUNDARY: this DigitalOcean service is the Skyforge "
        "orchestration/control plane only. The requested bounded work must be "
        "performed by a GitHub-backed producer, with automated build/test "
        "evidence in GitHub Actions. No project worker/worktree was created on "
        "the Droplet. Manual/interactive gates remain local-workstation only."
    )

    # Surface the boundary through the existing durable human-gate mechanism.
    # This is deliberately done before pausing so an operator can see exactly
    # why progress stopped and route the task to a GitHub-backed producer.
    try:
        self._post_gate(
            {
                "decision": "HUMAN_GATE",
                "lane": lane,
                "pr_number": source_pr,
                "reason": "DigitalOcean control-plane-only execution boundary",
                "human_message": (
                    f"{message}" + (f" Governing issue: #{target}." if target else "")
                ),
            }
        )
    finally:
        self.set_paused(True, actor="execution-boundary")

    raise core.SafetyPause(message)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    snapshot["execution_boundary"] = {
        "mode": "digitalocean-control-plane-only",
        "hosted_project_workers_enabled": False,
        "automated_project_execution": "github-actions",
        "manual_verification": "nicholas-local-workstation",
    }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_control_plane_only_extension_installed", False):
        return
    core.Orchestrator._resume_or_prepare_worker = _control_plane_only_resume_or_prepare_worker
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_control_plane_only_extension_installed = True


install_extension()


def main() -> int:
    return roadmap_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
