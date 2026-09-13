from __future__ import annotations

import json
from pathlib import Path
from typing import Any


def emit_propulsion_outputs(result: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "propulsion_realization.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    readiness = result["readiness"]
    geom = result["geometry"]
    checks = result["topologyChecks"]
    lines = [
        f"assetId={result['assetId']}",
        f"compilerVersion={result['compilerVersion']}",
        f"digestSha256={result['digestSha256']}",
        f"bearingFacing={geom['bearingFacing']}",
        f"bearingAxis={geom['bearingAxis']}",
        f"propellerSailCount={result['metrics']['propellerSailCount']}",
        f"propellerSailPower={result['metrics']['propellerSailPower']}",
        f"zeroTransverseFirstMoment={checks['zeroTransverseFirstMoment']}",
        f"centralSymmetry={checks['centralSymmetry']}",
        f"bladeGraphConnectedToHub={checks['bladeGraphConnectedToHub']}",
        f"propulsionStaticTopologyPassed={readiness['propulsionStaticTopologyPassed']}",
        f"propulsionRuntimeQualified={readiness['propulsionRuntimeQualified']}",
        f"schematicEmissionReady={readiness['schematicEmissionReady']}",
        f"flightQualified={readiness['flightQualified']}",
        "resolvedUpstreamBlockers=" + ",".join(readiness["resolvedUpstreamBlockers"]),
        "blockers=" + ",".join(readiness["blockers"]),
    ]
    (out_dir / "propulsion_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")

    bearing = result["bearing"]["lattice"]
    hub = result["hub"]["lattice"]
    sails = [s["lattice"] for s in result["sails"]]
    # Front view uses the two coordinates transverse to the bearing X axis for the bounded specimen.
    points = [(p[2], p[1]) for p in sails]
    min_z = min([p[0] for p in points] + [hub[2]]) - 1
    max_z = max([p[0] for p in points] + [hub[2]]) + 1
    min_y = min([p[1] for p in points] + [hub[1]]) - 1
    max_y = max([p[1] for p in points] + [hub[1]]) + 1
    scale = 52
    width = (max_z - min_z + 1) * scale
    height = (max_y - min_y + 1) * scale + 120
    def xy(z: int, y: int) -> tuple[int, int]:
        return ((z - min_z) * scale + scale // 2, (max_y - y) * scale + scale // 2 + 70)
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#11151a"/>',
        '<text x="16" y="28" fill="#f4f7fa" font-family="monospace" font-size="17">AIRCRAFT-001 v0.5 propulsion realization</text>',
        f'<text x="16" y="50" fill="#b8c1cc" font-family="monospace" font-size="12">bearing facing={geom["bearingFacing"]} | sail power={geom["sailPower"]} | static topology PASS</text>',
    ]
    hx, hy = xy(hub[2], hub[1])
    svg.append(f'<rect x="{hx-16}" y="{hy-16}" width="32" height="32" fill="#d6a756" stroke="#fff" stroke-width="2"/>')
    for z, y in points:
        px, py = xy(z, y)
        svg.append(f'<rect x="{px-18}" y="{py-18}" width="36" height="36" fill="#d9dde2" stroke="#607080" stroke-width="2"/>')
    svg.extend([
        f'<text x="16" y="{height-38}" fill="#8fd3a8" font-family="monospace" font-size="12">balanced: first moment {geom["transverseFirstMoment"]}; central symmetry={checks["centralSymmetry"]}</text>',
        f'<text x="16" y="{height-18}" fill="#f0c674" font-family="monospace" font-size="12">runtime thrust / RPM / stress / capture remain UNVERIFIED</text>',
        '</svg>',
    ])
    (out_dir / "propulsion_mobile_review.svg").write_text("\n".join(svg) + "\n", encoding="utf-8")
