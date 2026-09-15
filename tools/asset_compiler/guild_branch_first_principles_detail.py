from __future__ import annotations

import collections
import copy
import hashlib
import json

from architectural_detail_math import blue_noise_maximin_select, hungarian_min_cost, minimum_pair_distance
from architectural_math import (
    closeness_centrality,
    path_stretch,
    reachable_distances,
    shortest_path_length,
    unsupported_targets,
    visibility_count,
    visible_2d,
)
from guild_branch_first_principles_final import compile_guild_branch_first_principles_final
from model import BlockState, CompiledAsset, SpecError


def _material(spec: dict, role: str, fallback: str) -> str:
    roles = spec.get("materialRoles", {})
    value = roles.get(role) or roles.get(fallback)
    if not value:
        raise SpecError(f"materialRoles.{role} or fallback {fallback} is required")
    return str(value)


def _counter(model, field: str) -> dict[str, int]:
    if field == "material":
        values = (cell.state.canonical() for cell in model.cells.values())
    elif field == "role":
        values = (cell.role for cell in model.cells.values())
    elif field == "module":
        values = (cell.module for cell in model.cells.values() if cell.module)
    else:
        raise ValueError(field)
    return dict(sorted(collections.Counter(values).items()))


def _bounds(model) -> dict[str, list[int]]:
    xs = [p[0] for p in model.cells]
    ys = [p[1] for p in model.cells]
    zs = [p[2] for p in model.cells]
    lo = [min(xs), min(ys), min(zs)]
    hi = [max(xs), max(ys), max(zs)]
    return {"min": lo, "max": hi, "size": [hi[i] - lo[i] + 1 for i in range(3)]}


