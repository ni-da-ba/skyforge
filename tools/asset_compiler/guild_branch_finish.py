from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from articulation import contiguous_groups
from guild_branch_detail_resolution import compile_guild_branch_v09
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def compile_guild_branch_v10(spec: dict[str, Any]) -> CompiledAsset:
    """Finish-resolution pass over v0.9.

    v0.10 addresses the second in-game detail gate rather than changing massing or palette:
    explicit door heads at the public entry and working bays, a resolved north/rear elevation,
    conservative replacement of ambiguous v0.9 decorative stairs, and a more finished interior
    while preserving semantic anchors and circulation.
    """
    if spec.get("schemaVersion") != "0.10":
        raise SpecError("v0.10 finish compiler requires schemaVersion=0.10")

    finish_cfg = spec.get("finishResolution")
    if not isinstance(finish_cfg, dict):
        raise SpecError("v0.10 requires finishResolution object")

    materials = spec.get("materialRoles", {})
    required = {
        "structuralFrame",
        "wallInfill",
        "foundation",
        "lighting",
        "institutionalAccent",
        "brassAccent",
        "seating",
        "recordsShelf",
        "publicBoard",
        "freightContainer",
        "repairBench",
        "toolStorage",
        "desk",
        "hardware",
        "exteriorTrimSlab",
        "masonryTrimSlab",
        # v0.9 still requires these roles even though v0.10 audits their use.
        "exteriorTrimStair",
        "masonryTrimStair",
        "bayTransom",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.10 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.9"
    compiled = compile_guild_branch_v09(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]

    bay_w = int(rp["bayWidth"])
    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    counter_z = int(rp["counterZ"])
    divider_z = int(rp["dividerZ"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x1 = hall_w + wing_w - 1
    inner_x = wing_x1 - 1
    outer_x = wing_x1
    public_center = (door_x0 + door_x1) // 2

    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_x = BlockState.of(_material(materials, "structuralFrame"), axis="x")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    foundation = BlockState.of(_material(materials, "foundation"))
    planks = BlockState.of(_material(materials, "publicBoard", "structuralFrame"))
    trim_slab_top = BlockState.of(_material(materials, "exteriorTrimSlab", "structuralFrame"), type="top")
    trim_slab_bottom = BlockState.of(_material(materials, "exteriorTrimSlab", "structuralFrame"), type="bottom")
    masonry_slab = BlockState.of(_material(materials, "masonryTrimSlab", "foundation"), type="bottom")
    seating = BlockState.of(_material(materials, "seating", "floor"), type="bottom")
    shelf = BlockState.of(_material(materials, "recordsShelf", "structuralFrame"))
    freight = BlockState.of(_material(materials, "freightContainer", "hardware"))
    repair_bench = BlockState.of(_material(materials, "repairBench", "hardware"))
    tools = BlockState.of(_material(materials, "toolStorage", "hardware"))
    desk = BlockState.of(_material(materials, "desk", "structuralFrame"), type="top")
    hardware = BlockState.of(_material(materials, "hardware"))
    accent = BlockState.of(_material(materials, "institutionalAccent", "structuralFrame"))
    brass = BlockState.of(_material(materials, "brassAccent", "hardware"))
    transom = BlockState.of(_material(materials, "bayTransom", "window"))
    lantern = BlockState.of(_material(materials, "lighting"), hanging=True)

    # 1. DOOR-HEAD CLOSURE --------------------------------------------------
    door_cfg = finish_cfg.get("doorHeads", {})
    if door_cfg.get("enabled", True):
        # The public doors terminate directly into a full timber header in the wall plane.
        for x in range(door_x0, door_x1 + 1):
            model.set(x, 4, hall_z1, "structural_frame", frame_x, "public_door_header_v10")

        # v0.9's recessed slab rail did not visually cap the working doors from the apron.
        # Put a full beam on both reveal planes, with the transom still recessed above it.
        def finish_portal(a: int, b: int, name: str) -> None:
            for z in range(a, b + 1):
                for x in (inner_x, outer_x):
                    model.set(x, 4, z, "structural_frame", frame_z, f"{name}_door_header_v10")
                model.set(inner_x, 5, z, "window", transom, f"{name}_transom_v10")
                model.clear(outer_x, 5, z)
                for x in (inner_x, outer_x):
                    model.set(x, 6, z, "structural_frame", frame_z, f"{name}_portal_lintel_v10")

        finish_portal(repair_a, repair_b, "repair")
        finish_portal(freight_a, freight_b, "freight")

    # 2. REAR / NORTH ELEVATION --------------------------------------------
    rear_cfg = finish_cfg.get("rearElevation", {})
    north_groups: list[tuple[int, int]] = []
    if rear_cfg.get("enabled", True):
        # Free the north interior wall before recessing its windows; furnishings are rebuilt below.
        removable = {
            "back_office_desk",
            "records_shelves",
            "clerk_backbar",
        }
        for pos, cell in list(model.cells.items()):
            if cell.module in removable:
                model.clear(*pos)

        north_windows = [
            (x, y, z, cell)
            for (x, y, z), cell in list(model.cells.items())
            if z == hall_z0 and cell.role == "window"
        ]
        north_xs = sorted({x for x, _y, _z, _cell in north_windows})
        north_groups = contiguous_groups(north_xs)
        for x, y, _z, cell in north_windows:
            model.clear(x, y, hall_z0)
            model.set(x, y, hall_z0 + 1, "window", cell.state, "north_window_recess_v10")

        for group_index, (x0, x1) in enumerate(north_groups):
            module = f"north_window_reveal_v10_{group_index}"
            for x in range(x0, x1 + 1):
                for y in (3, 4):
                    model.clear(x, y, hall_z0)
                model.set(x, 2, hall_z0 + 1, "foundation", foundation, module)
                model.set(x, 5, hall_z0 + 1, "structural_frame", frame_x, module)
                model.set(x, 2, hall_z0 - 1, "foundation", masonry_slab, f"north_window_sill_v10_{group_index}")
                model.set(x, 5, hall_z0 - 1, "structural_frame", trim_slab_top, f"north_window_hood_v10_{group_index}")
            for jamb_x in (x0 - 1, x1 + 1):
                if not 0 <= jamb_x < hall_w:
                    continue
                for z in (hall_z0, hall_z0 + 1):
                    for y in (3, 4):
                        model.set(jamb_x, y, z, "structural_frame", frame_y, module)

        # Give the rear face the same base/body/crown logic but keep it quieter than the public face.
        for x in range(0, hall_w):
            model.set(x, 2, hall_z0 - 1, "foundation", masonry_slab, "north_plinth_band_v10")
            model.set(x, wall_h, hall_z0 - 1, "structural_frame", trim_slab_top, "north_eave_band_v10")
        north_posts = sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1})
        for x in north_posts:
            for y in range(3, wall_h):
                model.set(x, y, hall_z0 - 1, "structural_frame", frame_y, "north_projected_pilaster_v10")

        # One restrained service marker prevents the rear from becoming a second public facade.
        service_x = max(1, min(hall_w - 2, hall_w - bay_w // 2 - 1))
        model.set(service_x, 3, hall_z0 - 1, "hardware", hardware, "north_service_marker_v10")

    # 3. STAIR ORIENTATION AUDIT -------------------------------------------
    stair_cfg = finish_cfg.get("stairAudit", {})
    audited_positions: list[list[int]] = []
    if stair_cfg.get("enabled", True):
        suspect_modules = {
            "public_plinth_corner_stair_v09",
            "southwest_plinth_corner_v09",
            "public_canopy_corner_stair_v09",
            "working_canopy_corner_stair_v09",
        }
        for pos, cell in list(model.cells.items()):
            module = cell.module or ""
            if module in suspect_modules or "west_window_sill_stair_v09_" in module or "west_window_hood_stair_v09_" in module:
                replacement = masonry_slab if cell.role == "foundation" else trim_slab_top
                model.set(*pos, cell.role, replacement, module.replace("stair_v09", "slab_v10").replace("_v09_", "_v10_"))
                audited_positions.append(list(pos))

    # 4. INTERIOR FINISH ----------------------------------------------------
    interior_cfg = finish_cfg.get("interior", {})
    interior_modules: list[str] = []
    if interior_cfg.get("enabled", True):
        # Remove the old proof-grade furnishing/lighting while leaving shell, anchors and circulation intact.
        replace_modules = {
            "waiting_bench",
            "public_interior_lighting",
            "staff_interior_lighting",
            "freight_stack",
            "repair_workbench",
            "repair_tools",
        }
        for pos, cell in list(model.cells.items()):
            if cell.module in replace_modules:
                model.clear(*pos)

        # Service counter: replace vertical-log blocks with a quieter dark-wood public-service front.
        counter_cells = [
            (pos, cell) for pos, cell in list(model.cells.items())
            if cell.module == "service_counter" and cell.role == "counter"
        ]
        for (x, y, z), _cell in counter_cells:
            model.set(x, y, z, "counter", planks, "service_counter_finish_v10")
        if counter_cells:
            counter_xs = sorted({pos[0] for pos, _cell in counter_cells})
            for x in (counter_xs[0], counter_xs[-1]):
                model.set(x, 3, counter_z, "hardware", brass, "service_counter_endcap_v10")

        # Two transverse ceiling beams give the hall a finished structural interior and support lighting.
        beam_zs = sorted({max(hall_z0 + 2, counter_z - 2), min(hall_z1 - 2, counter_z + 2)})
        for beam_z in beam_zs:
            for x in range(1, hall_w - 1):
                model.set(x, wall_h - 1, beam_z, "structural_frame", frame_x, "interior_ceiling_beam_v10")
            for x in (max(2, public_center - 3), min(hall_w - 3, public_center + 3)):
                model.set(x, wall_h - 2, beam_z, "lighting", lantern, "interior_lighting_v10")

        # Waiting area: paired low benches, intentionally clear of the central entrance/counter aisle.
        bench_z = min(hall_z1 - 3, counter_z + 3)
        left_bench = range(2, min(5, hall_w - 2))
        right_bench = range(max(hall_w - 5, 1), hall_w - 2)
        for x in left_bench:
            model.set(x, 2, bench_z, "seating", seating, "public_waiting_bench_v10")
        for x in right_bench:
            model.set(x, 2, bench_z, "seating", seating, "public_waiting_bench_v10")
        for x in (1, hall_w - 2):
            model.set(x, 2, bench_z, "desk", trim_slab_bottom, "public_waiting_side_table_v10")

        # Frame the existing public information points instead of leaving isolated board blocks.
        for anchor_name, module in (("CONTRACT_BOARD", "contract_board_frame_v10"), ("ROUTE_INFO", "route_info_frame_v10")):
            ax, _ay, az = layout["anchors"][anchor_name]
            board_z = az + 1
            model.set(ax, 4, board_z, "structural_frame", trim_slab_top, module)
            if ax - 1 >= 1:
                model.set(ax - 1, 3, board_z, "structural_frame", frame_y, module)
            if ax + 1 <= hall_w - 2:
                model.set(ax + 1, 3, board_z, "structural_frame", frame_y, module)

        # Clerk backbar and records: move them off the rear glazing and onto side-wall furniture zones.
        staff_z0 = hall_z0 + 2
        staff_z1 = max(staff_z0, counter_z - 2)
        for z in range(staff_z0, staff_z1 + 1):
            if z == layout["anchors"]["BACK_OFFICE_WORK"][2]:
                continue
            model.set(1, 2, z, "records", shelf, "records_wall_v10")
            if z % 2 == 0:
                model.set(1, 3, z, "records", shelf, "records_wall_v10")
            model.set(hall_w - 2, 2, z, "records", shelf, "clerk_records_v10")

        desk_z = min(counter_z - 2, hall_z0 + 3)
        for x in range(2, min(5, hall_w - 2)):
            model.set(x, 2, desk_z, "desk", desk, "back_office_desk_v10")
        model.set(2, 3, desk_z, "hardware", brass, "back_office_desk_detail_v10")

        # Staff light is supported by the northern ceiling beam rather than floating independently.
        staff_beam_z = max(hall_z0 + 2, counter_z - 2)
        model.set(public_center, wall_h - 2, staff_beam_z, "lighting", lantern, "staff_task_lighting_v10")

        # Working wing: keep the freight/repair lanes clear and finish the wall-side work zones.
        warehouse = layout["volumes"]["warehouse"]
        repair = layout["volumes"]["lightRepair"]
        freight_lane_z = int(layout["anchors"]["FREIGHT_PICKUP"][2])
        rack_x = int(warehouse["min"][0]) + 1
        for z in range(int(warehouse["min"][2]) + 1, int(warehouse["max"][2])):
            if abs(z - freight_lane_z) <= 1:
                continue
            model.set(rack_x, 2, z, "storage", freight, "freight_rack_v10")
            if z % 2 == 0:
                model.set(rack_x, 3, z, "storage", freight, "freight_rack_v10")
            model.set(rack_x, 4, z, "structural_frame", trim_slab_top, "freight_rack_cap_v10")

        repair_x = int(repair["min"][0]) + 1
        rz0 = int(repair["min"][2])
        rz1 = int(repair["max"][2])
        for z in range(rz0 + 1, rz1):
            model.set(repair_x, 2, z, "workbench", repair_bench, "repair_bench_v10")
        if rz0 + 1 < rz1:
            model.set(repair_x + 1, 2, rz0 + 1, "tool_storage", tools, "repair_tool_storage_v10")
            model.set(repair_x + 1, 3, rz0 + 1, "tool_storage", tools, "repair_tool_storage_v10")
        for z in range(rz0 + 1, rz1):
            model.set(repair_x, 4, z, "structural_frame", trim_slab_top, "repair_service_shelf_v10")
        if rz1 - rz0 >= 3:
            mid_z = (rz0 + rz1) // 2
            model.set(repair_x, 3, mid_z, "lighting", lantern, "repair_task_lighting_v10")

        interior_modules = sorted({
            cell.module for cell in model.cells.values()
            if cell.module and cell.module.endswith("_v10")
        })

        # Reassert the semantic circulation/anchor clearances after furnishing.
        for z in range(counter_z + 1, hall_z1):
            for y in (2, 3):
                cell = model.cells.get((public_center, y, z))
                if cell and cell.module not in {"public_entrance", "service_counter_finish_v10"}:
                    model.clear(public_center, y, z)
        for anchor_name in ("SERVICE_COUNTER", "NPC_WORK_POINT", "BACK_OFFICE_WORK"):
            ax, _ay, az = layout["anchors"][anchor_name]
            for y in (2, 3):
                cell = model.cells.get((ax, y, az))
                if cell and cell.module not in {"service_counter_finish_v10"}:
                    model.clear(ax, y, az)

    layout["finishResolution"] = {
        "stage": "architectural_finish_resolution",
        "doorHeads": {
            "public": "full_timber_header",
            "working": "full_door_header_recessed_transom_lintel",
        },
        "rearElevation": {
            "northWindowGroups": [list(group) for group in north_groups],
            "baseBodyCrown": bool(north_groups),
            "projectedPilasterRhythm": sorted(set(range(0, hall_w, bay_w)) | {hall_w - 1}),
        },
        "stairAudit": {
            "strategy": "replace_ambiguous_detail_stairs_with_orientation_free_slabs",
            "replacedPositions": audited_positions,
        },
        "interior": {
            "polish": "service_counter_beams_supported_lighting_waiting_records_working_zones",
            "modules": interior_modules,
        },
    }
    layout["resolvedParameters"].update({
        "runtimeCorrectionVersion": "0.10",
        "finishResolutionStage": "explicit",
        "doorHeadTreatment": "closed",
        "rearElevationTreatment": "north_recessed_windows_projected_frame_base_eave",
        "stairAuditTreatment": "ambiguous_v09_detail_stairs_replaced_by_slabs",
        "interiorFinishVersion": "0.10",
    })

    # VALIDATION ------------------------------------------------------------
    issues: list[str] = []
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")

    if door_cfg.get("enabled", True):
        for x in range(door_x0, door_x1 + 1):
            for y in (2, 3):
                door = model.cells.get((x, y, hall_z1))
                if door is None or door.role != "door":
                    issues.append(f"public entrance door missing at {(x, y, hall_z1)}")
            header = model.cells.get((x, 4, hall_z1))
            if header is None or header.module != "public_door_header_v10":
                issues.append(f"public entrance header missing at {(x, 4, hall_z1)}")
        for name, a, b in (("repair", repair_a, repair_b), ("freight", freight_a, freight_b)):
            for z in range(a, b + 1):
                for y in (2, 3):
                    door = model.cells.get((inner_x, y, z))
                    if door is None or door.role != "door":
                        issues.append(f"{name} door missing at {(inner_x, y, z)}")
                for x in (inner_x, outer_x):
                    beam = model.cells.get((x, 4, z))
                    if beam is None or beam.module != f"{name}_door_header_v10":
                        issues.append(f"{name} door header missing at {(x, 4, z)}")
                pane = model.cells.get((inner_x, 5, z))
                if pane is None or pane.module != f"{name}_transom_v10":
                    issues.append(f"{name} transom missing at {(inner_x, 5, z)}")
                if model.cells.get((outer_x, 5, z)) is not None:
                    issues.append(f"{name} outer transom reveal blocked at {(outer_x, 5, z)}")

    if rear_cfg.get("enabled", True):
        if not north_groups:
            issues.append("v0.10 found no north windows to articulate")
        for x0, x1 in north_groups:
            for x in range(x0, x1 + 1):
                for y in (3, 4):
                    pane = model.cells.get((x, y, hall_z0 + 1))
                    if pane is None or pane.role != "window":
                        issues.append(f"north recessed window missing at {(x, y, hall_z0 + 1)}")
                    if model.cells.get((x, y, hall_z0)) is not None:
                        issues.append(f"north outer aperture blocked at {(x, y, hall_z0)}")
        for module in ("north_plinth_band_v10", "north_eave_band_v10", "north_projected_pilaster_v10"):
            if not any(cell.module == module for cell in model.cells.values()):
                issues.append(f"missing v0.10 rear-elevation module: {module}")

    if stair_cfg.get("enabled", True):
        if not audited_positions:
            issues.append("v0.10 stair audit found no v0.9 detail stairs to resolve")
        for cell in model.cells.values():
            module = cell.module or ""
            if module in {
                "public_plinth_corner_stair_v09",
                "southwest_plinth_corner_v09",
                "public_canopy_corner_stair_v09",
                "working_canopy_corner_stair_v09",
            } or "west_window_sill_stair_v09_" in module or "west_window_hood_stair_v09_" in module:
                issues.append(f"unresolved v0.9 decorative stair remains: {module}")
                break

    if interior_cfg.get("enabled", True):
        required_interior = {
            "service_counter_finish_v10",
            "interior_ceiling_beam_v10",
            "interior_lighting_v10",
            "public_waiting_bench_v10",
            "records_wall_v10",
            "back_office_desk_v10",
            "freight_rack_v10",
            "repair_bench_v10",
        }
        present = {cell.module for cell in model.cells.values() if cell.module}
        for module in sorted(required_interior - present):
            issues.append(f"missing v0.10 interior finish module: {module}")
        for z in range(counter_z + 1, hall_z1):
            for y in (2, 3):
                cell = model.cells.get((public_center, y, z))
                if cell and cell.module not in {"public_entrance", "service_counter_finish_v10"}:
                    issues.append(f"v0.10 public aisle blocked at {(public_center, y, z)} by {cell.module or cell.role}")
        for anchor_name in ("SERVICE_COUNTER", "NPC_WORK_POINT", "BACK_OFFICE_WORK"):
            ax, _ay, az = layout["anchors"][anchor_name]
            for y in (2, 3):
                cell = model.cells.get((ax, y, az))
                if cell and cell.module not in {"service_counter_finish_v10"}:
                    issues.append(f"{anchor_name} obstructed by v0.10 finish at {(ax, y, az)}")

    palette = layout.get("paletteReview", {})
    if palette and palette.get("status") != "provisional":
        issues.append("v0.10 finish pass must not finalize the Guild palette")

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
        "schemaVersion": "0.10",
        "compilerVersion": "0.10",
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
