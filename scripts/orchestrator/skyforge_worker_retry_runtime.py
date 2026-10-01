#!/usr/bin/env python3
"""Hosted worker retry/context guard.

A pending ``editing`` worker is durable repository state. Preserve its Codex thread as well as its
worktree so a process restart/retry does not pay to reconstruct the same task from scratch. Also fail
closed after repeated interrupted/failed attempts that make no durable worktree progress, and bound
total provider-admitted model turns per task so partial progress cannot evade the retry circuit.

The controller remains the only network/git mutation authority. This extension changes only worker
turn lifecycle and telemetry; handoff, path, validation, roadmap, and provider pacing policy remain
unchanged.
"""

from __future__ import annotations

import hashlib
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_worker_retry_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot
_ORIGINAL_CONSUME_BUDGET = core.Orchestrator._consume_budget


def _max_no_progress_attempts() -> int:
    return core._env_int(
        "SKYFORGE_WORKER_MAX_NO_PROGRESS_ATTEMPTS",
        2,
        minimum=1,
    )


def _max_model_turns_per_worker() -> int:
    return core._env_int(
        "SKYFORGE_WORKER_MAX_MODEL_TURNS_PER_TASK",
        4,
        minimum=1,
    )


def _progress_stall_seconds() -> int:
    return core._env_int(
        "SKYFORGE_WORKER_PROGRESS_STALL_SECONDS",
        900,
        minimum=60,
    )


PATCH_BEGIN = "SKYFORGE_PATCH_BEGIN"
PATCH_END = "SKYFORGE_PATCH_END"
PATCH_SUMMARY = "SKYFORGE_WORKER_SUMMARY"


PATCH_WORKER_INSTRUCTIONS = core.WORKER_INSTRUCTIONS + f"""

HOSTED MUTATION TRANSPORT OVERRIDE:
The hosted worktree is read-only to you. The outer controller, not the model sandbox, owns all
repository mutation. Therefore do NOT attempt apply_patch, redirection/file writes, chmod, mv/cp, or
other filesystem mutation even though the base worker instructions describe local edits. Instead,
inspect the repository and produce the complete proposed local change as a git-style unified diff in
your final response using exactly these markers:

{PATCH_SUMMARY}
<concise implementation/verification summary or precise blocker>
{PATCH_BEGIN}
<complete git-style unified diff beginning with diff --git lines; empty only for a genuine blocker>
{PATCH_END}

Do not wrap the patch in Markdown fences. Do not include content after the patch terminator. You may
run read-only inspection commands. Verification that requires mutation or generated build outputs must
be left to the outer controller/GitHub Actions.
"""


def _hosted_worker_codex_config(config_type):
    """Use ordinary Codex configuration for read-only hosted reasoning.

    Repository mutation is controller-owned: the worker returns a unified patch and never needs
    Codex's host-dependent workspace-write sandbox. This avoids both Ubuntu user-namespace failures
    in bubblewrap and the deprecated legacy-Landlock compatibility path.
    """
    return config_type()


def _extract_controller_patch(response: str) -> tuple[str, str | None]:
    """Extract one controller-applied unified patch from a worker response."""
    text = str(response or "")
    if PATCH_BEGIN not in text and PATCH_END not in text:
        return text.strip(), None
    if text.count(PATCH_BEGIN) != 1 or text.count(PATCH_END) != 1:
        raise ValueError("worker patch response must contain exactly one patch marker pair")
    before, remainder = text.split(PATCH_BEGIN, 1)
    patch, after = remainder.split(PATCH_END, 1)
    if after.strip():
        raise ValueError("worker patch response contains trailing content after patch terminator")
    summary = before.replace(PATCH_SUMMARY, "", 1).strip()
    patch = patch.strip("\n")
    return summary, (patch + "\n" if patch.strip() else None)