def compile_guild_branch_first_principles_detail(spec: dict) -> CompiledAsset:
    """v0.14: preserve first-principles geometry, then add mathematically bounded richness.

    The detail stage is intentionally downstream. It never re-solves the building massing or semantic
    program. Instead it applies layer-based facade articulation, minimum-distance sparse history,
    minimum-cost fixture assignment, and field-derived roof/eave detailing to the accepted v0.13
    geometry, then re-runs the spatial and structural invariants.
    """
    if spec.get("schemaVersion") != "0.14":
        raise SpecError("first-principles detail compiler requires schemaVersion=0.14")
    if spec.get("generationMode") != "first_principles":
        raise SpecError("v0.14 requires generationMode=first_principles")
    detail_cfg = spec.get("detailPass", {})
    if not detail_cfg.get("enabled", True):
        raise SpecError("v0.14 detailPass.enabled must be true")

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.13"
    compiled = compile_guild_branch_first_principles_final(base_spec)
    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]
    seed = int(spec.get("seed", 0))

    hall_w = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_h = int(rp["wallHeight"])
    wing_w = int(rp["workingWingWidth"])
    hall_x0, hall_x1 = 0, hall_w - 1
    hall_z0, hall_z1 = 0, depth - 1
    wing_x0, wing_x1 = hall_w, hall_w + wing_w - 1
    counter_z = int(rp["counterZ"])
    public_center = sum(map(int, rp["publicEntranceSpan"])) // 2
    repair_open = tuple(map(int, rp["repairOpeningZ"]))
    freight_open = tuple(map(int, rp["freightOpeningZ"]))

    base_block_count = len(model.cells)
    added_positions: set[tuple[int, int, int]] = set()
    replaced_history: list[tuple[int, int, int]] = []

    trim_slab = BlockState.of(_material(spec, "detailTrimSlab", "exteriorTrimSlab"), type="bottom")
    masonry_slab = BlockState.of(_material(spec, "detailMasonrySlab", "masonryTrimSlab"), type="bottom")
    bracket = BlockState.of(_material(spec, "detailBracket", "structuralFrame"))
    history = BlockState.of(_material(spec, "historyMasonry", "foundation"))
    lantern = BlockState.of(_material(spec, "lighting", "lighting"), hanging=True)
    panel = BlockState.of(_material(spec, "interiorPanel", "publicBoard"))
    brass = BlockState.of(_material(spec, "brassAccent", "brassAccent"))
    carpet = BlockState.of(_material(spec, "publicRug", "institutionalAccent"))
    signal = BlockState.of(_material(spec, "signalHardware", "hardware"), facing="up")

    def set_empty(x: int, y: int, z: int, role: str, state: BlockState, module: str) -> bool:
        pos = (x, y, z)
        if pos in model.cells:
            return False
        model.set(x, y, z, role, state, module)
        added_positions.add(pos)
        return True

    # Layered exterior articulation. Detail fills only free planes so opening grammar stays authoritative.
    for x in range(hall_x0, hall_x1 + 1):
        for z in (hall_z0 - 1, hall_z1 + 1):
            set_empty(x, 2, z, "foundation", masonry_slab, "fp14_public_plinth_band")
            set_empty(x, wall_h, z, "structural_frame", trim_slab, "fp14_public_eave_fascia")
    for z in range(hall_z0, hall_z1 + 1):
        set_empty(hall_x0 - 1, 2, z, "foundation", masonry_slab, "fp14_west_plinth_band")
        set_empty(hall_x0 - 1, wall_h, z, "structural_frame", trim_slab, "fp14_west_eave_fascia")
    for x in range(wing_x0, wing_x1 + 1):
        for z in (hall_z0 - 1, hall_z1 + 1):
            set_empty(x, 2, z, "foundation", masonry_slab, "fp14_working_plinth_band")
            set_empty(x, wall_h, z, "structural_frame", trim_slab, "fp14_working_eave_fascia")
    excluded_service_z = set(range(repair_open[0] - 1, repair_open[1] + 2)) | set(range(freight_open[0] - 1, freight_open[1] + 2))
    for z in range(hall_z0, hall_z1 + 1):
        if z not in excluded_service_z:
            set_empty(wing_x1 + 1, 2, z, "foundation", masonry_slab, "fp14_working_face_plinth")

    # Deterministic minimum-distance samples at structurally meaningful stations.
    public_post_x = sorted({0, int(rp["bayWidth"]), 2 * int(rp["bayWidth"]), hall_x1})
    bracket_x = blue_noise_maximin_select(
        [(x, 0) for x in public_post_x], int(detail_cfg.get("publicBracketStations", 3)), 4.0, seed + 1401
    )
    for x, _ in bracket_x:
        set_empty(x, wall_h - 1, hall_z0 - 1, "structural_frame", bracket, "fp14_public_eave_bracket")
        set_empty(x, wall_h - 1, hall_z1 + 1, "structural_frame", bracket, "fp14_public_eave_bracket")
    working_candidates = [(wing_x0, 0), (wing_x0 + wing_w // 2, 0), (wing_x1, 0)]
    working_brackets = blue_noise_maximin_select(working_candidates, 2, 3.0, seed + 1402)
    for x, _ in working_brackets:
        set_empty(x, wall_h - 1, hall_z0 - 1, "structural_frame", bracket, "fp14_working_eave_bracket")
        set_empty(x, wall_h - 1, hall_z1 + 1, "structural_frame", bracket, "fp14_working_eave_bracket")

    north_candidates: list[tuple[int, int]] = []
    for x in range(hall_x0 + 1, hall_x1):
        for y in range(3, wall_h):
            cell = model.cells.get((x, y, hall_z0))
            if cell is not None and cell.role == "wall_infill":
                north_candidates.append((x, y))
    west_candidates: list[tuple[int, int]] = []
    for z in range(hall_z0 + 1, hall_z1):
        for y in range(3, wall_h):
            cell = model.cells.get((hall_x0, y, z))
            if cell is not None and cell.role == "wall_infill":
                west_candidates.append((z, y))
    north_history = blue_noise_maximin_select(
        north_candidates, int(detail_cfg.get("historyNorthCount", 3)), 4.0, seed + 1411
    )
    west_history = blue_noise_maximin_select(
        west_candidates, int(detail_cfg.get("historyWestCount", 2)), 4.0, seed + 1412
    )
    for x, y in north_history:
        model.set(x, y, hall_z0, "wall_infill", history, "fp14_controlled_history")
        replaced_history.append((x, y, hall_z0))
    for z, y in west_history:
        model.set(hall_x0, y, z, "wall_infill", history, "fp14_controlled_history")
        replaced_history.append((hall_x0, y, z))

    service_mark_z = max(repair_open[1] + 1, min(freight_open[0] - 1, (repair_open[1] + freight_open[0]) // 2))
    if model.cells.get((wing_x1, 4, service_mark_z)) is not None:
        model.set(wing_x1, 4, service_mark_z, "hardware", brass, "fp14_service_marker")

    # Interior fixtures use exact minimum-cost bipartite assignment to distinct supported sites.
    public_desired = [(3, 9), (12, 9), (public_center, counter_z + 1), (13, 3)]
    public_candidates: list[tuple[int, int]] = []
    for z in range(2, hall_z1):
        for x in range(2, hall_x1 - 1):
            support = model.cells.get((x, wall_h, z))
            if support is not None and support.role == "structural_frame" and (x, wall_h - 1, z) not in model.cells:
                if x in {3, 6, 9, 12}:
                    public_candidates.append((x, z))
    public_candidates = sorted(set(public_candidates))
    if len(public_candidates) < len(public_desired):
        raise SpecError("v0.14 could not find enough supported public lighting sites")
    public_cost = [[abs(dx - cx) + abs(dz - cz) for cx, cz in public_candidates] for dx, dz in public_desired]
    pub_assignment, pub_cost = hungarian_min_cost(public_cost)
    public_lights = [public_candidates[j] for j in pub_assignment]
    for x, z in public_lights:
        set_empty(x, wall_h - 1, z, "lighting", lantern, "fp14_assigned_public_light")

    working_desired = [(wing_x0 + 3, sum(repair_open) // 2), (wing_x0 + 3, sum(freight_open) // 2)]
    working_candidates2: list[tuple[int, int]] = []
    for z in range(1, hall_z1):
        for x in range(wing_x0 + 1, wing_x1):
            support = model.cells.get((x, wall_h, z))
            if support is not None and support.role == "structural_frame" and (x, wall_h - 1, z) not in model.cells:
                if x in {wing_x0 + 2, wing_x0 + 5}:
                    working_candidates2.append((x, z))
    working_candidates2 = sorted(set(working_candidates2))
    if len(working_candidates2) < len(working_desired):
        raise SpecError("v0.14 could not find enough supported working lighting sites")
    working_cost = [[abs(dx - cx) + abs(dz - cz) for cx, cz in working_candidates2] for dx, dz in working_desired]
    work_assignment, work_cost = hungarian_min_cost(working_cost)
    working_lights = [working_candidates2[j] for j in work_assignment]
    for x, z in working_lights:
        set_empty(x, wall_h - 1, z, "lighting", lantern, "fp14_assigned_working_light")

    # Existing furniture stays authoritative; detail attaches without competing for circulation cells.
    for x in range(6, 10):
        set_empty(x, 3, counter_z, "counter_detail", trim_slab, "fp14_counter_cap")
    for x in (2, 3, 4, 11, 12, 13):
        if model.cells.get((x, 2, 9)) is not None:
            set_empty(x, 3, 9, "seating_detail", bracket, "fp14_waiting_back")
    for z in range(2, 6):
        if model.cells.get((14, 2, z)) is not None:
            set_empty(14, 4, z, "records_detail", trim_slab, "fp14_records_cap")
    for x in range(public_center, public_center + 2):
        for z in (9, 10):
            set_empty(x, 2, z, "floor_detail_passable", carpet, "fp14_public_rug")
    set_empty(18, 4, 3, "hardware", panel, "fp14_repair_tool_panel")
    set_empty(18, 4, 9, "hardware", panel, "fp14_freight_manifest")

    # Roof identity is located from the already-solved shared roof field, not from a new coordinate rule.
    ridge = layout["firstPrinciples"]["roofFields"]["public"]["ridgeStations"]
    if ridge:
        rz = int(ridge[len(ridge) // 2])
        roof_y = max(
            y for (x, y, z), cell in model.cells.items()
            if x == public_center and z == rz and cell.role == "roof"
        )
        set_empty(public_center, roof_y + 1, rz, "hardware", signal, "fp14_compact_roof_signal")

    # Revalidate public space after detail. Carpet is explicitly passable.
    entrance = layout["anchors"]["PUBLIC_ENTRANCE"]
    service = layout["anchors"]["SERVICE_COUNTER"]
    entrance_node = (int(entrance[0]), int(entrance[2]))
    service_node = (int(service[0]), int(service[2]))
    passable_roles = {"floor_detail_passable"}
    public_walkable: set[tuple[int, int]] = set()
    for x in range(hall_x0 + 1, hall_x1):
        for z in range(counter_z + 1, hall_z1):
            blocked = False
            for y in (2, 3):
                cell = model.cells.get((x, y, z))
                if cell is not None and cell.role not in passable_roles:
                    blocked = True
                    break
            if not blocked:
                public_walkable.add((x, z))
    public_walkable.add(entrance_node)
    public_walkable.add(service_node)
    distances = reachable_distances(public_walkable, entrance_node)
    service_distance = shortest_path_length(public_walkable, entrance_node, service_node)
    opaque = {
        (x, z)
        for (x, y, z), cell in model.cells.items()
        if y == 2
        and hall_x0 <= x <= hall_x1
        and counter_z <= z <= hall_z1
        and cell.role not in passable_roles
        and cell.role in {"wall_infill", "structural_frame", "counter", "records", "storage", "workbench", "tool_storage", "seating"}
    }
    sees_service = visible_2d(opaque, entrance_node, service_node)
    detail_space = {
        "domain": "public_side_of_service_counter",
        "walkableNodeCount": len(public_walkable),
        "entranceReachableCount": len(distances),
        "entranceConnectedFraction": len(distances) / max(len(public_walkable), 1),
        "entranceToServiceDistance": service_distance,
        "entranceToServicePathStretch": path_stretch(service_distance, entrance_node, service_node),
        "entranceCloseness": closeness_centrality(public_walkable, entrance_node),
        "serviceCloseness": closeness_centrality(public_walkable, service_node),
        "entranceVisibilityCount": visibility_count(public_walkable, opaque, entrance_node),
        "entranceSeesService": sees_service,
    }

    structural_nodes = {pos for pos, cell in model.cells.items() if cell.role in {"foundation", "structural_frame"}}
    supports = {pos for pos in structural_nodes if pos[1] <= 1}
    roof_targets = {
        tuple(p)
        for p in layout["firstPrinciples"]["roofFields"].get("publicRafterCells", [])
        if int(p[1]) >= wall_h
    } | {
        tuple(p)
        for p in layout["firstPrinciples"]["roofFields"].get("workingRafterCells", [])
        if int(p[1]) >= wall_h
    }
    unsupported = unsupported_targets(structural_nodes, supports, roof_targets)

    min_hist_north = minimum_pair_distance(north_history)
    min_hist_west = minimum_pair_distance(west_history)
    detail_added_count = len(added_positions)
    detail_density = detail_added_count / max(base_block_count, 1)
    issues = list(summary["validation"]["issues"])
    if len(distances) != len(public_walkable):
        issues.append("v0.14 public walkable graph is not fully entrance-connected")
    if service_distance is None:
        issues.append("v0.14 public entrance cannot reach service counter")
    if spec.get("program", {}).get("entranceSeesService", True) and not sees_service:
        issues.append("v0.14 public entrance does not see service counter")
    if unsupported:
        issues.append(f"v0.14 structural roof targets unsupported: {len(unsupported)}")
    if min_hist_north is not None and min_hist_north + 1e-9 < 4.0:
        issues.append("north controlled-history spacing violated")
    if min_hist_west is not None and min_hist_west + 1e-9 < 4.0:
        issues.append("west controlled-history spacing violated")
    max_density = float(detail_cfg.get("maxAddedBlockFraction", 0.14))
    if detail_density > max_density:
        issues.append(f"detail density {detail_density:.4f} exceeds {max_density:.4f}")

    layout["bounds"] = _bounds(model)
    layout["firstPrinciples"]["spaceGraph"] = detail_space
    layout["firstPrinciples"]["structuralGraph"] = {
        "nodeCount": len(structural_nodes),
        "supportCount": len(supports),
        "roofTargetCount": len(roof_targets),
        "unsupportedTargets": [list(v) for v in sorted(unsupported)],
        "claim": "structural_legibility_only_not_load_capacity",
    }
    layout["firstPrinciplesDetail"] = {
        "version": "0.14",
        "baseCompiler": "0.13-first-principles",
        "authorities": [
            "layer_based_facade_detail",
            "discrete_blue_noise_minimum_distance_sampling",
            "linear_assignment_hungarian_algorithm",
            "shared_roof_field_preserved",
            "space_graph_revalidated_after_detail",
            "structural_graph_revalidated_after_detail",
        ],
        "facadeLayers": {
            "plinthBand": True,
            "eaveFascia": True,
            "bracketStations": [x for x, _ in bracket_x],
            "workingBracketStations": [x for x, _ in working_brackets],
        },
        "controlledHistory": {
            "north": [list(v) for v in north_history],
            "west": [list(v) for v in west_history],
            "northMinimumPairDistance": min_hist_north,
            "westMinimumPairDistance": min_hist_west,
            "placedBlockCount": len(replaced_history),
        },
        "fixtureAssignment": {
            "publicDesired": [list(v) for v in public_desired],
            "publicSites": [list(v) for v in public_lights],
            "publicTotalCost": pub_cost,
            "workingDesired": [list(v) for v in working_desired],
            "workingSites": [list(v) for v in working_lights],
            "workingTotalCost": work_cost,
            "algorithm": "hungarian_minimum_cost_assignment",
        },
        "detailAddedBlockCount": detail_added_count,
        "detailAddedBlockFraction": detail_density,
        "replacedHistoryBlockCount": len(replaced_history),
        "paletteStatus": layout.get("paletteReview", {}).get("status", "unknown"),
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
    summary.update(
        {
            "schemaVersion": "0.14",
            "compilerVersion": "0.14-first-principles-detail",
            "assetId": str(spec["assetId"]),
            "blockCount": len(model.cells),
            "layout": layout,
            "materialCounts": _counter(model, "material"),
            "roleCounts": _counter(model, "role"),
            "moduleBlockCounts": _counter(model, "module"),
            "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
            "validation": {"passed": not issues, "issues": issues},
        }
    )
    return CompiledAsset(summary, model)
