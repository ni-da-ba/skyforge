#!/usr/bin/env python3
"""Hosted Skyforge runtime with replay-safe controls, task-owned PRs, and guarded auto-merge.

The quota runtime installs the adaptive provider governor and merged-handoff retirement logic. This
final hosted wrapper adds three deliberately narrow control-plane protections:

1. replayed ``/skyforge-discard-worker`` controls are idempotent after their target is gone;
2. a new standalone task may not silently reuse an unrelated lane-level managed PR;
3. machine-only Implementation PRs may auto-merge only after an exact-head, live, model-free safety
   check. Human/visual/manual work, protected paths, unexpected head movement, review requirements,
   and changed-path drift remain hard merge blockers.

The controller remains the sole git/GitHub mutation authority. Workers still have no network access.
"""

from __future__ import annotations

import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import skyforge_quota_runtime as quota_runtime


core = quota_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_control_replay_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_DISCARD_PENDING_WORKER = core.Orchestrator.discard_pending_worker
_ORIGINAL_RESUME_OR_PREPARE_WORKER = core.Orchestrator._resume_or_prepare_worker
_ORIGINAL_HANDOFF_CHANGES = core.Orchestrator._handoff_changes

AUTO_MERGE_DENY_PREFIXES = (
    "scripts/orchestrator/",
    "deploy/orchestrator/",
    ".github/",
    "docs/reviews/",
    "assets/music/",
    "assets/audio/",
)
AUTO_MERGE_HUMAN_TERMS = (
    "human gate",
    "human_gate",
    "human review",
    "human-eye",
    "human eye",
    "owner review",
    "visual review",
    "manual review",
    "manual run",
    "manual gate",
    "audition",
    "listen for",
    "screenshot review",
    "approval required",
)


def _normalized_path(path: str) -> str:
    return str(path or "").replace("\\", "/").lstrip("./")


def _current_authority_identity(
    self: core.Orchestrator,
    source_pr: int | None = None,
) -> tuple[str | None, int | None]:
    """Return the durable task identity that owns a new worker branch.

    Explicit issue-backed task authority is strongest. An explicit source PR is continuation
    authority only when it already matches the lane's managed PR; otherwise it receives its own
    ``pr:<n>`` identity. Generic repository wakes intentionally return no reusable authority so they
    cannot inherit an unrelated lane PR.
    """
    record = self._decision_record() or {}
    task_issues = [
        int(value)
        for value in (record.get("task_issue_numbers") or [])
        if str(value).isdigit()
    ]
    if len(task_issues) == 1:
        issue = task_issues[0]
        return f"task:{issue}", issue

    if source_pr:
        managed_map = self.state.data.get("managed") or {}
        for value in managed_map.values():
            if not isinstance(value, dict):
                continue
            if int(value.get("pr_number") or 0) == int(source_pr):
                key = str(value.get("authority_key") or "").strip()
                issue = value.get("authority_issue")
                return (key or f"pr:{int(source_pr)}"), (int(issue) if str(issue).isdigit() else None)
        return f"pr:{int(source_pr)}", None

    return None, None


def _managed_record_matches_authority(
    managed: dict[str, Any] | None,
    *,
    authority_key: str | None,
    source_pr: int | None,
) -> bool:
    if not isinstance(managed, dict):
        return False
    managed_pr = int(managed.get("pr_number") or 0)
    if source_pr and managed_pr == int(source_pr):
        return True
    if authority_key:
        return str(managed.get("authority_key") or "") == authority_key
    return False


def _prepare_worker_branch(
    self: core.Orchestrator,
    lane: str,
    source_pr: int | None,
) -> tuple[str, int | None, Path]:
    """Reuse a managed PR only for the same durable authority or explicit PR repair.

    This closes the lane-level ownership hole exposed when task #467 was appended to stale HS-03
    draft PR #465 merely because both were Implementation work.
    """
    authority_key, authority_issue = _current_authority_identity(self, source_pr)
    managed = self._validated_managed_branch(lane)
    if managed and managed.get("branch") and _managed_record_matches_authority(
        managed,
        authority_key=authority_key,
        source_pr=source_pr,
    ):
        branch = str(managed["branch"])
        core._run(["git", "fetch", "origin", branch], cwd=self.root, timeout=120)
        worktree = self._ensure_worker_worktree(branch, f"origin/{branch}")
        return branch, managed.get("pr_number"), worktree

    if managed and managed.get("branch"):
        self._metric("cross_authority_pr_reuse_prevented")
        print(
            f"[orchestrator] refused cross-authority reuse of {lane} PR "
            f"#{managed.get('pr_number')}: managed={managed.get('authority_key')!r}, "
            f"current={authority_key!r}",
            flush=True,
        )

    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    lane_slug = re.sub(r"[^a-z0-9]+", "-", lane.lower()).strip("-") or "lane"
    authority_slug = (
        f"-task-{authority_issue}"
        if authority_issue is not None
        else ""
    )
    branch = f"codex/{lane_slug}{authority_slug}-{stamp}"
    worktree = self._ensure_worker_worktree(branch, "origin/main")

    if source_pr:
        try:
            pr = core._json_cmd(
                [
                    "gh", "pr", "view", str(source_pr),
                    "--repo", self.repo,
                    "--json", "headRefName",
                ],
                cwd=self.root,
            )
            old_branch = pr.get("headRefName")
            if old_branch:
                core._run(["git", "fetch", "origin", old_branch], cwd=self.root, timeout=120)
        except Exception:
            pass
    return branch, None, worktree


