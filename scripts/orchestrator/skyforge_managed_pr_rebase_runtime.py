#!/usr/bin/env python3
"""Model-free current-main refresh for controller-managed PRs.

Parallel manual producers may advance main while a hosted worker is editing or while its PR is in CI.
GitHub can then report a task-owned managed PR as BEHIND or DIRTY. A model worker cannot repair Git
ancestry safely, and repeatedly asking it to do so wastes quota.

Before classifier/worker spend, this extension attempts one deterministic rebase for an exact,
controller-owned PR whose expected head has not moved. A clean rebase is pushed with force-with-lease
and the managed expected-head/path record is updated. A genuine rebase conflict is aborted locally and
safety-pauses the controller before any model call; semantic overlap then becomes an explicit repair
boundary rather than a retry loop.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_managed_pr_rebase_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_DISPATCH = core.Orchestrator.dispatch
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot

REBASE_STATES = {"BEHIND", "DIRTY"}


def _managed_records(self: core.Orchestrator) -> list[tuple[str, dict[str, Any]]]:
    with self._state_lock:
        return [
            (str(lane), dict(value))
            for lane, value in (self.state.data.get("managed") or {}).items()
            if isinstance(value, dict)
            and value.get("pr_number")
            and value.get("branch")
            and value.get("expected_head")
        ]


def _record_rebase_event(
    self: core.Orchestrator,
    key: str,
    payload: dict[str, Any],
) -> None:
    with self._state_lock:
        self.state.data[key] = {"at": core._utc_now(), **payload}
        self.state.save()


def _refresh_one_managed_pr(
    self: core.Orchestrator,
    lane: str,
    managed: dict[str, Any],
) -> bool:
    number = int(managed.get("pr_number") or 0)
    branch = str(managed.get("branch") or "").strip()
    expected_head = str(managed.get("expected_head") or "").strip()
    if number <= 0 or not branch or not expected_head:
        return False

    pr = core._json_cmd(
        [
            "gh", "pr", "view", str(number),
            "--repo", self.repo,
            "--json", "state,baseRefName,headRefName,headRefOid,mergeStateStatus",
        ],
        cwd=self.root,
        timeout=60,
    )
    if str(pr.get("state") or "").upper() != "OPEN":
        return False
    if str(pr.get("baseRefName") or "") != "main":
        return False
    if str(pr.get("headRefName") or "") != branch:
        raise core.SafetyPause(
            f"managed PR #{number} branch identity drifted before model-free base refresh"
        )
    live_head = str(pr.get("headRefOid") or "")
    if live_head != expected_head:
        raise core.SafetyPause(
            f"managed PR #{number} head moved outside controller ownership before base refresh"
        )
    merge_state = str(pr.get("mergeStateStatus") or "").upper()
    if merge_state not in REBASE_STATES:
        return False

    with self._state_lock:
        if isinstance(self.state.data.get("pending_worker"), dict):
            # Never rewrite ancestry beneath a live isolated worker. The next pass after handoff may
            # refresh the PR if main moved during that worker's execution.
            return False

    core._run(["git", "fetch", "--prune", "origin"], cwd=self.root, timeout=180)
    core._run(["git", "fetch", "origin", branch], cwd=self.root, timeout=120)
    worktree: Path = self._ensure_worker_worktree(branch, f"origin/{branch}")
    try:
        if not self._worktree_clean(worktree):
            raise core.SafetyPause(
                f"managed PR #{number} rebase worktree is unexpectedly dirty"
            )
        local_head = core._run(
            ["git", "rev-parse", "HEAD"], cwd=worktree
        ).stdout.strip()
        if local_head != expected_head:
            raise core.SafetyPause(
                f"managed PR #{number} local head {local_head[:12]} != expected {expected_head[:12]}"
            )

        rebase = core._run(
            ["git", "rebase", "origin/main"],
            cwd=worktree,
            check=False,
            timeout=300,
        )
        if rebase.returncode != 0:
            core._run(["git", "rebase", "--abort"], cwd=worktree, check=False, timeout=60)
            _record_rebase_event(
                self,
                "last_managed_pr_rebase_conflict",
                {
                    "lane": lane,
                    "pr_number": number,
                    "branch": branch,
                    "expected_head": expected_head,
                    "merge_state": merge_state,
                    "summary": (rebase.stderr or rebase.stdout or "rebase conflict")[-1500:],
                },
            )
            self._metric("managed_pr_rebase_conflicts")
            self.set_paused(True, actor="managed-pr-rebase-conflict")
            self._post_gate(
                {
                    "decision": "HUMAN_GATE",
                    "lane": lane,
                    "pr_number": number,
                    "reason": "controller-managed PR conflicts with current main",
                    "human_message": (
                        f"SAFETY PAUSE: controller-managed PR #{number} no longer rebases cleanly "
                        "onto current main after parallel repository progress. The rebase was aborted "
                        "without changing the remote branch. Resolve the concrete overlap once; do "
                        "not spend worker turns retrying Git ancestry."
                    ),
                }
            )
            raise core.SafetyPause(
                f"managed PR #{number} rebase conflicts with current main; controller paused"
            )

        new_head = core._run(
            ["git", "rev-parse", "HEAD"], cwd=worktree
        ).stdout.strip()
        if new_head == expected_head:
            return False

        # Compare-and-swap push: refuse to overwrite any head movement not made by this controller.
        core._run(
            [
                "git", "push",
                f"--force-with-lease=refs/heads/{branch}:{expected_head}",
                "origin", f"HEAD:refs/heads/{branch}",
            ],
            cwd=worktree,
            timeout=180,
        )
        changed_paths = sorted(
            {
                line.strip()
                for line in core._run(
                    ["git", "diff", "--name-only", "origin/main...HEAD"],
                    cwd=worktree,
                ).stdout.splitlines()
                if line.strip()
            }
        )
        with self._state_lock:
            current = (self.state.data.get("managed") or {}).get(lane)
            if not isinstance(current, dict):
                raise core.SafetyPause(
                    f"managed ownership disappeared while rebasing PR #{number}"
                )
            if int(current.get("pr_number") or 0) != number:
                raise core.SafetyPause(
                    f"managed ownership changed while rebasing PR #{number}"
                )
            if str(current.get("expected_head") or "") != expected_head:
                raise core.SafetyPause(
                    f"managed expected head changed while rebasing PR #{number}"
                )
            current["expected_head"] = new_head
            current["changed_paths"] = changed_paths
            current["rebased_from_head"] = expected_head
            current["rebased_at"] = core._utc_now()
            self.state.data["last_managed_pr_rebase"] = {
                "at": core._utc_now(),
                "lane": lane,
                "pr_number": number,
                "branch": branch,
                "from_head": expected_head,
                "to_head": new_head,
                "prior_merge_state": merge_state,
                "changed_paths": changed_paths,
            }
            self.state.save()
        self._metric("managed_pr_rebases")
        return True
    finally:
        # Successful rebase leaves a clean worktree. Conflict path aborts before reaching here. If
        # cleanup itself fails, preserve the durable managed record and let existing worktree recovery
        # safeguards surface the anomaly rather than deleting dirty state blindly.
        if worktree.exists() and self._worktree_clean(worktree):
            try:
                self._retire_worker_worktree(worktree)
            except Exception:
                self._metric("managed_pr_rebase_cleanup_failures")


def _refresh_managed_pr_bases(self: core.Orchestrator) -> int:
    with self._state_lock:
        if isinstance(self.state.data.get("pending_worker"), dict):
            return 0
    refreshed = 0
    for lane, managed in _managed_records(self):
        try:
            if _refresh_one_managed_pr(self, lane, managed):
                refreshed += 1
        except core.SafetyPause:
            raise
        except Exception as exc:
            _record_rebase_event(
                self,
                "last_managed_pr_rebase_error",
                {
                    "lane": lane,
                    "pr_number": managed.get("pr_number"),
                    "branch": managed.get("branch"),
                    "kind": type(exc).__name__,
                    "summary": str(exc)[:1000],
                },
            )
            self._metric("managed_pr_rebase_errors")
            # Visibility/transport failure is not permission to mutate. Let the ordinary controller
            # continue; its current-state guards remain authoritative and will fail closed if needed.
    return refreshed


def _dispatch(self: core.Orchestrator, events: list[core.EventDecision]) -> None:
    # Reconcile Git ancestry before any classifier/worker call. If a branch advances, Actions will
    # rerun on the new exact head and ordinary quiescence/merge policy takes over.
    refreshed = _refresh_managed_pr_bases(self)
    if refreshed:
        self._metric("managed_pr_rebase_dispatch_deferrals")
        self._schedule_pending(max(30, self.debounce_seconds))
        return
    _ORIGINAL_DISPATCH(self, events)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    with self._state_lock:
        snapshot["managed_pr_base_refresh"] = {
            "last_rebase": self.state.data.get("last_managed_pr_rebase"),
            "last_conflict": self.state.data.get("last_managed_pr_rebase_conflict"),
            "last_error": self.state.data.get("last_managed_pr_rebase_error"),
        }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_managed_pr_rebase_extension_installed", False):
        return
    core.Orchestrator.dispatch = _dispatch
    core.Orchestrator.health_snapshot = health_snapshot
    core.Orchestrator.refresh_managed_pr_bases = _refresh_managed_pr_bases
    core._skyforge_managed_pr_rebase_extension_installed = True


install_extension()
