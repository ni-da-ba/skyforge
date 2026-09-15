from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from guild_branch import compile_guild_branch
from model import BlockState, CompiledAsset, SpecError, Volume


def _state(materials: dict[str, str], role: str, fallback: str, **properties: Any) -> BlockState:
    name = materials.get(role, materials.get(fallback))
    if not name:
        raise SpecError(f"materialRoles.{role} or fallback {fallback} is required")
    return BlockState.of(name, **properties)


def _centered(lo: int, hi: int, width: int) -> tuple[int, int]:
    available = hi - lo + 1
    if width > available:
        raise SpecError(f"span {width} exceeds available width {available}")
    start = lo + (available - width) // 2
    return start, start + width - 1


def _roof_height(z: int, z0: int, z1: int, rise: int, roof_base: int) -> int:
    center = (z0 + z1) / 2.0
    half = max((z1 - z0) / 2.0, 1.0)
    return roof_base + max(0, round(rise * (1 - abs(z - center) / half)))


def compile_guild_branch_v03(spec: dict[str, Any]) -> CompiledAsset:
    """Decorative/functional v0.3 lowering layered over the accepted v0.2 structural compiler.

    This deliberately keeps v0.2 as the stable structural pass and proves that a second grammar pass
    can add visual hierarchy, roofs, canopies and Guild identity without hard-coding one specimen.
    """
    if spec.get("schemaVersion") != "0.3":
        raise SpecError("v0.3 detail compiler requires schemaVersion=0.3")
    required_detail_roles = {"institutionalAccent", "brassAccent", "canopy", "roofStair", "roofSlab"}
    missing = sorted(required_detail_roles - set(spec.get("materialRoles", {})))
    if missing:
        raise SpecError(f"missing v0.3 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.2"
    compiled = compile_guild_branch(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    mats = spec["materialRoles"]
    structure = spec["structure"]

    bays = int(rp["bayCount"])
    bay_w = int(rp["bayWidth"])
    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    entrance_bay = int(rp["publicEntranceBay"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_w
    wing_x1 = hall_w + wing_w - 1
    wing_z0, wing_z1 = 0, hall_z1
    roof_cfg = structure.get("roof", {})
    main_rise = int(roof_cfg.get("mainRise", 4))
    wing_rise = int(roof_cfg.get("workingWingRise", 2))
    overhang = int(roof_cfg.get("overhang", 1))
    public_canopy_depth = int(structure.get("publicCanopy", {}).get("depth", 2))
    working_canopy_depth = int(structure.get("workingCanopy", {}).get("depth", 2))
    if not 0 <= overhang <= 2:
        raise SpecError("structure.roof.overhang must be between 0 and 2")
    if public_canopy_depth < 1 or working_canopy_depth < 1:
        raise SpecError("v0.3 canopy depths must be >= 1")

    foundation = BlockState.of(mats["foundation"])
    frame_y = BlockState.of(mats["structuralFrame"], axis="y")
    frame_x = BlockState.of(mats["structuralFrame"], axis="x")
    wall = BlockState.of(mats["wallInfill"])
    roof_north = _state(mats, "roofStair", "roof", facing="north", half="bottom", shape="straight")
    roof_south = _state(mats, "roofStair", "roof", facing="south", half="bottom", shape="straight")
    roof_slab = _state(mats, "roofSlab", "roof", type="bottom")
    accent = _state(mats, "institutionalAccent", "structuralFrame")
    brass = _state(mats, "brassAccent", "hardware")
    canopy = _state(mats, "canopy", "structuralFrame", type="bottom")
    lantern = BlockState.of(mats["lighting"], hanging=True)

    # Durable masonry base course around existing shell.
    for (x, y, z), cell in list(model.cells.items()):
        if y == 2 and cell.role == "wall_infill":
            model.set(x, y, z, "foundation", foundation, cell.module or "shell")

    # Window sills/lintels repeat by the same bay grammar as v0.2.
    def window_span(index: int) -> tuple[int, int]:
        lo, hi = index * bay_w + 1, (index + 1) * bay_w - 1
        width = max(2, min(3, (hi - lo + 1) - 1 if hi - lo + 1 > 2 else hi - lo + 1))
        return _centered(lo, hi, width)

    for bay in range(bays):
        wx0, wx1 = window_span(bay)
        for facade_z in (hall_z0, hall_z1):
            if facade_z == hall_z1 and bay == entrance_bay:
                continue
            for x in range(wx0, wx1 + 1):
                model.set(x, 2, facade_z, "hardware", brass, "window_sill")
                model.set(x, min(5, wall_h - 1), facade_z, "structural_frame", frame_x, "window_lintel")

    # Convert roof surface to directional stairs/slabs, add overhang, and close gable ends.
    roof_base = wall_h + 1

    def detail_gable(x0: int, x1: int, z0: int, z1: int, rise: int, module: str) -> None:
        center = (z0 + z1) / 2.0
        for (x, y, z), cell in list(model.cells.items()):
            if cell.role != "roof" or cell.module != module:
                continue
            sample_z = min(max(z, z0), z1)
            if abs(sample_z - center) <= 0.5:
                state = roof_slab
            elif sample_z < center:
                state = roof_north
            else:
                state = roof_south
            model.set(x, y, z, "roof", state, module)

        for z in range(max(0, z0 - overhang), z1 + overhang + 1):
            if z0 <= z <= z1:
                continue
            sample_z = min(max(z, z0), z1)
            y = _roof_height(sample_z, z0, z1, rise, roof_base)
            state = roof_north if sample_z <= center else roof_south
            for x in range(x0, x1 + 1):
                model.set(x, y, z, "roof", state, module)

        for gx in (x0, x1):
            for z in range(z0, z1 + 1):
                top = _roof_height(z, z0, z1, rise, roof_base)
                for y in range(wall_h + 1, top):
                    model.set(gx, y, z, "wall_infill", wall, f"{module}_gable")

        for x in range(x0, x1 + 1):
            for z in (max(0, z0 - overhang), z1 + overhang):
                model.set(x, roof_base, z, "structural_frame", frame_x, module)
        ridge_z = round(center)
        ridge_y = _roof_height(ridge_z, z0, z1, rise, roof_base)
        for x in range(x0, x1 + 1):
            model.set(x, ridge_y, ridge_z, "roof", roof_slab, module)

    detail_gable(0, hall_w - 1, hall_z0, hall_z1, main_rise, "public_hall")
    detail_gable(wing_x0, wing_x1, wing_z0, wing_z1, wing_rise, "working_wing")

    # Public face: framed entry, navy identity band, restrained brass focal point, canopy and apron.
    public_center = (door_x0 + door_x1) // 2
    surround_x0 = max(1, door_x0 - 1)
    surround_x1 = min(hall_w - 2, door_x1 + 1)
    for x in (surround_x0, surround_x1):
        for y in range(2, wall_h + 1):
            model.set(x, y, hall_z1, "structural_frame", frame_y, "public_entrance")
    sign_y = min(wall_h - 1, 5)
    for x in range(surround_x0, surround_x1 + 1):
        model.set(x, sign_y, hall_z1, "counter", accent, "guild_identity")
    model.set(public_center, sign_y, hall_z1, "hardware", brass, "guild_identity")

    public_outer_z = hall_z1 + public_canopy_depth
    for x in range(surround_x0, surround_x1 + 1):
        for z in range(hall_z1 + 1, public_outer_z + 1):
            model.set(x, 4, z, "structural_frame", canopy, "public_canopy")
            model.set(x, 0, z, "foundation", foundation, "public_apron")
            model.set(x, 1, z, "floor", foundation, "public_apron")
    for x in (surround_x0, surround_x1):
        for y in range(1, 4):
            model.set(x, y, public_outer_z, "structural_frame", frame_y, "public_canopy")
    for x in (max(1, door_x0 - 2), min(hall_w - 2, door_x1 + 2)):
        model.set(x, min(wall_h, 5), hall_z1, "lighting", lantern, "public_entrance")

    # Working face: separate repair/freight canopies, aprons, posts and task lighting.
    working_outer_x = wing_x1 + working_canopy_depth
    canopy_volumes: dict[str, Volume] = {}
    for name, (a, b) in (("repairCanopy", (repair_a, repair_b)), ("freightCanopy", (freight_a, freight_b))):
        z0 = max(0, a - 1)
        z1 = min(wing_z1, b + 1)
        for x in range(wing_x1 + 1, working_outer_x + 1):
            for z in range(z0, z1 + 1):
                model.set(x, wall_h, z, "structural_frame", canopy, name)
                model.set(x, 0, z, "foundation", foundation, f"{name}_apron")
                model.set(x, 1, z, "floor", foundation, f"{name}_apron")
        for z in (z0, z1):
            for y in range(1, wall_h):
                model.set(working_outer_x, y, z, "structural_frame", frame_y, name)
        model.set(working_outer_x, wall_h - 1, (a + b) // 2, "lighting", lantern, name)
        canopy_volumes[name] = Volume((wing_x1 + 1, 0, z0), (working_outer_x, wall_h, z1), "working")

    # Extend semantic layout without changing the v0.2 interior authority.
    layout["volumes"].update({name: volume.to_dict() for name, volume in canopy_volumes.items()})
    layout["volumes"]["publicApproach"] = Volume(
        (surround_x0, 0, hall_z1 + 1), (surround_x1, 4, public_outer_z), "public_service"
    ).to_dict()
    layout["resolvedParameters"].update({
        "roofOverhang": overhang,
        "publicCanopyDepth": public_canopy_depth,
        "workingCanopyDepth": working_canopy_depth,
        "workingCanopyOuterX": working_outer_x,
    })
    layout["anchors"]["AIRFIELD_INTERFACE"] = [
        working_outer_x + 1,
        1,
        (layout["anchors"]["FREIGHT_PICKUP"][2] + layout["anchors"]["REPAIR_BAY"][2]) // 2,
    ]

    min_x = min(x for (x, _y, _z) in model.cells)
    min_y = min(y for (_x, y, _z) in model.cells)
    min_z = min(z for (_x, _y, z) in model.cells)
    max_x = max(x for (x, _y, _z) in model.cells)
    max_y = max(y for (_x, y, _z) in model.cells)
    max_z = max(z for (_x, _y, z) in model.cells)
    layout["bounds"] = {
        "min": [min_x, min_y, min_z],
        "max": [max_x, max_y, max_z],
        "size": [max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1],
    }

    issues: list[str] = []
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")
    ax, _ay, _az = layout["anchors"]["AIRFIELD_INTERFACE"]
    if ax != working_outer_x + 1:
        issues.append("AIRFIELD_INTERFACE is not immediately beyond working canopies")
    for required_volume in ("publicApproach", "repairCanopy", "freightCanopy"):
        if required_volume not in layout["volumes"]:
            issues.append(f"missing v0.3 volume: {required_volume}")
    if not any(cell.module == "guild_identity" for cell in model.cells.values()):
        issues.append("Guild identity treatment missing")
    if not any(cell.module == "public_canopy" for cell in model.cells.values()):
        issues.append("public canopy missing")

    canonical = json.dumps({
        "assetId": spec["assetId"],
        "layout": layout,
        "cells": [
            {"pos": [x, y, z], **cell.to_dict()}
            for (x, y, z), cell in sorted(model.cells.items())
        ],
    }, sort_keys=True, separators=(",", ":"))

    summary.update({
        "schemaVersion": "0.3",
        "compilerVersion": "0.3",
        "assetId": spec["assetId"],
        "layout": layout,
        "blockCount": len(model.cells),
        "materialCounts": dict(sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())),
        "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
        "moduleBlockCounts": dict(sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())),
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        "validation": {"passed": not issues, "issues": issues},
    })
    return CompiledAsset(summary, model)
