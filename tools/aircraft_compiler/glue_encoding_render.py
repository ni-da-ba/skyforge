from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_glue_encoding_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "glue_encoding.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    commands = [
        "# AIRCRAFT-001 v0.11 assembly fixture",
        "# Run probe_place.mcfunction first. This file places the Physics Assembler and applies edge-exact Create Super Glue.",
        "# Requires command permission level 2 because Create's glue command is privileged.",
        result["physicsAssemblerCommand"],
        *result["glueCommands"],
    ]
    (out_dir / "probe_assembly_fixture.mcfunction").write_text("\n".join(commands) + "\n", encoding="utf-8")

    r = result["readiness"]
    m = result["metrics"]
    lines = [
        f"assetId={result['assetId']}",
        f"compilerVersion={result['compilerVersion']}",
        f"digestSha256={result['digestSha256']}",
        f"mainBodyPlacementCount={m['mainBodyPlacementCount']}",
        f"nestedChildPlacementCount={m['nestedChildPlacementCount']}",
        f"glueCommandCount={m['glueCommandCount']}",
        f"assemblerPlacementCommandCount={m['assemblerPlacementCommandCount']}",
        f"adhesionApplicationEncodingReady={r['adhesionApplicationEncodingReady']}",
        f"mainBodyPhysicsAssemblyProbeReady={r['mainBodyPhysicsAssemblyProbeReady']}",
        f"physicsAssemblyProbeReady={r['physicsAssemblyProbeReady']}",
        f"runtimeQualificationReady={r['runtimeQualificationReady']}",
        f"flightQualified={r['flightQualified']}",
        "remainingMechanicalBlockers=" + ",".join(r["remainingMechanicalBlockers"]),
        "runtimeBlockers=" + ",".join(r["runtimeBlockers"]),
    ]
    for key, value in sorted(result["checks"].items()):
        lines.append(f"{key}={value}")
    (out_dir / "glue_encoding_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
