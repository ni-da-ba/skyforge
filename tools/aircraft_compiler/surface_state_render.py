from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_surface_state_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "surface_state_resolution.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    metrics = result["metrics"]
    readiness = result["readiness"]
    lines = [
        f"assetId={result['assetId']}",
        f"compilerVersion={result['compilerVersion']}",
        f"digestSha256={result['digestSha256']}",
        f"providerPlacementCount={metrics['providerPlacementCount']}",
        f"stateResolvedCount={metrics['stateResolvedCount']}",
        f"stateConflictCount={metrics['stateConflictCount']}",
        f"remainingUnresolvedStateOrResourceCount={metrics['remainingUnresolvedStateOrResourceCount']}",
        f"surfaceStateResolutionComplete={readiness['surfaceStateResolutionComplete']}",
        f"probeSchematicEmissionReady={readiness['probeSchematicEmissionReady']}",
        f"runtimeQualificationReady={readiness['runtimeQualificationReady']}",
        f"flightQualified={readiness['flightQualified']}",
        "staticBlockers=" + ",".join(readiness["staticBlockers"]),
        "runtimeBlockers=" + ",".join(readiness["runtimeBlockers"]),
    ]
    for conflict in result["conflicts"]:
        lines.append(
            "conflict=" + json.dumps(
                {"lattice": conflict["lattice"], "roles": conflict["roles"], "demandedAxes": conflict["demandedAxes"], "reason": conflict["reason"]},
                sort_keys=True,
                separators=(",", ":"),
            )
        )
    (out_dir / "surface_state_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