def _resume_or_prepare_worker(
    self: core.Orchestrator,
    lane: str,
    source_pr: int | None,
    objective: str,
    worker_tier: str,
    allowed_paths: list[str] | None,
) -> tuple[str, int | None, Path]:
    authority_key, authority_issue = _current_authority_identity(self, source_pr)
    pending = self.state.data.get("pending_worker")
    if isinstance(pending, dict) and pending.get("branch"):
        stored = str(pending.get("authority_key") or "").strip() or None
        if stored and authority_key and stored != authority_key:
            raise core.SafetyPause(
                "Interrupted worker authority no longer matches the cached dispatch; "
                "refusing cross-task branch reuse"
            )

    result = _ORIGINAL_RESUME_OR_PREPARE_WORKER(
        self,
        lane,
        source_pr,
        objective,
        worker_tier,
        allowed_paths,
    )
    with self._state_lock:
        current = self.state.data.get("pending_worker")
        if isinstance(current, dict):
            if authority_key:
                current["authority_key"] = authority_key
            if authority_issue is not None:
                current["authority_issue"] = authority_issue
            self.state.save()
    return result


def _machine_only_auto_merge_candidate(
    *,
    lane: str,
    decision: dict[str, Any] | None,
    paths: list[str],
    authority_key: str | None,
    prior_eligible: bool = False,
) -> bool:
    if str(lane or "").strip().lower() != "implementation":
        return False
    if not authority_key:
        return False

    normalized_paths = [_normalized_path(path) for path in paths]
    if any(
        any(path.startswith(prefix) for prefix in AUTO_MERGE_DENY_PREFIXES)
        for path in normalized_paths
    ):
        return False

    decision = decision if isinstance(decision, dict) else {}
    text = " ".join(
        str(decision.get(key) or "")
        for key in (
            "objective",
            "stop_boundary",
            "reason",
            "human_message",
            "reusable_evidence",
        )
    ).lower()
    if any(term in text for term in AUTO_MERGE_HUMAN_TERMS):
        return False

    # CI/debug repair of an already-eligible controller PR remains eligible. A fresh task is eligible
    # only when its durable issue authority is explicit.
    return prior_eligible or authority_key.startswith("task:")


def _handoff_changes(
    self: core.Orchestrator,
    lane: str,
    objective: str,
    branch: str,
    managed_pr: int | None,
    worker_summary: str,
    allowed_paths: list[str] | None = None,
    worker_root: Path | None = None,
) -> bool:
    worktree = worker_root or self.root
    pending = self.state.data.get("pending_worker")
    authority_key = (
        str(pending.get("authority_key") or "").strip()
        if isinstance(pending, dict)
        else ""
    ) or None
    authority_issue = (
        pending.get("authority_issue")
        if isinstance(pending, dict)
        else None
    )

    pre_paths = self._changed_paths(worktree)
    prior = self._managed_branch(lane)
    prior_paths = [
        str(value)
        for value in ((prior or {}).get("changed_paths") or [])
        if str(value)
    ]
    prior_eligible = bool((prior or {}).get("auto_merge_eligible"))

    created = _ORIGINAL_HANDOFF_CHANGES(
        self,
        lane,
        objective,
        branch,
        managed_pr,
        worker_summary,
        allowed_paths,
        worktree,
    )
    if not created:
        return False

    expected_head = core._run(
        ["git", "rev-parse", "HEAD"],
        cwd=worktree,
    ).stdout.strip()
    record = self._decision_record() or {}
    decision = record.get("decision")
    all_paths = sorted(set(prior_paths) | set(pre_paths))
    eligible = _machine_only_auto_merge_candidate(
        lane=lane,
        decision=decision if isinstance(decision, dict) else None,
        paths=all_paths,
        authority_key=authority_key,
        prior_eligible=prior_eligible,
    )

    with self._state_lock:
        managed = self._managed_branch(lane)
        if (
            not managed
            or str(managed.get("branch") or "") != branch
        ):
            raise RuntimeError("Managed PR ownership changed during authority metadata handoff")
        managed["authority_key"] = authority_key
        managed["authority_issue"] = authority_issue
        managed["expected_head"] = expected_head
        managed["changed_paths"] = all_paths
        managed["auto_merge_eligible"] = eligible
        managed["auto_merge_policy"] = "machine-only-implementation-v1"
        self.state.save()
    return True


