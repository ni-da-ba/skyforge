#!/usr/bin/env python3
"""Recovery extension for stuck open managed handoffs.

Normal discard remains strict. This extension adds one explicit paused-only recovery case:
a worker that already reached ``handoff`` for an OPEN controller-managed PR may be detached
without merging or closing that PR. The remote PR remains the durable shared surface while
all local committed/uncommitted worker deltas are archived before the isolated worktree is
removed. Pending classifier state is cleared so current repository evidence can be
reclassified on resume.
"""

from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

import skyforge_control_replay_base as replay_base

core = replay_base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_handoff_recovery_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_DISCARD_PENDING_WORKER = core.Orchestrator.discard_pending_worker


def _managed_lane_for_pr(self: core.Orchestrator, pr_number: int) -> tuple[str | None, dict[str, Any] | None]:
    managed = self.state.data.get("managed") or {}
    for lane, value in managed.items():
        if isinstance(value, dict) and int(value.get("pr_number") or 0) == pr_number:
            return str(lane), value
    return None, None


def _worktree_path(self: core.Orchestrator, pending: dict[str, Any]) -> Path:
    raw = pending.get("worktree")
    if not raw:
        raise RuntimeError("Refusing open-handoff recovery without an isolated worktree")
    path = Path(str(raw))
    if not path.is_absolute():
        path = self.root / path
    if path.resolve() == self.root.resolve():
        raise RuntimeError("Refusing to recover the controller checkout as a worker")
    if not path.exists():
        raise RuntimeError(f"Pending worker worktree is missing: {path}")
    return path


def _archive_open_handoff(
    self: core.Orchestrator,
    pending: dict[str, Any],
    *,
    actor: str | None,
    pr: dict[str, Any],
) -> tuple[list[str], Path, Path]:
    pr_number = int(pending.get("managed_pr") or 0)
    worktree = _worktree_path(self, pending)
    changed_paths = self._changed_paths(worktree)
    worker_head = core._run(["git", "rev-parse", "HEAD"], cwd=worktree).stdout.strip()
    pr_head = str(pr.get("headRefOid") or "").strip()

    recovery_dir = self.root / core.STATE_DIR / "recovery"
    recovery_dir.mkdir(parents=True, exist_ok=True)
    stamp = str(int(time.time()))
    prefix = recovery_dir / f"managed-pr-{pr_number}-open-handoff-{stamp}"
    patch_path = prefix.with_suffix(".patch")
    metadata_path = prefix.with_suffix(".json")

    parts: list[str] = []
    if pr_head and worker_head != pr_head:
        committed = core._run(
            ["git", "diff", "--binary", pr_head, worker_head],
            cwd=worktree,
            check=False,
        ).stdout
        if committed:
            parts.append(f"# committed local delta: {pr_head}..{worker_head}\n{committed}")
    working = core._run(
        ["git", "diff", "--binary", "HEAD"],
        cwd=worktree,
        check=False,
    ).stdout
    if working:
        parts.append(f"# uncommitted local delta at {worker_head}\n{working}")
    patch_path.write_text("\n".join(parts))

    metadata = {
        "archived_at": core._utc_now(),
        "actor": actor,
        "managed_pr": pr_number,
        "branch": pending.get("branch"),
        "worker_stage": pending.get("stage"),
        "worker_head": worker_head,
        "pr_head": pr_head,
        "pr_state": pr.get("state"),
        "changed_paths": changed_paths[:100],
        "reason": "operator detached stuck open managed handoff; PR preserved for clean reclassification",
    }
    metadata_path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n")
    return changed_paths, patch_path, metadata_path


def _detach_open_managed_handoff(
    self: core.Orchestrator,
    pending: dict[str, Any],
    *,
    actor: str | None,
) -> None:
    if pending.get("stage") != "handoff":
        raise RuntimeError("Open managed handoff recovery is allowed only at durable handoff stage")
    try:
        pr_number = int(pending.get("managed_pr") or 0)
    except (TypeError, ValueError):
        pr_number = 0
    if pr_number <= 0:
        raise RuntimeError("Open managed handoff is missing a valid PR number")

    lane, managed = _managed_lane_for_pr(self, pr_number)
    if not lane or not managed:
        raise RuntimeError("Refusing open-handoff recovery for a PR not owned by orchestrator state")
    if str(managed.get("branch") or "") != str(pending.get("branch") or ""):
        raise RuntimeError("Refusing open-handoff recovery after managed branch identity drift")

    pr = core._json_cmd(
        [
            "gh", "pr", "view", str(pr_number),
            "--repo", self.repo,
            "--json", "state,mergedAt,headRefName,headRefOid",
        ],
        cwd=self.root,
        timeout=60,
    )
    state = str(pr.get("state") or "").upper()
    if state == "MERGED" and pr.get("mergedAt"):
        _ORIGINAL_DISCARD_PENDING_WORKER(self, actor=actor)
        return
    if state != "OPEN":
        raise RuntimeError(
            f"Refusing open-handoff recovery because PR #{pr_number} state is {state or 'UNKNOWN'}"
        )
    if str(pr.get("headRefName") or "") != str(pending.get("branch") or ""):
        raise RuntimeError("Refusing open-handoff recovery after live PR branch identity drift")

    worktree = _worktree_path(self, pending)
    changed_paths, patch_path, metadata_path = _archive_open_handoff(
        self,
        pending,
        actor=actor,
        pr=pr,
    )

    core._run(
        ["git", "worktree", "remove", "--force", str(worktree)],
        cwd=self.root,
        timeout=120,
    )
    core._run(["git", "worktree", "prune"], cwd=self.root, check=False)

    with self._state_lock:
        current = self.state.data.get("pending_worker")
        if not isinstance(current, dict) or current.get("branch") != pending.get("branch"):
            raise RuntimeError("Pending worker ownership changed during open-handoff recovery")
        self.state.data["pending_worker"] = None
        self.state.data["pending_decision"] = None
        self.state.data["blocked_until_epoch"] = 0.0
        self.state.data["blocked_kind"] = None
        self.state.data["blocked_reason"] = None
        self.state.data["last_worker_discard"] = {
            "discarded_at": core._utc_now(),
            "discarded_by": actor,
            "branch": pending.get("branch"),
            "stage": pending.get("stage"),
            "managed_pr": pr_number,
            "changed_paths": changed_paths[:50],
            "preserved_patch": str(patch_path.relative_to(self.root)),
            "preserved_metadata": str(metadata_path.relative_to(self.root)),
            "reason": "stuck open managed handoff detached; PR retained for clean repair reclassification",
        }
        self.state.data["last_periodic_reconcile_error"] = None
        self.state.save()
    self._metric("operator_open_handoff_detachments")


def discard_pending_worker(self: core.Orchestrator, *, actor: str | None = None) -> None:
    with self._state_lock:
        if not self.state.data.get("paused"):
            raise RuntimeError("Worker discard requires the controller to be paused")
        pending = self.state.data.get("pending_worker")
        if not isinstance(pending, dict):
            _ORIGINAL_DISCARD_PENDING_WORKER(self, actor=actor)
            return
        pending = dict(pending)

    if pending.get("stage") == "handoff" and pending.get("managed_pr"):
        with self._dispatch_lock:
            _detach_open_managed_handoff(self, pending, actor=actor)
        return

    _ORIGINAL_DISCARD_PENDING_WORKER(self, actor=actor)


def install_extension() -> None:
    if getattr(core, "_skyforge_open_handoff_recovery_installed", False):
        return
    core.Orchestrator.discard_pending_worker = discard_pending_worker
    core._skyforge_open_handoff_recovery_installed = True


install_extension()
