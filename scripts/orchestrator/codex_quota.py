#!/usr/bin/env python3
"""Read ChatGPT/Codex account rate-limit telemetry without starting a model turn.

This module launches the authenticated local ``codex app-server`` process, performs only the
app-server initialization handshake plus ``account/rateLimits/read``, and returns a deliberately
redacted/normalized snapshot suitable for Skyforge controller telemetry.

It is observational only. It does not change routing, local call ceilings, account state, credits,
or provider-side quota.
"""

from __future__ import annotations

import argparse
import json
import os
import queue
import shutil
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from typing import Any, Iterable, Sequence

DEFAULT_TIMEOUT_SECONDS = 20.0
FIVE_HOUR_MINUTES = 5 * 60
WEEK_MINUTES = 7 * 24 * 60


class CodexQuotaError(RuntimeError):
    """Safe-to-surface quota probe failure."""


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _safe_float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _safe_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def classify_window(window_duration_mins: int | None) -> str:
    """Classify by duration rather than primary/secondary position.

    Backend ordering is not a semantic contract. Tolerances permit small provider-side duration
    changes without silently relabeling some unrelated bucket as the 5-hour or weekly allowance.
    """
    if window_duration_mins is None:
        return "unknown"
    if 240 <= window_duration_mins <= 360:
        return "five_hour"
    if 9_000 <= window_duration_mins <= 11_000:
        return "weekly"
    return f"rolling_{window_duration_mins}m"


def normalize_window(value: Any, *, position: str) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    used = _safe_float(value.get("usedPercent"))
    duration = _safe_int(value.get("windowDurationMins"))
    resets_at = _safe_int(value.get("resetsAt"))
    if used is None and duration is None and resets_at is None:
        return None
    used = min(100.0, max(0.0, used)) if used is not None else None
    remaining = round(100.0 - used, 6) if used is not None else None
    return {
        "position": position,
        "window_kind": classify_window(duration),
        "used_percent": used,
        "remaining_percent": remaining,
        "window_duration_mins": duration,
        "resets_at": resets_at,
    }


def _normalized_limit(value: Any, *, fallback_limit_id: str | None = None) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    limit_id = value.get("limitId") or fallback_limit_id
    windows = []
    for position in ("primary", "secondary"):
        normalized = normalize_window(value.get(position), position=position)
        if normalized is not None:
            windows.append(normalized)
    return {
        "limit_id": str(limit_id) if limit_id is not None else None,
        "limit_name": value.get("limitName"),
        "normal_model_slug": value.get("normalModelSlug"),
        "plan_type": value.get("planType"),
        "rate_limit_reached_type": value.get("rateLimitReachedType"),
        "spend_control_reached": value.get("spendControlReached"),
        "windows": windows,
    }


def normalize_rate_limits(raw: Any, *, captured_at: str | None = None) -> dict[str, Any]:
    """Return a non-secret, stable subset of ``account/rateLimits/read``.

    Deliberately omitted: account id, upsell payload, credit detail rows, auth/session data, and any
    opaque backend metadata. Reset-credit *count* is retained because it is quota capacity rather than
    a credential.
    """
    if not isinstance(raw, dict):
        raise CodexQuotaError("Codex quota response was not a JSON object")

    limits: list[dict[str, Any]] = []
    seen: set[str] = set()

    by_id = raw.get("rateLimitsByLimitId")
    if isinstance(by_id, dict):
        for key in sorted(by_id):
            normalized = _normalized_limit(by_id.get(key), fallback_limit_id=str(key))
            if normalized is None:
                continue
            identity = normalized.get("limit_id") or f"by-id:{key}"
            seen.add(str(identity))
            limits.append(normalized)

    historical = _normalized_limit(raw.get("rateLimits"))
    if historical is not None:
        identity = historical.get("limit_id")
        if identity is None or str(identity) not in seen:
            limits.insert(0, historical)

    reset_credits = raw.get("rateLimitResetCredits")
    available_reset_credits = None
    if isinstance(reset_credits, dict):
        available_reset_credits = _safe_int(reset_credits.get("availableCount"))

    return {
        "captured_at": captured_at or _utc_now(),
        "ordinary_usage_allowed": raw.get("ordinaryUsageAllowed"),
        "account_id_present": bool(raw.get("accountId")),
        "available_reset_credits": available_reset_credits,
        "limits": limits,
    }


def _reader(stdout: Any, output: "queue.Queue[object]") -> None:
    try:
        for line in iter(stdout.readline, ""):
            output.put(line)
    except Exception as exc:  # pragma: no cover - defensive transport boundary
        output.put(exc)
    finally:
        output.put(None)


def _send_line(stdin: Any, message: dict[str, Any]) -> None:
    stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
    stdin.flush()


