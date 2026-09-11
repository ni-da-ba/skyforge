#!/usr/bin/env python3
"""Skyforge orchestrator entrypoint with Codex quota telemetry and adaptive pacing.

Provider quota is the primary pacing authority when a complete weekly Codex window is available.
The weekly window governs sustainable long-run burn; the five-hour window protects burst headroom.
LUNA and TERRA remain capability/routing choices and do not receive independent hard budgets while
provider telemetry is authoritative. If telemetry is unavailable, incomplete, stale, or explicitly
disabled, the accepted local 72/12-style ceilings remain the fail-safe fallback.
"""

from __future__ import annotations

import json
import os
import time
from typing import Any, Iterable

import codex_quota
import quota_governor
import skyforge_orchestrator as core


QUOTA_COMMAND = "/skyforge-quota"
QUOTA_MARKER = f"{core.SELF_COMMENT_MARKER} QUOTA"
QUOTA_ERROR_MARKER = f"{core.SELF_COMMENT_MARKER} QUOTA_ERROR"

# Make ordinary runtime refresh aware of the full quota/governor surface.
core.CONTROLLER_RUNTIME_PATHS.update(
    {
        "scripts/orchestrator/codex_quota.py",
        "scripts/orchestrator/quota_governor.py",
        "scripts/orchestrator/skyforge_quota_runtime.py",
    }
)

_ORIGINAL_CLASSIFY_CONTROL_COMMAND = core.classify_control_command
_ORIGINAL_APPLY_CONTROL_PAYLOAD = core.Orchestrator._apply_control_payload
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot
_ORIGINAL_CONSUME_BUDGET = core.Orchestrator._consume_budget


