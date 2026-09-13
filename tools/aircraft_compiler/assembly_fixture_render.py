from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_assembly_fixture_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "assembly_fixture.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    r = result["readiness"]
    m = result["metrics"]
    a = result["physicsAssemblerPlacement"]
    lines = [
        f"assetId={result['assetId']}",
        f"compilerVersion={result['compilerVersion']}",
        f"digestSha256={result['digestSha256']}",
        f"physicsAssemblerLattice={a['lattice']}",
        f"physicsAssemblerResource={a['resourceId']}",
        f"physicsAssemblerState={json.dumps(a['blockState'], sort_keys=True, separators=(',',':'))}",
        f"assemblySeedLattice={a['seedLattice']}",
        f"manifestPlacementCount={m['manifestPlacementCount']}",
        f"fixturePlacementCount={m['fixturePlacementCount']}",
        f"mainBodyManifestPlacementCount={m['mainBodyManifestPlacementCount']}",
        f"mainBodyPlacementCount={m['mainBodyPlacementCount']}",
        f"nestedChildPlacementCount={m['nestedChildPlacementCount']}",
        f"adhesionIntentEdgeCount={m['adhesionIntentEdgeCount']}",
        f"mainBodyUnreachableCount={m['mainBodyUnreachableCount']}",
        f"assemblyFixtureTopologyPassed={r['assemblyFixtureTopologyPassed']}",
        f"physicsAssemblerPlacementResolved={r['physicsAssemblerPlacementResolved']}",
        f"mainBodyAdhesionGraphResolved={r['mainBodyAdhesionGraphResolved']}",
        f"adhesionApplicationEncodingReady={r['adhesionApplicationEncodingReady']}",
        f"physicsAssemblyProbeReady={r['physicsAssemblyProbeReady']}",
        f"runtimeQualificationReady={r['runtimeQualificationReady']}",
        f"flightQualified={r['flightQualified']}",
        "mechanicalBlockers=" + ",".join(r["mechanicalBlockers"]),
    ]
    for key, value in sorted(result["topologyChecks"].items()):
        lines.append(f"{key}={value}")
    (out_dir / "assembly_fixture_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
