from __future__ import annotations

import collections
import hashlib
import itertools
import json
from pathlib import Path
from typing import Any

from architectural_math import (
    LayerClaim,
    TwoEaveGableField,
    closeness_centrality,
    digital_connected_path,
    facade_bay_grammar,
    grammar_description_length_proxy,
    is_four_connected,
    layer_conflicts,
    normalized_bound_violation,
    normalized_square_error,
    path_stretch,
    reachable_distances,
    shortest_path_length,
    solve_discrete,
    unsupported_targets,
    visibility_count,
    visible_2d,
)
from model import BlockState, CompiledAsset, SpecError, Volume, VoxelModel


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def _profile(filename: str) -> dict[str, Any]:
    path = Path(__file__).resolve().parent / "reference_profiles" / filename
    try:
        result = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SpecError(f"unable to read first-principles reference profile {filename}: {exc}") from exc
    if not isinstance(result.get("ratios"), dict):
        raise SpecError("first-principles reference profile requires ratios")
    return result


def _counter(model: VoxelModel, field: str) -> dict[str, int]:
    if field == "material":
        values = (cell.state.canonical() for cell in model.cells.values())
    elif field == "role":
        values = (cell.role for cell in model.cells.values())
    elif field == "module":
        values = (cell.module for cell in model.cells.values() if cell.module)
    else:
        raise ValueError(field)
    return dict(sorted(collections.Counter(values).items()))


def _bounds(model: VoxelModel) -> dict[str, list[int]]:
    xs = [p[0] for p in model.cells]
    ys = [p[1] for p in model.cells]
    zs = [p[2] for p in model.cells]
    minimum = [min(xs), min(ys), min(zs)]
    maximum = [max(xs), max(ys), max(zs)]
    return {
        "min": minimum,
        "max": maximum,
        "size": [maximum[i] - minimum[i] + 1 for i in range(3)],
    }


def _centered(lo: int, hi: int, width: int) -> tuple[int, int]:
    available = hi - lo + 1
    if width < 1 or width > available:
        raise SpecError(f"cannot center width {width} in span {lo}..{hi}")
    start = lo + (available - width) // 2
    return start, start + width - 1


def _ratio_metrics(candidate: dict[str, int]) -> dict[str, float]:
    hall_width = candidate["bayCount"] * candidate["bayWidth"] + 1
    return {
        "bayWidthToWallHeight": candidate["bayWidth"] / candidate["wallHeight"],
        "mainDepthToHallWidth": candidate["hallDepth"] / hall_width,
        "workingWingWidthToHallWidth": candidate["workingWingWidth"] / hall_width,
        "mainRoofRiseToDepth": candidate["roofRise"] / candidate["hallDepth"],
        "roofOverhangToDepth": candidate["roofOverhang"] / candidate["hallDepth"],
        "publicEntranceWidthToBayWidth": 2 / candidate["bayWidth"],
        "averagePublicWindowWidthToBayWidth": candidate["windowWidth"] / candidate["bayWidth"],
    }