def _env_bool(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


def _env_float(name: str, default: float) -> float:
    value = os.environ.get(name)
    if value is None:
        return float(default)
    try:
        return float(value)
    except ValueError:
        return float(default)


def _governor_enabled() -> bool:
    return _env_bool("SKYFORGE_PROVIDER_QUOTA_GOVERNOR", True)


def _governor_settings() -> dict[str, Any]:
    return {
        "preferred_limit_id": os.environ.get("SKYFORGE_QUOTA_LIMIT_ID") or None,
        "weekly_reserve_percent": _env_float(
            "SKYFORGE_WEEKLY_RESERVE_PERCENT",
            quota_governor.DEFAULT_WEEKLY_RESERVE_PERCENT,
        ),
        "five_hour_reserve_percent": _env_float(
            "SKYFORGE_FIVE_HOUR_RESERVE_PERCENT",
            quota_governor.DEFAULT_FIVE_HOUR_RESERVE_PERCENT,
        ),
        "weekly_burst_margin_percent": _env_float(
            "SKYFORGE_WEEKLY_BURST_MARGIN_PERCENT",
            quota_governor.DEFAULT_WEEKLY_BURST_MARGIN_PERCENT,
        ),
        "meter_tolerance_percent": _env_float(
            "SKYFORGE_QUOTA_METER_TOLERANCE_PERCENT",
            quota_governor.DEFAULT_METER_TOLERANCE_PERCENT,
        ),
    }


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


def _evaluate(snapshot: dict[str, Any]) -> dict[str, Any]:
    return quota_governor.evaluate_quota(
        snapshot,
        now_epoch=time.time(),
        **_governor_settings(),
    )


def _persist_quota_result(
    self: core.Orchestrator,
    *,
    snapshot: dict[str, Any] | None,
    decision: dict[str, Any] | None,
    error: dict[str, Any] | None,
    attempted_kind: str | None = None,
) -> None:
    with self._state_lock:
        if snapshot is not None:
            self.state.data["last_codex_quota_snapshot"] = snapshot
        self.state.data["last_codex_quota_error"] = error
        if decision is not None:
            record = dict(decision)
            record["evaluated_at"] = core._utc_now()
            record["attempted_kind"] = attempted_kind
            self.state.data["last_quota_governor_decision"] = record
            self.state.data["quota_governor_mode"] = (
                "provider" if decision.get("authoritative") else "fallback_local"
            )
        elif error is not None:
            self.state.data["quota_governor_mode"] = "fallback_local"
        self.state.save()


def _provider_decision(self: core.Orchestrator, kind: str) -> dict[str, Any] | None:
    if not _governor_enabled():
        with self._state_lock:
            self.state.data["quota_governor_mode"] = "disabled_local_fallback"
            self.state.save()
        return None
    try:
        snapshot = codex_quota.quota_snapshot()
        decision = _evaluate(snapshot)
    except Exception as exc:
        error = _safe_error(exc)
        _persist_quota_result(
            self,
            snapshot=None,
            decision=None,
            error=error,
            attempted_kind=kind,
        )
        self._metric("quota_governor_probe_failures")
        return None

    _persist_quota_result(
        self,
        snapshot=snapshot,
        decision=decision,
        error=None,
        attempted_kind=kind,
    )
    if not decision.get("authoritative"):
        self._metric("quota_governor_local_fallbacks")
        return None
    return decision


def _record_governed_attempt(self: core.Orchestrator, kind: str) -> None:
    """Preserve attempt telemetry without applying independent Luna/Terra ceilings."""
    with self._state_lock:
        self._reset_daily_budget_if_needed()
        if kind == "classifier":
            key = "classifier_calls_today"
            metric_key = "classifier_attempts"
        elif kind == "luna_worker":
            key = "luna_worker_calls_today"
            metric_key = "luna_worker_attempts"
        elif kind == "worker":
            key = "worker_calls_today"
            metric_key = "terra_worker_attempts"
        else:
            raise ValueError(f"unknown budget kind: {kind}")

        self.state.data[key] = int(self.state.data.get(key) or 0) + 1
        metrics = self.state.data.setdefault("metrics", {})
        metrics[metric_key] = int(metrics.get(metric_key) or 0) + 1
        metrics["provider_quota_governed_attempts"] = int(
            metrics.get("provider_quota_governed_attempts") or 0
        ) + 1
        if kind in {"luna_worker", "worker"}:
            metrics["worker_attempts"] = int(metrics.get("worker_attempts") or 0) + 1
        self.state.save()


def _consume_budget(self: core.Orchestrator, kind: str) -> None:
    """Use provider pacing when authoritative; otherwise retain accepted local ceilings."""
    decision = _provider_decision(self, kind)
    if decision is None:
        _ORIGINAL_CONSUME_BUDGET(self, kind)
        return
    if not decision.get("allowed"):
        raise core.RetryBlocked(
            str(decision.get("block_kind") or "quota_pacing"),
            int(decision.get("retry_after_seconds") or 300),
            str(decision.get("reason") or "provider quota pacing deferred this model turn"),
        )
    _record_governed_attempt(self, kind)


def post_quota(self: core.Orchestrator, target: int | str = 378) -> None:
    """Read provider quota and post only the redacted normalized snapshot and pacing decision."""
    try:
        snapshot = codex_quota.quota_snapshot()
        decision = _evaluate(snapshot)
    except Exception as exc:
        error = _safe_error(exc)
        _persist_quota_result(
            self,
            snapshot=None,
            decision=None,
            error=error,
        )
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

    _persist_quota_result(
        self,
        snapshot=snapshot,
        decision=decision,
        error=None,
    )
    self._metric("quota_commands")
    body = (
        f"{QUOTA_MARKER}\n\n"
        + "~~~json\n"
        + json.dumps(
            {"quota": snapshot, "governor": decision},
            indent=2,
            sort_keys=True,
        )
        + "\n~~~"
    )
    core._run(
        ["gh", "issue", "comment", str(target), "--repo", self.repo, "--body", body],
        cwd=self.root,
        timeout=60,
    )


def _apply_control_payload(
    self: core.Orchestrator,
    control: str,
    payload: dict[str, Any],
) -> None:
    if control == "quota":
        self.post_quota(_quota_target(payload))
        return
    _ORIGINAL_APPLY_CONTROL_PAYLOAD(self, control, payload)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    with self._state_lock:
        snapshot["last_codex_quota_snapshot"] = self.state.data.get(
            "last_codex_quota_snapshot"
        )
        snapshot["last_codex_quota_error"] = self.state.data.get("last_codex_quota_error")
        snapshot["quota_governor_mode"] = self.state.data.get("quota_governor_mode")
        snapshot["last_quota_governor_decision"] = self.state.data.get(
            "last_quota_governor_decision"
        )
        snapshot["quota_governor_enabled"] = _governor_enabled()
        snapshot["quota_governor_settings"] = _governor_settings()
    return snapshot


def install_extension() -> None:
    """Install the extension idempotently into the imported controller module."""
    if getattr(core, "_skyforge_quota_extension_installed", False):
        return
    core.classify_control_command = classify_control_command
    core.Orchestrator.post_quota = post_quota
    core.Orchestrator._apply_control_payload = _apply_control_payload
    core.Orchestrator.health_snapshot = health_snapshot
    core.Orchestrator._consume_budget = _consume_budget
    core._skyforge_quota_extension_installed = True


install_extension()


def main() -> int:
    return core.main()


if __name__ == "__main__":
    raise SystemExit(main())
