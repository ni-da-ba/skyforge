#!/usr/bin/env python3
"""Model-free daily value report for the hosted Skyforge orchestrator.

Reads the controller's durable counters plus GitHub PR metadata, persists a JSON/Markdown report under
.skyforge-orchestrator/reports/, and optionally posts the same summary to the tracking issue. This
script never starts Codex and therefore consumes zero model turns.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

REPO = "ni-da-ba/skyforge"
STATE_DIR = ".skyforge-orchestrator"
STATE_FILE = "state.json"
REPORT_STATE_FILE = "report_state.json"
REPORTS_DIR = "reports"
DEFAULT_ISSUE = 378
SELF_MARKER = "[skyforge-orchestrator] DAILY_VALUE_REPORT"
CENTRAL = ZoneInfo("America/Chicago")

COUNTER_KEYS = (
    "events_seen",
    "events_filtered",
    "events_actionable",
    "duplicate_deliveries",
    "webhook_signature_rejections",
    "classifier_attempts",
    "classifier_noops",
    "cached_decision_reuses",
    "worker_attempts",
    "worker_handoffs",
    "worker_resumes",
    "handoff_resumes",
    "worker_no_change",
    "human_gates",
    "retry_blocks",
    "codex_blocks",
    "dispatch_failures",
    "restart_replays",
    "startup_reconcile_baselines",
    "startup_reconcile_noops",
    "startup_reconciliations",
    "startup_reconcile_failures",
    "managed_merges",
    "controller_starts",
)


def _run(args: list[str], *, cwd: Path, timeout: int = 120) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        timeout=timeout,
    )


def _json_cmd(args: list[str], *, cwd: Path, timeout: int = 120) -> Any:
    return json.loads(_run(args, cwd=cwd, timeout=timeout).stdout or "null")


def _parse_time(value: str | None) -> datetime | None:
    if not value:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def metric_deltas(current: dict[str, Any], previous: dict[str, Any]) -> dict[str, int]:
    result: dict[str, int] = {}
    for key in COUNTER_KEYS:
        now = int(current.get(key) or 0)
        before = int(previous.get(key) or 0)
        # A state reset should be visible as a fresh baseline rather than a negative daily metric.
        result[key] = max(0, now - before)
    return result


def _ratio(numerator: int, denominator: int) -> float | None:
    if denominator <= 0:
        return None
    return numerator / denominator


def _is_overnight(value: datetime) -> bool:
    local = value.astimezone(CENTRAL)
    return local.hour >= 22 or local.hour < 8


def _controller_prs(prs: list[dict[str, Any]], start: datetime, end: datetime) -> dict[str, Any]:
    controller = [
        pr for pr in prs if str(pr.get("headRefName") or "").startswith("codex/")
    ]
    created = []
    merged = []
    overnight_created = []
    overnight_merged = []
    open_now = []

    for pr in controller:
        created_at = _parse_time(pr.get("createdAt"))
        merged_at = _parse_time(pr.get("mergedAt"))
        if created_at and start <= created_at < end:
            created.append(pr)
            if _is_overnight(created_at):
                overnight_created.append(pr)
        if merged_at and start <= merged_at < end:
            merged.append(pr)
            if _is_overnight(merged_at):
                overnight_merged.append(pr)
        if str(pr.get("state") or "").upper() == "OPEN":
            open_now.append(pr)

    def compact(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [
            {
                "number": item.get("number"),
                "title": item.get("title"),
                "url": item.get("url"),
                "headRefName": item.get("headRefName"),
            }
            for item in items
        ]

    return {
        "created": compact(created),
        "merged": compact(merged),
        "open_now": compact(open_now),
        "overnight_created": compact(overnight_created),
        "overnight_merged": compact(overnight_merged),
    }


def evaluation_signal(
    *,
    elapsed_days: float,
    worker_attempts: int,
    worker_handoffs: int,
    worker_no_change: int,
    merged_prs: int,
    dispatch_failures: int,
    codex_blocks: int,
) -> tuple[str, str]:
    """Return a conservative advisory signal; never destroys or disables resources."""
    handoff_yield = _ratio(worker_handoffs, worker_attempts) or 0.0
    no_change_rate = _ratio(worker_no_change, worker_attempts) or 0.0

    if elapsed_days < 7 and worker_attempts < 4 and merged_prs < 2:
        return (
            "INSUFFICIENT_DATA",
            "Continue collecting evidence; the host has not yet accumulated a representative worker sample.",
        )

    if (
        elapsed_days >= 7
        and worker_attempts >= 4
        and merged_prs == 0
        and worker_handoffs < 2
    ):
        return (
            "CANCEL_CANDIDATE",
            "Paid uptime has not produced enough merged or information-bearing controller work.",
        )

    if (
        (worker_attempts >= 4 and (handoff_yield < 0.5 or no_change_rate > 0.5))
        or dispatch_failures >= 3
        or codex_blocks >= 5
    ):
        return (
            "REWORK",
            "The hosted path is active but worker yield, failures, or capacity blocking are wasting material runtime.",
        )

    if merged_prs >= 2 or (worker_handoffs >= 4 and handoff_yield >= 0.5):
        return (
            "KEEP",
            "The host is repeatedly producing information-bearing handoffs or merged controller PRs at acceptable yield.",
        )

    return (
        "INSUFFICIENT_DATA",
        "Evidence is mixed or sparse; continue collecting data before making the hosting decision.",
    )


def _load_json(path: Path, default: dict[str, Any]) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text())
        return value if isinstance(value, dict) else default
    except FileNotFoundError:
        return default
    except Exception:
        return default


def _atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    tmp.replace(path)


def _load_recent_reports(report_dir: Path, days: int = 7) -> list[dict[str, Any]]:
    reports: list[dict[str, Any]] = []
    for path in sorted(report_dir.glob("*.json"))[-days:]:
        try:
            value = json.loads(path.read_text())
            if isinstance(value, dict):
                reports.append(value)
        except Exception:
            continue
    return reports


def _sum_report_metric(reports: list[dict[str, Any]], key: str) -> int:
    return sum(int((report.get("metric_deltas") or {}).get(key) or 0) for report in reports)


def _sum_pr_count(reports: list[dict[str, Any]], key: str) -> int:
    return sum(len((report.get("controller_prs") or {}).get(key) or []) for report in reports)


def _money(value: float | None) -> str:
    return "unknown" if value is None else f"${value:.2f}"


def _render_markdown(report: dict[str, Any]) -> str:
    d = report["metric_deltas"]
    prs = report["controller_prs"]
    trailing = report["trailing_window"]
    cost = report["cost"]
    signal = report["evaluation"]

    classifier_attempts = int(d.get("classifier_attempts") or 0)
    worker_attempts = int(d.get("worker_attempts") or 0)
    noop_rate = _ratio(int(d.get("classifier_noops") or 0), classifier_attempts)
    handoff_yield = _ratio(int(d.get("worker_handoffs") or 0), worker_attempts)
    no_change_rate = _ratio(int(d.get("worker_no_change") or 0), worker_attempts)

    def pct(value: float | None) -> str:
        return "n/a" if value is None else f"{value * 100:.0f}%"

    lines = [
        SELF_MARKER,
        "",
        f"## Hosted value report — {report['report_date_central']}",
        "",
        f"Period: `{report['period_start']}` → `{report['period_end']}`",
        f"Estimated compute cost this period: **{_money(cost.get('period_estimate_usd'))}** "
        f"(cumulative estimate: **{_money(cost.get('cumulative_estimate_usd'))}**)",
        "",
        "### Daily flow",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
        f"| Webhook events seen | {d.get('events_seen', 0)} |",
        f"| Filtered before useful dispatch | {d.get('events_filtered', 0)} |",
        f"| Actionable events | {d.get('events_actionable', 0)} |",
        f"| Duplicate deliveries | {d.get('duplicate_deliveries', 0)} |",
        f"| Signature rejects | {d.get('webhook_signature_rejections', 0)} |",
        f"| Luna classifier attempts | {classifier_attempts} |",
        f"| Luna NOOPs | {d.get('classifier_noops', 0)} ({pct(noop_rate)}) |",
        f"| Terra worker attempts | {worker_attempts} |",
        f"| Worker handoffs | {d.get('worker_handoffs', 0)} ({pct(handoff_yield)} yield) |",
        f"| Worker no-change | {d.get('worker_no_change', 0)} ({pct(no_change_rate)}) |",
        f"| Human gates | {d.get('human_gates', 0)} |",
        f"| Codex capacity/auth blocks | {d.get('codex_blocks', 0)} |",
        f"| Dispatch failures | {d.get('dispatch_failures', 0)} |",
        f"| Controller starts | {d.get('controller_starts', 0)} |",
        f"| Startup reconciliations | {d.get('startup_reconciliations', 0)} |",
        "",
        "### Controller PR evidence",
        "",
        f"- Created this period: **{len(prs['created'])}**",
        f"- Merged this period: **{len(prs['merged'])}**",
        f"- Still open: **{len(prs['open_now'])}**",
        f"- Created overnight (22:00–08:00 America/Chicago): **{len(prs['overnight_created'])}**",
        f"- Merged overnight: **{len(prs['overnight_merged'])}**",
        "",
        "### Trailing decision evidence",
        "",
        f"- Reports in trailing window: **{trailing['report_count']}**",
        f"- Estimated trailing compute cost: **{_money(trailing.get('estimated_cost_usd'))}**",
        f"- Terra attempts / handoffs / no-change: "
        f"**{trailing['worker_attempts']} / {trailing['worker_handoffs']} / {trailing['worker_no_change']}**",
        f"- Controller PRs created / merged: "
        f"**{trailing['controller_prs_created']} / {trailing['controller_prs_merged']}**",
        f"- Codex blocks / dispatch failures: "
        f"**{trailing['codex_blocks']} / {trailing['dispatch_failures']}**",
        "",
        f"### Advisory: **{signal['signal']}**",
        "",
        signal["reason"],
        "",
        "_This report is model-free and does not itself spend a Luna/Terra turn. "
        "It is evidence for the human/Audit keep–rework–cancel decision, not an automatic shutdown trigger._",
    ]
    return "\n".join(lines) + "\n"


def build_report(
    *,
    root: Path,
    repo: str,
    state: dict[str, Any],
    report_state: dict[str, Any],
    prs: list[dict[str, Any]],
    now: datetime,
    hourly_usd: float | None,
) -> dict[str, Any]:
    current_metrics = dict(state.get("metrics") or {})
    previous_metrics = dict(report_state.get("last_metrics") or {})
    start = _parse_time(report_state.get("last_report_at"))
    activated = _parse_time(report_state.get("host_activated_at"))

    if activated is None:
        activated = now
    if start is None:
        start = activated
    if start > now:
        start = now

    deltas = metric_deltas(current_metrics, previous_metrics)
    controller_prs = _controller_prs(prs, start, now)

    report_dir = root / STATE_DIR / REPORTS_DIR
    recent = _load_recent_reports(report_dir, days=6)

    period_hours = max(0.0, (now - start).total_seconds() / 3600.0)
    cumulative_hours = max(0.0, (now - activated).total_seconds() / 3600.0)
    period_cost = period_hours * hourly_usd if hourly_usd is not None else None
    cumulative_cost = cumulative_hours * hourly_usd if hourly_usd is not None else None

    proto = {
        "captured_at": now.isoformat(),
        "period_start": start.isoformat(),
        "period_end": now.isoformat(),
        "report_date_central": now.astimezone(CENTRAL).date().isoformat(),
        "metric_deltas": deltas,
        "controller_prs": controller_prs,
        "cost": {
            "hourly_usd": hourly_usd,
            "period_hours": period_hours,
            "period_estimate_usd": period_cost,
            "cumulative_hours": cumulative_hours,
            "cumulative_estimate_usd": cumulative_cost,
        },
    }

    window = recent + [proto]
    trailing_costs = [
        float((item.get("cost") or {}).get("period_estimate_usd"))
        for item in window
        if (item.get("cost") or {}).get("period_estimate_usd") is not None
    ]
    trailing = {
        "report_count": len(window),
        "worker_attempts": sum(
            int((item.get("metric_deltas") or {}).get("worker_attempts") or 0)
            for item in window
        ),
        "worker_handoffs": sum(
            int((item.get("metric_deltas") or {}).get("worker_handoffs") or 0)
            for item in window
        ),
        "worker_no_change": sum(
            int((item.get("metric_deltas") or {}).get("worker_no_change") or 0)
            for item in window
        ),
        "classifier_attempts": sum(
            int((item.get("metric_deltas") or {}).get("classifier_attempts") or 0)
            for item in window
        ),
        "classifier_noops": sum(
            int((item.get("metric_deltas") or {}).get("classifier_noops") or 0)
            for item in window
        ),
        "codex_blocks": sum(
            int((item.get("metric_deltas") or {}).get("codex_blocks") or 0)
            for item in window
        ),
        "dispatch_failures": sum(
            int((item.get("metric_deltas") or {}).get("dispatch_failures") or 0)
            for item in window
        ),
        "controller_prs_created": sum(
            len((item.get("controller_prs") or {}).get("created") or [])
            for item in window
        ),
        "controller_prs_merged": sum(
            len((item.get("controller_prs") or {}).get("merged") or [])
            for item in window
        ),
        "overnight_prs_created": sum(
            len((item.get("controller_prs") or {}).get("overnight_created") or [])
            for item in window
        ),
        "overnight_prs_merged": sum(
            len((item.get("controller_prs") or {}).get("overnight_merged") or [])
            for item in window
        ),
        "estimated_cost_usd": sum(trailing_costs) if trailing_costs else None,
    }

    elapsed_days = max(0.0, (now - activated).total_seconds() / 86400.0)
    signal, reason = evaluation_signal(
        elapsed_days=elapsed_days,
        worker_attempts=trailing["worker_attempts"],
        worker_handoffs=trailing["worker_handoffs"],
        worker_no_change=trailing["worker_no_change"],
        merged_prs=trailing["controller_prs_merged"],
        dispatch_failures=trailing["dispatch_failures"],
        codex_blocks=trailing["codex_blocks"],
    )

    proto["trailing_window"] = trailing
    proto["evaluation"] = {
        "signal": signal,
        "reason": reason,
        "elapsed_days": elapsed_days,
    }
    return proto


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--repo", default=REPO)
    parser.add_argument("--issue", type=int, default=int(os.environ.get("SKYFORGE_VALUE_REPORT_ISSUE", DEFAULT_ISSUE)))
    parser.add_argument("--initialize", action="store_true")
    parser.add_argument("--no-post", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    state_dir = root / STATE_DIR
    state = _load_json(state_dir / STATE_FILE, {"metrics": {}})
    report_state_path = state_dir / REPORT_STATE_FILE
    report_state = _load_json(report_state_path, {})

    now = _utc_now()
    activated_env = os.environ.get("SKYFORGE_HOST_ACTIVATED_AT")
    if not report_state.get("host_activated_at"):
        report_state["host_activated_at"] = (
            _parse_time(activated_env).isoformat()
            if _parse_time(activated_env)
            else now.isoformat()
        )

    if args.initialize:
        report_state["last_report_at"] = now.isoformat()
        report_state["last_metrics"] = dict(state.get("metrics") or {})
        _atomic_json(report_state_path, report_state)
        print("[value-report] initialized daily telemetry baseline")
        return 0

    hourly_raw = os.environ.get("SKYFORGE_DROPLET_HOURLY_USD")
    hourly_usd: float | None = None
    if hourly_raw:
        try:
            hourly_usd = max(0.0, float(hourly_raw))
        except ValueError:
            hourly_usd = None

    prs = _json_cmd(
        [
            "gh", "pr", "list",
            "--repo", args.repo,
            "--state", "all",
            "--limit", "100",
            "--json",
            "number,title,headRefName,state,isDraft,createdAt,updatedAt,mergedAt,url",
        ],
        cwd=root,
        timeout=60,
    )
    report = build_report(
        root=root,
        repo=args.repo,
        state=state,
        report_state=report_state,
        prs=prs,
        now=now,
        hourly_usd=hourly_usd,
    )

    report_dir = state_dir / REPORTS_DIR
    report_dir.mkdir(parents=True, exist_ok=True)
    stem = report["report_date_central"]
    json_path = report_dir / f"{stem}.json"
    md_path = report_dir / f"{stem}.md"
    _atomic_json(json_path, report)
    markdown = _render_markdown(report)
    md_path.write_text(markdown)

    # Advance the baseline only after both durable report artifacts exist.
    report_state["last_report_at"] = now.isoformat()
    report_state["last_metrics"] = dict(state.get("metrics") or {})
    _atomic_json(report_state_path, report_state)

    if not args.no_post:
        try:
            _run(
                [
                    "gh", "issue", "comment", str(args.issue),
                    "--repo", args.repo,
                    "--body-file", str(md_path),
                ],
                cwd=root,
                timeout=60,
            )
        except Exception as exc:
            print(f"[value-report] GitHub post failed after local report persistence: {exc}")
            return 2

    print(markdown, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
