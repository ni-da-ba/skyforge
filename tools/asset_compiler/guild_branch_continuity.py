from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from articulation import contiguous_groups
from guild_branch_finish import compile_guild_branch_v10
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
    sample = min(max(z, z0), z1)
    half = max((z1 - z0) // 2, 1)
    edge_distance = min(sample - z0, z1 - sample)
    return roof_base + (rise * edge_distance) // half


def compile_guild_branch_v11(spec: dict[str, Any]) -> CompiledAsset:
    """Continuity-resolution pass over v0.10.

    v0.11 treats the building as connected structure and connected space rather than a collection
    of resolved details. It normalizes public-hall window assemblies, moves interior furniture and
    information fixtures out of glazing/reveal zones, enforces a two-block public circulation spine,
    keeps direct working-bay lanes open, and resolves the roof as a solid weather skin with exterior
    eave/ridge detail plus an exposed interior timber support system.
    """
    if spec.get("schemaVersion") != "0.11":
        raise SpecError("v0.11 continuity compiler requires schemaVersion=0.11")

    continuity_cfg = spec.get("continuityResolution")
    if not isinstance(continuity_cfg, dict):
        raise SpecError("v0.11 requires continuityResolution object")

    materials = spec.get("materialRoles", {})
    required = {
        "structuralFrame", "foundation", "window", "roof", "roofStair", "roofSlab",
        "exteriorTrimSlab", "masonryTrimSlab", "publicBoard", "recordsShelf",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.11 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.10"
    compiled = compile_guild_branch_v10(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    structure = spec["structure"]

    bay_w = int(rp["bayWidth"])
    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    counter_z = int(rp["counterZ"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_w
    wing_x1 = hall_w + wing_w - 1
    inner_x = wing_x1 - 1

    roof_cfg = structure.get("roof", {})
    main_rise = int(roof_cfg.get("mainRise", 4))
    wing_rise = int(roof_cfg.get("workingWingRise", 2))
    overhang = int(roof_cfg.get("overhang", 1))
    roof_base = wall_h + 1

    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_x = BlockState.of(_material(materials, "structuralFrame"), axis="x")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    foundation = BlockState.of(_material(materials, "foundation"))
    pane_state = BlockState.of(_material(materials, "window"))
    masonry_slab = BlockState.of(_material(materials, "masonryTrimSlab", "foundation"), type="bottom")
    trim_slab_top = BlockState.of(_material(materials, "exteriorTrimSlab", "structuralFrame"), type="top")
    board = BlockState.of(_material(materials, "publicBoard", "structuralFrame"))
    shelf = BlockState.of(_material(materials, "recordsShelf", "structuralFrame"))
    roof_slab = BlockState.of(_material(materials, "roofSlab", "roof"), type="bottom")
    roof_stair_n = BlockState.of(_material(materials, "roofStair", "roof"), facing="north", half="bottom", shape="straight")
    roof_stair_s = BlockState.of(_material(materials, "roofStair", "roof"), facing="south", half="bottom", shape="straight")

    # 1. WINDOW / WALL COHERENCE ------------------------------------------
    window_cfg = continuity_cfg.get("windowsAndWalls", {})
    front_groups: list[tuple[int, int]] = []
    north_groups: list[tuple[int, int]] = []
    west_groups: list[tuple[int, int]] = []

    if window_cfg.get("enabled", True):
        front_xs = sorted({x for (x, _y, _z), cell in model.cells.items() if cell.module == "public_window_recess_v06"})
        front_groups = contiguous_groups(front_xs)
        north_groups = [tuple(map(int, group)) for group in layout.get("finishResolution", {}).get("rearElevation", {}).get("northWindowGroups", [])]
        west_groups = [tuple(map(int, group)) for group in layout.get("detailResolution", {}).get("secondaryElevation", {}).get("westWindowGroups", [])]

        def normalize_z_face(groups: list[tuple[int, int]], wall_z: int, pane_z: int, trim_z: int, prefix: str) -> None:
            for index, (x0, x1) in enumerate(groups):
                reveal = f"{prefix}_window_reveal_v11_{index}"
                for x in range(x0, x1 + 1):
                    for y in (3, 4):
                        model.clear(x, y, wall_z)
                        model.set(x, y, pane_z, "window", pane_state, f"{prefix}_window_pane_v11")
                    model.set(x, 2, pane_z, "foundation", foundation, reveal)
                    model.set(x, 5, pane_z, "structural_frame", frame_x, reveal)
                    model.set(x, 2, trim_z, "foundation", masonry_slab, f"{prefix}_window_sill_v11_{index}")
                    model.set(x, 5, trim_z, "structural_frame", trim_slab_top, f"{prefix}_window_hood_v11_{index}")
                for jamb_x in (x0 - 1, x1 + 1):
                    if not 0 <= jamb_x < hall_w:
                        continue
                    for z in (wall_z, pane_z):
                        for y in (3, 4):
                            model.set(jamb_x, y, z, "structural_frame", frame_y, reveal)

        normalize_z_face(front_groups, hall_z1, hall_z1 - 1, hall_z1 + 1, "south")
        normalize_z_face(north_groups, hall_z0, hall_z0 + 1, hall_z0 - 1, "north")

        for index, (z0, z1) in enumerate(west_groups):
            reveal = f"west_window_reveal_v11_{index}"
            for z in range(z0, z1 + 1):
                for y in (3, 4):
                    model.clear(0, y, z)
                    model.set(1, y, z, "window", pane_state, "west_window_pane_v11")
                model.set(1, 2, z, "foundation", foundation, reveal)
                model.set(1, 5, z, "structural_frame", frame_z, reveal)
                model.set(-1, 2, z, "foundation", masonry_slab, f"west_window_sill_v11_{index}")
                model.set(-1, 5, z, "structural_frame", trim_slab_top, f"west_window_hood_v11_{index}")
            for jamb_z in (z0 - 1, z1 + 1):
                if not hall_z0 <= jamb_z <= hall_z1:
                    continue
                for x in (0, 1):
                    for y in (3, 4):
                        model.set(x, y, jamb_z, "structural_frame", frame_y, reveal)

        displaced_modules = {
            "contract_board", "route_info_panel", "contract_board_frame_v10", "route_info_frame_v10",
            "records_wall_v10", "clerk_records_v10",
        }
        for pos, cell in list(model.cells.items()):
            if cell.module in displaced_modules:
                model.clear(*pos)

        info_left_x = max(1, door_x0 - 2)
        info_right_x = min(hall_w - 2, door_x1 + 2)
        info_z = hall_z1 - 1
        for x, anchor_name, module in (
            (info_left_x, "CONTRACT_BOARD", "contract_board_wall_v11"),
            (info_right_x, "ROUTE_INFO", "route_info_wall_v11"),
        ):
            model.set(x, 3, info_z, "board", board, module)
            model.set(x, 4, info_z, "structural_frame", trim_slab_top, f"{module}_cap")
            layout["anchors"][anchor_name] = [x, 1, info_z - 1]

        staff_z0 = hall_z0 + 2
        staff_z1 = max(staff_z0, counter_z - 2)
        records_x = hall_w - 2
        for z in range(staff_z0, staff_z1 + 1):
            if z == layout["anchors"]["BACK_OFFICE_WORK"][2]:
                continue
            model.set(records_x, 2, z, "records", shelf, "records_wall_v11")
            if z % 2 == 0:
                model.set(records_x, 3, z, "records", shelf, "records_wall_v11")

    # 2. STRUCTURAL + SPATIAL CONTINUITY -----------------------------------
    structure_cfg = continuity_cfg.get("structureAndSpace", {})
    public_spine_cells: list[list[int]] = []
    working_lane_cells: list[list[int]] = []
    post_xs = sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1})

    if structure_cfg.get("enabled", True):
        for x in post_xs:
            for facade_z in (hall_z0, hall_z1):
                for y in range(2, wall_h + 1):
                    model.set(x, y, facade_z, "structural_frame", frame_y, "continuous_bay_post_v11")

        for x in range(0, hall_w):
            model.set(x, wall_h, hall_z0, "structural_frame", frame_x, "public_wall_plate_v11")
            model.set(x, wall_h, hall_z1, "structural_frame", frame_x, "public_wall_plate_v11")

        for x in range(door_x0, door_x1 + 1):
            for z in range(counter_z + 1, hall_z1):
                for y in (2, 3):
                    cell = model.cells.get((x, y, z))
                    if cell and cell.role != "door" and cell.module != "service_counter_finish_v10":
                        model.clear(x, y, z)
                    public_spine_cells.append([x, y, z])

        for a, b, anchor_name in (
            (repair_a, repair_b, "REPAIR_BAY"),
            (freight_a, freight_b, "FREIGHT_PICKUP"),
        ):
            lane_z = int(layout["anchors"][anchor_name][2])
            lane_z = max(a, min(b, lane_z))
            for x in range(wing_x0 + 1, inner_x):
                for y in (2, 3):
                    cell = model.cells.get((x, y, lane_z))
                    if cell and cell.role != "door":
                        model.clear(x, y, lane_z)
                    working_lane_cells.append([x, y, lane_z])

    # 3. ROOF INSIDE / OUT -------------------------------------------------
    roof_detail_cfg = continuity_cfg.get("roofDetail", {})
    public_rafter_cells: list[list[int]] = []
    working_rafter_cells: list[list[int]] = []

    if roof_detail_cfg.get("enabled", True):
        public_north_eave = hall_z0 - overhang
        public_south_eave = hall_z1 + overhang
        for x in range(0, hall_w):
            model.set(x, _roof_y(public_north_eave, hall_z0, hall_z1, main_rise, roof_base), public_north_eave,
                      "roof", roof_stair_n, "public_roof_eave_north_v11")
            model.set(x, _roof_y(public_south_eave, hall_z0, hall_z1, main_rise, roof_base), public_south_eave,
                      "roof", roof_stair_s, "public_roof_eave_south_v11")

        working_north_eave = -overhang
        working_south_eave = hall_z1 + overhang
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, _roof_y(working_north_eave, 0, hall_z1, wing_rise, roof_base), working_north_eave,
                      "roof", roof_stair_n, "working_roof_eave_north_v11")
            model.set(x, _roof_y(working_south_eave, 0, hall_z1, wing_rise, roof_base), working_south_eave,
                      "roof", roof_stair_s, "working_roof_eave_south_v11")

        public_ridge_z = max(range(hall_z0, hall_z1 + 1), key=lambda z: _roof_y(z, hall_z0, hall_z1, main_rise, roof_base))
        public_ridge_y = _roof_y(public_ridge_z, hall_z0, hall_z1, main_rise, roof_base)
        for x in range(0, hall_w):
            model.set(x, public_ridge_y + 1, public_ridge_z, "roof", roof_slab, "public_roof_ridge_cap_v11")

        working_ridge_z = max(range(0, hall_z1 + 1), key=lambda z: _roof_y(z, 0, hall_z1, wing_rise, roof_base))
        working_ridge_y = _roof_y(working_ridge_z, 0, hall_z1, wing_rise, roof_base)
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, working_ridge_y + 1, working_ridge_z, "roof", roof_slab, "working_roof_ridge_cap_v11")

        for x in post_xs:
            for z in range(hall_z0, hall_z1 + 1):
                y = _roof_y(z, hall_z0, hall_z1, main_rise, roof_base) - 1
                model.set(x, y, z, "structural_frame", frame_z, "public_roof_rafter_v11")
                public_rafter_cells.append([x, y, z])

        working_rafter_xs = sorted({wing_x0, wing_x0 + wing_w // 2, wing_x1})
        for x in working_rafter_xs:
            for z in range(0, hall_z1 + 1):
                y = _roof_y(z, 0, hall_z1, wing_rise, roof_base) - 1
                model.set(x, y, z, "structural_frame", frame_z, "working_roof_rafter_v11")
                working_rafter_cells.append([x, y, z])

        tie_zs = sorted({hall_z0 + 2, (hall_z0 + hall_z1) // 2, hall_z1 - 2})
        for z in tie_zs:
            for x in range(1, hall_w - 1):
                model.set(x, wall_h, z, "structural_frame", frame_x, "public_roof_tie_beam_v11")

    layout["continuityResolution"] = {
        "stage": "structure_space_roof_continuity",
        "windowsAndWalls": {
            "southWindowGroups": [list(g) for g in front_groups],
            "northWindowGroups": [list(g) for g in north_groups],
            "westWindowGroups": [list(g) for g in west_groups],
            "assembly": "complete_two_high_recessed_pane_boxed_reveal_sill_hood",
            "interiorFixturesMayOccupyGlazing": False,
        },
        "structureAndSpace": {
            "continuousBayPosts": post_xs,
            "publicCirculationWidth": door_x1 - door_x0 + 1,
            "publicSpineCells": public_spine_cells,
            "workingLaneCells": working_lane_cells,
        },
        "roofDetail": {
            "weatherSkin": "solid_stepped_full_block_preserved",
            "eaves": "single_directional_stair_row",
            "ridge": "slab_cap",
            "interior": "bay_aligned_exposed_rafters_and_tie_beams",
            "publicRafterCells": public_rafter_cells,
            "workingRafterCells": working_rafter_cells,
        },
    }
    layout["resolvedParameters"].update({
        "runtimeCorrectionVersion": "0.11",
        "continuityResolutionStage": "explicit",
        "windowWallTreatment": "normalized_complete_recessed_assemblies",
        "publicCirculationSpineWidth": door_x1 - door_x0 + 1,
        "roofTreatment": "solid_skin_detailed_eaves_ridge_exposed_interior_frame",
        "interiorRoofTreatment": "bay_aligned_rafters_tie_beams",
    })

    issues: list[str] = []
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")

    if window_cfg.get("enabled", True):
        if not front_groups or not north_groups or not west_groups:
            issues.append("v0.11 requires south, north, and west window groups")
        for prefix, groups, wall_z, pane_z in (
            ("south", front_groups, hall_z1, hall_z1 - 1),
            ("north", north_groups, hall_z0, hall_z0 + 1),
        ):
            for x0, x1 in groups:
                for x in range(x0, x1 + 1):
                    for y in (3, 4):
                        pane = model.cells.get((x, y, pane_z))
                        if pane is None or pane.role != "window" or pane.module != f"{prefix}_window_pane_v11":
                            issues.append(f"{prefix} complete pane missing at {(x, y, pane_z)}")
                        if model.cells.get((x, y, wall_z)) is not None:
                            issues.append(f"{prefix} outer window aperture blocked at {(x, y, wall_z)}")
        for z0, z1 in west_groups:
            for z in range(z0, z1 + 1):
                for y in (3, 4):
                    pane = model.cells.get((1, y, z))
                    if pane is None or pane.role != "window" or pane.module != "west_window_pane_v11":
                        issues.append(f"west complete pane missing at {(1, y, z)}")
                    if model.cells.get((0, y, z)) is not None:
                        issues.append(f"west outer window aperture blocked at {(0, y, z)}")

    if structure_cfg.get("enabled", True):
        for x in post_xs:
            for facade_z in (hall_z0, hall_z1):
                for y in range(2, wall_h + 1):
                    cell = model.cells.get((x, y, facade_z))
                    if cell is None or cell.module not in {"continuous_bay_post_v11", "public_wall_plate_v11"}:
                        issues.append(f"bay post discontinuity at {(x, y, facade_z)}")
                        break
        for x in range(door_x0, door_x1 + 1):
            for z in range(counter_z + 1, hall_z1):
                for y in (2, 3):
                    cell = model.cells.get((x, y, z))
                    if cell and cell.role != "door" and cell.module != "service_counter_finish_v10":
                        issues.append(f"public two-wide circulation spine blocked at {(x, y, z)}")

    if roof_detail_cfg.get("enabled", True):
        for name, x0, x1, z0, z1, rise in (
            ("public", 0, hall_w - 1, hall_z0, hall_z1, main_rise),
            ("working", wing_x0, wing_x1, 0, hall_z1, wing_rise),
        ):
            for z in range(z0 - overhang, z1 + overhang + 1):
                y = _roof_y(z, z0, z1, rise, roof_base)
                for x in range(x0, x1 + 1):
                    cell = model.cells.get((x, y, z))
                    if cell is None or cell.role != "roof":
                        issues.append(f"{name} weather skin gap at {(x, y, z)}")
                        break
                if issues and issues[-1].startswith(f"{name} weather skin gap"):
                    break
        for x in post_xs:
            for z in range(hall_z0, hall_z1 + 1):
                y = _roof_y(z, hall_z0, hall_z1, main_rise, roof_base) - 1
                cell = model.cells.get((x, y, z))
                if cell is None or cell.module != "public_roof_rafter_v11":
                    issues.append(f"public interior rafter discontinuity at {(x, y, z)}")
                    break
        for module in (
            "public_roof_eave_north_v11", "public_roof_eave_south_v11", "public_roof_ridge_cap_v11",
            "public_roof_tie_beam_v11", "working_roof_eave_north_v11", "working_roof_eave_south_v11",
            "working_roof_ridge_cap_v11", "working_roof_rafter_v11",
        ):
            if not any(cell.module == module for cell in model.cells.values()):
                issues.append(f"missing v0.11 roof module: {module}")

    palette = layout.get("paletteReview", {})
    if palette and palette.get("status") != "provisional":
        issues.append("v0.11 continuity pass must not finalize the Guild palette")

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
    summary.update({
        "schemaVersion": "0.11",
        "compilerVersion": "0.11",
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