def _patch_paths(patch: str) -> list[str]:
    paths: list[str] = []
    for line in patch.splitlines():
        if not line.startswith("diff --git "):
            continue
        match = re.fullmatch(r"diff --git a/([^\t\r\n]+) b/([^\t\r\n]+)", line)
        if match is None:
            raise ValueError(f"unsupported git patch header: {line[:200]}")
        for raw in match.groups():
            normalized = raw.replace("\\", "/")
            if (
                not normalized
                or normalized.startswith("/")
                or normalized.startswith("../")
                or "/../" in normalized
                or normalized == ".."
            ):
                raise ValueError(f"unsafe patch path: {raw}")
            paths.append(normalized)
    if patch.strip() and not paths:
        raise ValueError("worker returned non-empty content without git diff headers")
    return sorted(set(paths))


def _apply_controller_patch(
    self: core.Orchestrator,
    root: Path,
    patch: str,
    *,
    lane: str | None,
    allowed_paths: list[str] | None,
) -> list[str]:
    """Validate and apply a model-produced patch using controller repository authority only."""
    paths = _patch_paths(patch)
    forbidden = [
        path for path in paths
        if self._worker_path_forbidden(path, lane=lane, allowed_paths=allowed_paths)
    ]
    out_of_scope = [path for path in paths if not self._worker_path_allowed(path, allowed_paths)]
    if forbidden or out_of_scope:
        raise core.SafetyPause(
            f"controller-applied worker patch rejected: forbidden={forbidden}, "
            f"out_of_scope={out_of_scope}"
        )
    for args in (["git", "apply", "--check", "--whitespace=error-all", "-"], ["git", "apply", "-"]):
        completed = subprocess.run(
            args, cwd=root, input=patch, text=True, capture_output=True, timeout=60, check=False
        )
        if completed.returncode != 0:
            raise ValueError(
                f"controller git apply failed ({' '.join(args)}): "
                f"{(completed.stderr or completed.stdout).strip()[:2000]}"
            )
    changed = sorted(set(self._changed_paths(root)))
    if not changed:
        raise ValueError("controller applied worker patch but worktree remained clean")
    unexpected = [path for path in changed if path not in paths]
    if unexpected:
        raise core.SafetyPause(
            f"controller-applied worker patch changed paths absent from patch headers: {unexpected}"
        )
    self._metric("worker_controller_patches_applied")
    return changed


