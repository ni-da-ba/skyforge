from __future__ import annotations

import html
import json
from pathlib import Path

from model import Cell, CompiledAsset

ROLE_FILL = {
    "foundation": "#6f6f6f", "floor": "#98704c", "wall_infill": "#dedbcf",
    "structural_frame": "#49301f", "window": "#8fc4df", "roof": "#424752",
    "door": "#75533a", "counter": "#263d5e", "hardware": "#b38a3c",
    "institutional_accent": "#173a73",
    "lighting": "#d7b55b", "signal_mast": "#4e5965", "board": "#34506f",
    "seating": "#8a6848", "records": "#6a4a32", "desk": "#705238",
    "storage": "#9a7447", "workbench": "#60666b", "tool_storage": "#4d555d",
}


def _projection(compiled: CompiledAsset, view: str) -> dict[tuple[int, int], Cell]:
    result: dict[tuple[int, int], tuple[int, Cell]] = {}
    for (x, y, z), cell in compiled.model.cells.items():
        if view == "front":
            key, depth = (x, y), z
        elif view == "east":
            key, depth = (z, y), x
        elif view == "top":
            key, depth = (x, z), y
        else:
            raise ValueError(view)
        if key not in result or depth > result[key][0]:
            result[key] = (depth, cell)
    return {k: v for k, (_d, v) in result.items()}


