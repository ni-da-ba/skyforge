from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from guild_branch_runtime import compile_guild_branch_v05, _roof_y
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def compile_guild_branch_v06(spec: dict[str, Any]) -> CompiledAsset:
    """Second runtime-informed visual pass over the bounded Guild-branch proof.

    v0.5 fixed the catastrophic roof discontinuity but the next in-game inspection exposed a more
    ordinary architectural problem: the building remained too planar, the working-bay doors had
    literal gaps between their edge doors, and the chain antenna read as an arbitrary prop rather
    than integrated Guild infrastructure. v0.6 stays deliberately narrow and addresses those exact
    observations with facade reveals, continuous eave trim, recessed working-bay door banks, and a
    low rooftop beacon pedestal.
    """
    if spec.get("schemaVersion") != "0.6":
        raise SpecError("v0.6 facade compiler requires schemaVersion=0.6")

    materials = spec.get("materialRoles", {})
    required = {
        "exteriorTrimSlab",
        "masonryTrimSlab",
        "bayTransom",
        "signalPost",
        "signalBeacon",
        "institutionalAccent",
        "workingDoor",
        "structuralFrame",
        "foundation",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.6 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.5"
    compiled = compile_guild_branch_v05(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    structure = spec["structure"]

    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_w
    wing_x1 = hall_w + wing_w - 1
    wing_z0, wing_z1 = 0, hall_z1

    roof_cfg = structure.get("roof", {})
    wing_rise = int(roof_cfg.get("workingWingRise", 2))
    roof_base = wall_h + 1

    trim_slab = BlockState.of(_material(materials, "exteriorTrimSlab"), type="top")
    sill_slab = BlockState.of(_material(materials, "masonryTrimSlab"), type="bottom")
    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    foundation = BlockState.of(_material(materials, "foundation"))
    transom = BlockState.of(_material(materials, "bayTransom"))
    working_door = _material(materials, "workingDoor")
    signal_post = BlockState.of(_material(materials, "signalPost"))
    signal_beacon = BlockState.of(_material(materials, "signalBeacon"), hanging=False)

    # The v0.5 chain mast was mechanically small but still read as a stray antenna. Remove it and
    # replace it with a low, two-block roof beacon pedestal. This is service hardware integrated into
    # the roof silhouette rather than a competing vertical architectural element.
    for pos, cell in list(model.cells.items()):
        if cell.module == "signal_mast_v05":
            model.clear(*pos)
    layout["anchors"].pop("SIGNAL_BEACON", None)

    beacon_x = wing_x1 - max(1, wing_w // 3)
    beacon_z = max(wing_z0 + 2, min(wing_z1 - 2, repair_a - 1))
    beacon_roof_y = _roof_y(beacon_z, wing_z0, wing_z1, wing_rise, roof_base)
    model.set(beacon_x, beacon_roof_y + 1, beacon_z, "signal_mast", signal_post, "signal_beacon_v06")
    model.set(beacon_x, beacon_roof_y + 2, beacon_z, "lighting", signal_beacon, "signal_beacon_v06")
    layout["anchors"]["SIGNAL_BEACON"] = [beacon_x, beacon_roof_y + 2, beacon_z]

    # Rebuild each east-facing working bay as a proper one-block-deep portal. v0.2/v0.5 only placed
    # doors at the two edge columns of a wider opening, leaving literal holes between them. The new
    # door plane is recessed one block, spans every bay column, and receives a clerestory/transom plus
    # an expressed jamb/lintel at the outer wall plane.
    def rebuild_working_bay(a: int, b: int, module: str, door_module: str) -> None:
        inner_x = wing_x1 - 1

        for pos, cell in list(model.cells.items()):
            x, y, z = pos
            if a <= z <= b and y in (2, 3) and x in (wing_x1, inner_x):
                if cell.role == "door" or x == inner_x:
                    model.clear(*pos)

        # Outer portal is open at player height; the recessed door bank closes the opening one block
        # behind it. Every opening column receives both door halves, so there can be no center gap.
        for index, z in enumerate(range(a, b + 1)):
            for y in (2, 3):
                model.clear(wing_x1, y, z)
            hinge = "left" if index % 2 == 0 else "right"
            model.set(
                inner_x,
                2,
                z,
                "door",
                BlockState.of(working_door, facing="east", half="lower", hinge=hinge, open=False),
                door_module,
            )
            model.set(
                inner_x,
                3,
                z,
                "door",
                BlockState.of(working_door, facing="east", half="upper", hinge=hinge, open=False),
                door_module,
            )
            model.set(wing_x1, 4, z, "window", transom, f"{module}_transom_v06")
            model.set(wing_x1, 5, z, "structural_frame", frame_z, f"{module}_lintel_v06")

        # Expressed side jambs make the portal read as one designed opening instead of a hole punched
        # through a flat wall. Keep them on the exterior plane so the one-block reveal remains visible.
        for jamb_z in (max(wing_z0, a - 1), min(wing_z1, b + 1)):
            for y in range(2, 6):
                model.set(wing_x1, y, jamb_z, "structural_frame", frame_y, f"{module}_jamb_v06")

        # A masonry threshold repeats the public-facade base vocabulary at the working interface.
        for z in range(a, b + 1):
            model.set(wing_x1, 1, z, "foundation", foundation, f"{module}_threshold_v06")

    rebuild_working_bay(repair_a, repair_b, "repair", "repair_bay_doors_v06")
    rebuild_working_bay(freight_a, freight_b, "freight", "freight_bay_doors_v06")

    # Recess the public windows one block behind the expressed timber frame. This is a small change in
    # block count but materially changes facade depth at player eye level. Replace the mustard v0.3
    # sill with a projecting masonry slab and pair it with a restrained timber hood.
    front_windows = [
        (x, y, z, cell)
        for (x, y, z), cell in list(model.cells.items())
        if z == hall_z1 and cell.role == "window" and cell.module == "public_hall"
    ]
    front_window_xs = sorted({x for x, _y, _z, _cell in front_windows})
    for x, y, z, cell in front_windows:
        model.clear(x, y, z)
        model.set(x, y, z - 1, "window", cell.state, "public_window_recess_v06")

    for pos, cell in list(model.cells.items()):
        x, y, z = pos
        if z == hall_z1 and y == 2 and cell.module == "window_sill" and x in front_window_xs:
            model.set(x, y, z, "foundation", foundation, "public_window_base_v06")

    for x in front_window_xs:
        model.set(x, 2, hall_z1 + 1, "foundation", sill_slab, "public_window_sill_v06")
        model.set(x, 5, hall_z1 + 1, "structural_frame", trim_slab, "public_window_hood_v06")

    # One continuous under-eave line is shared by the public and working faces. This ties previously
    # isolated bays/canopies into one architectural family and gives the walls a visible shadow line.
    for x in range(0, hall_w):
        model.set(x, wall_h, hall_z1 + 1, "structural_frame", trim_slab, "public_eave_fascia_v06")
        model.set(x, wall_h, hall_z0 - 1, "structural_frame", trim_slab, "rear_eave_fascia_v06")
    for z in range(wing_z0, wing_z1 + 1):
        model.set(wing_x1 + 1, wall_h, z, "structural_frame", trim_slab, "working_eave_fascia_v06")
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, wall_h, wing_z0 - 1, "structural_frame", trim_slab, "working_north_fascia_v06")
        model.set(x, wall_h, wing_z1 + 1, "structural_frame", trim_slab, "working_south_fascia_v06")

    layout["resolvedParameters"].update(
        {
            "runtimeCorrectionVersion": "0.6",
            "workingBayDoorTreatment": "recessed_full_width_double_pairs",
            "publicWindowRevealDepth": 1,
            "signalTreatment": "low_roof_beacon_pedestal",
            "institutionalAccentBlock": _material(materials, "institutionalAccent"),
        }
    )

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

    beacon_cells = [(pos, cell) for pos, cell in model.cells.items() if cell.module == "signal_beacon_v06"]
    if len(beacon_cells) != 2:
        issues.append(f"v0.6 roof beacon must contain exactly 2 blocks, got {len(beacon_cells)}")

    inner_x = wing_x1 - 1
    for name, a, b, module in (
        ("repair", repair_a, repair_b, "repair_bay_doors_v06"),
        ("freight", freight_a, freight_b, "freight_bay_doors_v06"),
    ):
        for z in range(a, b + 1):
            for y in (2, 3):
                cell = model.cells.get((inner_x, y, z))
                if cell is None or cell.role != "door" or cell.module != module:
                    issues.append(f"{name} bay door gap at {(inner_x, y, z)}")
        for z in range(a, b + 1):
            if model.cells.get((wing_x1, 4, z), None) is None:
                issues.append(f"{name} bay transom gap at {(wing_x1, 4, z)}")

    if front_window_xs:
        if any(
            cell.role == "window" and z == hall_z1 and x in front_window_xs
            for (x, _y, z), cell in model.cells.items()
        ):
            issues.append("v0.6 public windows were not fully recessed from facade plane")
        if not any(cell.module == "public_window_sill_v06" for cell in model.cells.values()):
            issues.append("v0.6 public projecting window sills missing")
    else:
        issues.append("v0.6 could not find front public windows to recess")

    for required_module in (
        "public_eave_fascia_v06",
        "working_eave_fascia_v06",
        "repair_jamb_v06",
        "freight_jamb_v06",
    ):
        if not any(cell.module == required_module for cell in model.cells.values()):
            issues.append(f"missing v0.6 exterior module: {required_module}")

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
            "schemaVersion": "0.6",
            "compilerVersion": "0.6",
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