def _post_auto_merge_gate(
    self: core.Orchestrator,
    *,
    lane: str,
    pr_number: int,
    reason: str,
) -> None:
    self._metric("safe_auto_merge_human_gates")
    self._post_gate(
        {
            "decision": "HUMAN_GATE",
            "lane": lane,
            "pr_number": pr_number,
            "reason": reason,
            "human_message": (
                f"Controller-managed PR #{pr_number} is machine-ready but is outside the "
                f"machine-only auto-merge policy: {reason}. Review/merge manually when appropriate."
            ),
        }
    )


def _merge_managed(self: core.Orchestrator, lane: str, pr_number: int | None) -> None:
    """Merge only an exact expected head owned by one machine-only Implementation authority."""
    if not self.auto_merge:
        print("[orchestrator] MERGE selected, but safe auto-merge is disabled", flush=True)
        return

    managed = self._managed_branch(lane)
    if not managed or int(managed.get("pr_number") or 0) != int(pr_number or 0):
        raise RuntimeError("Refusing to merge a PR not owned by the local orchestrator state")

    number = int(pr_number or 0)
    authority_key = str(managed.get("authority_key") or "").strip()
    expected_head = str(managed.get("expected_head") or "").strip()
    branch = str(managed.get("branch") or "").strip()
    if not bool(managed.get("auto_merge_eligible")):
        _post_auto_merge_gate(
            self,
            lane=lane,
            pr_number=number,
            reason="task/lane/path/human-gate policy did not mark this PR auto-merge eligible",
        )
        return
    if not authority_key or not expected_head or not branch:
        raise RuntimeError("Refusing auto-merge without durable authority, branch, and expected head")

    with self._state_lock:
        if isinstance(self.state.data.get("pending_worker"), dict):
            raise RuntimeError("Refusing auto-merge while a worker still owns mutable handoff state")

    def read_pr() -> dict[str, Any]:
        return core._json_cmd(
            [
                "gh", "pr", "view", str(number),
                "--repo", self.repo,
                "--json",
                (
                    "isDraft,mergeStateStatus,statusCheckRollup,state,"
                    "headRefOid,headRefName,baseRefName,reviewDecision"
                ),
            ],
            cwd=self.root,
            timeout=60,
        )

    pr = read_pr()
    if str(pr.get("state") or "").upper() != "OPEN":
        return
    if str(pr.get("baseRefName") or "") != "main":
        raise RuntimeError("Refusing auto-merge for a PR not targeting main")
    if str(pr.get("headRefName") or "") != branch:
        raise RuntimeError("Refusing auto-merge after managed branch identity drift")
    if str(pr.get("headRefOid") or "") != expected_head:
        raise RuntimeError("Refusing auto-merge after unexpected PR head movement")

    review = str(pr.get("reviewDecision") or "").upper()
    if review in {"CHANGES_REQUESTED", "REVIEW_REQUIRED"}:
        _post_auto_merge_gate(
            self,
            lane=lane,
            pr_number=number,
            reason=f"GitHub review decision is {review}",
        )
        return

    checks = pr.get("statusCheckRollup") or []
    if not checks:
        raise RuntimeError("Refusing auto-merge without any machine check evidence")
    bad: list[str] = []
    active: list[str] = []
    successful = 0
    for check in checks:
        status = str(check.get("status") or "").upper()
        conclusion = str(check.get("conclusion") or "").upper()
        name = str(check.get("name") or check.get("context") or "check")
        if status != "COMPLETED":
            active.append(name)
        elif conclusion in {"SUCCESS", "SKIPPED", "NEUTRAL"}:
            if conclusion == "SUCCESS":
                successful += 1
        else:
            bad.append(f"{name}:{conclusion}")
    if active or bad or successful <= 0:
        raise RuntimeError(
            f"Managed PR not machine-green; active={active}, bad={bad}, successes={successful}"
        )

    live_paths = sorted(
        {
            line.strip()
            for line in core._run(
                ["gh", "pr", "diff", str(number), "--repo", self.repo, "--name-only"],
                cwd=self.root,
                timeout=60,
            ).stdout.splitlines()
            if line.strip()
        }
    )
    recorded_paths = {
        _normalized_path(path)
        for path in (managed.get("changed_paths") or [])
        if str(path).strip()
    }
    unexpected = [
        path
        for path in live_paths
        if _normalized_path(path) not in recorded_paths
    ]
    denied = [
        path
        for path in live_paths
        if any(_normalized_path(path).startswith(prefix) for prefix in AUTO_MERGE_DENY_PREFIXES)
    ]
    if unexpected or denied:
        raise RuntimeError(
            f"Refusing auto-merge after changed-path drift; unexpected={unexpected}, denied={denied}"
        )

    if pr.get("isDraft"):
        core._run(
            ["gh", "pr", "ready", str(number), "--repo", self.repo],
            cwd=self.root,
            timeout=60,
        )
        pr = read_pr()
        if str(pr.get("headRefOid") or "") != expected_head:
            raise RuntimeError("Refusing auto-merge because PR head changed while leaving draft")
        if pr.get("isDraft"):
            raise RuntimeError("Refusing auto-merge because PR remained draft")

    merge_state = str(pr.get("mergeStateStatus") or "").upper()
    if merge_state != "CLEAN":
        raise RuntimeError(f"Refusing auto-merge because merge state is {merge_state or 'UNKNOWN'}")

    # GitHub CLI performs the final compare-and-swap against the expected commit, closing the tiny race
    # between the last read and the merge mutation itself.
    core._run(
        [
            "gh", "pr", "merge", str(number),
            "--repo", self.repo,
            "--merge",
            "--match-head-commit", expected_head,
        ],
        cwd=self.root,
        timeout=120,
    )
    with self._state_lock:
        current = self._managed_branch(lane)
        if (
            current
            and int(current.get("pr_number") or 0) == number
            and str(current.get("expected_head") or "") == expected_head
        ):
            self.state.data.setdefault("managed", {}).pop(lane, None)
            self.state.save()
    self._metric("managed_merges")
    self._metric("safe_auto_merges")
    print(
        f"[orchestrator] safely auto-merged managed PR #{number} at {expected_head[:12]}",
        flush=True,
    )


