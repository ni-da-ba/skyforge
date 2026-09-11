#!/usr/bin/env python3
"""Bounded Skyforge asset-compiler proof for issue #488.

Stage 1-4 only:
AssetSpec -> resolved layout -> voxel/block model -> anchors/validation -> SVG QA.

No Minecraft export is emitted yet.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
from pathlib import Path

ROLE_FILL = {
    "foundation": "#6f6f6f",
    "floor": "#98704c",
    "wall_infill": "#dedbcf",
    "structural_frame": "#49301f",
    "window": "#8fc4df",
    "roof": "#424752",
}


def compile_branch(spec: dict) -> tuple[dict, dict[tuple[int, int, int], dict]]:
    s = spec["structure"]
    mats = spec["materialRoles"]

    bays = int(s["bayCount"])
    bay_w = int(s["bayWidth"])
    wall_h = int(s["wallHeight"])
    main_depth = int(s["mainDepth"])
    wing_w = int(s["workingWingWidth"])

    hall_w = bays * bay_w + 1
    hall_z0 = 4
    hall_z1 = hall_z0 + main_depth - 1
    wing_x0 = hall_w
    wing_x1 = wing_x0 + wing_w - 1
    wing_z0 = 0
    wing_z1 = hall_z1
    max_x, max_z = wing_x1, wing_z1

    def footprint(x: int, z: int) -> bool:
        hall = 0 <= x < hall_w and hall_z0 <= z <= hall_z1
        wing = wing_x0 <= x <= wing_x1 and wing_z0 <= z <= wing_z1
        return hall or wing

    cells: dict[tuple[int, int, int], dict] = {}

    def put(x: int, y: int, z: int, role: str, block: str) -> None:
        cells[(x, y, z)] = {"role": role, "block": block}

    for x in range(max_x + 1):
        for z in range(max_z + 1):
            if footprint(x, z):
                put(x, 0, z, "foundation", mats["foundation"])
                put(x, 1, z, "floor", mats["floor"])

    for x in range(max_x + 1):
        for z in range(max_z + 1):
            if not footprint(x, z):
                continue
            edge = any(
                not footprint(x + dx, z + dz)
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            if edge:
                for y in range(1, wall_h + 1):
                    put(x, y, z, "wall_infill", mats["wallInfill"])

    frame = set()
    for x in range(0, hall_w, bay_w):
        frame.add((x, hall_z0))
        frame.add((x, hall_z1))
    frame |= {(hall_w - 1, hall_z0), (hall_w - 1, hall_z1)}
    for z in (hall_z0, (hall_z0 + hall_z1) // 2, hall_z1):
        frame.add((0, z))
    for z in (wing_z0, 4, 8, 12, wing_z1):
        frame.add((wing_x1, z))
    frame |= {(wing_x0, wing_z0), (wing_x1, wing_z0)}

    for x, z in frame:
        if footprint(x, z):
            for y in range(1, wall_h + 1):
                put(x, y, z, "structural_frame", mats["structuralFrame"])

    for x in (7, 8):
        for y in (1, 2, 3):
            cells.pop((x, y, hall_z1), None)

    for z in range(10, 14):
        for y in range(1, 5):
            cells.pop((wing_x1, y, z), None)

    for z in range(2, 6):
        for y in range(1, 5):
            cells.pop((wing_x1, y, z), None)

    for x in (2, 3, 12, 13):
        for y in (3, 4):
            put(x, y, hall_z1, "window", mats["window"])

    for z in (7, 8, 12, 13):
        for y in (3, 4):
            put(0, y, z, "window", mats["window"])

    for x in (2, 3, 7, 8, 12, 13):
        for y in (3, 4):
            put(x, y, hall_z0, "window", mats["window"])

    roof_base = wall_h + 1

    def gable(x0: int, x1: int, z0: int, z1: int, rise: int) -> None:
        center = (z0 + z1) // 2
        halfspan = max(center - z0, z1 - center, 1)
        for z in range(z0, z1 + 1):
            d = abs(z - center)
            y = roof_base + round(rise * (1 - d / halfspan))
            for x in range(x0, x1 + 1):
                put(x, y, z, "roof", mats["roof"])

    gable(0, hall_w - 1, hall_z0, hall_z1, int(s["roof"]["mainRise"]))
    gable(
        wing_x0,
        wing_x1,
        wing_z0,
        wing_z1,
        int(s["roof"]["workingWingRise"]),
    )

    anchors = {
        "PUBLIC_ENTRANCE": [7, 1, hall_z1],
        "SERVICE_COUNTER": [8, 1, 11],
        "CONTRACT_BOARD": [3, 1, 14],
        "ROUTE_INFO": [12, 1, 14],
        "NPC_WORK_POINT": [8, 1, 9],
        "FREIGHT_PICKUP": [20, 1, 12],
        "FREIGHT_DROPOFF": [21, 1, 12],
        "REPAIR_BAY": [20, 1, 4],
        "AIRFIELD_INTERFACE": [wing_x1 + 1, 1, 8],
    }

    max_y = roof_base + max(
        int(s["roof"]["mainRise"]), int(s["roof"]["workingWingRise"])
    )

    layout = {
        "assetId": spec["assetId"],
        "bounds": {
            "min": [0, 0, 0],
            "max": [max_x, max_y, max_z],
            "size": [max_x + 1, max_y + 1, max_z + 1],
        },
        "volumes": {
            "publicHall": {
                "min": [0, 1, hall_z0],
                "max": [hall_w - 1, wall_h, hall_z1],
            },
            "workingWing": {
                "min": [wing_x0, 1, wing_z0],
                "max": [wing_x1, wall_h, wing_z1],
            },
            "warehouse": {
                "min": [wing_x0 + 1, 1, 9],
                "max": [wing_x1 - 1, wall_h - 1, 15],
            },
            "lightRepair": {
                "min": [wing_x0 + 1, 1, 1],
                "max": [wing_x1 - 1, wall_h - 1, 7],
            },
        },
        "anchors": anchors,
    }

    issues: list[str] = []
    for name, (x, _y, z) in anchors.items():
        if name == "AIRFIELD_INTERFACE":
            if x != wing_x1 + 1:
                issues.append(f"{name}: not immediately outside east working face")
        elif not footprint(x, z):
            issues.append(f"{name}: outside footprint")

    for x in (7, 8):
        for y in (1, 2, 3):
            if (x, y, hall_z1) in cells:
                issues.append("PUBLIC_ENTRANCE: blocked")
                break

    walkable = set()
    for x in range(max_x + 1):
        for z in range(max_z + 1):
            if footprint(x, z) and (x, 2, z) not in cells and (x, 3, z) not in cells:
                walkable.add((x, z))

    start = (7, hall_z1 - 1)
    queue = collections.deque([start])
    seen = {start}
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, z + dz)
            if p in walkable and p not in seen:
                seen.add(p)
                queue.append(p)

    for name in (
        "SERVICE_COUNTER",
        "CONTRACT_BOARD",
        "ROUTE_INFO",
        "NPC_WORK_POINT",
        "FREIGHT_PICKUP",
        "FREIGHT_DROPOFF",
        "REPAIR_BAY",
    ):
        x, _y, z = anchors[name]
        adjacent = ((x, z), (x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))
        if not any(p in seen for p in adjacent):
            issues.append(f"{name}: not reachable from public entrance")

    canonical = "\n".join(
        f"{x},{y},{z}|{v['role']}|{v['block']}"
        for (x, y, z), v in sorted(cells.items())
    )
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    material_counts = collections.Counter(v["block"] for v in cells.values())
    role_counts = collections.Counter(v["role"] for v in cells.values())

    summary = {
        "schemaVersion": spec["schemaVersion"],
        "assetId": spec["assetId"],
        "layout": layout,
        "blockCount": len(cells),
        "materialCounts": dict(sorted(material_counts.items())),
        "roleCounts": dict(sorted(role_counts.items())),
        "digestSha256": digest,
        "validation": {"passed": not issues, "issues": issues},
    }
    return summary, cells


def write_svg(path: Path, cells: dict, bounds: dict, view: str, scale: int = 18) -> None:
    max_x, max_y, max_z = bounds["max"]

    if view == "top":
        width, height = (max_x + 1) * scale, (max_z + 1) * scale
        key1, key2 = range(max_x + 1), range(max_z + 1)

        def visible(a, b):
            values = [(y, v) for (x, y, z), v in cells.items() if x == a and z == b]
            return max(values, default=None, key=lambda t: t[0])

        def rect(a, b):
            return a * scale, (max_z - b) * scale

    elif view == "front":
        width, height = (max_x + 1) * scale, (max_y + 1) * scale
        key1, key2 = range(max_x + 1), range(max_y + 1)

        def visible(a, b):
            values = [(z, v) for (x, y, z), v in cells.items() if x == a and y == b]
            return max(values, default=None, key=lambda t: t[0])

        def rect(a, b):
            return a * scale, (max_y - b) * scale

    elif view == "east":
        width, height = (max_z + 1) * scale, (max_y + 1) * scale
        key1, key2 = range(max_z + 1), range(max_y + 1)

        def visible(a, b):
            values = [(x, v) for (x, y, z), v in cells.items() if z == a and y == b]
            return max(values, default=None, key=lambda t: t[0])

        def rect(a, b):
            return a * scale, (max_y - b) * scale

    else:
        raise ValueError(view)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#f4f4f2"/>',
    ]
    for a in key1:
        for b in key2:
            item = visible(a, b)
            if not item:
                continue
            _depth, value = item
            x, y = rect(a, b)
            fill = ROLE_FILL.get(value["role"], "#aaaaaa")
            parts.append(
                f'<rect x="{x}" y="{y}" width="{scale}" height="{scale}" '
                f'fill="{fill}" stroke="#d0d0d0" stroke-width="0.5"/>'
            )
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("spec", type=Path)
    parser.add_argument("--out", type=Path, default=Path("build/asset-compiler"))
    args = parser.parse_args()

    spec = json.loads(args.spec.read_text(encoding="utf-8"))
    if spec.get("assetType") != "guild_branch":
        raise SystemExit("v0.1 proof currently supports only assetType=guild_branch")

    summary, cells = compile_branch(spec)
    out = args.out
    out.mkdir(parents=True, exist_ok=True)

    (out / "resolved.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True), encoding="utf-8"
    )
    (out / "blocks.json").write_text(
        json.dumps(
            [
                {"pos": [x, y, z], **value}
                for (x, y, z), value in sorted(cells.items())
            ],
            indent=2,
        ),
        encoding="utf-8",
    )
    (out / "anchors.json").write_text(
        json.dumps(summary["layout"]["anchors"], indent=2, sort_keys=True),
        encoding="utf-8",
    )
    (out / "validation.txt").write_text(
        (
            "PASS\n"
            if summary["validation"]["passed"]
            else "FAIL\n" + "\n".join(summary["validation"]["issues"]) + "\n"
        )
        + f"blocks={summary['blockCount']}\n"
        + f"digestSha256={summary['digestSha256']}\n",
        encoding="utf-8",
    )

    for view in ("front", "east", "top"):
        write_svg(out / f"{view}.svg", cells, summary["layout"]["bounds"], view)

    print(
        json.dumps(
            {
                "assetId": summary["assetId"],
                "blockCount": summary["blockCount"],
                "bounds": summary["layout"]["bounds"]["size"],
                "validation": summary["validation"],
                "digestSha256": summary["digestSha256"],
                "output": str(out),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
