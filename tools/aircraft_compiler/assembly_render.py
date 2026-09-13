from __future__ import annotations

import html
import json
from pathlib import Path
from typing import Any


def _esc(value: Any) -> str:
    return html.escape(str(value))


def _mobile_svg(plan: dict[str, Any]) -> str:
    sites = plan["sites"]
    xs = [site["x"] for site in sites]
    zs = [site["z"] for site in sites]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    lattice_w = max(1, max_x - min_x + 1)
    lattice_h = max(1, max_z - min_z + 1)
    plot_w = 720.0
    plot_h = 520.0
    cell = min(plot_w / lattice_w, plot_h / lattice_h, 24.0)
    origin_x = 40.0
    origin_y = 240.0

    def px(x: int) -> float:
        return origin_x + (x - min_x) * cell

    def py(z: int) -> float:
        return origin_y + (max_z - z) * cell

    rects = []
    overlap_marks = []
    for site in sites:
        x = px(site["x"])
        y = py(site["z"])
        rects.append(
            f'<rect x="{x:.2f}" y="{y:.2f}" width="{cell:.2f}" height="{cell:.2f}" '
            'fill="#f4f4f4" stroke="#333" stroke-width="0.8"/>'
        )
        if len(site["roles"]) > 1:
            overlap_marks.append(
                f'<circle cx="{x + cell/2:.2f}" cy="{y + cell/2:.2f}" r="{max(2.0, cell*0.22):.2f}" '
                'fill="#333"/>'
            )

    station_marks = []
    for station in plan.get("stations", []):
        sx, _, sz = station["lattice"]
        x = px(sx) + cell / 2
        y = py(sz) + cell / 2
        station_marks.append(
            f'<path d="M {x-5:.2f} {y:.2f} L {x+5:.2f} {y:.2f} M {x:.2f} {y-5:.2f} L {x:.2f} {y+5:.2f}" '
            'stroke="#000" stroke-width="2"/>'
        )

    metrics = plan["metrics"]
    validation = plan["validation"]
    title = "AIRCRAFT-001 v0.3 assembly-site proof"
    subtitle = (
        f'{metrics["inputCellRecordCount"]} semantic records -> '
        f'{metrics["uniqueAssemblySiteCount"]} unique lattice sites; '
        f'{metrics["multiRoleSiteCount"]} multi-role sites'
    )
    digest = plan["digestSha256"]
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="820" height="920" viewBox="0 0 820 920">
<rect width="820" height="920" fill="white"/>
<text x="40" y="52" font-family="system-ui, sans-serif" font-size="26" font-weight="700">{_esc(title)}</text>
<text x="40" y="82" font-family="system-ui, sans-serif" font-size="15">{_esc(subtitle)}</text>
<text x="40" y="108" font-family="system-ui, sans-serif" font-size="14">Validation: {_esc(validation["scope"])} = {_esc(validation["passed"])}</text>
<text x="40" y="139" font-family="system-ui, sans-serif" font-size="17" font-weight="700">Top projection: coordinate-unique assembly sites</text>
<g>{''.join(rects)}{''.join(overlap_marks)}{''.join(station_marks)}</g>
<text x="40" y="790" font-family="system-ui, sans-serif" font-size="14">Filled dot = multiple semantic roles coalesced at one block coordinate.</text>
<text x="40" y="814" font-family="system-ui, sans-serif" font-size="14">Cross = semantic station/anchor; it is not silently counted as an occupied block.</text>
<text x="40" y="850" font-family="ui-monospace, monospace" font-size="12">digest: {_esc(digest)}</text>
<text x="40" y="878" font-family="system-ui, sans-serif" font-size="13">Concrete Create Aeronautics resource identity remains deferred pending runtime evidence.</text>
</svg>'''


def emit_assembly_outputs(plan: dict[str, Any], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "assembly_plan.json").write_text(
        json.dumps(plan, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    metrics = plan["metrics"]
    lines = [
        "AIRCRAFT-001 v0.3 assembly-site validation",
        f"passed={plan['validation']['passed']}",
        f"scope={plan['validation']['scope']}",
        f"inputCellRecordCount={metrics['inputCellRecordCount']}",
        f"uniqueAssemblySiteCount={metrics['uniqueAssemblySiteCount']}",
        f"collapsedRoleRecordCount={metrics['collapsedRoleRecordCount']}",
        f"multiRoleSiteCount={metrics['multiRoleSiteCount']}",
        f"coordinateUniquenessSatisfied={metrics['coordinateUniquenessSatisfied']}",
        f"allSourceRolesPreserved={metrics['allSourceRolesPreserved']}",
        f"digestSha256={plan['digestSha256']}",
    ]
    (out_dir / "assembly_validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (out_dir / "assembly_mobile_review.svg").write_text(_mobile_svg(plan), encoding="utf-8")
