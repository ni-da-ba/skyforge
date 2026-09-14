#!/usr/bin/env python3
"""Skyforge hosted control-plane runtime.

The DigitalOcean host is orchestration infrastructure only. It may classify,
reconcile, journal, coordinate GitHub state, and resume a *completed* legacy
handoff, but it must never create or resume a project-editing worker/worktree.

Issue-backed project development is delegated to a GitHub cloud coding agent.
That agent executes in GitHub's Actions-backed execution plane; automated build
and test evidence remains in GitHub Actions. Manual/interactive verification
belongs to Nicholas' local workstation. See
``docs/agent-state/EXECUTION_BOUNDARIES.md``.
"""

from __future__ import annotations

import json
import os
import subprocess
from typing import Any

import skyforge_roadmap_runtime as roadmap_runtime


core = roadmap_runtime.core
_ORIGINAL_RESUME_OR_PREPARE_WORKER = core.Orchestrator._resume_or_prepare_worker
_ORIGINAL_DISPATCH = core.Orchestrator.dispatch
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot
_ORIGINAL_ROADMAP_RESOLVE_ACTIVE = roadmap_runtime._roadmap_resolve_active

GITHUB_AGENT_LOGIN = os.environ.get(
    "SKYFORGE_GITHUB_AGENT_LOGIN",
    "copilot-swe-agent[bot]",
).strip()
GITHUB_AGENT_LOGINS = {
    GITHUB_AGENT_LOGIN.lower(),
    "copilot-swe-agent",
    "copilot-swe-agent[bot]",
}


class GitHubAgentDispatched(Exception):
    """Internal non-error control flow after a durable GitHub agent handoff."""


def _task_issue_from_decision(self: core.Orchestrator) -> int | None:
    with self._state_lock:
        decision_record = self.state.data.get("pending_decision")
        if not isinstance(decision_record, dict):
            return None
        values = decision_record.get("task_issue_numbers") or []
    if len(values) != 1:
        return None
    try:
        return int(values[0])
    except (TypeError, ValueError):
        return None


def _current_decision(self: core.Orchestrator) -> dict[str, Any]:
    with self._state_lock:
        record = self.state.data.get("pending_decision")
        if not isinstance(record, dict):
            return {}
        decision = record.get("decision")
        return dict(decision) if isinstance(decision, dict) else {}


def _github_agent_assigned(self: core.Orchestrator, issue_number: int) -> bool:
    issue = core._json_cmd(
        [
            "gh", "issue", "view", str(issue_number),
            "--repo", self.repo,
            "--json", "assignees,state",
        ],
        cwd=self.root,
        timeout=60,
    )
    if str(issue.get("state") or "").upper() != "OPEN":
        return False
    for value in issue.get("assignees") or []:
        login = str((value or {}).get("login") or "").strip().lower()
        if login in GITHUB_AGENT_LOGINS:
            return True
    return False


def _assign_github_agent(
    self: core.Orchestrator,
    *,
    issue_number: int,
    lane: str,
    objective: str,
    stop_boundary: str,
) -> None:
    """Assign one bounded issue to GitHub's cloud coding execution plane.

    GitHub's documented REST contract uses ``copilot-swe-agent[bot]`` plus an
    optional ``agent_assignment`` object. The command is executed by ``gh`` on
    the control plane, but the coding session itself is GitHub-hosted.
    """

    if not GITHUB_AGENT_LOGIN:
        raise RuntimeError("SKYFORGE_GITHUB_AGENT_LOGIN is empty")

    if not _github_agent_assigned(self, issue_number):
        instructions = (
            "Skyforge execution boundary: perform all project edits and automated verification "
            "inside GitHub's cloud execution plane. Read AGENTS.md and "
            "docs/agent-state/EXECUTION_BOUNDARIES.md first. Do not use the DigitalOcean host for "
            "project development or tests. Work only on this issue's accepted authority.\n\n"
            f"Lane: {lane}\n"
            f"Bounded objective: {objective}\n"
            f"Stop boundary: {stop_boundary}\n"
            "Leave any genuinely manual/visual/interactive judgment as a local-workstation human gate."
        )
        payload = {
            "assignees": [GITHUB_AGENT_LOGIN],
            "agent_assignment": {
                "target_repo": self.repo,
                "base_branch": "main",
                "custom_instructions": instructions[:8000],
                "custom_agent": "",
                "model": "",
            },
        }
        command = [
            "gh", "api", "--method", "POST",
            "-H", "Accept: application/vnd.github+json",
            "-H", "X-GitHub-Api-Version: 2022-11-28",
            f"repos/{self.repo}/issues/{issue_number}/assignees",
            "--input", "-",
        ]
        completed = subprocess.run(
            command,
            cwd=self.root,
            input=json.dumps(payload),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=120,
            check=False,
        )
        if completed.returncode != 0:
            summary = (completed.stderr or completed.stdout or "GitHub agent assignment failed").strip()
            raise RuntimeError(summary[:1200])

    authority_key = f"task:{issue_number}"
    with self._state_lock:
        records = self.state.data.setdefault("github_agent_dispatches", {})
        prior = records.get(authority_key)
        prior = dict(prior) if isinstance(prior, dict) else {}
        records[authority_key] = {
            **prior,
            "authority_key": authority_key,
            "issue_number": issue_number,
            "lane": lane,
            "objective": objective,
            "stop_boundary": stop_boundary,
            "agent_login": GITHUB_AGENT_LOGIN,
            "assigned_at": prior.get("assigned_at") or core._utc_now(),
            "last_confirmed_at": core._utc_now(),
            "state": "assigned",
        }
        self.state.save()

    body = (
        f"{core.SELF_COMMENT_MARKER} GITHUB_AGENT_DISPATCH\n\n"
        f"Issue #{issue_number} has been handed to GitHub's cloud coding execution plane.\n\n"
        f"**Lane:** {lane}\n\n"
        f"**Objective:** {objective}\n\n"
        f"**Stop boundary:** {stop_boundary}\n\n"
        "The DigitalOcean Droplet remains orchestration/control-plane only. Project edits and "
        "automated verification belong to GitHub/GitHub Actions; any manual/interactive gate remains "
        "on Nicholas' local workstation."
    )
    try:
        core._run(
            [
                "gh", "issue", "comment", str(issue_number),
                "--repo", self.repo,
                "--body", body,
            ],
            cwd=self.root,
            timeout=60,
        )
    except Exception as exc:
        print(
            f"[orchestrator] GitHub agent assignment succeeded but visibility comment failed: "
            f"{type(exc).__name__}: {exc}",
            flush=True,
        )