def _volume_area(candidate: dict[str, int]) -> tuple[int, int, int, int]:
    hall_width = candidate["bayCount"] * candidate["bayWidth"] + 1
    hall_area = max(0, hall_width - 2) * max(0, candidate["hallDepth"] - 2)
    wing_inside_w = max(0, candidate["workingWingWidth"] - 2)
    usable_depth = max(0, candidate["hallDepth"] - 3)
    repair_depth = max(4, usable_depth // 2)
    warehouse_depth = max(4, usable_depth - repair_depth - 1)
    return hall_area, wing_inside_w * repair_depth, wing_inside_w * warehouse_depth, hall_width


def _candidate_solver(spec: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    cfg = spec["firstPrinciples"]
    program = spec["program"]
    domains = cfg["candidateDomains"]
    keys = (
        "bayCount",
        "bayWidth",
        "wallHeight",
        "hallDepth",
        "workingWingWidth",
        "roofRise",
        "roofOverhang",
        "windowWidth",
    )
    values: dict[str, list[int]] = {}
    for key in keys:
        domain = [int(v) for v in domains.get(key, [])]
        if not domain:
            raise SpecError(f"firstPrinciples.candidateDomains.{key} may not be empty")
        values[key] = domain

    candidates = [
        dict(zip(keys, choice))
        for choice in itertools.product(*(values[key] for key in keys))
    ]
    ratios = profile["ratios"]
    required_ratio_keys = (
        "bayWidthToWallHeight",
        "mainDepthToHallWidth",
        "workingWingWidthToHallWidth",
        "mainRoofRiseToDepth",
        "roofOverhangToDepth",
        "publicEntranceWidthToBayWidth",
        "averagePublicWindowWidthToBayWidth",
    )
    missing = [key for key in required_ratio_keys if key not in ratios]
    if missing:
        raise SpecError("reference profile missing ratios: " + ", ".join(missing))

    min_public = int(program.get("publicHallMinArea", 100))
    min_repair = int(program.get("repairMinArea", 18))
    min_warehouse = int(program.get("warehouseMinArea", 22))
    max_blocks = int(program.get("maxFootprintArea", 500))

    hard_rules = [
        ("program.public_area", lambda c: _volume_area(c)[0] >= min_public),
        ("program.repair_area", lambda c: _volume_area(c)[1] >= min_repair),
        ("program.warehouse_area", lambda c: _volume_area(c)[2] >= min_warehouse),
        (
            "program.footprint_cap",
            lambda c: _volume_area(c)[3] * c["hallDepth"] + c["workingWingWidth"] * c["hallDepth"] <= max_blocks,
        ),
        ("grammar.centered_public_entrance", lambda c: c["bayCount"] % 2 == 1),
        ("grammar.window_has_jambs", lambda c: 1 <= c["windowWidth"] <= c["bayWidth"] - 2),
        ("roof.positive_rise", lambda c: c["roofRise"] >= 1),
        ("roof.nonzero_overhang", lambda c: c["roofOverhang"] >= 1),
    ]

    for key in required_ratio_keys:
        entry = ratios[key]
        hard_rules.append(
            (
                f"{key}.broad_envelope",
                lambda c, key=key, entry=entry: (
                    float(entry["min"]) - 0.08
                    <= _ratio_metrics(c)[key]
                    <= float(entry["max"]) + 0.08
                ),
            )
        )

    weights = cfg.get("objectiveWeights", {})
    soft_terms = []
    for key in required_ratio_keys:
        entry = ratios[key]
        soft_terms.append(
            (
                f"ratio.{key}",
                float(weights.get(key, 1.0)),
                lambda c, key=key, entry=entry: normalized_square_error(
                    _ratio_metrics(c)[key],
                    float(entry["target"]),
                    float(entry["min"]),
                    float(entry["max"]),
                ),
            )
        )
        soft_terms.append(
            (
                f"bound.{key}",
                float(weights.get("boundViolation", 4.0)),
                lambda c, key=key, entry=entry: normalized_bound_violation(
                    _ratio_metrics(c)[key], float(entry["min"]), float(entry["max"])
                ),
            )
        )

    required_area = max(1, min_public + min_repair + min_warehouse)
    soft_terms.extend(
        [
            (
                "program.compactness",
                float(weights.get("compactness", 0.5)),
                lambda c: (((_volume_area(c)[0] + _volume_area(c)[1] + _volume_area(c)[2]) / required_area) - 1.0) ** 2,
            ),
            (
                "program.working_balance",
                float(weights.get("workingBalance", 0.25)),
                lambda c: ((_volume_area(c)[1] - _volume_area(c)[2]) / max(_volume_area(c)[1] + _volume_area(c)[2], 1)) ** 2,
            ),
            (
                "grammar.rule_count_proxy",
                float(weights.get("grammarComplexity", 0.15)),
                lambda c: grammar_description_length_proxy(
                    facade_bay_grammar(c["bayCount"], c["bayWidth"], c["windowWidth"], [c["bayCount"] // 2])[1]
                ) / 10.0,
            ),
        ]
    )
    try:
        return solve_discrete(candidates, hard_rules, soft_terms)
    except ValueError as exc:
        raise SpecError(str(exc)) from exc


def compile_guild_branch_first_principles(spec: dict[str, Any]) -> CompiledAsset:
    """Generate a Guild branch from semantic program, constraints, grammar and fields.

    Unlike v0.12, this function does not call any earlier Guild-branch compiler. No accepted v0.1-v0.12
    geometry is inherited. It solves a bounded semantic/architectural design space first, then derives
    volumes, openings, structure, roof, furnishings and validation evidence from that solved state.
    """
    if spec.get("schemaVersion") != "0.13":
        raise SpecError("first-principles Guild compiler requires schemaVersion=0.13")
    if spec.get("assetType") != "guild_branch":
        raise SpecError("first-principles proof supports assetType=guild_branch")
    if spec.get("generationMode") != "first_principles":
        raise SpecError("v0.13 requires generationMode=first_principles")
    if not isinstance(spec.get("program"), dict) or not isinstance(spec.get("firstPrinciples"), dict):
        raise SpecError("v0.13 requires program and firstPrinciples objects")

    materials = spec.get("materialRoles", {})
    required_materials = {
        "foundation", "structuralFrame", "wallInfill", "floor", "roof", "roofStair", "roofSlab",
        "window", "publicDoor", "workingDoor", "hardware", "lighting", "institutionalAccent",
        "brassAccent", "canopy", "seating", "recordsShelf", "publicBoard", "freightContainer",
        "repairBench", "toolStorage", "desk", "exteriorTrimSlab", "masonryTrimSlab", "bayTransom",
    }
    missing = sorted(required_materials - set(materials))
    if missing:
        raise SpecError("missing v0.13 material roles: " + ", ".join(missing))

    cfg = spec["firstPrinciples"]
    profile_name = str(cfg.get("referenceProfile", "guild_branch_temperate_small_working_reference_v0.1.json"))
    reference = _profile(profile_name)
    optimization = _candidate_solver(spec, reference)
    chosen = optimization["chosen"]

    bays = int(chosen["bayCount"])
    bay_w = int(chosen["bayWidth"])
    wall_h = int(chosen["wallHeight"])
    depth = int(chosen["hallDepth"])
    wing_w = int(chosen["workingWingWidth"])
    roof_rise = int(chosen["roofRise"])
    overhang = int(chosen["roofOverhang"])
    window_w = int(chosen["windowWidth"])

    hall_w = bays * bay_w + 1
    hall_x0, hall_x1 = 0, hall_w - 1
    hall_z0, hall_z1 = 0, depth - 1
    wing_x0, wing_x1 = hall_w, hall_w + wing_w - 1
    roof_base = wall_h + 1
    entrance_bay = bays // 2
    entrance_x0, entrance_x1 = _centered(entrance_bay * bay_w + 1, (entrance_bay + 1) * bay_w - 1, 2)
    public_center = (entrance_x0 + entrance_x1) // 2

    usable_working_depth = depth - 3
    repair_depth = max(4, usable_working_depth // 2)
    split_z = 1 + repair_depth
    repair_z0, repair_z1 = 1, split_z - 1
    warehouse_z0, warehouse_z1 = split_z + 1, depth - 2
    counter_z = max(4, min(depth - 5, depth // 2 + 1))
    backoffice_z0 = 1
    backoffice_z1 = max(backoffice_z0 + 2, counter_z - 2)

    model = VoxelModel()
    foundation = BlockState.of(_material(materials, "foundation"))
    floor = BlockState.of(_material(materials, "floor"))
    wall = BlockState.of(_material(materials, "wallInfill"))
    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_x = BlockState.of(_material(materials, "structuralFrame"), axis="x")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    pane = BlockState.of(_material(materials, "window"))
    roof = BlockState.of(_material(materials, "roof"))
    roof_n = BlockState.of(_material(materials, "roofStair"), facing="north", half="bottom", shape="straight")
    roof_s = BlockState.of(_material(materials, "roofStair"), facing="south", half="bottom", shape="straight")
    roof_slab = BlockState.of(_material(materials, "roofSlab"), type="bottom")
    trim_top = BlockState.of(_material(materials, "exteriorTrimSlab"), type="top")
    masonry_slab = BlockState.of(_material(materials, "masonryTrimSlab"), type="bottom")
    canopy = BlockState.of(_material(materials, "canopy"), type="bottom")
    lantern = BlockState.of(_material(materials, "lighting"), hanging=True)
    hardware = BlockState.of(_material(materials, "hardware"))
    accent = BlockState.of(_material(materials, "institutionalAccent"))
    brass = BlockState.of(_material(materials, "brassAccent"))
    seating = BlockState.of(_material(materials, "seating"), type="bottom")
    records = BlockState.of(_material(materials, "recordsShelf"))
    board = BlockState.of(_material(materials, "publicBoard"))
    freight = BlockState.of(_material(materials, "freightContainer"))
    repair_bench = BlockState.of(_material(materials, "repairBench"))
    tools = BlockState.of(_material(materials, "toolStorage"))
    desk = BlockState.of(_material(materials, "desk"), type="top")
    transom = BlockState.of(_material(materials, "bayTransom"))

    def in_hall(x: int, z: int) -> bool:
        return hall_x0 <= x <= hall_x1 and hall_z0 <= z <= hall_z1

    def in_wing(x: int, z: int) -> bool:
        return wing_x0 <= x <= wing_x1 and hall_z0 <= z <= hall_z1

    for x in range(hall_x0, wing_x1 + 1):
        for z in range(hall_z0, hall_z1 + 1):
            if not (in_hall(x, z) or in_wing(x, z)):
                continue
            model.set(x, 0, z, "foundation", foundation, "fp_foundation")
            model.set(x, 1, z, "floor", floor, "fp_floor")

    for x in range(hall_x0, hall_x1 + 1):
        for y in range(2, wall_h + 1):
            model.set(x, y, hall_z0, "wall_infill", wall, "fp_public_envelope")
            model.set(x, y, hall_z1, "wall_infill", wall, "fp_public_envelope")
    for z in range(hall_z0, hall_z1 + 1):
        for y in range(2, wall_h + 1):
            model.set(hall_x0, y, z, "wall_infill", wall, "fp_public_envelope")
            model.set(hall_x1, y, z, "wall_infill", wall, "fp_staff_separator")

    for x in range(wing_x0, wing_x1 + 1):
        for y in range(2, wall_h + 1):
            model.set(x, y, hall_z0, "wall_infill", wall, "fp_working_envelope")
            model.set(x, y, hall_z1, "wall_infill", wall, "fp_working_envelope")
    for z in range(hall_z0, hall_z1 + 1):
        for y in range(2, wall_h + 1):
            model.set(wing_x1, y, z, "wall_infill", wall, "fp_working_envelope")

    public_posts = sorted(set(range(0, hall_w, bay_w)) | {hall_x1})
    for x in public_posts:
        for z in (hall_z0, hall_z1):
            for y in range(1, wall_h + 1):
                model.set(x, y, z, "structural_frame", frame_y, "fp_public_post")
    west_post_zs = sorted({hall_z0, depth // 2, hall_z1})
    for z in west_post_zs:
        for y in range(1, wall_h + 1):
            model.set(hall_x0, y, z, "structural_frame", frame_y, "fp_public_post")
    for x in range(hall_x0, hall_x1 + 1):
        for z in (hall_z0, hall_z1):
            model.set(x, wall_h, z, "structural_frame", frame_x, "fp_public_wall_plate")
    for z in range(hall_z0, hall_z1 + 1):
        model.set(hall_x0, wall_h, z, "structural_frame", frame_z, "fp_public_wall_plate")
        model.set(hall_x1, wall_h, z, "structural_frame", frame_z, "fp_public_wall_plate")

    working_post_zs = sorted({hall_z0, repair_z1, warehouse_z0, hall_z1})
    for x in (wing_x0, wing_x1):
        for z in working_post_zs:
            for y in range(1, wall_h + 1):
                model.set(x, y, z, "structural_frame", frame_y, "fp_working_post")
    for x in range(wing_x0, wing_x1 + 1):
        for z in (hall_z0, hall_z1):
            model.set(x, wall_h, z, "structural_frame", frame_x, "fp_working_wall_plate")
    for z in range(hall_z0, hall_z1 + 1):
        for x in (wing_x0, wing_x1):
            model.set(x, wall_h, z, "structural_frame", frame_z, "fp_working_wall_plate")

    south_groups, south_trace = facade_bay_grammar(bays, bay_w, window_w, [entrance_bay])
    north_groups, north_trace = facade_bay_grammar(bays, bay_w, window_w)
    west_segment = max(5, depth // 2)
    west_groups, west_trace = facade_bay_grammar(2, west_segment, min(window_w, west_segment - 2))
    west_groups = [(a, b) for a, b in west_groups if b < hall_z1]

    def carve_z_window(a: int, b: int, wall_z: int, pane_z: int, trim_z: int, prefix: str, index: int) -> None:
        for x in range(a, b + 1):
            for y in (3, 4):
                model.clear(x, y, wall_z)
                model.set(x, y, pane_z, "window", pane, f"fp_{prefix}_pane")
            model.set(x, 2, pane_z, "foundation", foundation, f"fp_{prefix}_reveal")
            model.set(x, 5, pane_z, "structural_frame", frame_x, f"fp_{prefix}_reveal")
            model.set(x, 2, trim_z, "foundation", masonry_slab, f"fp_{prefix}_sill_{index}")
            model.set(x, 5, trim_z, "structural_frame", trim_top, f"fp_{prefix}_hood_{index}")
        for jamb_x in (a - 1, b + 1):
            if hall_x0 <= jamb_x <= hall_x1:
                for y in (3, 4):
                    model.set(jamb_x, y, wall_z, "structural_frame", frame_y, f"fp_{prefix}_jamb_{index}")

    for i, (a, b) in enumerate(south_groups):
        carve_z_window(a, b, hall_z1, hall_z1 - 1, hall_z1 + 1, "south_window", i)
    for i, (a, b) in enumerate(north_groups):
        carve_z_window(a, b, hall_z0, hall_z0 + 1, hall_z0 - 1, "north_window", i)
    for i, (a, b) in enumerate(west_groups):
        for z in range(a, b + 1):
            for y in (3, 4):
                model.clear(hall_x0, y, z)
                model.set(hall_x0 + 1, y, z, "window", pane, "fp_west_window_pane")
            model.set(hall_x0 + 1, 2, z, "foundation", foundation, "fp_west_window_reveal")
            model.set(hall_x0 + 1, 5, z, "structural_frame", frame_z, "fp_west_window_reveal")
            model.set(hall_x0 - 1, 2, z, "foundation", masonry_slab, f"fp_west_window_sill_{i}")
            model.set(hall_x0 - 1, 5, z, "structural_frame", trim_top, f"fp_west_window_hood_{i}")
        for jamb_z in (a - 1, b + 1):
            if hall_z0 <= jamb_z <= hall_z1:
                for y in (3, 4):
                    model.set(hall_x0, y, jamb_z, "structural_frame", frame_y, f"fp_west_window_jamb_{i}")

    for x in range(entrance_x0, entrance_x1 + 1):
        for y in range(2, 5):
            model.clear(x, y, hall_z1)
        hinge = "left" if x == entrance_x0 else "right"
        model.set(x, 2, hall_z1, "door", BlockState.of(_material(materials, "publicDoor"), facing="south", half="lower", hinge=hinge), "fp_public_entrance")
        model.set(x, 3, hall_z1, "door", BlockState.of(_material(materials, "publicDoor"), facing="south", half="upper", hinge=hinge), "fp_public_entrance")
        model.set(x, 4, hall_z1, "structural_frame", frame_x, "fp_public_door_header")
    for x in range(max(hall_x0 + 1, entrance_x0 - 1), min(hall_x1, entrance_x1 + 1) + 1):
        for z in range(hall_z1 + 1, hall_z1 + 3):
            model.set(x, 5, z, "structural_frame", canopy, "fp_public_canopy")
    for x in (entrance_x0 - 1, entrance_x1 + 1):
        if hall_x0 < x < hall_x1:
            model.set(x, 4, hall_z1 + 1, "lighting", lantern, "fp_public_lantern")
    model.set(public_center, 5, hall_z1 + 1, "institutional_accent", accent, "fp_guild_identity")
    model.set(public_center, 4, hall_z1 + 1, "hardware", brass, "fp_guild_identity")

    staff_z = max(2, min(depth - 3, counter_z - 1))
    for y in (2, 3):
        model.clear(hall_x1, y, staff_z)
    model.set(hall_x1, 2, staff_z, "door", BlockState.of(_material(materials, "workingDoor"), facing="east", half="lower", hinge="left"), "fp_staff_connection")
    model.set(hall_x1, 3, staff_z, "door", BlockState.of(_material(materials, "workingDoor"), facing="east", half="upper", hinge="left"), "fp_staff_connection")
    model.set(hall_x1, 4, staff_z, "structural_frame", frame_z, "fp_staff_connection_header")

    def working_portal(z0: int, z1: int, width: int, name: str) -> tuple[int, int]:
        a, b = _centered(z0, z1, min(width, z1 - z0 + 1))
        for z in range(a, b + 1):
            for y in range(2, 6):
                model.clear(wing_x1, y, z)
            hinge = "left" if (z - a) % 2 == 0 else "right"
            model.set(wing_x1, 2, z, "door", BlockState.of(_material(materials, "workingDoor"), facing="east", half="lower", hinge=hinge), f"fp_{name}_door")
            model.set(wing_x1, 3, z, "door", BlockState.of(_material(materials, "workingDoor"), facing="east", half="upper", hinge=hinge), f"fp_{name}_door")
            model.set(wing_x1 - 1, 4, z, "window", transom, f"fp_{name}_transom")
            model.set(wing_x1, 4, z, "structural_frame", frame_z, f"fp_{name}_header")
            model.set(wing_x1, 5, z, "structural_frame", frame_z, f"fp_{name}_lintel")
            for x in range(wing_x1 + 1, wing_x1 + 3):
                model.set(x, 5, z, "structural_frame", canopy, f"fp_{name}_canopy")
        for jamb_z in (a - 1, b + 1):
            if hall_z0 <= jamb_z <= hall_z1:
                for y in range(2, 6):
                    model.set(wing_x1, y, jamb_z, "structural_frame", frame_y, f"fp_{name}_jamb")
        return a, b

    repair_open = working_portal(repair_z0, repair_z1, 3, "repair")
    freight_open = working_portal(warehouse_z0, warehouse_z1, 4, "freight")

    counter_x0 = max(2, entrance_x0 - 2)
    counter_x1 = min(hall_x1 - 2, entrance_x1 + 2)
    for x in range(counter_x0, counter_x1 + 1):
        model.set(x, 2, counter_z, "counter", BlockState.of(_material(materials, "publicBoard")), "fp_service_counter")
    for x in (counter_x0, counter_x1):
        model.set(x, 3, counter_z, "hardware", brass, "fp_service_counter_end")

    waiting_z = min(hall_z1 - 3, counter_z + 3)
    for x in range(2, min(5, hall_x1 - 1)):
        model.set(x, 2, waiting_z, "seating", seating, "fp_waiting_bench")
    for x in range(max(1, hall_x1 - 4), hall_x1 - 1):
        model.set(x, 2, waiting_z, "seating", seating, "fp_waiting_bench")

    info_left = max(1, entrance_x0 - 2)
    info_right = min(hall_x1 - 1, entrance_x1 + 2)
    for x, module in ((info_left, "fp_contract_board"), (info_right, "fp_route_info")):
        model.set(x, 3, hall_z1 - 1, "board", board, module)
        model.set(x, 4, hall_z1 - 1, "structural_frame", trim_top, module + "_cap")

    for z in range(backoffice_z0 + 1, backoffice_z1 + 1):
        if z == staff_z:
            continue
        model.set(hall_x1 - 1, 2, z, "records", records, "fp_records_wall")
        if z % 2 == 0:
            model.set(hall_x1 - 1, 3, z, "records", records, "fp_records_wall")
    desk_z = min(backoffice_z1, backoffice_z0 + 2)
    for x in range(2, min(5, hall_x1 - 1)):
        model.set(x, 2, desk_z, "desk", desk, "fp_backoffice_desk")

    for x in range(wing_x0 + 1, wing_x1):
        if x not in (wing_x0 + 2, wing_x0 + 3):
            for y in range(2, 5):
                model.set(x, y, split_z, "wall_infill", wall, "fp_working_partition")
    repair_bench_x = wing_x0 + 1
    for z in range(repair_z0 + 1, repair_z1):
        model.set(repair_bench_x, 2, z, "workbench", repair_bench, "fp_repair_bench")
        model.set(repair_bench_x, 4, z, "structural_frame", trim_top, "fp_repair_service_shelf")
    model.set(repair_bench_x + 1, 2, repair_z0 + 1, "tool_storage", tools, "fp_repair_tools")
    model.set(repair_bench_x + 1, 3, repair_z0 + 1, "tool_storage", tools, "fp_repair_tools")

    freight_x = wing_x0 + 1
    freight_lane_z = sum(freight_open) // 2
    for z in range(warehouse_z0 + 1, warehouse_z1):
        if abs(z - freight_lane_z) <= 1:
            continue
        model.set(freight_x, 2, z, "storage", freight, "fp_freight_rack")
        if z % 2 == 0:
            model.set(freight_x, 3, z, "storage", freight, "fp_freight_rack")

    public_field = TwoEaveGableField(hall_z0, hall_z1, roof_rise, roof_base)
    wing_rise = max(2, round(roof_rise * 0.6))
    working_field = TwoEaveGableField(hall_z0, hall_z1, wing_rise, roof_base)

    for z, y in public_field.profile(overhang):
        for x in range(hall_x0, hall_x1 + 1):
            model.set(x, y, z, "roof", roof, "fp_public_weather_skin")
    for z, y in working_field.profile(overhang):
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, y, z, "roof", roof, "fp_working_weather_skin")

    for x in range(hall_x0, hall_x1 + 1):
        model.set(x, public_field.height(hall_z0 - overhang), hall_z0 - overhang, "roof", roof_n, "fp_public_eave")
        model.set(x, public_field.height(hall_z1 + overhang), hall_z1 + overhang, "roof", roof_s, "fp_public_eave")
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, working_field.height(hall_z0 - overhang), hall_z0 - overhang, "roof", roof_n, "fp_working_eave")
        model.set(x, working_field.height(hall_z1 + overhang), hall_z1 + overhang, "roof", roof_s, "fp_working_eave")

    public_ridges = public_field.ridge_stations()
    working_ridges = working_field.ridge_stations()
    for rz in public_ridges:
        for x in range(hall_x0, hall_x1 + 1):
            model.set(x, public_field.height(rz) + 1, rz, "roof", roof_slab, "fp_public_ridge")
    for rz in working_ridges:
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, working_field.height(rz) + 1, rz, "roof", roof_slab, "fp_working_ridge")

    roof_targets: set[tuple[int, int, int]] = set()

    def realize_rafter(x: int, field: TwoEaveGableField, prefix: str) -> list[tuple[int, int, int]]:
        samples = [(z, field.height(z) - 1) for z in range(field.z0, field.z1 + 1)]
        path = digital_connected_path(samples)
        if not is_four_connected(path):
            raise SpecError(f"{prefix} digital rafter path is not four-connected")
        positions: list[tuple[int, int, int]] = []
        for z, y in path:
            state = frame_y if positions and positions[-1][2] == z else frame_z
            model.set(x, y, z, "structural_frame", state, prefix)
            positions.append((x, y, z))
            if y >= wall_h:
                roof_targets.add((x, y, z))
        return positions

    public_rafter_cells: list[list[int]] = []
    for x in public_posts:
        public_rafter_cells.extend([list(p) for p in realize_rafter(x, public_field, "fp_public_rafter")])
    working_rafter_xs = sorted({wing_x0, wing_x0 + wing_w // 2, wing_x1})
    working_rafter_cells: list[list[int]] = []
    for x in working_rafter_xs:
        working_rafter_cells.extend([list(p) for p in realize_rafter(x, working_field, "fp_working_rafter")])

    public_tie_zs = sorted({max(2, backoffice_z1), counter_z, min(hall_z1 - 2, waiting_z)})
    for z in public_tie_zs:
        for x in range(hall_x0 + 1, hall_x1):
            model.set(x, wall_h, z, "structural_frame", frame_x, "fp_public_tie_beam")
            roof_targets.add((x, wall_h, z))
    working_tie_zs = sorted({repair_z1, warehouse_z0})
    for z in working_tie_zs:
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, wall_h, z, "structural_frame", frame_x, "fp_working_tie_beam")
            roof_targets.add((x, wall_h, z))

    repair_center_z = sum(repair_open) // 2
    freight_center_z = sum(freight_open) // 2
    service_anchor = (public_center, counter_z + 1)
    entrance_anchor = (public_center, hall_z1 - 1)
    waiting_anchor = (max(2, public_center - 3), waiting_z - 1)
    staff_anchor = (public_center, counter_z - 1)
    backoffice_anchor = (3, desk_z + 1)
    repair_anchor = (wing_x1 - 2, repair_center_z)
    freight_anchor = (wing_x1 - 2, freight_center_z)

    anchors = {
        "PUBLIC_ENTRANCE": [entrance_anchor[0], 1, entrance_anchor[1]],
        "SERVICE_COUNTER": [service_anchor[0], 1, service_anchor[1]],
        "CONTRACT_BOARD": [info_left, 1, hall_z1 - 2],
        "ROUTE_INFO": [info_right, 1, hall_z1 - 2],
        "NPC_WORK_POINT": [staff_anchor[0], 1, staff_anchor[1]],
        "BACK_OFFICE_WORK": [backoffice_anchor[0], 1, backoffice_anchor[1]],
        "FREIGHT_PICKUP": [freight_anchor[0], 1, freight_anchor[1]],
        "FREIGHT_DROPOFF": [wing_x1 - 3, 1, freight_anchor[1]],
        "REPAIR_BAY": [repair_anchor[0], 1, repair_anchor[1]],
        "AIRFIELD_INTERFACE": [wing_x1 + 3, 1, (repair_center_z + freight_center_z) // 2],
    }

    walkable: set[tuple[int, int]] = set()
    for x in range(hall_x0 + 1, hall_x1):
        for z in range(hall_z0 + 1, hall_z1):
            if all(model.cells.get((x, y, z)) is None for y in (2, 3)):
                walkable.add((x, z))
    walkable.add(service_anchor)
    walkable.add(entrance_anchor)

    distances = reachable_distances(walkable, entrance_anchor)
    service_distance = shortest_path_length(walkable, entrance_anchor, service_anchor)
    opaque = {
        (x, z)
        for (x, y, z), cell in model.cells.items()
        if y == 2 and cell.role in {"wall_infill", "structural_frame", "counter", "records", "storage", "workbench", "tool_storage"}
    }
    entrance_visibility = visibility_count(walkable, opaque, entrance_anchor)
    entrance_sees_service = visible_2d(opaque, entrance_anchor, service_anchor)

    semantic_graph = {
        "nodes": {
            "publicEntrance": list(entrance_anchor), "waiting": list(waiting_anchor), "serviceCounter": list(service_anchor),
            "staffWork": list(staff_anchor), "backOffice": list(backoffice_anchor), "repairBay": list(repair_anchor),
            "warehouse": list(freight_anchor), "airfield": [wing_x1 + 3, (repair_center_z + freight_center_z) // 2],
        },
        "requiredEdges": [
            ["publicEntrance", "waiting"], ["waiting", "serviceCounter"], ["serviceCounter", "staffWork"],
            ["staffWork", "backOffice"], ["staffWork", "repairBay"], ["staffWork", "warehouse"],
            ["repairBay", "airfield"], ["warehouse", "airfield"],
        ],
    }

    layer_claims: list[LayerClaim] = []
    for pos, cell in model.cells.items():
        if cell.role == "structural_frame":
            layer_claims.append(LayerClaim(pos, "structure", cell.module or cell.role))
        elif cell.role == "wall_infill":
            layer_claims.append(LayerClaim(pos, "envelope", cell.module or cell.role))
        elif cell.role == "window":
            layer_claims.append(LayerClaim(pos, "glazing", cell.module or cell.role))
        elif cell.role in {"hardware", "institutional_accent", "lighting"}:
            layer_claims.append(LayerClaim(pos, "hardware", cell.module or cell.role))
        elif cell.role in {"board", "records", "desk", "counter", "seating", "storage", "workbench", "tool_storage"}:
            layer_claims.append(LayerClaim(pos, "furnishing", cell.module or cell.role))
    for a, b in south_groups:
        for x in range(a, b + 1):
            for y in (3, 4):
                layer_claims.append(LayerClaim((x, y, hall_z1), "opening_void", "fp_south_opening"))
    for a, b in north_groups:
        for x in range(a, b + 1):
            for y in (3, 4):
                layer_claims.append(LayerClaim((x, y, hall_z0), "opening_void", "fp_north_opening"))
    for a, b in west_groups:
        for z in range(a, b + 1):
            for y in (3, 4):
                layer_claims.append(LayerClaim((hall_x0, y, z), "opening_void", "fp_west_opening"))
    layer_issues = layer_conflicts(layer_claims)

    structural_nodes = {pos for pos, cell in model.cells.items() if cell.role in {"foundation", "structural_frame"}}
    supports = {pos for pos in structural_nodes if pos[1] <= 1}
    unsupported = unsupported_targets(structural_nodes, supports, roof_targets)
    grammar_trace = south_trace + north_trace + west_trace
    grammar_complexity = grammar_description_length_proxy(grammar_trace)
    required_anchors = set(spec.get("requestedAnchors", []))
    issues: list[str] = []
    missing_anchors = sorted(required_anchors - set(anchors))
    if missing_anchors:
        issues.append("missing requested anchors: " + ", ".join(missing_anchors))
    if layer_issues:
        issues.append(f"facade layer conflicts: {len(layer_issues)}")
    if unsupported:
        issues.append(f"unsupported roof-frame targets: {len(unsupported)}")
    if service_distance is None:
        issues.append("public entrance cannot reach service counter")
    if len(distances) != len(walkable):
        issues.append("public walkable graph is not fully entrance-connected")
    if spec.get("program", {}).get("entranceSeesService", True) and not entrance_sees_service:
        issues.append("public entrance does not have line of sight to service counter")
    if not south_groups or not north_groups or not west_groups:
        issues.append("first-principles facade grammar produced an empty public elevation group")
    if not public_ridges or not working_ridges:
        issues.append("roof field produced no ridge station")

    ratios = _ratio_metrics(chosen)
    volumes = {
        "publicHall": Volume((hall_x0, 1, hall_z0), (hall_x1, wall_h, hall_z1), "public_service").to_dict(),
        "administrative": Volume((1, 1, backoffice_z0), (hall_x1 - 1, wall_h - 1, backoffice_z1), "administrative").to_dict(),
        "workingWing": Volume((wing_x0, 1, hall_z0), (wing_x1, wall_h, hall_z1), "working").to_dict(),
        "lightRepair": Volume((wing_x0 + 1, 1, repair_z0), (wing_x1 - 1, wall_h - 1, repair_z1), "repair").to_dict(),
        "warehouse": Volume((wing_x0 + 1, 1, warehouse_z0), (wing_x1 - 1, wall_h - 1, warehouse_z1), "freight_storage").to_dict(),
    }

    layout = {
        "generationMode": "first_principles",
        "bounds": _bounds(model),
        "anchors": anchors,
        "volumes": volumes,
        "resolvedParameters": {
            "bayCount": bays, "bayWidth": bay_w, "hallWidth": hall_w, "hallDepth": depth, "wallHeight": wall_h,
            "workingWingWidth": wing_w, "counterZ": counter_z, "publicEntranceSpan": [entrance_x0, entrance_x1],
            "repairOpeningZ": list(repair_open), "freightOpeningZ": list(freight_open), "mainRoofRise": roof_rise,
            "workingRoofRise": wing_rise, "roofOverhang": overhang, "publicWindowWidth": window_w,
            "generationAuthority": "semantic_program_constraint_optimization_grammar_fields",
        },
        "firstPrinciples": {
            "program": spec["program"],
            "referenceProfile": {
                "profileId": reference.get("profileId"), "status": reference.get("status"), "provenance": reference.get("provenance"),
            },
            "optimization": {
                "chosen": chosen, "score": optimization["score"], "candidateCount": optimization["candidateCount"],
                "feasibleCount": optimization["feasibleCount"], "terms": optimization["terms"], "ratioMetrics": ratios,
            },
            "semanticGraph": semantic_graph,
            "facadeGrammar": {
                "southWindowGroups": [list(v) for v in south_groups], "northWindowGroups": [list(v) for v in north_groups],
                "westWindowGroups": [list(v) for v in west_groups], "trace": grammar_trace,
                "descriptionLengthProxy": grammar_complexity,
            },
            "facadeLayers": {"claimCount": len(layer_claims), "conflicts": layer_issues},
            "roofFields": {
                "public": {"type": "two_eave_wavefront", "zRange": [public_field.z0, public_field.z1], "rise": public_field.rise,
                           "baseY": public_field.base_y, "pitchRatio": public_field.pitch_ratio, "ridgeStations": public_ridges},
                "working": {"type": "two_eave_wavefront", "zRange": [working_field.z0, working_field.z1], "rise": working_field.rise,
                            "baseY": working_field.base_y, "pitchRatio": working_field.pitch_ratio, "ridgeStations": working_ridges},
                "publicRafterCells": public_rafter_cells, "workingRafterCells": working_rafter_cells,
            },
            "spaceGraph": {
                "walkableNodeCount": len(walkable), "entranceReachableCount": len(distances),
                "entranceConnectedFraction": len(distances) / max(len(walkable), 1), "entranceToServiceDistance": service_distance,
                "entranceToServicePathStretch": path_stretch(service_distance, entrance_anchor, service_anchor),
                "entranceCloseness": closeness_centrality(walkable, entrance_anchor),
                "serviceCloseness": closeness_centrality(walkable, service_anchor),
                "entranceVisibilityCount": entrance_visibility, "entranceSeesService": entrance_sees_service,
            },
            "structuralGraph": {
                "nodeCount": len(structural_nodes), "supportCount": len(supports), "roofTargetCount": len(roof_targets),
                "unsupportedTargets": [list(v) for v in sorted(unsupported)], "claim": "structural_legibility_only_not_load_capacity",
            },
        },
        "paletteReview": spec.get("paletteReview", {"status": "provisional"}),
    }

    canonical = json.dumps(
        {"assetId": spec["assetId"], "layout": layout,
         "cells": [{"pos": [x, y, z], **cell.to_dict()} for (x, y, z), cell in sorted(model.cells.items())]},
        sort_keys=True, separators=(",", ":"),
    )
    summary = {
        "schemaVersion": "0.13", "compilerVersion": "0.13-first-principles", "assetId": spec["assetId"],
        "blockCount": len(model.cells), "layout": layout, "materialCounts": _counter(model, "material"),
        "roleCounts": _counter(model, "role"), "moduleBlockCounts": _counter(model, "module"),
        "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
        "validation": {"passed": not issues, "issues": issues},
    }
    return CompiledAsset(summary, model)
