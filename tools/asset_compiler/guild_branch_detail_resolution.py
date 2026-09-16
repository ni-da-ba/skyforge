from __future__ import annotations

import collections
import copy
import hashlib
import json
from typing import Any

from articulation import contiguous_groups
from guild_branch_articulation import compile_guild_branch_v08
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def compile_guild_branch_v09(spec: dict[str, Any]) -> CompiledAsset:
    """Minecraft-scale detail-resolution pass over the accepted v0.8 articulation stage.

    v0.8 established composition, proportion, depth vocabulary and structural-joint logic. The next
    in-game review exposed local block-resolution defects rather than composition defects: the west
    secondary elevation remained visually flat, lanterns replaced structural wall/post cells, service
    doors and their transoms read as unrelated stacked systems, and several hard 90-degree terminations
    wanted Minecraft-native stair/slab resolution.

    v0.9 therefore stays downstream of articulation. It does not change massing, room layout, palette,
    semantic anchors, or the reference profile. It resolves those established forms into a more coherent
    Minecraft block vocabulary.
    """
    if spec.get("schemaVersion") != "0.9":
        raise SpecError("v0.9 detail-resolution compiler requires schemaVersion=0.9")

    detail_cfg = spec.get("detailResolution")
    if not isinstance(detail_cfg, dict):
        raise SpecError("v0.9 requires detailResolution object")

    materials = spec.get("materialRoles", {})
    required = {
        "structuralFrame",
        "wallInfill",
        "foundation",
        "lighting",
        "bayTransom",
        "exteriorTrimSlab",
        "masonryTrimSlab",
        "exteriorTrimStair",
        "masonryTrimStair",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.9 material roles: {', '.join(missing)}")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.8"
    compiled = compile_guild_branch_v08(base_spec)
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
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x1 = hall_w + wing_w - 1
    inner_x = wing_x1 - 1
    outer_x = wing_x1

    structure = spec["structure"]
    public_canopy_depth = int(structure.get("publicCanopy", {}).get("depth", 2))
    public_outer_z = hall_z1 + public_canopy_depth
    surround_x0 = max(1, door_x0 - 1)
    surround_x1 = min(hall_w - 2, door_x1 + 1)

    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    wall = BlockState.of(_material(materials, "wallInfill"))
    foundation = BlockState.of(_material(materials, "foundation"))
    trim_slab_top = BlockState.of(_material(materials, "exteriorTrimSlab"), type="top")
    masonry_slab = BlockState.of(_material(materials, "masonryTrimSlab"), type="bottom")
    transom = BlockState.of(_material(materials, "bayTransom"))
    lantern_hanging = BlockState.of(_material(materials, "lighting"), hanging=True)

    exterior_stair_name = _material(materials, "exteriorTrimStair")
    masonry_stair_name = _material(materials, "masonryTrimStair")

    # SECONDARY / WEST ELEVATION -------------------------------------------
    # The side elevation is allowed to be quieter than the public facade, but not unresolved. Reuse
    # the existing structural rhythm and existing side-window locations: recess those windows one block,
    # box their reveals, then give the face one continuous base/eave hierarchy and projected pilasters.
    west_cfg = detail_cfg.get("secondaryElevation", {}).get("west", {})
    west_groups: list[tuple[int, int]] = []
    if west_cfg.get("enabled", True):
        west_windows = [
            (x, y, z, cell)
            for (x, y, z), cell in list(model.cells.items())
            if x == 0 and cell.role == "window" and cell.module == "public_hall"
        ]
        west_zs = sorted({z for _x, _y, z, _cell in west_windows})
        west_groups = contiguous_groups(west_zs)
        for _x, y, z, cell in west_windows:
            model.clear(0, y, z)
            model.set(1, y, z, "window", cell.state, "west_window_recess_v09")

        for group_index, (z0, z1) in enumerate(west_groups):
            module = f"west_window_reveal_v09_{group_index}"
            for z in range(z0, z1 + 1):
                # Outer aperture remains empty at player-visible window height.
                for y in (3, 4):
                    model.clear(0, y, z)
                # Inner bottom/head returns make the one-block recess a real opening.
                model.set(1, 2, z, "foundation", foundation, module)
                model.set(1, 5, z, "structural_frame", frame_z, module)
                # Projecting Minecraft-scale sill/hood: stairs soften the otherwise blocky termination.
                model.set(
                    -1, 2, z, "foundation",
                    BlockState.of(
                        masonry_stair_name,
                        facing="east",
                        half="bottom",
                        shape="straight",
                    ),
                    f"west_window_sill_stair_v09_{group_index}",
                )
                model.set(
                    -1, 5, z, "structural_frame",
                    BlockState.of(
                        exterior_stair_name,
                        facing="west",
                        half="top",
                        shape="straight",
                    ),
                    f"west_window_hood_stair_v09_{group_index}",
                )

            for jamb_z in (z0 - 1, z1 + 1):
                if not hall_z0 <= jamb_z <= hall_z1:
                    continue
                for x in (0, 1):
                    for y in (3, 4):
                        model.set(x, y, jamb_z, "structural_frame", frame_y, module)

        # A quiet base/body/crown hierarchy keeps the secondary face subordinate but complete.
        for z in range(hall_z0, hall_z1 + 1):
            model.set(-1, 2, z, "foundation", masonry_slab, "west_plinth_band_v09")
            model.set(-1, wall_h, z, "structural_frame", trim_slab_top, "west_eave_band_v09")

        side_post_zs = sorted({hall_z0, hall_z0 + depth // 2, hall_z1})
        for z in side_post_zs:
            for y in range(3, wall_h):
                model.set(-1, y, z, "structural_frame", frame_y, "west_projected_pilaster_v09")

        # Restore the stair sill/hood after the continuous bands where they overlap.
        for group_index, (z0, z1) in enumerate(west_groups):
            for z in range(z0, z1 + 1):
                model.set(
                    -1, 2, z, "foundation",
                    BlockState.of(
                        masonry_stair_name,
                        facing="east",
                        half="bottom",
                        shape="straight",
                    ),
                    f"west_window_sill_stair_v09_{group_index}",
                )
                model.set(
                    -1, 5, z, "structural_frame",
                    BlockState.of(
                        exterior_stair_name,
                        facing="west",
                        half="top",
                        shape="straight",
                    ),
                    f"west_window_hood_stair_v09_{group_index}",
                )

    # PUBLIC LIGHTING MOUNTS ------------------------------------------------
    # v0.3 placed the facade lanterns directly into cells that belong to the bay posts. Restore those
    # structural cells and move the lights under the canopy, where the architecture supports them.
    old_wall_lights = [
        (pos, cell)
        for pos, cell in list(model.cells.items())
        if cell.role == "lighting"
        and cell.module == "public_entrance"
        and pos[2] == hall_z1
    ]
    restored_light_cells: list[tuple[int, int, int]] = []
    for (x, y, z), _cell in old_wall_lights:
        model.set(x, y, z, "structural_frame", frame_y, "public_hall")
        restored_light_cells.append((x, y, z))

    public_light_positions = [
        (surround_x0, 3, hall_z1 + 1),
        (surround_x1, 3, hall_z1 + 1),
    ]
    for x, y, z in public_light_positions:
        model.set(x, y, z, "lighting", lantern_hanging, "public_lighting_under_canopy_v09")

    # WORKING PORTAL AS ONE COMPOSED ASSEMBLY -------------------------------
    # Doors remain at y=2/3. A shallow timber rail at y=4 now separates them from the transom, the
    # transom moves to y=5, and the structural head moves to y=6. From the working approach this reads
    # as one door+rail+clerestory+head composition instead of doors with glass pasted directly above.
    portal_cfg = detail_cfg.get("workingPortal", {})
    if portal_cfg.get("integratedTransom", True):
        def resolve_portal(a: int, b: int, name: str) -> None:
            for z in range(a, b + 1):
                # Remove the old v0.7 transom/head geometry at both depth planes.
                for x in (inner_x, outer_x):
                    model.clear(x, 4, z)
                    model.clear(x, 5, z)

                model.set(
                    inner_x, 4, z,
                    "structural_frame",
                    trim_slab_top,
                    f"{name}_transom_rail_v09",
                )
                model.set(
                    inner_x, 5, z,
                    "window",
                    transom,
                    f"{name}_transom_v09",
                )
                # Keep the outer plane open through rail/transom height so the closure remains recessed.
                model.clear(outer_x, 4, z)
                model.clear(outer_x, 5, z)
                for x in (inner_x, outer_x):
                    model.set(
                        x, 6, z,
                        "structural_frame",
                        frame_z,
                        f"{name}_portal_head_v09",
                    )

            for return_z in (a - 1, b + 1):
                if not 0 <= return_z <= hall_z1:
                    continue
                for x in (inner_x, outer_x):
                    for y in range(2, 6):
                        model.set(
                            x, y, return_z,
                            "structural_frame",
                            frame_y,
                            f"{name}_portal_return_v09",
                        )

        resolve_portal(repair_a, repair_b, "repair")
        resolve_portal(freight_a, freight_b, "freight")

    # MINECRAFT-NATIVE CORNER RESOLUTION ------------------------------------
    # Use stairs only where a hard block termination is visually exposed. This is a bounded
    # micro-articulation vocabulary, not a generic "add stairs everywhere" decoration pass.
    corner_cfg = detail_cfg.get("cornerSmoothing", {})
    smoothing_positions: list[list[int]] = []
    if corner_cfg.get("enabled", True):
        public_corner_states = {
            0: BlockState.of(
                masonry_stair_name, facing="east", half="bottom", shape="straight"
            ),
            hall_w - 1: BlockState.of(
                masonry_stair_name, facing="west", half="bottom", shape="straight"
            ),
        }
        for x, state in public_corner_states.items():
            pos = (x, 2, hall_z1 + 1)
            model.set(*pos, "foundation", state, "public_plinth_corner_stair_v09")
            smoothing_positions.append(list(pos))

        # Bridge the projecting west plinth into the public plinth with a real stair corner.
        southwest_corner = (-1, 2, hall_z1 + 1)
        model.set(
            *southwest_corner,
            "foundation",
            BlockState.of(
                masonry_stair_name,
                facing="east",
                half="bottom",
                shape="outer_right",
            ),
            "southwest_plinth_corner_v09",
        )
        smoothing_positions.append(list(southwest_corner))

        # Resolve the exposed outer corners of the public canopy with top-half timber stairs.
        for x, facing in ((surround_x0, "east"), (surround_x1, "west")):
            pos = (x, 4, public_outer_z)
            model.set(
                *pos,
                "structural_frame",
                BlockState.of(
                    exterior_stair_name,
                    facing=facing,
                    half="top",
                    shape="straight",
                ),
                "public_canopy_corner_stair_v09",
            )
            smoothing_positions.append(list(pos))

        # Apply the same bounded treatment to the outer corners of the two working canopies.
        for volume_name in ("repairCanopy", "freightCanopy"):
            volume = layout["volumes"].get(volume_name)
            if not volume:
                continue
            canopy_x = int(volume["max"][0])
            z0 = int(volume["min"][2])
            z1 = int(volume["max"][2])
            for z, facing in ((z0, "south"), (z1, "north")):
                pos = (canopy_x, wall_h, z)
                model.set(
                    *pos,
                    "structural_frame",
                    BlockState.of(
                        exterior_stair_name,
                        facing=facing,
                        half="top",
                        shape="straight",
                    ),
                    "working_canopy_corner_stair_v09",
                )
                smoothing_positions.append(list(pos))

    layout["detailResolution"] = {
        "stage": "minecraft_detail_resolution",
        "secondaryElevation": {
            "westWindowGroups": [list(group) for group in west_groups],
            "projectedPilasterRhythm": [hall_z0, hall_z0 + depth // 2, hall_z1],
            "baseBodyCrown": True,
        },
        "lighting": {
            "restoredWallCells": [list(pos) for pos in restored_light_cells],
            "underCanopyPositions": [list(pos) for pos in public_light_positions],
            "wallReplacementLightingAllowed": False,
        },
        "workingPortal": {
            "doorLevels": [2, 3],
            "transomRailLevel": 4,
            "transomLevel": 5,
            "headLevel": 6,
            "closurePlane": "recessed",
        },
        "cornerSmoothing": {
            "strategy": "bounded_stair_resolution_at_exposed_terminations",
            "positions": smoothing_positions,
        },
    }
    layout["resolvedParameters"].update({
        "runtimeCorrectionVersion": "0.9",
        "detailResolutionStage": "explicit",
        "secondaryElevationTreatment": "west_recessed_windows_projected_frame_base_eave",
        "publicLightingTreatment": "under_canopy_preserve_wall_structure",
        "workingPortalTreatment": "doors_rail_transom_head",
        "cornerSmoothingTreatment": "bounded_stair_resolution",
    })

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

    # Secondary elevation must actually contain depth and rhythm, not just metadata.
    if west_cfg.get("enabled", True):
        if not west_groups:
            issues.append("v0.9 found no west-side windows to articulate")
        for z0, z1 in west_groups:
            for z in range(z0, z1 + 1):
                for y in (3, 4):
                    pane = model.cells.get((1, y, z))
                    if pane is None or pane.role != "window":
                        issues.append(f"west recessed window pane missing at {(1, y, z)}")
                    if model.cells.get((0, y, z)) is not None:
                        issues.append(f"west outer window aperture blocked at {(0, y, z)}")
        for module in ("west_plinth_band_v09", "west_eave_band_v09", "west_projected_pilaster_v09"):
            if not any(cell.module == module for cell in model.cells.values()):
                issues.append(f"missing v0.9 west-elevation module: {module}")

    # Lighting may not consume the old structural wall/post cells.
    if not old_wall_lights:
        issues.append("v0.9 found no legacy public wall lanterns to relocate")
    for pos in restored_light_cells:
        cell = model.cells.get(pos)
        if cell is None or cell.role != "structural_frame":
            issues.append(f"public lantern wall cell was not restored to structure at {pos}")
    new_lights = [
        (pos, cell)
        for pos, cell in model.cells.items()
        if cell.module == "public_lighting_under_canopy_v09"
    ]
    if len(new_lights) != 2:
        issues.append(f"v0.9 public under-canopy lighting expected 2 lanterns, got {len(new_lights)}")
    for (x, y, z), _cell in new_lights:
        if model.cells.get((x, y + 1, z)) is None:
            issues.append(f"public under-canopy lantern lacks support above at {(x, y, z)}")
    if any(
        cell.role == "lighting" and z == hall_z1
        for (x, _y, z), cell in model.cells.items()
        if cell.module == "public_entrance"
    ):
        issues.append("legacy public wall-plane lantern remains after v0.9 relocation")

    # Working portal must be one vertically composed assembly.
    for name, a, b, door_module in (
        ("repair", repair_a, repair_b, "repair_bay_doors_v06"),
        ("freight", freight_a, freight_b, "freight_bay_doors_v06"),
    ):
        for z in range(a, b + 1):
            for y in (2, 3):
                door = model.cells.get((inner_x, y, z))
                if door is None or door.role != "door" or door.module != door_module:
                    issues.append(f"{name} portal door missing at {(inner_x, y, z)}")
            rail = model.cells.get((inner_x, 4, z))
            if rail is None or rail.module != f"{name}_transom_rail_v09":
                issues.append(f"{name} portal transom rail missing at {(inner_x, 4, z)}")
            pane = model.cells.get((inner_x, 5, z))
            if pane is None or pane.role != "window" or pane.module != f"{name}_transom_v09":
                issues.append(f"{name} portal transom missing at {(inner_x, 5, z)}")
            for y in (4, 5):
                if model.cells.get((outer_x, y, z)) is not None:
                    issues.append(f"{name} portal outer reveal blocked at {(outer_x, y, z)}")
            for x in (inner_x, outer_x):
                head = model.cells.get((x, 6, z))
                if head is None or head.module != f"{name}_portal_head_v09":
                    issues.append(f"{name} portal head missing at {(x, 6, z)}")

    if corner_cfg.get("enabled", True):
        stair_modules = {
            "public_plinth_corner_stair_v09",
            "southwest_plinth_corner_v09",
            "public_canopy_corner_stair_v09",
            "working_canopy_corner_stair_v09",
        }
        stair_cells = [
            cell for cell in model.cells.values()
            if cell.module in stair_modules
        ]
        if not stair_cells:
            issues.append("v0.9 corner smoothing produced no stair geometry")
        allowed_stairs = {exterior_stair_name, masonry_stair_name}
        if any(cell.state.name not in allowed_stairs for cell in stair_cells):
            issues.append("v0.9 corner smoothing contains a non-stair material")

    # Palette remains outside this geometry/detail pass.
    palette = layout.get("paletteReview", {})
    if palette and palette.get("status") != "provisional":
        issues.append("v0.9 detail pass must not finalize the Guild palette")

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
        "schemaVersion": "0.9",
        "compilerVersion": "0.9",
        "assetId": spec["assetId"],
        "layout": layout,
        "blockCount": len(model.cells),
        "materialCounts": dict(
            sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())
        ),
        "roleCounts": dict(
            sorted(collections.Counter(c.role for c in model.cells.values()).items())
        ),
        "moduleBlockCounts": dict(
            sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())
        ),
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        "validation": {"passed": not issues, "issues": issues},
    })
    return CompiledAsset(summary, model)