def _github_agent_linked_pr(self: core.Orchestrator, issue_number: int) -> int | None:
    """Find a PR cross-referenced from the dispatched issue after assignment."""

    authority_key = f"task:{issue_number}"
    with self._state_lock:
        record = (self.state.data.get("github_agent_dispatches") or {}).get(authority_key)
        record = dict(record) if isinstance(record, dict) else {}
    if record.get("pr_number"):
        try:
            return int(record["pr_number"])
        except (TypeError, ValueError):
            pass

    try:
        timeline = core._json_cmd(
            [
                "gh", "api",
                "-H", "Accept: application/vnd.github+json",
                f"repos/{self.repo}/issues/{issue_number}/timeline?per_page=100",
            ],
            cwd=self.root,
            timeout=60,
        )
    except Exception as exc:
        print(
            f"[orchestrator] could not inspect issue #{issue_number} timeline for cloud-agent PR: "
            f"{type(exc).__name__}: {exc}",
            flush=True,
        )
        return None

    assigned_at = str(record.get("assigned_at") or "")
    candidates: list[tuple[str, int]] = []
    for event in timeline if isinstance(timeline, list) else []:
        if not isinstance(event, dict) or str(event.get("event") or "") != "cross-referenced":
            continue
        created_at = str(event.get("created_at") or "")
        if assigned_at and created_at and created_at < assigned_at:
            continue
        source_issue = ((event.get("source") or {}).get("issue") or {})
        if not isinstance(source_issue, dict) or not source_issue.get("pull_request"):
            continue
        number = source_issue.get("number")
        try:
            candidates.append((created_at, int(number)))
        except (TypeError, ValueError):
            continue

    if not candidates:
        return None
    candidates.sort()
    return candidates[-1][1]


def _control_plane_roadmap_resolve_active(
    self: core.Orchestrator,
    manifest,
    state: dict[str, Any],
) -> bool:
    """Keep roadmap ownership with an active GitHub cloud-agent dispatch."""

    active = state.get("active")
    if isinstance(active, dict) and active.get("issue_number"):
        issue_number = int(active["issue_number"])
        authority_key = f"task:{issue_number}"
        with self._state_lock:
            records = self.state.data.get("github_agent_dispatches") or {}
            dispatch = records.get(authority_key)
            dispatch = dict(dispatch) if isinstance(dispatch, dict) else None

        if dispatch:
            pr_number = _github_agent_linked_pr(self, issue_number)
            if pr_number:
                with self._state_lock:
                    current_records = self.state.data.setdefault("github_agent_dispatches", {})
                    current = current_records.get(authority_key)
                    if isinstance(current, dict):
                        current["pr_number"] = pr_number
                        current["state"] = "pr_open"
                        current["last_confirmed_at"] = core._utc_now()
                    active["pr_number"] = pr_number
                    self.state.save()
                self._metric("github_agent_pr_bindings")
                return _ORIGINAL_ROADMAP_RESOLVE_ACTIVE(self, manifest, state)

            try:
                assigned = _github_agent_assigned(self, issue_number)
            except Exception as exc:
                state["last_error"] = {
                    "at": core._utc_now(),
                    "kind": type(exc).__name__,
                    "summary": "GitHub cloud-agent assignment state could not be verified",
                }
                self.state.save()
                return True

            if not assigned:
                try:
                    _assign_github_agent(
                        self,
                        issue_number=issue_number,
                        lane=str(dispatch.get("lane") or active.get("lane") or "Implementation"),
                        objective=str(dispatch.get("objective") or "Resume the bounded issue objective."),
                        stop_boundary=str(dispatch.get("stop_boundary") or "Honor the issue stop boundary."),
                    )
                except Exception as exc:
                    state["last_error"] = {
                        "at": core._utc_now(),
                        "kind": type(exc).__name__,
                        "summary": f"GitHub cloud-agent assignment unavailable: {exc}"[:1000],
                    }
                    self.state.save()
                    return True
            return True

    return _ORIGINAL_ROADMAP_RESOLVE_ACTIVE(self, manifest, state)


