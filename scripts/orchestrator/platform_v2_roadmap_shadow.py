"""Read-only live roadmap observation/evaluation for Platform v2 shadow."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import Any, Callable, Mapping, Sequence

import platform_v2_shadow_collector as base
from v2.events import DurableEvent
from v2.roadmap_shadow import (
    RoadmapControlObservation,
    RoadmapIssueTruth,
    ShadowRoadmapManifest,
    ShadowRoadmapState,
    evaluate_roadmap_shadow,
)


MANIFEST_RELATIVE_PATH = Path("docs/agent-state/ORCHESTRATOR_ROADMAP.json")
ISSUE_JSON_FIELDS = "state,title"


def validate_readonly_issue_command(
    args: Sequence[str],
    *,
    repo: str,
    allowed_issue_numbers: set[int],
) -> tuple[str, ...]:
    validated_repo = base.validate_repo(repo)
    command = tuple(str(part) for part in args)
    if len(command) != 9 or command[:3] != ("gh", "issue", "view"):
        raise ValueError("roadmap shadow permits only exact gh issue view reads")
    try:
        issue = int(command[3])
    except ValueError as exc:
        raise ValueError("issue target must be a positive integer") from exc
    if issue <= 0 or issue not in allowed_issue_numbers:
        raise ValueError("issue target is not authorized by accepted roadmap manifest")
    expected = (
        "gh",
        "issue",
        "view",
        str(issue),
        "--repo",
        validated_repo,
        "--json",
        ISSUE_JSON_FIELDS,
        "--jq=.",
    )
    if command != expected:
        raise ValueError("roadmap issue read command shape is not allowlisted")
    return command


def _read_manifest(code_root: Path) -> tuple[dict[str, Any], ShadowRoadmapManifest]:
    path = code_root / MANIFEST_RELATIVE_PATH
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"accepted roadmap manifest is unreadable: {path}") from exc
    return payload, ShadowRoadmapManifest.from_mapping(payload)


def _issue_truth(
    *,
    root: Path,
    repo: str,
    manifest: ShadowRoadmapManifest,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> dict[int, RoadmapIssueTruth]:
    allowed = {
        int(node.issue_number)
        for node in manifest.nodes
        if node.issue_number is not None
    }
    result: dict[int, RoadmapIssueTruth] = {}
    for issue in sorted(allowed):
        command = [
            "gh",
            "issue",
            "view",
            str(issue),
            "--repo",
            repo,
            "--json",
            ISSUE_JSON_FIELDS,
            "--jq=.",
        ]
        validated = validate_readonly_issue_command(
            command,
            repo=repo,
            allowed_issue_numbers=allowed,
        )
        completed = runner(
            list(validated),
            cwd=root,
            check=True,
            text=True,
            capture_output=True,
            timeout=60,
        )
        try:
            payload = json.loads(completed.stdout)
        except json.JSONDecodeError as exc:
            raise ValueError(f"issue #{issue} returned malformed JSON") from exc
        if not isinstance(payload, Mapping):
            raise ValueError(f"issue #{issue} observation must be an object")
        state = str(payload.get("state") or "").upper()
        if state == "OPEN":
            result[issue] = RoadmapIssueTruth.OPEN
        elif state == "CLOSED":
            result[issue] = RoadmapIssueTruth.CLOSED
        else:
            result[issue] = RoadmapIssueTruth.UNKNOWN
    return result


def _protected_pending_authority(state: Mapping[str, Any]) -> bool:
    pending = state.get("pending_events") or []
    if not isinstance(pending, list):
        raise ValueError("legacy pending_events must be a list")
    for raw in pending:
        event = DurableEvent.from_legacy_mapping(raw)
        if event.event == "roadmap" or event.protected_authority:
            return True
    return False


def _task_owned_managed_records(state: Mapping[str, Any]) -> tuple[int, ...]:
    managed = state.get("managed") or {}
    if not isinstance(managed, Mapping):
        raise ValueError("legacy managed state must be an object")
    values: list[int] = []
    for raw in managed.values():
        if not isinstance(raw, Mapping):
            raise ValueError("managed record must be an object")
        if not str(raw.get("authority_key") or "").startswith("task:"):
            continue
        number = raw.get("pr_number")
        if isinstance(number, bool) or not isinstance(number, int) or number <= 0:
            raise ValueError("task-owned managed record has invalid PR number")
        values.append(number)
    return tuple(sorted(values))


def _no_change_issues(state: Mapping[str, Any]) -> tuple[int, ...]:
    raw = state.get("task_no_change_blockers") or {}
    if not isinstance(raw, Mapping):
        raise ValueError("task_no_change_blockers must be an object")
    result: list[int] = []
    for key in raw:
        try:
            issue = int(key)
        except (TypeError, ValueError) as exc:
            raise ValueError("task_no_change blocker key must be an issue number") from exc
        if issue <= 0:
            raise ValueError("task_no_change blocker issue must be positive")
        result.append(issue)
    return tuple(sorted(result))


def collect_roadmap_shadow(
    *,
    state_root: Path,
    code_root: Path,
    repo: str,
    gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> dict[str, Any]:
    repo = base.validate_repo(repo)
    state = base.read_legacy_state(state_root)
    manifest_payload, manifest = _read_manifest(code_root)
    roadmap_state = ShadowRoadmapState.from_legacy(state.get("roadmap"), manifest)
    issue_truth = _issue_truth(
        root=state_root,
        repo=repo,
        manifest=manifest,
        runner=gh_runner,
    )

    task_owned = _task_owned_managed_records(state)
    control = RoadmapControlObservation(
        paused=bool(state.get("paused", False)),
        protected_pending_authority=_protected_pending_authority(state),
        pending_decision=isinstance(state.get("pending_decision"), Mapping),
        pending_worker=isinstance(state.get("pending_worker"), Mapping),
        blocked_kind=bool(state.get("blocked_kind")),
        open_task_owned_prs=(),
        task_pr_truth_complete=not bool(task_owned),
        no_change_blocked_issues=_no_change_issues(state),
    )
    decision = evaluate_roadmap_shadow(
        manifest=manifest,
        state=roadmap_state,
        issue_truth=issue_truth,
        control=control,
    )
    return {
        "schema_version": 1,
        "manifest": {
            "roadmap_id": manifest.roadmap_id,
            "fingerprint": manifest.fingerprint,
            "node_count": len(manifest.nodes),
        },
        "state_digest": roadmap_state.digest,
        "issue_truth": {
            str(issue): truth.value for issue, truth in sorted(issue_truth.items())
        },
        "task_owned_managed_prs": list(task_owned),
        "decision": {
            "disposition": decision.disposition.value,
            "reason": decision.reason,
            "selected_node_id": decision.selected_node_id,
            "selected_lane": decision.selected_lane,
            "selected_issue_number": decision.selected_issue_number,
            "retired_closed_blocked": list(decision.retired_closed_blocked),
            "skipped_closed_selected": list(decision.skipped_closed_selected),
            "projected_completed_runs": [list(item) for item in decision.projected_completed_runs],
            "projected_blocked_nodes": list(decision.projected_blocked_nodes),
            "digest": decision.digest,
        },
    }


def render(value: Mapping[str, Any]) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Evaluate current bounded-roadmap recovery/progression read-only."
    )
    parser.add_argument("--state-root", required=True)
    parser.add_argument("--code-root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--repo", default=base.DEFAULT_REPO)
    args = parser.parse_args(argv)
    try:
        value = collect_roadmap_shadow(
            state_root=Path(args.state_root).resolve(),
            code_root=Path(args.code_root).resolve(),
            repo=args.repo,
        )
    except (ValueError, subprocess.SubprocessError) as exc:
        print(f"roadmap shadow rejected: {exc}", file=sys.stderr)
        return 2
    print(render(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