def _age_seconds(raw: Any, *, now: datetime | None = None) -> float | None:
    if not raw:
        return None
    try:
        parsed = datetime.fromisoformat(str(raw).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        current = now or datetime.now(timezone.utc)
        return max(0.0, (current - parsed.astimezone(timezone.utc)).total_seconds())
    except (TypeError, ValueError):
        return None


def _worker_root_matches(pending: dict[str, Any], worker_root: Path) -> bool:
    raw = pending.get("worktree")
    if not raw:
        return worker_root.resolve() == Path.cwd().resolve()
    path = Path(str(raw))
    if not path.is_absolute():
        # Pending worker paths are normally absolute. A relative legacy path is rooted at the
        # controller checkout; callers resolve it before invoking the worker.
        return path.name == worker_root.name
    return path.resolve() == worker_root.resolve()


def _worktree_fingerprint(self: core.Orchestrator, worker_root: Path) -> str:
    """Hash HEAD plus changed-path content, including untracked files.

    ``git diff`` alone misses edits to an already-untracked file. Hash every changed regular file
    through git's object hashing and retain a missing/non-file marker for deletions/directories.
    """
    head = core._run(["git", "rev-parse", "HEAD"], cwd=worker_root).stdout.strip()
    rows = [f"HEAD\t{head}"]
    for path in sorted(set(self._changed_paths(worker_root))):
        target = worker_root / path
        if target.is_file():
            digest = core._run(
                ["git", "hash-object", "--", path],
                cwd=worker_root,
            ).stdout.strip()
        elif target.exists():
            digest = "<non-file>"
        else:
            digest = "<missing>"
        rows.append(f"{path}\t{digest}")
    return hashlib.sha256("\n".join(rows).encode("utf-8")).hexdigest()


def _worktree_progress_summary(self: core.Orchestrator, worker_root: Path) -> dict[str, Any]:
    fingerprint = _worktree_fingerprint(self, worker_root)
    changed_paths = sorted(set(self._changed_paths(worker_root)))
    additions = 0
    deletions = 0
    diff = core._run(
        ["git", "diff", "--numstat", "HEAD", "--"],
        cwd=worker_root,
    ).stdout.splitlines()
    for row in diff:
        parts = row.split("\t", 2)
        if len(parts) < 2:
            continue
        if parts[0].isdigit():
            additions += int(parts[0])
        if parts[1].isdigit():
            deletions += int(parts[1])
    return {
        "fingerprint": fingerprint,
        "dirty_file_count": len(changed_paths),
        "changed_paths": changed_paths[:20],
        "diff_additions": additions,
        "diff_deletions": deletions,
    }


def _matching_pending(self: core.Orchestrator, worker_root: Path) -> dict[str, Any] | None:
    pending = self.state.data.get("pending_worker")
    if not isinstance(pending, dict) or pending.get("stage") != "editing":
        return None
    if not _worker_root_matches(pending, worker_root):
        return None
    return pending


def _trip_stall_circuit(
    self: core.Orchestrator,
    pending: dict[str, Any],
    *,
    stalled_attempts: int,
) -> None:
    with self._state_lock:
        current = self.state.data.get("pending_worker")
        if isinstance(current, dict) and current.get("branch") == pending.get("branch"):
            current["worker_retry_circuit_open"] = True
            current["worker_retry_circuit_at"] = core._utc_now()
            current["worker_stalled_attempts"] = stalled_attempts
            self.state.data["last_worker_retry_circuit"] = {
                "at": current["worker_retry_circuit_at"],
                "branch": current.get("branch"),
                "lane": current.get("lane"),
                "attempt_count": int(current.get("worker_attempt_count") or 0),
                "model_turn_count": int(current.get("worker_model_turn_count") or 0),
                "stalled_attempts": stalled_attempts,
                "reason": "repeated worker attempts made no durable worktree progress",
            }
            self.state.save()
    self._metric("worker_retry_circuit_pauses")
    self.set_paused(True, actor="worker-retry-circuit")
    self._post_gate(
        {
            "human_message": (
                "WORKER RETRY CIRCUIT: hosted worker "
                f"{pending.get('lane') or 'unknown'} / {pending.get('branch') or 'unknown'} "
                f"made no durable worktree progress across {stalled_attempts} interrupted/failed "
                "attempts. The controller paused before spending another worker turn. The isolated "
                "worktree and task authority are preserved for inspection/recovery."
            )
        }
    )
    raise core.SafetyPause(
        "worker retry circuit opened after repeated no-progress attempts; controller safety-paused"
    )


def _trip_turn_cap(
    self: core.Orchestrator,
    pending: dict[str, Any],
    *,
    model_turns: int,
    limit: int,
) -> None:
    with self._state_lock:
        current = self.state.data.get("pending_worker")
        if isinstance(current, dict) and current.get("branch") == pending.get("branch"):
            current["worker_retry_circuit_open"] = True
            current["worker_retry_circuit_at"] = core._utc_now()
            self.state.data["last_worker_retry_circuit"] = {
                "at": current["worker_retry_circuit_at"],
                "branch": current.get("branch"),
                "lane": current.get("lane"),
                "attempt_count": int(current.get("worker_attempt_count") or 0),
                "model_turn_count": model_turns,
                "stalled_attempts": int(current.get("worker_stalled_attempts") or 0),
                "reason": f"per-worker provider model-turn cap reached ({model_turns}/{limit})",
            }
            self.state.save()
    self._metric("worker_model_turn_cap_pauses")
    self.set_paused(True, actor="worker-turn-cap")
    self._post_gate(
        {
            "human_message": (
                "WORKER TURN CAP: hosted worker "
                f"{pending.get('lane') or 'unknown'} / {pending.get('branch') or 'unknown'} "
                f"has already consumed {model_turns} provider-admitted model turns (cap {limit}) "
                "without reaching durable handoff. The controller paused before another model turn. "
                "The isolated worktree, reusable thread, and task authority are preserved."
            )
        }
    )
    raise core.SafetyPause(
        "worker provider model-turn cap reached; controller safety-paused before another turn"
    )


def _consume_budget(self: core.Orchestrator, kind: str) -> None:
    """Apply provider pacing plus a durable per-worker model-turn ceiling.

    The cap is checked before calling the accepted provider governor. The per-worker count increments
    only after that governor admits the turn, so quota-pacing deferrals do not consume the task cap.
    """
    if kind not in {"worker", "luna_worker"}:
        _ORIGINAL_CONSUME_BUDGET(self, kind)
        return

    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        pending_snapshot = dict(pending) if isinstance(pending, dict) and pending.get("stage") == "editing" else None
        model_turns = int((pending_snapshot or {}).get("worker_model_turn_count") or 0)
    limit = _max_model_turns_per_worker()
    if pending_snapshot is not None and model_turns >= limit:
        _trip_turn_cap(self, pending_snapshot, model_turns=model_turns, limit=limit)

    # Authoritative provider pacing/local fallback remains wholly owned by the prior runtime.
    _ORIGINAL_CONSUME_BUDGET(self, kind)

    if pending_snapshot is None:
        return
    with self._state_lock:
        current = self.state.data.get("pending_worker")
        if (
            isinstance(current, dict)
            and current.get("stage") == "editing"
            and current.get("branch") == pending_snapshot.get("branch")
        ):
            current["worker_model_turn_count"] = int(current.get("worker_model_turn_count") or 0) + 1
            current["last_worker_model_turn_admitted_at"] = core._utc_now()
            self.state.save()


def _begin_worker_attempt(
    self: core.Orchestrator,
    worker_root: Path,
) -> tuple[str, dict[str, Any] | None]:
    fingerprint = _worktree_fingerprint(self, worker_root)
    with self._state_lock:
        pending = _matching_pending(self, worker_root)
        if pending is None:
            return fingerprint, None

        stalled = int(pending.get("worker_stalled_attempts") or 0)
        prior_result = str(pending.get("last_worker_attempt_result") or "")
        prior_before = str(pending.get("last_worker_attempt_before_fingerprint") or "")
        if prior_result == "in_flight":
            # The process died/interrupted after the turn was admitted. Repository progress is the
            # only durable evidence that the interrupted attempt achieved anything useful.
            if prior_before and fingerprint == prior_before:
                stalled += 1
            else:
                stalled = 0
            pending["last_worker_attempt_result"] = "interrupted"
            pending["last_worker_attempt_finished_at"] = core._utc_now()
            pending["last_worker_attempt_after_fingerprint"] = fingerprint
            pending["worker_stalled_attempts"] = stalled
            self.state.save()

        limit = _max_no_progress_attempts()
        if stalled >= limit:
            snapshot = dict(pending)
        else:
            pending["worker_attempt_count"] = int(pending.get("worker_attempt_count") or 0) + 1
            pending["worker_stalled_attempts"] = stalled
            pending["last_worker_attempt_started_at"] = core._utc_now()
            pending["last_worker_attempt_before_fingerprint"] = fingerprint
            pending["last_worker_attempt_after_fingerprint"] = None
            pending["last_worker_attempt_result"] = "in_flight"
            pending["last_worker_attempt_failure_kind"] = None
            # A prior pre-handoff compile/diff failure is the reason this new turn exists. Clear the
            # current repair marker when admitting the repair attempt so that, if the model finishes
            # and the process dies before validation, its saved response can be replayed without
            # buying yet another turn. Historical error telemetry remains at controller scope.
            pending["last_pre_handoff_validation_error"] = None
            pending["worker_retry_circuit_open"] = False
            self.state.save()
            self._metric("worker_retry_guard_admitted_attempts")
            return fingerprint, dict(pending)

    _trip_stall_circuit(self, snapshot, stalled_attempts=stalled)
    raise AssertionError("unreachable")


def _record_worker_result(
    self: core.Orchestrator,
    worker_root: Path,
    *,
    before_fingerprint: str,
    result: str,
    failure_kind: str | None = None,
    response: str | None = None,
) -> None:
    try:
        after = _worktree_fingerprint(self, worker_root)
    except Exception:
        after = before_fingerprint
    with self._state_lock:
        pending = _matching_pending(self, worker_root)
        if pending is None:
            return
        stalled = int(pending.get("worker_stalled_attempts") or 0)
        progressed = after != before_fingerprint
        provider_blocks = {"quota", "quota_pacing", "rate_limit", "authentication"}
        if result == "failed" and failure_kind not in provider_blocks:
            stalled = 0 if progressed else stalled + 1
        elif progressed:
            stalled = 0
        pending["worker_stalled_attempts"] = stalled
        pending["last_worker_attempt_after_fingerprint"] = after
        pending["last_worker_attempt_finished_at"] = core._utc_now()
        pending["last_worker_attempt_result"] = result
        pending["last_worker_attempt_failure_kind"] = failure_kind
        if response is not None:
            pending["last_worker_response"] = str(response)[:8000]
        self.state.save()
    if progressed:
        self._metric("worker_attempts_with_durable_progress")
    elif result == "failed" and failure_kind not in provider_blocks:
        self._metric("worker_attempts_without_durable_progress")


def _persist_worker_thread_id(
    self: core.Orchestrator,
    worker_root: Path,
    thread_id: str,
) -> None:
    with self._state_lock:
        pending = _matching_pending(self, worker_root)
        if pending is None:
            return
        pending["worker_thread_id"] = thread_id
        pending["worker_thread_updated_at"] = core._utc_now()
        self.state.save()


def _clear_worker_thread_id(self: core.Orchestrator, worker_root: Path) -> None:
    with self._state_lock:
        pending = _matching_pending(self, worker_root)
        if pending is None:
            return
        pending["worker_thread_id"] = None
        pending["worker_thread_updated_at"] = core._utc_now()
        self.state.save()


def _worker(
    self: core.Orchestrator,
    prompt: str,
    worker_tier: str,
    worker_root: Path | None = None,
) -> str:
    root = (worker_root or self.root).resolve()

    # A process can die after the model returned but before _mark_worker_handoff persisted the next
    # stage. Reuse the already-durable response rather than buying the same turn again. A current
    # pre-handoff validation error is different: the worker must re-enter the same thread to repair
    # the rejected output instead of replaying the response that produced it.
    with self._state_lock:
        pending = _matching_pending(self, root)
        if (
            pending is not None
            and pending.get("last_worker_attempt_result") == "completed"
            and pending.get("last_worker_response")
            and not pending.get("last_pre_handoff_validation_error")
        ):
            response = str(pending["last_worker_response"])
            self._metric("worker_completed_response_replays")
            return response

    before, _pending_snapshot = _begin_worker_attempt(self, root)

    tier = str(worker_tier or "TERRA").strip().upper()
    if tier == "LUNA":
        budget_kind = "luna_worker"
        model = os.environ.get(
            "SKYFORGE_LUNA_WORKER_MODEL",
            os.environ.get("SKYFORGE_ORCHESTRATOR_MODEL", "gpt-5.6-luna"),
        )
        effort = os.environ.get("SKYFORGE_LUNA_WORKER_REASONING", "low")
    elif tier == "TERRA":
        budget_kind = "worker"
        model = os.environ.get("SKYFORGE_WORKER_MODEL", "gpt-5.6-terra")
        effort = os.environ.get("SKYFORGE_WORKER_REASONING", "medium")
    else:
        _record_worker_result(
            self,
            root,
            before_fingerprint=before,
            result="failed",
            failure_kind="invalid_tier",
        )
        raise RuntimeError(f"Unknown worker tier: {tier}")

    try:
        # The wrapped budget method checks the per-worker hard cap before provider admission, then
        # increments the durable model-turn count only after the provider governor allows the turn.
        self._consume_budget(budget_kind)
        from openai_codex import Codex, CodexConfig, Sandbox

        with Codex(_hosted_worker_codex_config(CodexConfig)) as codex:
            thread_id = None
            with self._state_lock:
                current = _matching_pending(self, root)
                if current is not None:
                    thread_id = str(current.get("worker_thread_id") or "").strip() or None

            if thread_id:
                try:
                    thread = codex.thread_resume(
                        thread_id,
                        cwd=str(root),
                        model=model,
                        config={"model_reasoning_effort": effort},
                        sandbox=Sandbox.read_only,
                        developer_instructions=PATCH_WORKER_INSTRUCTIONS,
                    )
                    self._metric("worker_thread_resumes")
                except Exception as exc:
                    # Do not start a replacement thread in the same admitted attempt: that can turn
                    # one provider failure into two chargeable operations. Clear the stale identity
                    # and let the ordinary retry/backoff path create one replacement later.
                    _clear_worker_thread_id(self, root)
                    kind, retry = core._codex_failure_policy(exc)
                    raise core.RetryBlocked(
                        kind,
                        retry,
                        f"{tier.lower()} worker thread resume failed; durable worktree preserved",
                    ) from exc
            else:
                # Deliberately non-ephemeral: the thread id is part of the durable pending-worker
                # record and can be resumed after service/process interruption.
                thread = codex.thread_start(
                    cwd=str(root),
                    model=model,
                    config={"model_reasoning_effort": effort},
                    sandbox=Sandbox.read_only,
                    developer_instructions=PATCH_WORKER_INSTRUCTIONS,
                )
                _persist_worker_thread_id(self, root, str(thread.id))
                self._metric("worker_thread_starts")

            patch_prompt = prompt + f"""

Return the bounded result using the controller-applied patch format required by your developer
instructions. The outer controller will validate scope and apply any non-empty patch.
"""
            result_obj = thread.run(patch_prompt, sandbox=Sandbox.read_only)
            raw_response = str(result_obj.final_response)
            response, patch = _extract_controller_patch(raw_response)
            if patch is not None:
                with self._state_lock:
                    current = _matching_pending(self, root)
                    lane = str((current or {}).get("lane") or "") or None
                    raw_scope = (current or {}).get("allowed_paths")
                    allowed_paths = (
                        [str(value) for value in raw_scope]
                        if isinstance(raw_scope, list)
                        else None
                    )
                applied_paths = _apply_controller_patch(
                    self, root, patch, lane=lane, allowed_paths=allowed_paths
                )
                response = (
                    response + "\n\nController-applied patch paths: " + ", ".join(applied_paths)
                ).strip()
            _record_worker_result(
                self,
                root,
                before_fingerprint=before,
                result="completed",
                response=response,
            )
            return response
    except core.SafetyPause:
        raise
    except core.RetryBlocked as exc:
        _record_worker_result(
            self,
            root,
            before_fingerprint=before,
            result="failed",
            failure_kind=exc.kind,
        )
        raise
    except Exception as exc:
        kind, retry = core._codex_failure_policy(exc)
        _record_worker_result(
            self,
            root,
            before_fingerprint=before,
            result="failed",
            failure_kind=kind,
        )
        raise core.RetryBlocked(
            kind,
            retry,
            f"{tier.lower()} worker call failed; durable worktree/thread preserved",
        ) from exc


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = _ORIGINAL_HEALTH_SNAPSHOT(self)
    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        if not isinstance(pending, dict):
            return snapshot
        pending_snapshot = dict(pending)

    progress: dict[str, Any] | None = None
    worktree_raw = pending_snapshot.get("worktree")
    if worktree_raw and pending_snapshot.get("stage") == "editing":
        try:
            progress = _worktree_progress_summary(self, Path(str(worktree_raw)))
        except Exception as exc:
            progress = {"error": f"{type(exc).__name__}: {exc}"[:500]}

    now = datetime.now(timezone.utc)
    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        if not isinstance(pending, dict):
            return snapshot

        if progress and "fingerprint" in progress:
            current_fp = str(progress["fingerprint"])
            prior_fp = str(pending.get("worker_observed_fingerprint") or "")
            dirty_count = int(progress.get("dirty_file_count") or 0)
            if prior_fp and current_fp != prior_fp:
                pending["worker_last_progress_at"] = now.isoformat()
                pending["worker_last_progress_kind"] = "worktree_fingerprint_changed"
            elif not prior_fp and dirty_count > 0 and not pending.get("worker_last_progress_at"):
                pending["worker_last_progress_at"] = now.isoformat()
                pending["worker_last_progress_kind"] = "worktree_observed_dirty"
            pending["worker_observed_fingerprint"] = current_fp
            pending["worker_dirty_file_count"] = dirty_count
            pending["worker_diff_additions"] = int(progress.get("diff_additions") or 0)
            pending["worker_diff_deletions"] = int(progress.get("diff_deletions") or 0)
            pending["worker_changed_paths"] = list(progress.get("changed_paths") or [])
            self.state.save()

        progress_age = _age_seconds(pending.get("worker_last_progress_at"), now=now)
        admitted_age = _age_seconds(pending.get("last_worker_model_turn_admitted_at"), now=now)
        threshold = _progress_stall_seconds()
        stage = str(pending.get("stage") or "")
        if stage != "editing":
            progress_state = stage.upper() or "UNKNOWN"
        elif bool(pending.get("worker_retry_circuit_open")):
            progress_state = "STALLED"
        elif progress_age is not None and progress_age <= threshold:
            progress_state = "ACTIVE"
        elif (
            pending.get("last_worker_attempt_result") == "in_flight"
            and admitted_age is not None
            and admitted_age <= threshold
        ):
            progress_state = "IN_FLIGHT"
        else:
            progress_state = "STALLED"

        stalled_for = None
        if progress_state == "STALLED":
            basis_age = progress_age
            if basis_age is None:
                basis_age = _age_seconds(pending.get("last_worker_attempt_started_at"), now=now)
            stalled_for = int(basis_age or 0)

        snapshot.update(
            {
                "worker_attempt_count": int(pending.get("worker_attempt_count") or 0),
                "worker_model_turn_count": int(pending.get("worker_model_turn_count") or 0),
                "worker_model_turn_limit": _max_model_turns_per_worker(),
                "worker_stalled_attempts": int(pending.get("worker_stalled_attempts") or 0),
                "worker_thread_reusable": bool(pending.get("worker_thread_id")),
                "last_worker_attempt_result": pending.get("last_worker_attempt_result"),
                "worker_retry_circuit_open": bool(pending.get("worker_retry_circuit_open")),
                "worker_progress_state": progress_state,
                "worker_last_progress_at": pending.get("worker_last_progress_at"),
                "worker_last_progress_kind": pending.get("worker_last_progress_kind"),
                "worker_progress_age_seconds": int(progress_age) if progress_age is not None else None,
                "worker_progress_stall_threshold_seconds": threshold,
                "worker_stalled_for_seconds": stalled_for,
                "worker_worktree_fingerprint": pending.get("worker_observed_fingerprint"),
                "worker_dirty_file_count": int(pending.get("worker_dirty_file_count") or 0),
                "worker_diff_additions": int(pending.get("worker_diff_additions") or 0),
                "worker_diff_deletions": int(pending.get("worker_diff_deletions") or 0),
                "worker_changed_paths": list(pending.get("worker_changed_paths") or []),
                "worker_progress_probe_error": progress.get("error") if progress else None,
            }
        )
    return snapshot


core.Orchestrator._consume_budget = _consume_budget
core.Orchestrator._worker = _worker
core.Orchestrator.health_snapshot = health_snapshot