def _wait_response(
    output: "queue.Queue[object]",
    request_id: int,
    *,
    deadline: float,
) -> Any:
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise CodexQuotaError(f"Timed out waiting for Codex app-server response {request_id}")
        try:
            item = output.get(timeout=remaining)
        except queue.Empty as exc:
            raise CodexQuotaError(
                f"Timed out waiting for Codex app-server response {request_id}"
            ) from exc
        if item is None:
            raise CodexQuotaError("Codex app-server exited before returning quota telemetry")
        if isinstance(item, Exception):
            raise CodexQuotaError(f"Codex app-server stdout reader failed: {type(item).__name__}")
        text = str(item).strip()
        if not text:
            continue
        try:
            message = json.loads(text)
        except json.JSONDecodeError:
            # stdout should be JSONL, but ignore non-JSON startup noise without ever reflecting it
            # into public telemetry where it could contain machine-local details.
            continue
        if message.get("id") != request_id:
            continue
        if message.get("error") is not None:
            error = message.get("error")
            code = error.get("code") if isinstance(error, dict) else None
            raise CodexQuotaError(
                "Codex app-server rejected quota request"
                + (f" (code {code})" if code is not None else "")
            )
        return message.get("result")


def _bundled_codex_path() -> str | None:
    """Resolve the runtime bundled with the pinned openai-codex SDK when available."""
    try:
        from codex_cli_bin import bundled_codex_path
    except (ImportError, AttributeError):
        return None
    try:
        path = bundled_codex_path()
    except Exception:
        return None
    return str(path) if path else None


def _default_command(codex_bin: str | None = None) -> list[str]:
    candidate = (
        codex_bin
        or os.environ.get("SKYFORGE_CODEX_BIN")
        or shutil.which("codex")
        or _bundled_codex_path()
    )
    if not candidate:
        raise CodexQuotaError(
            "Could not locate the Codex runtime; set SKYFORGE_CODEX_BIN or install the pinned openai-codex runtime"
        )
    return [candidate, "app-server"]


def read_codex_rate_limits(
    *,
    codex_bin: str | None = None,
    command: Sequence[str] | None = None,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    env: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Read the authenticated account's quota snapshot through local Codex app-server JSON-RPC."""
    argv = list(command) if command is not None else _default_command(codex_bin)
    if not argv:
        raise CodexQuotaError("Codex app-server command is empty")
    timeout_seconds = max(1.0, float(timeout_seconds))
    process_env = os.environ.copy()
    if env:
        process_env.update(env)

    try:
        proc = subprocess.Popen(
            argv,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
            env=process_env,
        )
    except OSError as exc:
        raise CodexQuotaError(f"Could not launch Codex app-server: {type(exc).__name__}") from exc

    if proc.stdin is None or proc.stdout is None:  # pragma: no cover - Popen contract guard
        proc.kill()
        raise CodexQuotaError("Codex app-server stdio was unavailable")

    output: "queue.Queue[object]" = queue.Queue()
    thread = threading.Thread(target=_reader, args=(proc.stdout, output), daemon=True)
    thread.start()
    deadline = time.monotonic() + timeout_seconds

    try:
        _send_line(
            proc.stdin,
            {
                "id": 1,
                "method": "initialize",
                "params": {
                    "clientInfo": {
                        "name": "skyforge-quota-probe",
                        "title": "Skyforge Quota Probe",
                        "version": "1.0.0",
                    },
                    "capabilities": {"experimentalApi": False},
                },
            },
        )
        _wait_response(output, 1, deadline=deadline)

        _send_line(proc.stdin, {"method": "initialized"})
        _send_line(
            proc.stdin,
            {
                "id": 2,
                "method": "account/rateLimits/read",
                "params": {"excludeResetCreditDetails": True},
            },
        )
        result = _wait_response(output, 2, deadline=deadline)
        if not isinstance(result, dict):
            raise CodexQuotaError("Codex quota response did not contain an object result")
        return result
    finally:
        try:
            proc.stdin.close()
        except Exception:
            pass
        if proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(timeout=2)
        thread.join(timeout=0.5)


def quota_snapshot(
    *,
    codex_bin: str | None = None,
    command: Sequence[str] | None = None,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    env: dict[str, str] | None = None,
) -> dict[str, Any]:
    captured_at = _utc_now()
    raw = read_codex_rate_limits(
        codex_bin=codex_bin,
        command=command,
        timeout_seconds=timeout_seconds,
        env=env,
    )
    return normalize_rate_limits(raw, captured_at=captured_at)


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--codex-bin", default=None)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        snapshot = quota_snapshot(codex_bin=args.codex_bin, timeout_seconds=args.timeout)
    except CodexQuotaError as exc:
        print(json.dumps({"ok": False, "error": str(exc)}, sort_keys=True))
        return 2
    print(json.dumps({"ok": True, "quota": snapshot}, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
