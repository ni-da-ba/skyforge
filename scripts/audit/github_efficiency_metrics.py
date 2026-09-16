#!/usr/bin/env python3
"""Capture a low-cost GitHub workflow-efficiency snapshot for Skyforge."""
from __future__ import annotations
import argparse, datetime as dt, json, subprocess, sys
from collections import Counter

UTC = dt.timezone.utc

def gh_json(*args: str):
    proc = subprocess.run(["gh", "api", *args], check=True, text=True, capture_output=True)
    return json.loads(proc.stdout)

def gh_pages(endpoint: str, *fields: tuple[str, str]):
    pages = []
    page = 1
    while True:
        cmd = ["gh", "api", "--method", "GET", endpoint, "-f", "per_page=100", "-f", f"page={page}"]
        for key, value in fields:
            if key != "per_page":
                cmd += ["-f", f"{key}={value}"]
        proc = subprocess.run(cmd, check=True, text=True, capture_output=True)
        payload = json.loads(proc.stdout)
        pages.append(payload)
        items = payload.get("workflow_runs", []) if isinstance(payload, dict) else payload
        if len(items) < 100:
            break
        page += 1
    return pages

def search_count(query: str) -> int:
    data = gh_json("--method", "GET", "search/issues", "-f", f"q={query}", "-f", "per_page=1")
    return int(data["total_count"])

def iso(value: str | None):
    if not value:
        return None
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))

def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--repo", default="ni-da-ba/skyforge")
    p.add_argument("--days", type=int, default=7)
    p.add_argument("--output")
    args = p.parse_args()
    now = dt.datetime.now(UTC)
    since = (now - dt.timedelta(days=args.days)).date().isoformat()
    repo = gh_json(f"repos/{args.repo}")
    branch_pages = gh_pages(f"repos/{args.repo}/branches", ("per_page", "100"))
    branches = [b for page in branch_pages for b in page]
    workflows = gh_json("--method", "GET", f"repos/{args.repo}/actions/workflows", "-f", "per_page=100")
    workflow_contents = gh_json(f"repos/{args.repo}/contents/.github/workflows?ref={repo.get('default_branch','main')}")
    current_workflow_files = sum(1 for item in workflow_contents if item.get("type") == "file" and item.get("name", "").endswith((".yml", ".yaml")))
    runs_by_id = {}
    for day_offset in range(args.days + 1):
        day = (now - dt.timedelta(days=day_offset)).date()
        if day < dt.date.fromisoformat(since):
            continue
        day_text = day.isoformat()
        run_pages = gh_pages(f"repos/{args.repo}/actions/runs", ("created", f"{day_text}..{day_text}"), ("per_page", "100"))
        for page_payload in run_pages:
            for run in page_payload.get("workflow_runs", []):
                runs_by_id[run["id"]] = run
    runs = list(runs_by_id.values())
    conclusions = Counter((r.get("conclusion") or r.get("status") or "unknown") for r in runs)
    elapsed = 0.0
    completed = 0
    for r in runs:
        start = iso(r.get("run_started_at") or r.get("created_at")); end = iso(r.get("updated_at"))
        if start and end and end >= start and r.get("status") == "completed":
            elapsed += (end-start).total_seconds()/60
            completed += 1
    merged = search_count(f"repo:{args.repo} is:pr is:merged merged:>={since}")
    open_prs = search_count(f"repo:{args.repo} is:pr is:open")
    cancelled = conclusions.get("cancelled", 0)
    snapshot = {
        "schema_version": 1,
        "captured_at": now.replace(microsecond=0).isoformat(),
        "window_days": args.days,
        "window_start_date": since,
        "repository": args.repo,
        "default_branch": repo.get("default_branch"),
        "branches": len(branches),
        "current_workflow_files": current_workflow_files,
        "registered_workflows": int(workflows.get("total_count", len(workflows.get("workflows", [])))),
        "open_pull_requests": open_prs,
        "merged_pull_requests": merged,
        "workflow_runs": len(runs),
        "workflow_run_conclusions": dict(sorted(conclusions.items())),
        "completed_workflow_elapsed_minutes": round(elapsed, 2),
        "completed_runs_with_elapsed_time": completed,
        "cancellation_rate": round(cancelled/len(runs), 4) if runs else 0.0,
        "runs_per_merged_pr": round(len(runs)/merged, 2) if merged else None,
        "elapsed_minutes_per_merged_pr": round(elapsed/merged, 2) if merged else None,
        "notes": [
            "elapsed minutes are workflow wall-clock duration summed across completed runs, not GitHub billable runner minutes",
            "current_workflow_files counts YAML files currently present under .github/workflows on the default branch",
            "registered_workflows is GitHub Actions historical workflow registration and can include disabled/removed workflows",
            "workflow runs are fetched in daily partitions to avoid GitHub's 1,000-result cap on broad filtered queries",
        ],
    }
    text=json.dumps(snapshot, indent=2, sort_keys=True)+"\n"
    if args.output:
        from pathlib import Path
        Path(args.output).write_text(text, encoding="utf-8")
    print(text, end="")
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
