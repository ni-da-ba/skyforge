"""Dry-run Release-4 canary guard.

This command evaluates authority only. It has no GitHub, Codex, workspace, or
repository mutation adapter.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any

import platform_v2_shadow_collector as legacy_reader
from v2.canary import (
    CanaryTaskContract,
    LegacyCanaryExclusion,
    MutationGateRecord,
    evaluate_canary_guard,
)
from v2.ownership import OwnershipToken


def _read_json(path: Path, label: str) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{label} is unreadable: {path}") from exc


def evaluate_files(
    *,
    gate_path: Path,
    spec_path: Path,
    legacy_root: Path,
    current_main: str,
    controller_id: str,
    generation: int,
    attempt_number: int,
    execute_requested: bool,
) -> dict[str, Any]:
    gate = MutationGateRecord.from_mapping(_read_json(gate_path, "mutation gate"))
    task = CanaryTaskContract.from_mapping(_read_json(spec_path, "canary task"))
    state = legacy_reader.read_legacy_state(legacy_root)
    exclusion = LegacyCanaryExclusion.from_legacy_state(
        state,
        issue_number=task.issue_number,
    )
    decision = evaluate_canary_guard(
        gate=gate,
        task=task,
        exclusion=exclusion,
        current_main=current_main,
        ownership_token=OwnershipToken(controller_id, generation),
        attempt_number=attempt_number,
        execute_requested=execute_requested,
    )
    return {
        "schema_version": 1,
        "disposition": decision.disposition.value,
        "reason": decision.reason,
        "task_spec_hash": decision.task_spec_hash,
        "attempt_id": decision.attempt_id,
        "ownership_token_digest": decision.ownership_token_digest,
        "writer_fence_relative_path": decision.writer_fence_relative_path,
        "decision_digest": decision.digest,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Evaluate the Platform v2 Release-4 canary guard without mutation."
    )
    parser.add_argument("--gate", required=True)
    parser.add_argument("--spec", required=True)
    parser.add_argument("--legacy-root", required=True)
    parser.add_argument("--current-main", required=True)
    parser.add_argument("--controller-id", required=True)
    parser.add_argument("--generation", required=True, type=int)
    parser.add_argument("--attempt", required=True, type=int)
    parser.add_argument("--execute-requested", action="store_true")
    args = parser.parse_args(argv)
    try:
        result = evaluate_files(
            gate_path=Path(args.gate).resolve(),
            spec_path=Path(args.spec).resolve(),
            legacy_root=Path(args.legacy_root).resolve(),
            current_main=args.current_main,
            controller_id=args.controller_id,
            generation=args.generation,
            attempt_number=args.attempt,
            execute_requested=args.execute_requested,
        )
    except ValueError as exc:
        print(f"canary guard rejected: {exc}", file=sys.stderr)
        return 2
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
