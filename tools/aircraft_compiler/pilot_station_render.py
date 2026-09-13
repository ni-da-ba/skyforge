from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_pilot_station_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "pilot_station_realization.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    r = result["readiness"]
    p = result["placement"]
    lines = [
        f"assetId={result['assetId']}",
        f"compilerVersion={result['compilerVersion']}",
        f"digestSha256={result['digestSha256']}",
        f"pilotAnchorLattice={result['semanticStation']['anchorLattice']}",
        f"pilotSeatLattice={p['lattice']}",
        f"pilotSeatResource={p['resourceId']}",
        f"pilotStationStaticPlacementPassed={r['pilotStationStaticPlacementPassed']}",
        f"probeSchematicEmissionReady={r['probeSchematicEmissionReady']}",
        f"runtimeQualificationReady={r['runtimeQualificationReady']}",
        f"flightQualified={r['flightQualified']}",
        "resolvedUpstreamBlockers=" + ",".join(r["resolvedUpstreamBlockers"]),
        "staticBlockers=" + ",".join(r["staticBlockers"]),
        "runtimeBlockers=" + ",".join(r["runtimeBlockers"]),
    ]
    for key, value in sorted(result["topologyChecks"].items()):
        lines.append(f"{key}={value}")
    (out_dir / "pilot_station_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
