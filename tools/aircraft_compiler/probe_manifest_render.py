from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def _state_suffix(state: dict[str, Any]) -> str:
    if not state:
        return ""
    parts = []
    for key, value in sorted(state.items()):
        text = ("true" if value else "false") if isinstance(value, bool) else str(value)
        parts.append(f"{key}={text}")
    return "[" + ",".join(parts) + "]"


def emit_probe_manifest_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "probe_placement_manifest.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    commands = ["# AIRCRAFT-001 v0.9 probe-only placement commands", "# Execute from the intended local origin. This does NOT create glue/assembler/control topology."]
    for p in result["placements"]:
        x, y, z = p["lattice"]
        commands.append(f"setblock ~{x} ~{y} ~{z} {p['resourceId']}{_state_suffix(p['blockState'])} replace")
    (out_dir / "probe_place.mcfunction").write_text("\n".join(commands) + "\n", encoding="utf-8")
    r = result["readiness"]
    lines = [
        f"assetId={result['assetId']}", f"compilerVersion={result['compilerVersion']}", f"digestSha256={result['digestSha256']}",
        f"placementCount={len(result['placements'])}", f"boundsMin={result['bounds']['min']}", f"boundsMax={result['bounds']['max']}", f"boundsSizeBlocks={result['bounds']['sizeBlocks']}",
        f"probePlacementManifestReady={r['probePlacementManifestReady']}", f"probePlacementCommandsReady={r['probePlacementCommandsReady']}", f"physicsAssemblyProbeReady={r['physicsAssemblyProbeReady']}",
        f"runtimeQualificationReady={r['runtimeQualificationReady']}", f"flightQualified={r['flightQualified']}",
        "staticBlockers=" + ",".join(r["staticBlockers"]), "mechanicalBlockers=" + ",".join(r["mechanicalBlockers"]), "runtimeBlockers=" + ",".join(r["runtimeBlockers"]),
    ]
    for key, value in sorted(result["kindCounts"].items()):
        lines.append(f"kindCount.{key}={value}")
    for key, value in sorted(result["validationChecks"].items()):
        lines.append(f"{key}={value}")
    (out_dir / "probe_manifest_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