def write_orthographic_svg(path: Path, compiled: CompiledAsset, view: str, scale: int = 18) -> None:
    max_x, max_y, max_z = compiled.summary["layout"]["bounds"]["max"]
    visible = _projection(compiled, view)
    if view == "front":
        w, h = max_x + 1, max_y + 1
        transform = lambda a, b: (a * scale, (max_y - b) * scale)
    elif view == "east":
        w, h = max_z + 1, max_y + 1
        transform = lambda a, b: (a * scale, (max_y - b) * scale)
    elif view == "top":
        w, h = max_x + 1, max_z + 1
        transform = lambda a, b: (a * scale, (max_z - b) * scale)
    else:
        raise ValueError(view)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w*scale}" height="{h*scale}" viewBox="0 0 {w*scale} {h*scale}">',
        '<rect width="100%" height="100%" fill="#f4f4f2"/>',
    ]
    for (a, b), cell in sorted(visible.items()):
        x, y = transform(a, b)
        parts.append(f'<rect x="{x}" y="{y}" width="{scale}" height="{scale}" fill="{ROLE_FILL.get(cell.role, "#aaa")}" stroke="#d0d0d0" stroke-width="0.5"/>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def write_floorplan_svg(path: Path, compiled: CompiledAsset, scale: int = 24) -> None:
    layout = compiled.summary["layout"]
    max_x, _max_y, max_z = layout["bounds"]["max"]
    colors = {"public_service":"#d7e4f2", "administrative":"#cad7e8", "working":"#e6d6bd", "freight_storage":"#dcc8a6", "repair":"#c8c8c8"}
    width, height = (max_x + 3) * scale, (max_z + 3) * scale
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#faf9f5"/>',
    ]
    for name, vol in layout["volumes"].items():
        x0, _y0, z0 = vol["min"]
        x1, _y1, z1 = vol["max"]
        rx, ry = (x0 + 1) * scale, (max_z - z1 + 1) * scale
        rw, rh = (x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale
        parts.append(f'<rect x="{rx}" y="{ry}" width="{rw}" height="{rh}" fill="{colors.get(vol["role"], "#eee")}" fill-opacity="0.55" stroke="#666"/>')
        parts.append(f'<text x="{rx+4}" y="{ry+14}" font-size="11" font-family="sans-serif">{html.escape(name)}</text>')
    for name, (x, _y, z) in layout["anchors"].items():
        cx, cy = (x + 1.5) * scale, (max_z - z + 1.5) * scale
        parts.append(f'<circle cx="{cx}" cy="{cy}" r="4" fill="#8e5f22"/><text x="{cx+6}" y="{cy-5}" font-size="9" font-family="sans-serif">{html.escape(name)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def _shade(color: str, factor: float) -> str:
    value = color.lstrip("#")
    rgb = [int(value[i:i+2], 16) for i in (0, 2, 4)]
    rgb = [max(0, min(255, int(v*factor))) for v in rgb]
    return "#" + "".join(f"{v:02x}" for v in rgb)


def _write_iso_cells(path: Path, cells: dict[tuple[int, int, int], Cell], bounds: dict, scale: int = 9) -> None:
    min_x, min_y, min_z = bounds["min"]
    max_x, max_y, max_z = bounds["max"]
    span_x, span_y, span_z = max_x-min_x+1, max_y-min_y+1, max_z-min_z+1
    ox, oy = (span_z + 2) * scale + 40, (span_y + 3) * scale + 20
    width = (span_x + span_z + 6) * scale + 100
    height = (span_y * 2 + span_x + span_z + 8) * scale // 2 + 100
    project = lambda x, y, z: (ox + ((x-min_x)-(z-min_z))*scale, oy + ((x-min_x)+(z-min_z))*scale*0.5 - (y-min_y)*scale)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#f5f4ef"/>',
    ]
    occupied = set(cells)
    for (x, y, z), cell in sorted(cells.items(), key=lambda item: (sum(item[0]), item[0][1], item[0][0])):
        base = ROLE_FILL.get(cell.role, "#aaa")
        faces = []
        if (x, y+1, z) not in occupied:
            faces.append(([project(x,y+1,z), project(x+1,y+1,z), project(x+1,y+1,z+1), project(x,y+1,z+1)], _shade(base,1.10)))
        if (x+1, y, z) not in occupied:
            faces.append(([project(x+1,y,z), project(x+1,y+1,z), project(x+1,y+1,z+1), project(x+1,y,z+1)], _shade(base,0.83)))
        if (x, y, z+1) not in occupied:
            faces.append(([project(x,y,z+1), project(x+1,y,z+1), project(x+1,y+1,z+1), project(x,y+1,z+1)], _shade(base,0.95)))
        for points, fill in faces:
            coords = " ".join(f"{px:.1f},{py:.1f}" for px, py in points)
            parts.append(f'<polygon points="{coords}" fill="{fill}" stroke="#555" stroke-width="0.35"/>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def write_isometric_svg(path: Path, compiled: CompiledAsset, scale: int = 9) -> None:
    _write_iso_cells(path, compiled.model.cells, compiled.summary["layout"]["bounds"], scale)


def write_interior_plan_svg(path: Path, compiled: CompiledAsset, scale: int = 24) -> None:
    """Top-down interior QA view emphasizing shell, furnishings, and functional anchors."""
    layout = compiled.summary["layout"]
    min_x, _min_y, min_z = layout["bounds"]["min"]
    max_x, _max_y, max_z = layout["bounds"]["max"]
    width, height = (max_x-min_x+3)*scale, (max_z-min_z+3)*scale
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#faf9f5"/>',
    ]

    priority = {
        "board": 10, "counter": 10, "seating": 9, "records": 9, "desk": 9,
        "storage": 9, "workbench": 9, "tool_storage": 9,
        "door": 8, "wall_infill": 7, "structural_frame": 7, "window": 6,
        "floor": 1, "foundation": 0,
    }
    visible: dict[tuple[int,int], tuple[int, Cell]] = {}
    for (x,y,z), cell in compiled.model.cells.items():
        if y > 4 or cell.role == "roof":
            continue
        p = priority.get(cell.role, 5)
        key = (x,z)
        if key not in visible or p > visible[key][0] or (p == visible[key][0] and y > 1):
            visible[key] = (p, cell)

    for (x,z), (_p,cell) in sorted(visible.items()):
        rx = (x-min_x+1)*scale
        ry = (max_z-z+1)*scale
        parts.append(f'<rect x="{rx}" y="{ry}" width="{scale}" height="{scale}" fill="{ROLE_FILL.get(cell.role,"#ddd")}" stroke="#ddd" stroke-width="0.5"/>')

    for name,(x,_y,z) in layout["anchors"].items():
        if name not in {"AIRFIELD_INTERFACE"}:
            cx=(x-min_x+1.5)*scale; cy=(max_z-z+1.5)*scale
            parts.append(f'<circle cx="{cx}" cy="{cy}" r="4" fill="#9b5e20"/><text x="{cx+6}" y="{cy-5}" font-size="8" font-family="sans-serif">{html.escape(name)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def write_cutaway_isometric_svg(path: Path, compiled: CompiledAsset, scale: int = 10) -> None:
    """Remove roof plus the south public facade/east working facade to expose the compiled interior."""
    layout = compiled.summary["layout"]
    public_hall = layout["volumes"]["publicHall"]
    working = layout["volumes"]["workingWing"]
    south_z = public_hall["max"][2]
    east_x = working["max"][0]
    cells: dict[tuple[int,int,int],Cell] = {}
    for pos,cell in compiled.model.cells.items():
        x,y,z = pos
        if cell.role == "roof":
            continue
        if z == south_z and public_hall["min"][0] <= x <= public_hall["max"][0] and y >= 2:
            if cell.module not in {"service_counter","guild_identity"}:
                continue
        if x == east_x and working["min"][2] <= z <= working["max"][2] and y >= 2:
            if cell.module not in {"repair_workbench","repair_tools","freight_stack"}:
                continue
        cells[pos]=cell
    _write_iso_cells(path,cells,layout["bounds"],scale)


def write_section_svg(path: Path, compiled: CompiledAsset, scale: int = 22) -> None:
    """Longitudinal section along the public entrance/counter centerline."""
    layout = compiled.summary["layout"]
    section_x = layout["anchors"]["PUBLIC_ENTRANCE"][0]
    min_x,min_y,min_z = layout["bounds"]["min"]
    max_x,max_y,max_z = layout["bounds"]["max"]
    width=(max_z-min_z+3)*scale; height=(max_y-min_y+3)*scale
    parts=[
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#faf9f5"/>',
    ]
    for (x,y,z),cell in sorted(compiled.model.cells.items()):
        if x != section_x:
            continue
        rx=(z-min_z+1)*scale; ry=(max_y-y+1)*scale
        parts.append(f'<rect x="{rx}" y="{ry}" width="{scale}" height="{scale}" fill="{ROLE_FILL.get(cell.role,"#aaa")}" stroke="#d2d2d2" stroke-width="0.5"/>')
    for name,(x,y,z) in layout["anchors"].items():
        if x != section_x or name == "AIRFIELD_INTERFACE":
            continue
        cx=(z-min_z+1.5)*scale; cy=(max_y-y+1.5)*scale
        parts.append(f'<circle cx="{cx}" cy="{cy}" r="4" fill="#9b5e20"/><text x="{cx+6}" y="{cy-6}" font-size="8" font-family="sans-serif">{html.escape(name)}</text>')
    parts.append("</svg>")
    path.write_text("\n".join(parts),encoding="utf-8")


def emit_outputs(compiled: CompiledAsset, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    s = compiled.summary
    (out / "resolved.json").write_text(json.dumps(s, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out / "blocks.json").write_text(json.dumps([{"pos":[x,y,z], **cell.to_dict()} for (x,y,z), cell in sorted(compiled.model.cells.items())], indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (out / "anchors.json").write_text(json.dumps(s["layout"]["anchors"], indent=2, sort_keys=True) + "\n", encoding="utf-8")
    lines = ["PASS" if s["validation"]["passed"] else "FAIL", *s["validation"]["issues"], f"blocks={s['blockCount']}", f"digestSha256={s['digestSha256']}"]
    (out / "validation.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    for view in ("front", "east", "top"):
        write_orthographic_svg(out / f"{view}.svg", compiled, view)
    write_floorplan_svg(out / "floorplan.svg", compiled)
    write_isometric_svg(out / "isometric.svg", compiled)
    if str(s.get("compilerVersion")) in {"0.4", "0.5"}:
        write_interior_plan_svg(out / "interior_plan.svg", compiled)
        write_cutaway_isometric_svg(out / "cutaway_isometric.svg", compiled)
        write_section_svg(out / "interior_section.svg", compiled)
