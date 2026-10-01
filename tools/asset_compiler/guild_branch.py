from __future__ import annotations

import collections
import hashlib
import json
from typing import Any, Callable

from model import BlockState, CompiledAsset, SpecError, Volume, VoxelModel


def _integer(value: Any, name: str, minimum: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise SpecError(f"{name} must be an integer")
    if value < minimum:
        raise SpecError(f"{name} must be >= {minimum}")
    return value


def _state(materials: dict[str, str], role: str, **properties: Any) -> BlockState:
    if role not in materials:
        raise SpecError(f"materialRoles.{role} is required")
    return BlockState.of(materials[role], **properties)


def validate_spec(spec: dict[str, Any]) -> None:
    if spec.get("schemaVersion") not in {"0.1", "0.2"}:
        raise SpecError("schemaVersion must be 0.1 or 0.2")
    if spec.get("assetType") != "guild_branch":
        raise SpecError("bounded proof supports only assetType=guild_branch")
    if not spec.get("assetId"):
        raise SpecError("assetId is required")

    structure = spec.get("structure", {})
    _integer(structure.get("bayCount"), "structure.bayCount", 2)
    _integer(structure.get("bayWidth"), "structure.bayWidth", 4)
    _integer(structure.get("wallHeight"), "structure.wallHeight", 5)
    _integer(structure.get("mainDepth"), "structure.mainDepth", 9)
    _integer(structure.get("workingWingWidth"), "structure.workingWingWidth", 6)

    orientation = spec.get("orientation", {})
    if orientation.get("publicAccess") != "south" or orientation.get("workingFace") != "east":
        raise SpecError("v0.2 fixes south public access and east working face")

    materials = spec.get("materialRoles", {})
    required = {"foundation", "structuralFrame", "wallInfill", "floor", "roof", "window", "publicDoor", "workingDoor", "hardware", "lighting"}
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing material roles: {', '.join(missing)}")


def _centered(lo: int, hi: int, width: int) -> tuple[int, int]:
    available = hi - lo + 1
    if width > available:
        raise SpecError(f"span {width} exceeds available width {available}")
    start = lo + (available - width) // 2
    return start, start + width - 1


def _bay_interior(index: int, bay_width: int) -> tuple[int, int]:
    return index * bay_width + 1, (index + 1) * bay_width - 1


def _window_span(index: int, bay_width: int) -> tuple[int, int]:
    lo, hi = _bay_interior(index, bay_width)
    available = hi - lo + 1
    return _centered(lo, hi, max(2, min(3, available - 1 if available > 2 else available)))


def _entrance_span(bay_count: int, bay_width: int) -> tuple[int, int, int]:
    bay = bay_count // 2
    lo, hi = _bay_interior(bay, bay_width)
    x0, x1 = _centered(lo, hi, 2 if hi - lo + 1 >= 2 else 1)
    return bay, x0, x1


def compile_guild_branch(spec: dict[str, Any]) -> CompiledAsset:
    validate_spec(spec)
    s = spec["structure"]
    mats = spec["materialRoles"]
    bays = _integer(s["bayCount"], "structure.bayCount", 2)
    bay_w = _integer(s["bayWidth"], "structure.bayWidth", 4)
    wall_h = _integer(s["wallHeight"], "structure.wallHeight", 5)
    depth = _integer(s["mainDepth"], "structure.mainDepth", 9)
    wing_w = _integer(s["workingWingWidth"], "structure.workingWingWidth", 6)
    roof = s.get("roof", {})
    main_rise = _integer(roof.get("mainRise", 3), "structure.roof.mainRise", 1)
    wing_rise = _integer(roof.get("workingWingRise", 2), "structure.roof.workingWingRise", 1)

    hall_w = bays * bay_w + 1
    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_w
    wing_x1 = hall_w + wing_w - 1
    wing_z0, wing_z1 = 0, hall_z1
    max_x, max_z = wing_x1, wing_z1

    repair_z0 = 1
    repair_z1 = max(5, min(7, hall_z1 - 7))
    warehouse_z1 = hall_z1 - 1
    warehouse_z0 = min(warehouse_z1 - 5, max(repair_z1 + 2, 9))
    if warehouse_z0 <= repair_z1:
        warehouse_z0 = repair_z1 + 2

    def footprint(x: int, z: int) -> bool:
        return (0 <= x < hall_w and hall_z0 <= z <= hall_z1) or (wing_x0 <= x <= wing_x1 and wing_z0 <= z <= wing_z1)

    model = VoxelModel()
    foundation = _state(mats, "foundation")
    floor = _state(mats, "floor")
    wall = _state(mats, "wallInfill")
    frame_y = _state(mats, "structuralFrame", axis="y")
    frame_x = _state(mats, "structuralFrame", axis="x")
    frame_z = _state(mats, "structuralFrame", axis="z")
    window = _state(mats, "window")
    roof_state = _state(mats, "roof")
    hardware = _state(mats, "hardware")

    for x in range(max_x + 1):
        for z in range(max_z + 1):
            if not footprint(x, z):
                continue
            model.set(x, 0, z, "foundation", foundation, "shell")
            model.set(x, 1, z, "floor", floor, "shell")
            if any(not footprint(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                for y in range(2, wall_h + 1):
                    model.set(x, y, z, "wall_infill", wall, "shell")

    # Expressed posts/beams are derived from bay rhythm, not absolute coordinates.
    for z in (hall_z0, hall_z1):
        for x in sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1}):
            for y in range(1, wall_h + 1):
                model.set(x, y, z, "structural_frame", frame_y, "public_hall")
        for x in range(hall_w):
            model.set(x, wall_h, z, "structural_frame", frame_x, "public_hall")
    for z in sorted({hall_z0, hall_z0 + depth // 2, hall_z1}):
        for y in range(1, wall_h + 1):
            model.set(0, y, z, "structural_frame", frame_y, "public_hall")

    wing_step = max(4, bay_w)
    wing_posts = list(range(wing_z0, wing_z1 + 1, wing_step))
    if wing_z1 not in wing_posts:
        wing_posts.append(wing_z1)
    for z in wing_posts:
        for y in range(1, wall_h + 1):
            model.set(wing_x1, y, z, "structural_frame", frame_y, "working_wing")
    for z in range(wing_z0, wing_z1 + 1):
        model.set(wing_x1, wall_h, z, "structural_frame", frame_z, "working_wing")

    entrance_bay, door_x0, door_x1 = _entrance_span(bays, bay_w)
    for x in range(door_x0, door_x1 + 1):
        for y in range(2, 5):
            model.clear(x, y, hall_z1)
        hinge = "left" if x == door_x0 else "right"
        model.set(x, 2, hall_z1, "door", BlockState.of(mats["publicDoor"], facing="south", half="lower", hinge=hinge), "public_entrance")
        model.set(x, 3, hall_z1, "door", BlockState.of(mats["publicDoor"], facing="south", half="upper", hinge=hinge), "public_entrance")

    for bay in range(bays):
        wx0, wx1 = _window_span(bay, bay_w)
        for facade_z in (hall_z0, hall_z1):
            if facade_z == hall_z1 and bay == entrance_bay:
                continue
            for x in range(wx0, wx1 + 1):
                for y in range(3, min(5, wall_h)):
                    model.set(x, y, facade_z, "window", window, "public_hall")

    for zc in (hall_z0 + depth // 3, hall_z0 + 2 * depth // 3):
        for z in range(max(hall_z0 + 1, zc - 1), min(hall_z1, zc + 1)):
            for y in range(3, min(5, wall_h)):
                model.set(0, y, z, "window", window, "public_hall")

    def working_opening(z0: int, z1: int, module: str) -> tuple[int, int]:
        width = min(4, max(3, z1 - z0 - 1))
        a, b = _centered(z0, z1, width)
        for z in range(a, b + 1):
            for y in range(2, min(6, wall_h + 1)):
                model.clear(wing_x1, y, z)
        for z, hinge in ((a, "left"), (b, "right")):
            model.set(wing_x1, 2, z, "door", BlockState.of(mats["workingDoor"], facing="east", half="lower", hinge=hinge, open=True), module)
            model.set(wing_x1, 3, z, "door", BlockState.of(mats["workingDoor"], facing="east", half="upper", hinge=hinge, open=True), module)
        return a, b

    repair_open = working_opening(repair_z0, repair_z1, "light_repair")
    freight_open = working_opening(warehouse_z0, warehouse_z1, "warehouse")

    counter_x0 = max(2, door_x0 - 2)
    counter_x1 = min(hall_w - 3, door_x1 + 2)
    counter_z = hall_z0 + max(3, depth // 2)
    for x in range(counter_x0, counter_x1 + 1):
        model.set(x, 2, counter_z, "counter", frame_y, "service_counter")
    for x in (counter_x0, counter_x1):
        model.set(x, 3, counter_z, "hardware", hardware, "service_counter")

    divider_z = min(hall_z1 - 3, counter_z + 3)
    pass_x0, pass_x1 = _centered(1, hall_w - 2, 3)
    for x in range(1, hall_w - 1):
        if pass_x0 <= x <= pass_x1:
            continue
        for y in range(2, min(5, wall_h + 1)):
            model.set(x, y, divider_z, "wall_infill", wall, "back_office")

    separator_z = repair_z1 + 1
    sep_x0, sep_x1 = _centered(wing_x0 + 1, wing_x1 - 1, min(3, wing_w - 2))
    for x in range(wing_x0 + 1, wing_x1):
        if sep_x0 <= x <= sep_x1:
            continue
        for y in range(2, min(5, wall_h + 1)):
            model.set(x, y, separator_z, "wall_infill", wall, "working_wing")

    roof_base = wall_h + 1

    def gable(x0: int, x1: int, z0: int, z1: int, rise: int, module: str) -> None:
        center = (z0 + z1) / 2.0
        half = max((z1 - z0) / 2.0, 1.0)
        for z in range(z0, z1 + 1):
            y = roof_base + max(0, round(rise * (1 - abs(z - center) / half)))
            for x in range(x0, x1 + 1):
                model.set(x, y, z, "roof", roof_state, module)

    gable(0, hall_w - 1, hall_z0, hall_z1, main_rise, "public_hall")
    gable(wing_x0, wing_x1, wing_z0, wing_z1, wing_rise, "working_wing")

    for x in range(hall_w):
        for z in (hall_z0, hall_z1):
            model.set(x, wall_h + 1, z, "structural_frame", frame_x, "public_hall")
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, wall_h + 1, wing_z0, "structural_frame", frame_x, "working_wing")

    public_center = (door_x0 + door_x1) // 2
    lantern = _state(mats, "lighting", hanging=True)
    for x in (max(1, door_x0 - 2), min(hall_w - 2, door_x1 + 2)):
        model.set(x, min(wall_h, 5), hall_z1, "lighting", lantern, "public_entrance")
    model.set(public_center, wall_h, hall_z1, "hardware", hardware, "guild_identity")

    if any(m.get("id") == "signal_mast" for m in spec.get("requestedModules", [])):
        mast_x, mast_z = wing_x1 - 1, max(1, repair_z0)
        mast_top = roof_base + max(main_rise, wing_rise) + 3
        for y in range(roof_base, mast_top + 1):
            model.set(mast_x, y, mast_z, "signal_mast", hardware, "signal_mast")
        model.set(mast_x - 1, mast_top, mast_z, "hardware", hardware, "signal_mast")
        model.set(mast_x + 1, mast_top, mast_z, "hardware", hardware, "signal_mast")
        model.set(mast_x, mast_top, mast_z + 1, "lighting", _state(mats, "lighting"), "signal_mast")

    freight_z = sum(freight_open) // 2
    repair_z = sum(repair_open) // 2
    anchors = {
        "PUBLIC_ENTRANCE": [public_center, 1, hall_z1],
        "SERVICE_COUNTER": [(counter_x0 + counter_x1) // 2, 1, counter_z - 1],
        "CONTRACT_BOARD": [max(1, door_x0 - 3), 1, hall_z1 - 2],
        "ROUTE_INFO": [min(hall_w - 2, door_x1 + 3), 1, hall_z1 - 2],
        "NPC_WORK_POINT": [(counter_x0 + counter_x1) // 2, 1, counter_z + 1],
        "FREIGHT_PICKUP": [wing_x1 - 2, 1, freight_z],
        "FREIGHT_DROPOFF": [wing_x1 - 3, 1, freight_z],
        "REPAIR_BAY": [wing_x1 - 2, 1, repair_z],
        "AIRFIELD_INTERFACE": [wing_x1 + 1, 1, (freight_z + repair_z) // 2],
    }
    volumes = {
        "publicHall": Volume((0, 1, hall_z0), (hall_w - 1, wall_h, hall_z1), "public_service"),
        "backOffice": Volume((1, 1, divider_z + 1), (hall_w - 2, wall_h - 1, hall_z1 - 1), "administrative"),
        "workingWing": Volume((wing_x0, 1, wing_z0), (wing_x1, wall_h, wing_z1), "working"),
        "warehouse": Volume((wing_x0 + 1, 1, warehouse_z0), (wing_x1 - 1, wall_h - 1, warehouse_z1), "freight_storage"),
        "lightRepair": Volume((wing_x0 + 1, 1, repair_z0), (wing_x1 - 1, wall_h - 1, repair_z1), "repair"),
    }

    max_y = max(y for (_x, y, _z) in model.cells)
    layout = {
        "assetId": spec["assetId"],
        "bounds": {"min": [0, 0, 0], "max": [max_x, max_y, max_z], "size": [max_x + 1, max_y + 1, max_z + 1]},
        "resolvedParameters": {
            "bayCount": bays, "bayWidth": bay_w, "hallWidth": hall_w, "hallDepth": depth,
            "wallHeight": wall_h, "workingWingWidth": wing_w, "publicEntranceBay": entrance_bay,
            "publicEntranceSpan": [door_x0, door_x1], "repairOpeningZ": list(repair_open),
            "freightOpeningZ": list(freight_open), "counterZ": counter_z, "dividerZ": divider_z,
        },
        "volumes": {name: volume.to_dict() for name, volume in volumes.items()},
        "anchors": anchors,
    }

    issues = _validate_compiled(spec, model, layout, footprint)
    canonical = json.dumps({
        "assetId": spec["assetId"], "layout": layout,
        "cells": [{"pos": [x, y, z], **cell.to_dict()} for (x, y, z), cell in sorted(model.cells.items())],
    }, sort_keys=True, separators=(",", ":"))
    digest = hashlib.sha256(canonical.encode()).hexdigest()

    return CompiledAsset({
        "schemaVersion": spec["schemaVersion"], "compilerVersion": "0.2", "assetId": spec["assetId"],
        "layout": layout, "blockCount": len(model.cells),
        "materialCounts": dict(sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())),
        "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
        "moduleBlockCounts": dict(sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())),
        "digestSha256": digest, "validation": {"passed": not issues, "issues": issues},
    }, model)


def _validate_compiled(spec: dict[str, Any], model: VoxelModel, layout: dict[str, Any], footprint: Callable[[int, int], bool]) -> list[str]:
    issues: list[str] = []
    max_x, max_y, max_z = layout["bounds"]["max"]
    anchors = layout["anchors"]
    for (x, y, z), cell in model.cells.items():
        if not (0 <= x <= max_x and 0 <= y <= max_y and 0 <= z <= max_z):
            issues.append(f"block out of bounds at {(x, y, z)}: {cell.state.canonical()}")

    missing = sorted(set(spec.get("requestedAnchors", [])) - set(anchors))
    if missing:
        issues.append(f"missing requested anchors: {', '.join(missing)}")

    ex, _ey, ez = anchors["PUBLIC_ENTRANCE"]
    for y in (2, 3):
        cell = model.cells.get((ex, y, ez))
        if cell and cell.role != "door":
            issues.append(f"PUBLIC_ENTRANCE blocked at y={y} by {cell.role}")

    floors = {(x, z) for (x, y, z), cell in model.cells.items() if y == 1 and cell.role == "floor"}
    walkable = set()
    for x, z in floors:
        if all((model.cells.get((x, y, z)) is None or model.cells[(x, y, z)].role == "door") for y in (2, 3)):
            walkable.add((x, z))
    start = (ex, ez - 1) if (ex, ez - 1) in walkable else (ex, ez - 2)
    seen: set[tuple[int, int]] = set()
    if start in walkable:
        queue = collections.deque([start]); seen.add(start)
        while queue:
            x, z = queue.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, z + dz)
                if p in walkable and p not in seen:
                    seen.add(p); queue.append(p)
    else:
        issues.append("PUBLIC_ENTRANCE has no walkable interior start cell")

    for name, (x, _y, z) in anchors.items():
        if name == "AIRFIELD_INTERFACE":
            continue
        if not ({(x, z), (x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)} & seen):
            issues.append(f"{name}: not reachable from public entrance")
        if not footprint(x, z):
            issues.append(f"{name}: outside compiled footprint")

    ax, _ay, az = anchors["AIRFIELD_INTERFACE"]
    if ax != layout["volumes"]["workingWing"]["max"][0] + 1:
        issues.append("AIRFIELD_INTERFACE is not immediately outside east working face")
    if not 0 <= az <= max_z:
        issues.append("AIRFIELD_INTERFACE z outside asset span")

    def inside(volume: str, pos: list[int]) -> bool:
        v = layout["volumes"][volume]
        return all(v["min"][i] <= pos[i] <= v["max"][i] for i in range(3))
    for anchor, volume in (("FREIGHT_PICKUP", "warehouse"), ("FREIGHT_DROPOFF", "warehouse"), ("REPAIR_BAY", "lightRepair")):
        if not inside(volume, anchors[anchor]):
            issues.append(f"{anchor} outside {volume} volume")
    return issues
