"""Compact pre-Bootstrap scorecard derived from existing durable evidence only."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable, Mapping


def _count(values: Iterable[Mapping[str, Any]], key: str, expected: str) -> int:
    return sum(str(value.get(key) or "") == expected for value in values)


def load_latest_value_report(root: Path) -> dict[str, Any]:
    root = Path(root).resolve()
    report_dir = root / ".skyforge-orchestrator" / "reports"
    paths = sorted(report_dir.glob("*.json")) if report_dir.is_dir() else []
    if not paths:
        return {"available": False, "reason": "no persisted hosted value report"}
    path = paths[-1]
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return {
            "available": False,
            "reason": f"latest hosted value report is unreadable: {type(exc).__name__}",
        }
    if not isinstance(raw, Mapping):
        return {"available": False, "reason": "latest hosted value report is malformed"}
    trailing = raw.get("trailing_window")
    evaluation = raw.get("evaluation")
    cost = raw.get("cost")
    return {
        "available": True,
        "report_file": path.name,
        "period_end": str(raw.get("period_end") or ""),
        "cost": dict(cost) if isinstance(cost, Mapping) else None,
        "trailing_window": dict(trailing) if isinstance(trailing, Mapping) else None,
        "evaluation": dict(evaluation) if isinstance(evaluation, Mapping) else None,
    }


def build_compact_scorecard(
    *,
    objectives: Iterable[Mapping[str, Any]],
    objective_controls: Iterable[Mapping[str, Any]],
    workers: Iterable[Mapping[str, Any]],
    completions: Iterable[Mapping[str, Any]],
    scheduler: Iterable[Mapping[str, Any]],
    reviews: Iterable[Mapping[str, Any]],
    external_claims: Iterable[Mapping[str, Any]],
    budget: Mapping[str, Any] | None,
    hosted_value: Mapping[str, Any],
) -> dict[str, Any]:
    objective_values = tuple(objectives)
    controls = tuple(objective_controls)
    worker_values = tuple(workers)
    completion_values = tuple(completions)
    scheduler_values = tuple(scheduler)
    review_values = tuple(reviews)
    claim_values = tuple(external_claims)
    return {
        "schema_version": 1,
        "objectives": {
            "total": len(objective_values),
            "paused": _count(controls, "state", "PAUSED"),
            "cancelled": _count(controls, "state", "CANCELLED"),
            "controlled": len(controls),
        },
        "workers": {
            "total": len(worker_values),
            "handoff_ready": _count(worker_values, "status", "HANDOFF_READY"),
            "failed": _count(worker_values, "status", "FAILED"),
            "interrupted": _count(worker_values, "status", "INTERRUPTED"),
        },
        "completions": {"total": len(completion_values)},
        "scheduler": {
            "executing": _count(scheduler_values, "state", "EXECUTING"),
            "waiting": sum(
                str(value.get("state") or "").startswith("WAIT_")
                for value in scheduler_values
            ),
            "recovery_required": _count(
                scheduler_values, "state", "RECOVERY_REQUIRED"
            ),
        },
        "human_reviews": {
            "total": len(review_values),
            "accepted": _count(review_values, "verdict", "ACCEPTED"),
            "changes_required": _count(
                review_values, "verdict", "CHANGES_REQUIRED"
            ),
        },
        "external_claims": {
            "active": sum(
                str(value.get("state") or "active").lower() == "active"
                for value in claim_values
            )
        },
        "budget": dict(budget) if isinstance(budget, Mapping) else None,
        "hosted_value": dict(hosted_value),
        "slo_note": (
            "No arbitrary SLO thresholds are inferred; scorecard surfaces observed "
            "durable evidence only."
        ),
    }
