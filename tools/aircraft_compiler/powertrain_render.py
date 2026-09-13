from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def _state_suffix(state: dict[str, Any]) -> str:
    if not state:
        return ""
    return "[" + ",".join(f"{k}={state[k]}" for k in sorted(state)) + "]"


def _rel_scalar(v: int) -> str:
    return "~" if v == 0 else f"~{v}"


def _rel_coord(values: list[int]) -> str:
    return " ".join(_rel_scalar(int(v)) for v in values)


def emit_powertrain_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "powertrain_v0.12.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    commands: list[str] = []
    for p in result["placements"]:
        commands.append(
            f"setblock {_rel_coord(p['lattice'])} {p['resourceId']}{_state_suffix(p.get('blockState', {}))} replace"
        )
    domain = result["powerplantGlueDomain"]
    commands.append(f"create glue {_rel_coord(domain['from'])} {_rel_coord(domain['to'])}")
    (out_dir / "powertrain_patch.mcfunction").write_text("\n".join(commands) + "\n", encoding="utf-8")

    summary = [
        "AIRCRAFT-001 v0.12 governed powertrain lowering",
        f"digestSha256={result['digestSha256']}",
        f"replacementCount={result['replacementCount']}",
        f"additionCount={result['additionCount']}",
        f"resultingMovingMainBodyPlacementCount={result['metrics']['resultingMovingMainBodyPlacementCount']}",
        f"expectedPrimarySableTransferCount={result['metrics']['expectedPrimarySableTransferCount']}",
        f"governorTargetRpm={result['governor']['firstAcceptedTargetRpm']}",
        f"staticTopologyPassed={str(result['readiness']['powertrainStaticTopologyPassed']).lower()}",
        "runtimeQualified=false",
        "flightQualified=false",
    ]
    (out_dir / "powertrain_validation.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")
