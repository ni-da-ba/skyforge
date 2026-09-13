from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from guild_branch_interior import compile_guild_branch_v04
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def _roof_y(z: int, z0: int, z1: int, rise: int, roof_base: int) -> int:
    """Discrete full-block gable profile used after the first in-game roof failure.

    v0.3 used directional stair rows. In Minecraft those rows read as separated ribs because the
    orientation/height combination did not form a visually continuous roof plane. v0.5 deliberately
    chooses the lower-risk Minecraft-native representation: a continuous stepped full-block skin.
    """
    sample = min(max(z, z0), z1)
    half = max((z1 - z0) // 2, 1)
    edge_distance = min(sample - z0, z1 - sample)
    return roof_base + (rise * edge_distance) // half


def compile_guild_branch_v05(spec: dict[str, Any]) -> CompiledAsset:
    """Runtime-informed correction pass layered over the v0.4 furnished Guild branch.

    The first real Minecraft placement proved the pipeline but exposed four material problems:
    ribbed/open roof lowering, an oversized masonry signal mast, weak working-bay differentiation,
    and overly dense records furnishing. This pass corrects those without broadening the compiler.
    """
    if spec.get("schemaVersion") != "0.5":
        raise SpecError("v0.5 runtime compiler requires schemaVersion=0.5")

    materials = spec.get("materialRoles", {})
    required = {"signalMast", "signalBeacon", "institutionalAccent", "recordsShelf", "desk"}
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.5 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.4"
    compiled = compile_guild_branch_v04(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    structure = spec["structure"]

    bays = int(rp["bayCount"])
    bay_w = int(rp["bayWidth"])
    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    counter_z = int(rp["counterZ"])
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
    roof_base = wall_h + 1

    roof = BlockState.of(_material(materials, "roof"))
    wall = BlockState.of(_material(materials, "wallInfill"))
    accent = BlockState.of(_material(materials, "institutionalAccent"))
    brass = BlockState.of(_material(materials, "brassAccent", "hardware"))
    mast = BlockState.of(_material(materials, "signalMast"), axis="y")
    beacon = BlockState.of(_material(materials, "signalBeacon"), hanging=False)
    shelf = BlockState.of(_material(materials, "recordsShelf"))
    desk = BlockState.of(_material(materials, "desk"), type="bottom")

    # Replace the first runtime roof rather than stacking another treatment on top of it.
    for pos, cell in list(model.cells.items()):
        if cell.role == "roof" or cell.module in {
            "public_hall_gable",
            "working_wing_gable",
            "signal_mast",
        }:
            model.clear(*pos)

    def solid_gable(x0: int, x1: int, z0: int, z1: int, rise: int, module: str) -> None:
        # Every X/Z roof projection has exactly one full roof block. The overhang samples the eave
        # height instead of extending the slope, producing a compact Minecraft-readable edge.
        for z in range(z0 - overhang, z1 + overhang + 1):
            y = _roof_y(z, z0, z1, rise, roof_base)
            for x in range(x0, x1 + 1):
                model.set(x, y, z, "roof", roof, module)

        # Close the two gable end planes under the stepped roof profile.
        for gx in (x0, x1):
            for z in range(z0, z1 + 1):
                top = _roof_y(z, z0, z1, rise, roof_base)
                for y in range(roof_base, top):
                    model.set(gx, y, z, "wall_infill", wall, f"{module}_gable_v05")

    solid_gable(0, hall_w - 1, hall_z0, hall_z1, main_rise, "public_hall_roof_v05")
    solid_gable(wing_x0, wing_x1, wing_z0, wing_z1, wing_rise, "working_wing_roof_v05")

    # Replace the oversized stone T with a four-block aviation/service mast: three slender chain
    # segments plus a single beacon. It should read as equipment attached to the building, not a
    # second architectural mass.
    mast_x = wing_x0 + max(1, wing_w // 2)
    mast_z = max(wing_z0 + 2, repair_a - 1)
    mast_base_y = _roof_y(mast_z, wing_z0, wing_z1, wing_rise, roof_base) + 1
    for y in range(mast_base_y, mast_base_y + 3):
        model.set(mast_x, y, mast_z, "signal_mast", mast, "signal_mast_v05")
    model.set(mast_x, mast_base_y + 3, mast_z, "lighting", beacon, "signal_mast_v05")

    # Distinguish the working openings before adding more clutter: repair is Guild/navy-coded,
    # freight is brass-coded. These headers replace the otherwise identical top beam above each bay.
    for z in range(repair_a, repair_b + 1):
        model.set(wing_x1, wall_h, z, "institutional_accent", accent, "repair_header_v05")
    for z in range(freight_a, freight_b + 1):
        model.set(wing_x1, wall_h, z, "hardware", brass, "freight_header_v05")

    # The first in-game interior made the records treatment look like a bookshelf wall. Remove the
    # dense v0.4 arrays and reintroduce two small record clusters plus two ledger shelves.
    for pos, cell in list(model.cells.items()):
        if cell.module in {"clerk_backbar", "records_shelves"}:
            model.clear(*pos)

    public_center = layout["anchors"]["PUBLIC_ENTRANCE"][0]
    rear_z = hall_z0 + 1
    for x in range(max(1, hall_w - 4), hall_w - 2):
        model.set(x, 2, rear_z, "records", shelf, "records_shelves_v05")
    ledger_z = max(hall_z0 + 2, counter_z - 3)
    for x in (max(2, public_center - 2), min(hall_w - 3, public_center + 2)):
        model.set(x, 2, ledger_z, "desk", desk, "ledger_shelf_v05")

    layout["resolvedParameters"].update(
        {
            "runtimeCorrectionVersion": "0.5",
            "roofTreatment": "solid_stepped_full_block",
            "signalMastHeightAboveRoof": 4,
            "institutionalAccentBlock": _material(materials, "institutionalAccent"),
        }
    )
    layout["anchors"]["SIGNAL_BEACON"] = [mast_x, mast_base_y + 3, mast_z]

    min_x = min(x for x, _y, _z in model.cells)
    min_y = min(y for _x, y, _z in model.cells)
    min_z = min(z for _x, _y, z in model.cells)
    max_x = max(x for x, _y, _z in model.cells)
    max_y = max(y for _x, y, _z in model.cells)
    max_z = max(z for _x, _y, z in model.cells)
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

    # Projection-completeness catches the exact ribbed-roof failure observed in Minecraft.
    for name, x0, x1, z0, z1, rise in (
        ("public", 0, hall_w - 1, hall_z0, hall_z1, main_rise),
        ("working", wing_x0, wing_x1, wing_z0, wing_z1, wing_rise),
    ):
        for z in range(z0 - overhang, z1 + overhang + 1):
            expected_y = _roof_y(z, z0, z1, rise, roof_base)
            for x in range(x0, x1 + 1):
                cell = model.cells.get((x, expected_y, z))
                if cell is None or cell.role != "roof":
                    issues.append(f"{name} roof projection gap at {(x, expected_y, z)}")
                    break
            if issues and issues[-1].startswith(f"{name} roof projection gap"):
                break

    mast_cells = [
        (pos, cell)
        for pos, cell in model.cells.items()
        if cell.module == "signal_mast_v05"
    ]
    if len(mast_cells) != 4:
        issues.append(f"v0.5 signal mast must contain exactly 4 blocks, got {len(mast_cells)}")
    if not any(cell.module == "repair_header_v05" for cell in model.cells.values()):
        issues.append("v0.5 repair header missing")
    if not any(cell.module == "freight_header_v05" for cell in model.cells.values()):
        issues.append("v0.5 freight header missing")
    if not any(cell.module == "records_shelves_v05" for cell in model.cells.values()):
        issues.append("v0.5 records shelving missing")
    if not any(cell.module == "ledger_shelf_v05" for cell in model.cells.values()):
        issues.append("v0.5 ledger shelving missing")

    canonical = json.dumps(
        {
            "assetId": spec["assetId"],
            "layout": layout,
            "cells": [
                {"pos": [x, y, z], **cell.to_dict()}
                for (x, y, z), cell in sorted(model.cells.items())
            ],
        },
        sort_keys=True,
        separators=(",", ":"),
    )

    summary.update(
        {
            "schemaVersion": "0.5",
            "compilerVersion": "0.5",
            "assetId": spec["assetId"],
            "layout": layout,
            "blockCount": len(model.cells),
            "materialCounts": dict(
                sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())
            ),
            "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
            "moduleBlockCounts": dict(
                sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())
            ),
            "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
            "validation": {"passed": not issues, "issues": issues},
        }
    )
    return CompiledAsset(summary, model)
