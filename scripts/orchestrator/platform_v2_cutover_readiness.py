"""Read-only Release-5 cutover readiness reporter."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Mapping

from platform_v2_shadow_collector import read_legacy_state
from v2.cutover import (
    CutoverReadinessInput,
    LegacyOperationalProjection,
    evaluate_cutover_readiness,
)


def _read_mapping(path: Path, label: str) -> Mapping[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{label} is unreadable: {path}") from exc
    if not isinstance(value, Mapping):
        raise ValueError(f"{label} must be a JSON object")
    return value


def build_report(
    *,
    legacy_state: Mapping[str, Any],
    observation: Mapping[str, Any],
) -> dict[str, Any]:
    projection = LegacyOperationalProjection.from_legacy_mapping(legacy_state)
    readiness = CutoverReadinessInput(
        release4_accepted=observation["release4_accepted"],
        mutation_gate_disabled=observation["mutation_gate_disabled"],
        accepted_main_sha=observation["accepted_main_sha"],
        legacy_runtime_sha=observation["legacy_runtime_sha"],
        legacy_service_active=observation["legacy_service_active"],
        pre_cutover_checkpoint_pass=observation["pre_cutover_checkpoint_pass"],
        hosted_v2_runtime_accepted=observation["hosted_v2_runtime_accepted"],
        ingress_handoff_defined=observation["ingress_handoff_defined"],
        writer_revocation_plan_defined=observation["writer_revocation_plan_defined"],
        legacy_revocation_mechanism_ready=observation["legacy_revocation_mechanism_ready"],
        state_projection_complete=observation["state_projection_complete"],
        projection=projection,
    )
    decision = evaluate_cutover_readiness(readiness)
    return {
        "schema_version": 1,
        "disposition": decision.disposition.value,
        "blockers": list(decision.blockers),
        "accepted_main_sha": decision.accepted_main_sha,
        "legacy_runtime_sha": decision.legacy_runtime_sha,
        "legacy_matches_accepted_main": readiness.legacy_matches_accepted_main,
        "projection": projection.as_dict(),
        "projection_digest": projection.digest,
        "decision_digest": decision.digest,
        "ordinary_v2_mutation_authority": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Project legacy state and evaluate Release-5 cutover readiness."
    )
    parser.add_argument("--root", required=True)
    parser.add_argument("--observation", required=True)
    args = parser.parse_args(argv)

    try:
        legacy_state = read_legacy_state(Path(args.root).resolve())
        observation = _read_mapping(Path(args.observation), "cutover observation")
        report = build_report(
            legacy_state=legacy_state,
            observation=observation,
        )
    except (KeyError, ValueError) as exc:
        print(f"cutover readiness rejected: {exc}", file=sys.stderr)
        return 2

    print(json.dumps(report, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