def discard_pending_worker(self: core.Orchestrator, *, actor: str | None = None) -> None:
    """Acknowledge a replayed discard after its target worker is already gone.

    The paused requirement remains authoritative. Existing pending workers still delegate to the
    quota runtime's normal guarded discard/merged-handoff retirement implementation.
    """
    with self._state_lock:
        if not self.state.data.get("paused"):
            raise RuntimeError("Worker discard requires the controller to be paused")
        pending = self.state.data.get("pending_worker")
        if isinstance(pending, dict):
            delegate = True
        else:
            delegate = False
            self.state.data["last_worker_discard_noop"] = {
                "at": core._utc_now(),
                "actor": actor,
                "reason": "discard control replayed after pending worker was already retired",
            }
            self.state.save()

    if delegate:
        _ORIGINAL_DISCARD_PENDING_WORKER(self, actor=actor)
        return

    self._metric("operator_discard_noops")


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(quota_runtime.health_snapshot(self))
    with self._state_lock:
        snapshot["last_worker_discard_noop"] = self.state.data.get("last_worker_discard_noop")
        managed = self.state.data.get("managed") or {}
        snapshot["safe_auto_merge"] = {
            "enabled": bool(getattr(self, "auto_merge", False)),
            "policy": "machine-only-implementation-v1",
            "managed": {
                lane: {
                    "pr_number": value.get("pr_number"),
                    "authority_key": value.get("authority_key"),
                    "expected_head": value.get("expected_head"),
                    "auto_merge_eligible": value.get("auto_merge_eligible"),
                }
                for lane, value in managed.items()
                if isinstance(value, dict)
            },
        }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_control_replay_extension_installed", False):
        return
    core.Orchestrator.discard_pending_worker = discard_pending_worker
    core.Orchestrator.health_snapshot = health_snapshot
    core.Orchestrator._prepare_worker_branch = _prepare_worker_branch
    core.Orchestrator._resume_or_prepare_worker = _resume_or_prepare_worker
    core.Orchestrator._handoff_changes = _handoff_changes
    core.Orchestrator._merge_managed = _merge_managed
    core._skyforge_control_replay_extension_installed = True


install_extension()


def main() -> int:
    # This hosted wrapper contains the safety policy, so machine-only auto-merge is on by default here.
    # Operators can fail closed without changing the unit by setting SKYFORGE_SAFE_AUTO_MERGE=0.
    if os.environ.get("SKYFORGE_SAFE_AUTO_MERGE", "1") == "1":
        os.environ["SKYFORGE_ORCHESTRATOR_AUTO_MERGE"] = "1"
    else:
        os.environ["SKYFORGE_ORCHESTRATOR_AUTO_MERGE"] = "0"
    return quota_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
