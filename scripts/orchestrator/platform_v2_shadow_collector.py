"""Live read-only managed-PR observation collector for Platform v2 shadow mode.

The collector reads durable legacy state and performs one allowlisted GitHub PR read.
It emits a prepared R3A snapshot and has no repository/controller mutation capability.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Callable, Mapping, Sequence


DEFAULT_REPO = "ni-da-ba/skyforge"
STATE_RELATIVE_PATH = Path(".skyforge-orchestrator") / "state.json"
PR_JSON_FIELDS = (
    "state,headRefName,headRefOid,baseRefName,reviewDecision,"
    "statusCheckRollup,isDraft,mergeStateStatus"
)
_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
_GOOD_CONCLUSIONS = {"SUCCESS", "SKIPPED", "NEUTRAL"}


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be an object")
    return value


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def validate_repo(repo: str) -> str:
    value = str(repo or "").strip()
    if not _REPO_RE.fullmatch(value):
        raise ValueError("repo must be owner/name using GitHub-safe characters")
    return value


def validate_readonly_gh_command(args: Sequence[str]) -> tuple[str, ...]:
    command = tuple(str(part) for part in args)
    if len(command) != 9:
        raise ValueError("shadow collector permits only the exact gh pr view read shape")
    if command[0:3] != ("gh", "pr", "view"):
        raise ValueError("shadow collector permits only gh pr view")
    try:
        pr_number = int(command[3])
    except ValueError as exc:
        raise ValueError("gh pr view target must be a positive integer") from exc
    _positive_int(pr_number, "PR number")
    if command[4] != "--repo":
        raise ValueError("gh pr view must bind an explicit repository")
    validate_repo(command[5])
    if command[6] != "--json" or command[7] != PR_JSON_FIELDS:
        raise ValueError("gh pr view JSON field set is not allowlisted")
    if command[8] != "--jq=.":
        raise ValueError("gh pr view must use the fixed identity jq projection")
    return command


def _run_readonly_gh(
    args: Sequence[str],
    *,
    cwd: Path,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> Any:
    command = validate_readonly_gh_command(args)
    result = runner(
        list(command),
        cwd=cwd,
        check=True,
        text=True,
        capture_output=True,
        timeout=60,
    )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise ValueError("GitHub read returned malformed JSON") from exc


def read_legacy_state(root: Path) -> Mapping[str, Any]:
    path = root / STATE_RELATIVE_PATH
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"legacy controller state is missing: {path}") from exc
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"legacy controller state is unreadable: {path}") from exc
    return _mapping(raw, "legacy controller state")


def _ci_state(checks: Any) -> str:
    if not isinstance(checks, list) or not checks:
        return "UNKNOWN"

    active = False
    bad = False
    successes = 0
    for raw in checks:
        check = _mapping(raw, "statusCheckRollup entry")
        status = str(check.get("status") or "").upper()
        conclusion = str(check.get("conclusion") or "").upper()
        if status != "COMPLETED":
            active = True
        elif conclusion not in _GOOD_CONCLUSIONS:
            bad = True
        elif conclusion == "SUCCESS":
            successes += 1

    if active:
        return "PENDING"
    if bad:
        return "FAIL"
    if successes > 0:
        return "PASS"
    return "UNKNOWN"


def _current_human_gate(
    state: Mapping[str, Any],
    *,
    lane: str,
    pr_number: int,
    head_sha: str,
    pr_state: str,
) -> tuple[bool, dict[str, Any]]:
    records = state.get("human_gate_records") or {}
    if not isinstance(records, Mapping):
        raise ValueError("legacy human_gate_records must be an object")
    key = f"pr:{pr_number}:{lane.strip().lower()}"
    raw = records.get(key)
    if raw is None:
        return False, {}
    record = _mapping(raw, f"human_gate_records.{key}")
    token = str(record.get("token") or "")
    expected_token = f"{head_sha}:{pr_state}"
    current = bool(head_sha and pr_state and token == expected_token)
    if not current:
        return False, {}
    return True, {
        key: {
            "token": token,
            "target": str(record.get("target") or pr_number),
            "seeded_from_github": bool(record.get("seeded_from_github", False)),
        }
    }


def _project_pending_worker(state: Mapping[str, Any]) -> dict[str, str] | None:
    raw = state.get("pending_worker")
    if raw is None:
        return None
    pending = _mapping(raw, "pending_worker")
    branch = str(pending.get("branch") or "").strip()
    if not branch:
        raise ValueError("pending_worker.branch is required")
    return {
        "branch": branch,
        "authority_key": str(pending.get("authority_key") or "").strip(),
    }


def collect_managed_snapshot(
    *,
    root: Path,
    repo: str,
    lane: str,
    gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> dict[str, Any]:
    repo = validate_repo(repo)
    lane = str(lane or "").strip()
    if not lane:
        raise ValueError("lane is required")

    state = read_legacy_state(root)
    managed_map = state.get("managed") or {}
    if not isinstance(managed_map, Mapping):
        raise ValueError("legacy managed state must be an object")
    managed = _mapping(managed_map.get(lane), f"managed.{lane}")

    branch = str(managed.get("branch") or "").strip()
    if not branch:
        raise ValueError(f"managed.{lane}.branch is required")
    pr_number = _positive_int(managed.get("pr_number"), f"managed.{lane}.pr_number")

    pr = _mapping(
        _run_readonly_gh(
            [
                "gh",
                "pr",
                "view",
                str(pr_number),
                "--repo",
                repo,
                "--json",
                PR_JSON_FIELDS,
                "--jq=.",
            ],
            cwd=root,
            runner=gh_runner,
        ),
        "GitHub PR observation",
    )

    pr_state = str(pr.get("state") or "").upper()
    head_sha = str(pr.get("headRefOid") or "").strip()
    live_branch = str(pr.get("headRefName") or "").strip()
    base_branch = str(pr.get("baseRefName") or "").strip()
    review = str(pr.get("reviewDecision") or "").upper()
    ci_state = _ci_state(pr.get("statusCheckRollup"))

    human_gate_pending, projected_gates = _current_human_gate(
        state,
        lane=lane,
        pr_number=pr_number,
        head_sha=head_sha,
        pr_state=pr_state,
    )
    review_required = review in {"CHANGES_REQUESTED", "REVIEW_REQUIRED"}

    projected_managed = {
        lane: {
            "branch": branch,
            "pr_number": pr_number,
            "authority_key": str(managed.get("authority_key") or "").strip(),
            "expected_head": str(managed.get("expected_head") or "").strip(),
            "auto_merge_eligible": bool(managed.get("auto_merge_eligible", False)),
            "changed_paths": [
                str(path)
                for path in (managed.get("changed_paths") or [])
                if isinstance(path, str) and path.strip()
            ],
        }
    }

    evidence_sha = head_sha if ci_state == "PASS" else ""

    return {
        "schema_version": 1,
        "managed": {
            "state": {
                "managed": projected_managed,
                "pending_worker": _project_pending_worker(state),
                "human_gate_records": projected_gates,
                "paused": bool(state.get("paused", False)),
            },
            "observation": {
                "lane": lane,
                "pr_number": pr_number,
                "active_pr": pr_state == "OPEN",
                "base_branch": base_branch,
                "head_branch": live_branch,
                "current_head_sha": head_sha,
                "evidence_sha": evidence_sha,
                "reviewed_sha": "",
                "task_spec_hash": "",
                "accepted_task_spec_hash": "",
                "ci_state": ci_state,
                "pr_class": "HUMAN_GATE" if (human_gate_pending or review_required) else "DELIVERY",
                "human_gate_pending": human_gate_pending,
                "review_required": review_required,
            },
        },
    }


def render_snapshot(snapshot: Mapping[str, Any]) -> str:
    return json.dumps(snapshot, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Collect one live managed-PR observation for Platform v2 shadow mode."
    )
    parser.add_argument("--root", default=".", help="Repository root containing legacy controller state.")
    parser.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repository owner/name.")
    parser.add_argument("--lane", required=True, help="Exact durable managed lane key.")
    args = parser.parse_args(argv)
    try:
        snapshot = collect_managed_snapshot(
            root=Path(args.root).resolve(),
            repo=args.repo,
            lane=args.lane,
        )
    except (ValueError, subprocess.SubprocessError) as exc:
        print(f"shadow collection rejected: {exc}", file=sys.stderr)
        return 2
    print(render_snapshot(snapshot))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