def _control_plane_only_resume_or_prepare_worker(
    self: core.Orchestrator,
    lane: str,
    source_pr: int | None,
    objective: str,
    worker_tier: str,
    allowed_paths: list[str] | None,
):
    """Delegate issue-backed work to GitHub and reject hosted project workers.

    A pre-existing worker that already reached durable ``handoff`` may finish
    controller-owned git/PR transport. An ``editing`` worker is never resumed by
    this runtime; legacy editing state must first be evacuated by infrastructure
    recovery.
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

    if pending:
        message = (
            "EXECUTION BOUNDARY: a pre-contract DigitalOcean editing worker is still attached. "
            "Evacuate/retire that recovery state before GitHub-backed dispatch. No hosted worker was resumed."
        )
        self.set_paused(True, actor="execution-boundary")
        raise core.SafetyPause(message)

    task_issue = _task_issue_from_decision(self)
    decision = _current_decision(self)
    stop_boundary = str(decision.get("stop_boundary") or "").strip()

    if task_issue is not None and source_pr is None:
        try:
            _assign_github_agent(
                self,
                issue_number=task_issue,
                lane=lane,
                objective=objective,
                stop_boundary=stop_boundary or "Honor the governing issue's stop boundary.",
            )
        except Exception as exc:
            message = (
                "GITHUB EXECUTION PLANE BLOCKED: the controller could not assign the bounded issue "
                f"to GitHub's cloud coding agent: {exc}"
            )
            try:
                self._post_gate(
                    {
                        "decision": "HUMAN_GATE",
                        "lane": lane,
                        "pr_number": None,
                        "reason": "GitHub cloud-agent dispatch unavailable",
                        "human_message": (
                            f"{message} Governing issue: #{task_issue}. Enable/authorize the GitHub "
                            "cloud coding agent for this repository, then resume orchestration."
                        ),
                    }
                )
            finally:
                self.set_paused(True, actor="github-execution-plane")
            raise core.SafetyPause(message) from exc

        raise GitHubAgentDispatched(f"task:{task_issue}")

    target = task_issue or source_pr
    message = (
        "EXECUTION BOUNDARY: this DigitalOcean service is the Skyforge orchestration/control plane only. "
        "The requested work is not an issue-backed GitHub-agent dispatch, so no project worker/worktree "
        "was created on the Droplet."
    )
    try:
        self._post_gate(
            {
                "decision": "HUMAN_GATE",
                "lane": lane,
                "pr_number": source_pr,
                "reason": "DigitalOcean control-plane-only execution boundary",
                "human_message": f"{message}" + (f" Governing target: #{target}." if target else ""),
            }
        )
    finally:
        self.set_paused(True, actor="execution-boundary")
    raise core.SafetyPause(message)


def dispatch(self: core.Orchestrator, events) -> None:
    try:
        _ORIGINAL_DISPATCH(self, events)
    except GitHubAgentDispatched as exc:
        # The issue assignment is the durable producer handoff. Retire the classifier-owned event;
        # roadmap ownership remains represented independently by github_agent_dispatches until the
        # linked PR reaches a terminal state.
        self._clear_completed_decision()
        self._metric("github_agent_dispatches")
        print(f"[orchestrator] GitHub execution-plane handoff complete: {exc}", flush=True)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    with self._state_lock:
        dispatches = {
            str(key): dict(value)
            for key, value in (self.state.data.get("github_agent_dispatches") or {}).items()
            if isinstance(value, dict)
        }
    snapshot["execution_boundary"] = {
        "mode": "digitalocean-control-plane-only",
        "hosted_project_workers_enabled": False,
        "automated_project_execution": "github-cloud-agent-and-actions",
        "github_agent_login": GITHUB_AGENT_LOGIN,
        "github_agent_dispatches": dispatches,
        "manual_verification": "nicholas-local-workstation",
    }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_control_plane_only_extension_installed", False):
        return
    core.Orchestrator._resume_or_prepare_worker = _control_plane_only_resume_or_prepare_worker
    core.Orchestrator.dispatch = dispatch
    core.Orchestrator.health_snapshot = health_snapshot
    roadmap_runtime._roadmap_resolve_active = _control_plane_roadmap_resolve_active
    core._skyforge_control_plane_only_extension_installed = True


install_extension()


def main() -> int:
    return roadmap_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
