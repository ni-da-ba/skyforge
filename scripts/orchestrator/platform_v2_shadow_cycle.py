"""One read-only hosted Platform v2 shadow/parity cycle.

The cycle reads durable legacy state, observes each managed PR through the R3B
collector, evaluates v2 through R3A, compares through R3C, and emits a single
canonical JSON document. It does not write repository/controller state.
"""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Callable, Mapping, Sequence

import platform_v2_shadow_collector as collector
import platform_v2_shadow_parity as parity_cli
import platform_v2_shadow_runner as shadow_runner
from v2.identity import canonical_digest


_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def validate_current_main_gh_command(
    args: Sequence[str],
    *,
    repo: str,
) -> tuple[str, ...]:
    validated_repo = collector.validate_repo(repo)
    command = tuple(str(part) for part in args)
    expected = (
        "gh",
        "api",
        f"repos/{validated_repo}/commits/main",
        "--jq",
        ".sha",
    )
    if command != expected:
        raise ValueError(
            "shadow cycle permits only the exact read-only GitHub main-commit lookup"
        )
    return command


def read_current_main(
    root: Path,
    *,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> str:
    validated_repo = collector.validate_repo(repo)
    command = validate_current_main_gh_command(
        [
            "gh",
            "api",
            f"repos/{validated_repo}/commits/main",
            "--jq",
            ".sha",
        ],
        repo=validated_repo,
    )
    result = runner(
        list(command),
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
        timeout=30,
    )
    sha = result.stdout.strip().lower()
    if not _SHA_RE.fullmatch(sha):
        raise ValueError(
            "GitHub main-commit lookup did not return a 40-character lowercase SHA"
        )
    return sha


def _managed_lanes(state: Mapping[str, Any]) -> tuple[str, ...]:
    managed = state.get("managed") or {}
    if not isinstance(managed, Mapping):
        raise ValueError("legacy managed state must be an object")
    lanes: list[str] = []
    for raw_lane, raw_record in managed.items():
        if not isinstance(raw_lane, str) or not raw_lane.strip():
            raise ValueError("managed lane keys must be non-empty strings")
        if not isinstance(raw_record, Mapping):
            raise ValueError(f"managed.{raw_lane} must be an object")
        lanes.append(raw_lane)
    return tuple(sorted(lanes))


def run_shadow_cycle(
    *,
    root: Path,
    repo: str,
    gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> dict[str, Any]:
    state = collector.read_legacy_state(root)
    lanes = _managed_lanes(state)
    current_main = read_current_main(root, repo=repo, runner=gh_runner)

    samples: list[dict[str, Any]] = []
    classifications: Counter[str] = Counter()

    for lane in lanes:
        snapshot = collector.collect_managed_snapshot(
            root=root,
            repo=repo,
            lane=lane,
            gh_runner=gh_runner,
        )
        report = json.loads(shadow_runner.render_report(snapshot))
        parity = json.loads(
            parity_cli.render_sample(
                state=state,
                snapshot=snapshot,
                report=report,
                current_main=current_main,
            )
        )
        sample = parity.get("sample")
        if not isinstance(sample, dict):
            raise ValueError(f"parity sample for lane {lane} is malformed")
        classification = str(sample.get("classification") or "").strip()
        if not classification:
            raise ValueError(f"parity sample for lane {lane} lacks classification")
        classifications[classification] += 1
        samples.append(
            {
                "lane": lane,
                "snapshot": snapshot,
                "shadow_report": report,
                "parity": parity,
            }
        )

    body = {
        "schema_version": 1,
        "current_main": current_main,
        "managed_lane_count": len(lanes),
        "samples": samples,
        "summary": dict(sorted(classifications.items())),
    }
    return {**body, "digest": canonical_digest(body)}


def render_cycle(value: Mapping[str, Any]) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Run one read-only Platform v2 live shadow/parity cycle."
    )
    parser.add_argument("--root", default=".", help="Repository root.")
    parser.add_argument("--repo", default=collector.DEFAULT_REPO, help="GitHub owner/name.")
    args = parser.parse_args(argv)

    try:
        value = run_shadow_cycle(
            root=Path(args.root).resolve(),
            repo=args.repo,
        )
    except (ValueError, subprocess.SubprocessError, json.JSONDecodeError) as exc:
        print(f"shadow cycle rejected: {exc}", file=sys.stderr)
        return 2

    print(render_cycle(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
