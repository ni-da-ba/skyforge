#!/usr/bin/env python3
"""Hosted worker retry/context guard.

A pending ``editing`` worker is durable repository state.  Preserve its Codex thread as well as its
worktree so a process restart/retry does not pay to reconstruct the same task from scratch.  Also
fail closed after repeated interrupted/failed attempts that make no durable worktree progress instead
of consuming unbounded worker turns.

The controller remains the only network/git mutation authority.  This extension changes only worker
turn lifecycle and telemetry; handoff, path, validation, roadmap, and quota policy remain unchanged.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
from typing import Any

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_worker_retry_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def _max_no_progress_attempts() -> int:
    return core._env_int(
        "SKYFORGE_WORKER_MAX_NO_PROGRESS_ATTEMPTS",
        2,
        minimum=1,
    )


def _worker_root_matches(pending: dict[str, Any], worker_root: Path) -> bool:
    raw = pending.get("worktree")
    if not raw:
        return worker_root.resolve() == Path.cwd().resolve()
    path = Path(str(raw))
    if not path.is_absolute():
        # Pending worker paths are normally absolute.  A relative legacy path is rooted at the
        # controller checkout; callers resolve it before invoking the worker.
        return path.name == worker_root.name
    return path.resolve() == worker_root.resolve()


def _worktree_fingerprint(self: core.Orchestrator, worker_root: Path) -> str:
    """Hash HEAD plus changed-path content, including untracked files.

    ``git diff`` alone misses edits to an already-untracked file.  Hash every changed regular file
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
            # The process died/interrupted after the turn was admitted.  Repository progress is the
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
        if result == "failed" and failure_kind not in {"quota", "rate_limit", "authentication"}:
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
    elif result == "failed":
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
    # stage. Reuse the already-durable response rather than buying the same turn again.
    with self._state_lock:
        pending = _matching_pending(self, root)
        if (
            pending is not None
            and pending.get("last_worker_attempt_result") == "completed"
            and pending.get("last_worker_response")
        ):
            response = str(pending["last_worker_response"])
            self._metric("worker_completed_response_replays")
            return response

    before, pending_snapshot = _begin_worker_attempt(self, root)

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
        # Quota admission happens after the no-progress circuit so a known-stalled worker cannot
        # consume another provider-governed attempt merely to rediscover its stall.
        self._consume_budget(budget_kind)
        from openai_codex import Codex, Sandbox

        with Codex() as codex:
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
                        sandbox=Sandbox.workspace_write,
                        developer_instructions=core.WORKER_INSTRUCTIONS,
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
                    sandbox=Sandbox.workspace_write,
                    developer_instructions=core.WORKER_INSTRUCTIONS,
                )
                _persist_worker_thread_id(self, root, str(thread.id))
                self._metric("worker_thread_starts")

            result_obj = thread.run(prompt, sandbox=Sandbox.workspace_write)
            response = str(result_obj.final_response)
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
        snapshot.update(
            {
                "worker_attempt_count": int(pending.get("worker_attempt_count") or 0),
                "worker_stalled_attempts": int(pending.get("worker_stalled_attempts") or 0),
                "worker_thread_reusable": bool(pending.get("worker_thread_id")),
                "last_worker_attempt_result": pending.get("last_worker_attempt_result"),
                "worker_retry_circuit_open": bool(pending.get("worker_retry_circuit_open")),
            }
        )
    return snapshot


core.Orchestrator._worker = _worker
core.Orchestrator.health_snapshot = health_snapshot
