#!/usr/bin/env python3
"""Skyforge orchestrator entrypoint with read-only Codex quota telemetry.

This is intentionally a thin runtime extension around ``skyforge_orchestrator``. It preserves the
accepted controller implementation and safety contract while adding one model-free trusted command:
``/skyforge-quota``. Once validated in production, the extension can be folded into the core controller
without changing its external contract.
"""

from __future__ import annotations

import json
from typing import Any, Iterable

import codex_quota
import skyforge_orchestrator as core


QUOTA_COMMAND = "/skyforge-quota"
QUOTA_MARKER = f"{core.SELF_COMMENT_MARKER} QUOTA"
QUOTA_ERROR_MARKER = f"{core.SELF_COMMENT_MARKER} QUOTA_ERROR"
CONTROL_REJECTED_MARKER = f"{core.SELF_COMMENT_MARKER} CONTROL_REJECTED"

# Operator controls are edge-triggered commands, not condition watches. A trusted command that is
# rejected by a local controller precondition must be consumed at that observed state rather than
# replayed indefinitely until some future worker happens to satisfy the old command's preconditions.
# External/transient failures are deliberately not swallowed here.
ONE_SHOT_PRECONDITION_CONTROLS = frozenset(
    {
        "reset_budget",
        "budget_profile_standard",
        "budget_profile_expanded",
        "refresh_runtime",
        "discard_worker",
    }
)

# Make ordinary runtime refresh aware of both extension files. Once this entrypoint is loaded,
# /skyforge-refresh-runtime will request a systemd restart whenever either file changes on main.
core.CONTROLLER_RUNTIME_PATHS.update(
    {
        "scripts/orchestrator/codex_quota.py",
        "scripts/orchestrator/skyforge_quota_runtime.py",
    }
)

_ORIGINAL_CLASSIFY_CONTROL_COMMAND = core.classify_control_command
_ORIGINAL_APPLY_CONTROL_PAYLOAD = core.Orchestrator._apply_control_payload
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def classify_control_command(
    event: str,
    payload: dict[str, Any],
    *,
    trusted_actors: Iterable[str] = core.DEFAULT_TRUSTED_GITHUB_ACTORS,
) -> str | None:
    existing = _ORIGINAL_CLASSIFY_CONTROL_COMMAND(
        event,
        payload,
        trusted_actors=trusted_actors,
    )
    if existing is not None:
        return existing
    if (event or "").strip().lower() != "issue_comment":
        return None
    if str(payload.get("action") or "").lower() != "created":
        return None
    body = str((payload.get("comment") or {}).get("body") or "").strip().lower()
    if body != QUOTA_COMMAND:
        return None
    if not core._trusted_actor(payload, trusted_actors):
        return None
    return "quota"


def _quota_target(payload: dict[str, Any]) -> int | str:
    issue = payload.get("issue") or {}
    return issue.get("number") or 378


def _safe_error(exc: Exception) -> dict[str, Any]:
    # CodexQuotaError deliberately contains no backend message or auth/session material. Unknown
    # exceptions are reduced to their class only rather than reflecting arbitrary text to GitHub.
    if isinstance(exc, codex_quota.CodexQuotaError):
        summary = str(exc)[:500]
    else:
        summary = "Unexpected quota probe failure"
    return {
        "at": core._utc_now(),
        "kind": type(exc).__name__,
        "summary": summary,
    }


