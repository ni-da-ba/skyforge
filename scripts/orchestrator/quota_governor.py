#!/usr/bin/env python3
"""Pure policy for pacing Skyforge against Codex provider quota windows.

The weekly window is the long-run budget. A small weekly reserve protects owner/emergency use, while
an explicit burst margin lets the controller spend ahead of the perfectly linear weekly curve and
then wait for time to catch up. The five-hour window is a short-horizon burst guard only.

This module is deliberately provider/model agnostic: LUNA and TERRA remain capability choices, not
separate budget buckets. Callers should fall back to the legacy local ceilings when the returned
policy is not authoritative because required provider telemetry is missing or malformed.
"""

from __future__ import annotations

import math
import time
from typing import Any

DEFAULT_WEEKLY_RESERVE_PERCENT = 10.0
DEFAULT_FIVE_HOUR_RESERVE_PERCENT = 20.0
DEFAULT_WEEKLY_BURST_MARGIN_PERCENT = 5.0
DEFAULT_METER_TOLERANCE_PERCENT = 0.25
DEFAULT_PROVIDER_RETRY_SECONDS = 300


def _float(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def _windows(limit: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for value in limit.get("windows") or []:
        if not isinstance(value, dict):
            continue
        kind = str(value.get("window_kind") or "")
        if kind in {"five_hour", "weekly"} and kind not in result:
            result[kind] = value
    return result


def select_limit(snapshot: dict[str, Any], preferred_limit_id: str | None = None) -> dict[str, Any] | None:
    """Select the most likely ordinary Codex allowance without relying on array position."""
    limits = [value for value in (snapshot.get("limits") or []) if isinstance(value, dict)]
    if preferred_limit_id:
        preferred = str(preferred_limit_id).strip().lower()
        for value in limits:
            if str(value.get("limit_id") or "").strip().lower() == preferred:
                return value

    def score(value: dict[str, Any]) -> tuple[int, int]:
        windows = _windows(value)
        limit_id = str(value.get("limit_id") or "").lower()
        points = 0
        if "weekly" in windows:
            points += 100
        if "five_hour" in windows:
            points += 40
        if "codex" in limit_id:
            points += 20
        if value.get("normal_model_slug"):
            points += 5
        return points, len(windows)

    return max(limits, key=score, default=None)


def _window_state(value: dict[str, Any] | None, now_epoch: float) -> dict[str, Any] | None:
    if not isinstance(value, dict):
        return None
    remaining = _float(value.get("remaining_percent"))
    used = _float(value.get("used_percent"))
    duration_mins = _int(value.get("window_duration_mins"))
    resets_at = _int(value.get("resets_at"))
    if remaining is None and used is not None:
        remaining = 100.0 - used
    if remaining is None or duration_mins is None or resets_at is None or duration_mins <= 0:
        return None
    seconds_remaining = float(resets_at) - float(now_epoch)
    # A reset in the past means the sample is stale and must not govern future work.
    if seconds_remaining <= 0:
        return None
    duration_seconds = float(duration_mins * 60)
    return {
        "remaining_percent": _clamp(remaining, 0.0, 100.0),
        "used_percent": _clamp(100.0 - remaining, 0.0, 100.0),
        "window_duration_mins": duration_mins,
        "resets_at": resets_at,
        "seconds_remaining": int(math.ceil(seconds_remaining)),
        "duration_seconds": int(duration_seconds),
        "time_remaining_fraction": _clamp(seconds_remaining / duration_seconds, 0.0, 1.0),
    }


def _retry_until_weekly_curve(
    *,
    remaining_percent: float,
    seconds_remaining: int,
    duration_seconds: int,
    reserve_percent: float,
    burst_margin_percent: float,
) -> int:
    spendable = max(0.001, 100.0 - reserve_percent)
    allowed_fraction = (remaining_percent + burst_margin_percent - reserve_percent) / spendable
    allowed_fraction = _clamp(allowed_fraction, 0.0, 1.0)
    allowed_seconds_remaining = allowed_fraction * float(duration_seconds)
    wait = float(seconds_remaining) - allowed_seconds_remaining
    return max(60, int(math.ceil(wait)))


def evaluate_quota(
    snapshot: dict[str, Any],
    *,
    now_epoch: float | None = None,
    preferred_limit_id: str | None = None,
    weekly_reserve_percent: float = DEFAULT_WEEKLY_RESERVE_PERCENT,
    five_hour_reserve_percent: float = DEFAULT_FIVE_HOUR_RESERVE_PERCENT,
    weekly_burst_margin_percent: float = DEFAULT_WEEKLY_BURST_MARGIN_PERCENT,
    meter_tolerance_percent: float = DEFAULT_METER_TOLERANCE_PERCENT,
) -> dict[str, Any]:
    """Return a safe pacing decision from one normalized quota snapshot.

    ``authoritative=False`` means required provider telemetry is absent/stale; callers should use the
    existing local safety ceilings. ``authoritative=True`` means the provider meter is sufficiently
    complete to replace the Luna/Terra daily buckets for this attempted model turn.
    """
    now = float(time.time() if now_epoch is None else now_epoch)
    weekly_reserve = _clamp(float(weekly_reserve_percent), 0.0, 50.0)
    five_hour_reserve = _clamp(float(five_hour_reserve_percent), 0.0, 80.0)
    burst_margin = _clamp(float(weekly_burst_margin_percent), 0.0, 25.0)
    tolerance = _clamp(float(meter_tolerance_percent), 0.0, 5.0)

    if snapshot.get("ordinary_usage_allowed") is False:
        resets = []
        for limit in snapshot.get("limits") or []:
            if not isinstance(limit, dict):
                continue
            for window in _windows(limit).values():
                reset = _int(window.get("resets_at"))
                if reset is not None and reset > now:
                    resets.append(reset)
        retry = max(60, int(min(resets) - now)) if resets else DEFAULT_PROVIDER_RETRY_SECONDS
        return {
            "authoritative": True,
            "allowed": False,
            "block_kind": "quota",
            "retry_after_seconds": retry,
            "reason": "provider reports ordinary Codex usage unavailable",
            "phase": "provider_blocked",
            "limit_id": None,
            "weekly": None,
            "five_hour": None,
        }

    limit = select_limit(snapshot, preferred_limit_id=preferred_limit_id)
    if not isinstance(limit, dict):
        return {
            "authoritative": False,
            "allowed": False,
            "block_kind": None,
            "retry_after_seconds": 0,
            "reason": "provider quota snapshot has no usable limit bucket",
            "phase": "fallback_local",
            "limit_id": None,
            "weekly": None,
            "five_hour": None,
        }

    windows = _windows(limit)
    weekly = _window_state(windows.get("weekly"), now)
    five_hour = _window_state(windows.get("five_hour"), now)
    limit_id = limit.get("limit_id")

    if weekly is None:
        return {
            "authoritative": False,
            "allowed": False,
            "block_kind": None,
            "retry_after_seconds": 0,
            "reason": "weekly provider quota window is missing, incomplete, or stale",
            "phase": "fallback_local",
            "limit_id": limit_id,
            "weekly": None,
            "five_hour": five_hour,
        }

    weekly_target = (
        weekly_reserve
        + (100.0 - weekly_reserve) * weekly["time_remaining_fraction"]
        - burst_margin
    )
    weekly_target = _clamp(weekly_target, weekly_reserve, 100.0)
    weekly["reserve_percent"] = weekly_reserve
    weekly["burst_margin_percent"] = burst_margin
    weekly["target_remaining_percent"] = round(weekly_target, 6)
    weekly["headroom_vs_target_percent"] = round(
        weekly["remaining_percent"] - weekly_target, 6
    )

    if five_hour is not None:
        five_hour["reserve_percent"] = five_hour_reserve
        five_hour["headroom_vs_reserve_percent"] = round(
            five_hour["remaining_percent"] - five_hour_reserve, 6
        )
        if five_hour["remaining_percent"] <= five_hour_reserve + tolerance:
            return {
                "authoritative": True,
                "allowed": False,
                "block_kind": "quota_pacing",
                "retry_after_seconds": max(60, five_hour["seconds_remaining"]),
                "reason": (
                    "five-hour Codex reserve reached "
                    f"({five_hour['remaining_percent']:.2f}% remaining; "
                    f"reserve {five_hour_reserve:.2f}%)"
                ),
                "phase": "five_hour_reserve",
                "limit_id": limit_id,
                "weekly": weekly,
                "five_hour": five_hour,
            }

    if weekly["remaining_percent"] <= weekly_reserve + tolerance:
        return {
            "authoritative": True,
            "allowed": False,
            "block_kind": "quota_pacing",
            "retry_after_seconds": max(60, weekly["seconds_remaining"]),
            "reason": (
                "weekly Codex reserve reached "
                f"({weekly['remaining_percent']:.2f}% remaining; reserve {weekly_reserve:.2f}%)"
            ),
            "phase": "weekly_reserve",
            "limit_id": limit_id,
            "weekly": weekly,
            "five_hour": five_hour,
        }

    if weekly["remaining_percent"] + tolerance < weekly_target:
        retry = _retry_until_weekly_curve(
            remaining_percent=weekly["remaining_percent"],
            seconds_remaining=weekly["seconds_remaining"],
            duration_seconds=weekly["duration_seconds"],
            reserve_percent=weekly_reserve,
            burst_margin_percent=burst_margin,
        )
        retry = min(max(60, retry), weekly["seconds_remaining"])
        return {
            "authoritative": True,
            "allowed": False,
            "block_kind": "quota_pacing",
            "retry_after_seconds": retry,
            "reason": (
                "weekly Codex usage is ahead of the sustainable curve "
                f"({weekly['remaining_percent']:.2f}% remaining; "
                f"target {weekly_target:.2f}%)"
            ),
            "phase": "weekly_catchup",
            "limit_id": limit_id,
            "weekly": weekly,
            "five_hour": five_hour,
        }

    headroom = weekly["remaining_percent"] - weekly_target
    phase = "surplus" if headroom >= 5.0 else "on_pace"
    return {
        "authoritative": True,
        "allowed": True,
        "block_kind": None,
        "retry_after_seconds": 0,
        "reason": (
            "provider quota is within the sustainable pacing envelope "
            f"({weekly['remaining_percent']:.2f}% weekly remaining; "
            f"target {weekly_target:.2f}%)"
        ),
        "phase": phase,
        "limit_id": limit_id,
        "weekly": weekly,
        "five_hour": five_hour,
    }
