from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from guild_branch_facade import compile_guild_branch_v06
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def _contiguous_groups(values: list[int]) -> list[tuple[int, int]]:
    if not values:
        return []
    ordered = sorted(set(values))
    groups: list[tuple[int, int]] = []
    start = previous = ordered[0]
    for value in ordered[1:]:
        if value != previous + 1:
            groups.append((start, previous))
            start = value
        previous = value
    groups.append((start, previous))
    return groups


def compile_guild_branch_v07(spec: dict[str, Any]) -> CompiledAsset:
    """Transition/joint pass over the v0.6 Guild-branch facade.

    v0.6 added useful facade depth, but the first in-game read exposed seams where the new depth
    planes met the old shell: recessed window panes lacked complete reveal returns, recessed working
    doors and their transoms occupied different planes, and the base vocabulary did not consistently
    bridge adjacent wall modules. v0.7 does not add decorative density. It closes and regularizes the
    architectural joints created by v0.6.

    The blue/brass palette remains explicitly provisional in this pass. Material-role abstraction is
    preserved so a later dedicated in-game palette gate can change the Guild color realization without
    reopening the geometry grammar.
    """
    if spec.get("schemaVersion") != "0.7":
        raise SpecError("v0.7 cohesion compiler requires schemaVersion=0.7")

    materials = spec.get("materialRoles", {})
    required = {
        "structuralFrame",
        "foundation",
        "masonryTrimSlab",
        "bayTransom",
        "institutionalAccent",
        "brassAccent",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.7 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.6"
    compiled = compile_guild_branch_v06(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]

    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_w
    wing_x1 = hall_w + wing_w - 1
    wing_z0, wing_z1 = 0, hall_z1

    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_x = BlockState.of(_material(materials, "structuralFrame"), axis="x")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    foundation = BlockState.of(_material(materials, "foundation"))
    plinth_slab = BlockState.of(_material(materials, "masonryTrimSlab"), type="bottom")
    transom = BlockState.of(_material(materials, "bayTransom"))

    # PUBLIC WINDOWS ---------------------------------------------------------
    # v0.6 moved the panes one block inward but did not fully line the resulting reveal. Group the
    # panes by contiguous facade opening, then build a complete bottom/top/side return around each
    # opening. The outer aperture stays empty: the depth is retained, but the opening now has a
    # coherent structural box instead of exposed discontinuous wall edges.
    window_cells = [
        (x, y, z, cell)
        for (x, y, z), cell in model.cells.items()
        if cell.module == "public_window_recess_v06" and cell.role == "window"
    ]
    window_xs = sorted({x for x, _y, _z, _cell in window_cells})
    window_groups = _contiguous_groups(window_xs)
    reveal_z = hall_z1 - 1

    for group_index, (x0, x1) in enumerate(window_groups):
        module = f"public_window_reveal_v07_{group_index}"

        # Clear any accidental facade-plane infill in the visual aperture.
        for x in range(x0, x1 + 1):
            for y in (3, 4):
                model.clear(x, y, hall_z1)

        # Inner bottom/head returns tie the pane plane back to the wall thickness.
        for x in range(x0, x1 + 1):
            model.set(x, 2, reveal_z, "foundation", foundation, module)
            model.set(x, 5, reveal_z, "structural_frame", frame_x, module)

        # Side returns occupy both depth planes, making the recess a closed rectangular reveal.
        for jamb_x in (x0 - 1, x1 + 1):
            if not 0 <= jamb_x < hall_w:
                continue
            for z in (reveal_z, hall_z1):
                for y in (3, 4):
                    model.set(jamb_x, y, z, "structural_frame", frame_y, module)

        # Extend the existing sill/hood one block beyond the pane width so those pieces terminate
        # against the jambs rather than appearing as disconnected strips.
        for x in range(max(0, x0 - 1), min(hall_w - 1, x1 + 1) + 1):
            model.set(x, 2, hall_z1 + 1, "foundation", plinth_slab, f"public_window_sill_v07_{group_index}")
            model.set(x, 5, hall_z1 + 1, "structural_frame", BlockState.of(_material(materials, "structuralFrame"), axis="x"), f"public_window_hood_v07_{group_index}")

    # WORKING-BAY PORTALS ----------------------------------------------------
    # v0.6 correctly made the door bank contiguous, but the doors sat one block inward while the
    # transom remained on the exterior plane. Move the transom to the same inner plane and line the
    # entire one-block-deep portal with threshold, head, and side returns.
    def close_working_portal(a: int, b: int, name: str) -> None:
        inner_x = wing_x1 - 1
        outer_x = wing_x1
        module = f"{name}_portal_reveal_v07"

        for z in range(a, b + 1):
            # Outer plane stays open through the transom height.
            for y in (2, 3, 4):
                model.clear(outer_x, y, z)
            # Recessed door + transom become one continuous closure plane.
            model.set(inner_x, 4, z, "window", transom, f"{name}_transom_v07")
            # Continuous threshold and head bridge the inner and outer planes.
            for x in (inner_x, outer_x):
                model.set(x, 1, z, "foundation", foundation, module)
                model.set(x, 5, z, "structural_frame", frame_z, module)

        # Side returns close the reveal at both ends for the full occupied portal height.
        for return_z in (max(wing_z0, a - 1), min(wing_z1, b + 1)):
            for x in (inner_x, outer_x):
                for y in range(2, 5):
                    model.set(x, y, return_z, "structural_frame", frame_y, module)

    close_working_portal(repair_a, repair_b, "repair")
    close_working_portal(freight_a, freight_b, "freight")

    # COHESIVE BASE COURSE ---------------------------------------------------
    # A continuous shallow plinth ties wall panels, window bays, posts, and service openings into the
    # same base/body/crown hierarchy. It intentionally breaks only where a player/vehicle actually
    # crosses the facade.
    entrance_skip = set(range(max(0, door_x0 - 1), min(hall_w - 1, door_x1 + 1) + 1))
    for x in range(0, hall_w):
        if x not in entrance_skip:
            model.set(x, 2, hall_z1 + 1, "foundation", plinth_slab, "public_plinth_band_v07")

    working_open_z = set(range(repair_a, repair_b + 1)) | set(range(freight_a, freight_b + 1))
    for z in range(wing_z0, wing_z1 + 1):
        if z not in working_open_z:
            model.set(wing_x1 + 1, 2, z, "foundation", plinth_slab, "working_plinth_band_v07")

    palette_review = copy.deepcopy(spec.get("paletteReview", {}))
    if palette_review:
        layout["paletteReview"] = palette_review

    layout["resolvedParameters"].update(
        {
            "runtimeCorrectionVersion": "0.7",
            "facadeTransitionTreatment": "boxed_reveals_and_continuous_plinth",
            "publicWindowRevealClosure": "bottom_head_side_returns",
            "workingPortalRevealClosure": "threshold_head_side_returns",
            "paletteStatus": palette_review.get("status", "provisional") if palette_review else "provisional",
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

    if not window_groups:
        issues.append("v0.7 found no recessed public windows to close")
    for group_index, (x0, x1) in enumerate(window_groups):
        module = f"public_window_reveal_v07_{group_index}"
        for x in range(x0, x1 + 1):
            for y in (3, 4):
                pane = model.cells.get((x, y, reveal_z))
                if pane is None or pane.role != "window":
                    issues.append(f"public window pane missing at {(x, y, reveal_z)}")
                if model.cells.get((x, y, hall_z1)) is not None:
                    issues.append(f"public window outer aperture blocked at {(x, y, hall_z1)}")
            if model.cells.get((x, 2, reveal_z), None) is None:
                issues.append(f"public window bottom return missing at {(x, 2, reveal_z)}")
            if model.cells.get((x, 5, reveal_z), None) is None:
                issues.append(f"public window head return missing at {(x, 5, reveal_z)}")
        if not any(cell.module == module for cell in model.cells.values()):
            issues.append(f"public window reveal module missing for group {group_index}")

    inner_x = wing_x1 - 1
    outer_x = wing_x1
    for name, a, b, door_module in (
        ("repair", repair_a, repair_b, "repair_bay_doors_v06"),
        ("freight", freight_a, freight_b, "freight_bay_doors_v06"),
    ):
        for z in range(a, b + 1):
            for y in (2, 3):
                door = model.cells.get((inner_x, y, z))
                if door is None or door.role != "door" or door.module != door_module:
                    issues.append(f"{name} recessed door missing at {(inner_x, y, z)}")
            transom_cell = model.cells.get((inner_x, 4, z))
            if transom_cell is None or transom_cell.module != f"{name}_transom_v07":
                issues.append(f"{name} recessed transom missing at {(inner_x, 4, z)}")
            for y in (2, 3, 4):
                if model.cells.get((outer_x, y, z)) is not None:
                    issues.append(f"{name} outer portal plane not open at {(outer_x, y, z)}")
            for x in (inner_x, outer_x):
                if model.cells.get((x, 1, z), None) is None:
                    issues.append(f"{name} portal threshold gap at {(x, 1, z)}")
                if model.cells.get((x, 5, z), None) is None:
                    issues.append(f"{name} portal head gap at {(x, 5, z)}")

    for required_module in ("public_plinth_band_v07", "working_plinth_band_v07"):
        if not any(cell.module == required_module for cell in model.cells.values()):
            issues.append(f"missing v0.7 cohesion module: {required_module}")

    if palette_review and palette_review.get("status") != "provisional":
        issues.append("v0.7 paletteReview.status must remain provisional until the dedicated palette gate")

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
            "schemaVersion": "0.7",
            "compilerVersion": "0.7",
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