def post_quota(self: core.Orchestrator, target: int | str = 378) -> None:
    """Read provider quota and post only the redacted normalized snapshot to GitHub."""
    try:
        snapshot = codex_quota.quota_snapshot()
    except Exception as exc:
        error = _safe_error(exc)
        with self._state_lock:
            self.state.data["last_codex_quota_error"] = error
            self.state.save()
        self._metric("quota_probe_failures")
        body = (
            f"{QUOTA_ERROR_MARKER}\n\n"
            + "~~~json\n"
            + json.dumps(error, indent=2, sort_keys=True)
            + "\n~~~"
        )
        core._run(
            ["gh", "issue", "comment", str(target), "--repo", self.repo, "--body", body],
            cwd=self.root,
            timeout=60,
        )
        return

    with self._state_lock:
        self.state.data["last_codex_quota_snapshot"] = snapshot
        self.state.data["last_codex_quota_error"] = None
        self.state.save()
    self._metric("quota_commands")
    body = (
        f"{QUOTA_MARKER}\n\n"
        + "~~~json\n"
        + json.dumps(snapshot, indent=2, sort_keys=True)
        + "\n~~~"
    )
    core._run(
        ["gh", "issue", "comment", str(target), "--repo", self.repo, "--body", body],
        cwd=self.root,
        timeout=60,
    )


def _record_control_rejection(
    self: core.Orchestrator,
    control: str,
    payload: dict[str, Any],
    exc: RuntimeError,
) -> None:
    """Consume a locally rejected edge-triggered control and preserve safe operator telemetry."""
    comment = payload.get("comment") or {}
    actor = str(((comment.get("user") or {}).get("login")) or "") or None
    source_id = str(comment.get("id")) if comment.get("id") is not None else None
    record = {
        "at": core._utc_now(),
        "control": control,
        "source_id": source_id,
        "actor": actor,
        "kind": type(exc).__name__,
        "summary": str(exc)[:500],
    }
    with self._state_lock:
        self.state.data["last_control_rejection"] = record
        self.state.save()
    self._metric("operator_control_rejections")

    body = (
        f"{CONTROL_REJECTED_MARKER}\n\n"
        f"Control `{control}` was rejected by the controller's current safety preconditions and "
        "has been consumed as a one-shot command; it will not become eligible later merely because "
        "controller state changes.\n\n"
        + "~~~json\n"
        + json.dumps(record, indent=2, sort_keys=True)
        + "\n~~~"
    )
    try:
        core._run(
            [
                "gh",
                "issue",
                "comment",
                str(_quota_target(payload)),
                "--repo",
                self.repo,
                "--body",
                body,
            ],
            cwd=self.root,
            timeout=60,
        )
    except Exception as post_exc:
        # Visibility failure must not turn a rejected edge-triggered control back into a latent future
        # action. The durable state record remains observable through /skyforge-status.
        with self._state_lock:
            current = dict(self.state.data.get("last_control_rejection") or record)
            current["visibility_error"] = type(post_exc).__name__
            self.state.data["last_control_rejection"] = current
            self.state.save()
        self._metric("control_rejection_post_failures")


def _apply_control_payload(
    self: core.Orchestrator,
    control: str,
    payload: dict[str, Any],
) -> None:
    if control == "quota":
        self.post_quota(_quota_target(payload))
        return
    try:
        _ORIGINAL_APPLY_CONTROL_PAYLOAD(self, control, payload)
    except RuntimeError as exc:
        if control not in ONE_SHOT_PRECONDITION_CONTROLS:
            raise
        self._record_control_rejection(control, payload, exc)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    with self._state_lock:
        snapshot["last_codex_quota_snapshot"] = self.state.data.get(
            "last_codex_quota_snapshot"
        )
        snapshot["last_codex_quota_error"] = self.state.data.get("last_codex_quota_error")
        snapshot["last_control_rejection"] = self.state.data.get("last_control_rejection")
    return snapshot


def install_extension() -> None:
    """Install the extension idempotently into the imported controller module."""
    if getattr(core, "_skyforge_quota_extension_installed", False):
        return
    core.classify_control_command = classify_control_command
    core.Orchestrator.post_quota = post_quota
    core.Orchestrator._record_control_rejection = _record_control_rejection
    core.Orchestrator._apply_control_payload = _apply_control_payload
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_quota_extension_installed = True


install_extension()


def main() -> int:
    return core.main()


if __name__ == "__main__":
    raise SystemExit(main())
