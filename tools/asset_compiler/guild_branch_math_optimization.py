from __future__ import annotations

import collections
import copy
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
    normalized_square_error,
    path_stretch,
    ratio_in_bounds,
    reachable_distances,
    shortest_path_length,
    solve_discrete,
    unsupported_targets,
    visibility_count,
    visible_2d,
)
from guild_branch_continuity import compile_guild_branch_v11
from model import BlockState, CompiledAsset, SpecError


def _material(materials: dict[str, str], role: str, fallback: str | None = None) -> str:
    value = materials.get(role)
    if value is None and fallback is not None:
        value = materials.get(fallback)
    if not value:
        suffix = f" or fallback {fallback}" if fallback else ""
        raise SpecError(f"materialRoles.{role}{suffix} is required")
    return value


def _load_reference_profile(filename: str) -> dict[str, Any]:
    root = Path(__file__).resolve().parent / "reference_profiles"
    path = root / filename
    try:
        profile = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SpecError(f"unable to read mathematical optimization reference profile {filename}: {exc}") from exc
    if not isinstance(profile.get("ratios"), dict):
        raise SpecError("optimization reference profile must contain ratios")
    return profile


def _role_layer(role: str) -> str | None:
    if role == "structural_frame":
        return "structure"
    if role == "wall_infill":
        return "envelope"
    if role == "window":
        return "glazing"
    if role in {"hardware", "foundation"}:
        return "hardware"
    if role in {
        "board",
        "records",
        "desk",
        "counter",
        "seating",
        "storage",
        "workbench",
        "tool_storage",
    }:
        return "furnishing"
    return None


def _path_states(path: list[tuple[int, int]], frame_y: BlockState, frame_z: BlockState) -> list[BlockState]:
    states: list[BlockState] = []
    for i, point in enumerate(path):
        prev = path[i - 1] if i else None
        nxt = path[i + 1] if i + 1 < len(path) else None
        vertical = (prev is not None and prev[0] == point[0]) or (nxt is not None and nxt[0] == point[0])
        states.append(frame_y if vertical else frame_z)
    return states


def compile_guild_branch_v12(spec: dict[str, Any]) -> CompiledAsset:
    """Mathematical optimization stage over the accepted v0.11 architectural proof.

    v0.12 replaces heuristic-only authority with a bounded formal system:
      * finite-domain constrained optimization against provenance-labelled ratio targets;
      * attributed split-grammar authority for repeated facade openings;
      * explicit facade-layer incompatibility contracts;
      * graph-theoretic circulation and visibility metrics;
      * a roof field shared by weather skin details, rafters, ridge beams, and ties;
      * a 6-connected structural support graph from roof framing to grounded supports;
      * an MDL-style grammar-complexity proxy that penalizes one-off exceptions.

    The current ratio profile remains an internal working reference, not measured master-builder data.
    The mathematics is authoritative; the target distribution remains provisional until measured donor
    references are ingested.
    """

    if spec.get("schemaVersion") != "0.12":
        raise SpecError("v0.12 mathematical compiler requires schemaVersion=0.12")
    cfg = spec.get("mathematicalOptimization")
    if not isinstance(cfg, dict):
        raise SpecError("v0.12 requires mathematicalOptimization object")

    materials = spec.get("materialRoles", {})
    required = {
        "structuralFrame",
        "foundation",
        "window",
        "roof",
        "roofStair",
        "roofSlab",
        "exteriorTrimSlab",
        "masonryTrimSlab",
    }
    missing = sorted(required - set(materials))
    if missing:
        raise SpecError(f"missing v0.12 material roles: {', '.join(missing)}")

    structure = spec.get("structure", {})
    bay_count = int(structure.get("bayCount", 0))
    bay_width = int(structure.get("bayWidth", 0))
    wall_height = int(structure.get("wallHeight", 0))
    depth = int(structure.get("mainDepth", 0))
    wing_width = int(structure.get("workingWingWidth", 0))
    if min(bay_count, bay_width, wall_height, depth, wing_width) <= 0:
        raise SpecError("v0.12 requires positive structural dimensions")

    profile_name = str(cfg.get("referenceProfile", "guild_branch_temperate_small_working_reference_v0.1.json"))
    profile = _load_reference_profile(profile_name)
    ratios = profile["ratios"]
    for key in ("mainRoofRiseToDepth", "roofOverhangToDepth", "averagePublicWindowWidthToBayWidth"):
        if key not in ratios:
            raise SpecError(f"reference profile missing optimization ratio {key}")

    domains = cfg.get("candidateDomains", {})
    rises = [int(v) for v in domains.get("mainRoofRise", [3, 4, 5])]
    overhangs = [int(v) for v in domains.get("roofOverhang", [0, 1, 2])]
    window_widths = [int(v) for v in domains.get("publicWindowWidth", [2, 3])]
    if not rises or not overhangs or not window_widths:
        raise SpecError("v0.12 optimization domains may not be empty")

    candidates = [
        {"mainRoofRise": rise, "roofOverhang": overhang, "publicWindowWidth": window_width}
        for rise, overhang, window_width in itertools.product(rises, overhangs, window_widths)
    ]

    def ratio_value(candidate: dict[str, int], key: str) -> float:
        if key == "mainRoofRiseToDepth":
            return candidate["mainRoofRise"] / depth
        if key == "roofOverhangToDepth":
            return candidate["roofOverhang"] / depth
        if key == "averagePublicWindowWidthToBayWidth":
            return candidate["publicWindowWidth"] / bay_width
        raise KeyError(key)

    hard_rules = []
    for key in ("mainRoofRiseToDepth", "roofOverhangToDepth", "averagePublicWindowWidthToBayWidth"):
        hard_rules.append((f"{key}.within_reference_bounds", lambda c, key=key: ratio_in_bounds(ratio_value(c, key), ratios[key])))
    hard_rules.extend(
        [
            ("window.leaves_structural_jamb", lambda c: 1 <= c["publicWindowWidth"] <= bay_width - 2),
            ("roof.rise_positive", lambda c: c["mainRoofRise"] >= 1),
            ("roof.overhang_bounded", lambda c: 0 <= c["roofOverhang"] <= 2),
        ]
    )

    weights = cfg.get("objectiveWeights", {})
    soft_terms = []
    for key in ("mainRoofRiseToDepth", "roofOverhangToDepth", "averagePublicWindowWidthToBayWidth"):
        entry = ratios[key]
        soft_terms.append(
            (
                f"{key}.target_fit",
                float(weights.get(key, 1.0)),
                lambda c, key=key, entry=entry: normalized_square_error(
                    ratio_value(c, key), float(entry["target"]), float(entry["min"]), float(entry["max"])
                ),
            )
        )

    optimization = solve_discrete(candidates, hard_rules, soft_terms)
    chosen = optimization["chosen"]

    base_spec = copy.deepcopy(spec)
    base_spec["schemaVersion"] = "0.11"
    base_spec["structure"]["roof"]["mainRise"] = int(chosen["mainRoofRise"])
    base_spec["structure"]["roof"]["overhang"] = int(chosen["roofOverhang"])
    compiled = compile_guild_branch_v11(base_spec)
    if not compiled.summary["validation"]["passed"]:
        return compiled

    model = compiled.model
    summary = compiled.summary
    layout = summary["layout"]
    rp = layout["resolvedParameters"]

    bay_count = int(rp["bayCount"])
    bay_width = int(rp["bayWidth"])
    hall_width = int(rp["hallWidth"])
    depth = int(rp["hallDepth"])
    wall_height = int(rp["wallHeight"])
    wing_width = int(rp["workingWingWidth"])
    counter_z = int(rp["counterZ"])
    entrance_bay = int(rp["publicEntranceBay"])
    door_x0, door_x1 = map(int, rp["publicEntranceSpan"])
    repair_a, repair_b = map(int, rp["repairOpeningZ"])
    freight_a, freight_b = map(int, rp["freightOpeningZ"])

    hall_z0 = 4
    hall_z1 = hall_z0 + depth - 1
    wing_x0 = hall_width
    wing_x1 = hall_width + wing_width - 1
    roof_base = wall_height + 1
    main_rise = int(chosen["mainRoofRise"])
    overhang = int(chosen["roofOverhang"])
    working_rise = int(spec["structure"].get("roof", {}).get("workingWingRise", 2))
    public_window_width = int(chosen["publicWindowWidth"])

    frame_y = BlockState.of(_material(materials, "structuralFrame"), axis="y")
    frame_x = BlockState.of(_material(materials, "structuralFrame"), axis="x")
    frame_z = BlockState.of(_material(materials, "structuralFrame"), axis="z")
    foundation = BlockState.of(_material(materials, "foundation"))
    roof_slab = BlockState.of(_material(materials, "roofSlab", "roof"), type="bottom")
    roof_north = BlockState.of(_material(materials, "roofStair", "roof"), facing="north", half="bottom", shape="straight")
    roof_south = BlockState.of(_material(materials, "roofStair", "roof"), facing="south", half="bottom", shape="straight")

    south_groups, south_trace = facade_bay_grammar(
        bay_count, bay_width, public_window_width, excluded_bays={entrance_bay}
    )
    north_groups, north_trace = facade_bay_grammar(bay_count, bay_width, public_window_width)
    grammar_trace = [{"face": "south", **item} for item in south_trace] + [
        {"face": "north", **item} for item in north_trace
    ]
    grammar_mdl = grammar_description_length_proxy(grammar_trace)

    current_windows = layout.get("continuityResolution", {}).get("windowsAndWalls", {})
    current_south = [tuple(map(int, g)) for g in current_windows.get("southWindowGroups", [])]
    current_north = [tuple(map(int, g)) for g in current_windows.get("northWindowGroups", [])]
    west_groups = [tuple(map(int, g)) for g in current_windows.get("westWindowGroups", [])]
    grammar_geometry_matches = current_south == south_groups and current_north == north_groups

    claims: list[LayerClaim] = []
    public_posts = sorted(set(range(0, hall_width, bay_width)) | {hall_width - 1})
    for wall_z, pane_z, groups, face in (
        (hall_z1, hall_z1 - 1, south_groups, "south"),
        (hall_z0, hall_z0 + 1, north_groups, "north"),
    ):
        for x in public_posts:
            for y in (3, 4):
                claims.append(LayerClaim((x, y, wall_z), "structure", f"{face}.bay_post"))
        for a, b in groups:
            for x in range(a, b + 1):
                for y in (3, 4):
                    claims.append(LayerClaim((x, y, wall_z), "opening_void", f"{face}.window_void"))
                    claims.append(LayerClaim((x, y, pane_z), "glazing", f"{face}.window_glazing"))
                    for pos in ((x, y, wall_z), (x, y, pane_z)):
                        actual = model.cells.get(pos)
                        if actual is not None:
                            layer = _role_layer(actual.role)
                            if layer:
                                claims.append(LayerClaim(pos, layer, f"actual.{actual.module or actual.role}"))
    for a, b in west_groups:
        for z in range(a, b + 1):
            for y in (3, 4):
                claims.append(LayerClaim((0, y, z), "opening_void", "west.window_void"))
                claims.append(LayerClaim((1, y, z), "glazing", "west.window_glazing"))
                for pos in ((0, y, z), (1, y, z)):
                    actual = model.cells.get(pos)
                    if actual is not None:
                        layer = _role_layer(actual.role)
                        if layer:
                            claims.append(LayerClaim(pos, layer, f"actual.{actual.module or actual.role}"))
    facade_conflicts = layer_conflicts(claims)

    roof_cfg = cfg.get("roofField", {})
    main_field = TwoEaveGableField(hall_z0, hall_z1, main_rise, roof_base)
    working_field = TwoEaveGableField(0, hall_z1, working_rise, roof_base)

    old_roof_modules = {
        "public_roof_eave_north_v11",
        "public_roof_eave_south_v11",
        "public_roof_ridge_cap_v11",
        "public_roof_tie_beam_v11",
        "working_roof_eave_north_v11",
        "working_roof_eave_south_v11",
        "working_roof_ridge_cap_v11",
    }
    for pos, cell in list(model.cells.items()):
        module = cell.module or ""
        if module in old_roof_modules or module in {"public_roof_rafter_v11", "working_roof_rafter_v11"}:
            model.clear(*pos)

    for x in range(hall_width):
        model.set(x, wall_height, hall_z0, "structural_frame", frame_x, "public_wall_plate_v12")
        model.set(x, wall_height, hall_z1, "structural_frame", frame_x, "public_wall_plate_v12")

    working_posts = sorted({wing_x0, wing_x0 + wing_width // 2, wing_x1})
    for x in working_posts:
        for z in (0, hall_z1):
            model.set(x, 1, z, "foundation", foundation, "working_post_footing_v12")
            for y in range(2, wall_height + 1):
                model.set(x, y, z, "structural_frame", frame_y, "working_eave_post_v12")
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, wall_height, 0, "structural_frame", frame_x, "working_wall_plate_v12")
        model.set(x, wall_height, hall_z1, "structural_frame", frame_x, "working_wall_plate_v12")

    for x in range(hall_width):
        model.set(x, main_field.height(hall_z0 - overhang), hall_z0 - overhang, "roof", roof_north, "public_roof_eave_v12")
        model.set(x, main_field.height(hall_z1 + overhang), hall_z1 + overhang, "roof", roof_south, "public_roof_eave_v12")
    public_ridge_zs = main_field.ridge_stations()
    public_ridge_z = public_ridge_zs[len(public_ridge_zs) // 2]
    public_ridge_y = main_field.height(public_ridge_z)
    for x in range(hall_width):
        model.set(x, public_ridge_y + 1, public_ridge_z, "roof", roof_slab, "public_roof_ridge_cap_v12")

    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, working_field.height(-overhang), -overhang, "roof", roof_north, "working_roof_eave_v12")
        model.set(x, working_field.height(hall_z1 + overhang), hall_z1 + overhang, "roof", roof_south, "working_roof_eave_v12")
    working_ridge_zs = working_field.ridge_stations()
    working_ridge_z = working_ridge_zs[len(working_ridge_zs) // 2]
    working_ridge_y = working_field.height(working_ridge_z)
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, working_ridge_y + 1, working_ridge_z, "roof", roof_slab, "working_roof_ridge_cap_v12")

    public_path_2d = digital_connected_path([(z, main_field.height(z) - 1) for z in range(hall_z0, hall_z1 + 1)])
    working_path_2d = digital_connected_path([(z, working_field.height(z) - 1) for z in range(0, hall_z1 + 1)])
    if not is_four_connected(public_path_2d) or not is_four_connected(working_path_2d):
        raise SpecError("roof digitalization failed to produce connected rafter paths")

    public_rafter_cells: list[list[int]] = []
    public_states = _path_states(public_path_2d, frame_y, frame_z)
    for x in public_posts:
        for (z, y), state in zip(public_path_2d, public_states):
            if z in (hall_z0, hall_z1) and y == wall_height:
                continue
            model.set(x, y, z, "structural_frame", state, "public_roof_rafter_v12")
            public_rafter_cells.append([x, y, z])

    working_rafter_cells: list[list[int]] = []
    working_states = _path_states(working_path_2d, frame_y, frame_z)
    for x in working_posts:
        for (z, y), state in zip(working_path_2d, working_states):
            if z in (0, hall_z1) and y == wall_height:
                continue
            model.set(x, y, z, "structural_frame", state, "working_roof_rafter_v12")
            working_rafter_cells.append([x, y, z])

    public_ridge_frame_y = public_ridge_y - 1
    for x in range(hall_width):
        model.set(x, public_ridge_frame_y, public_ridge_z, "structural_frame", frame_x, "public_roof_ridge_beam_v12")
    working_ridge_frame_y = working_ridge_y - 1
    for x in range(wing_x0, wing_x1 + 1):
        model.set(x, working_ridge_frame_y, working_ridge_z, "structural_frame", frame_x, "working_roof_ridge_beam_v12")

    public_ties = sorted({hall_z0 + round((hall_z1 - hall_z0) * q) for q in (0.25, 0.5, 0.75)})
    for z in public_ties:
        for x in range(hall_width):
            model.set(x, wall_height, z, "structural_frame", frame_x, "public_roof_tie_beam_v12")
    working_ties = sorted({round(hall_z1 * q) for q in (1 / 3, 2 / 3)})
    for z in working_ties:
        for x in range(wing_x0, wing_x1 + 1):
            model.set(x, wall_height, z, "structural_frame", frame_x, "working_roof_tie_beam_v12")

    structural_nodes = {pos for pos, cell in model.cells.items() if cell.role in {"structural_frame", "foundation"}}
    supports = {
        pos
        for pos in structural_nodes
        if pos[1] <= 1 or (model.cells[pos].role == "foundation" and pos[1] <= 2)
    }
    roof_targets = {
        pos
        for pos, cell in model.cells.items()
        if cell.module
        in {
            "public_roof_rafter_v12",
            "working_roof_rafter_v12",
            "public_roof_ridge_beam_v12",
            "working_roof_ridge_beam_v12",
            "public_roof_tie_beam_v12",
            "working_roof_tie_beam_v12",
        }
    }
    unsupported = unsupported_targets(structural_nodes, supports, roof_targets)

    space_cfg = cfg.get("spaceGraph", {})
    walkable: set[tuple[int, int]] = set()
    for x in range(1, hall_width - 1):
        for z in range(hall_z0 + 1, hall_z1):
            floor = model.cells.get((x, 1, z))
            if floor is None:
                continue
            if model.cells.get((x, 2, z)) is None and model.cells.get((x, 3, z)) is None:
                walkable.add((x, z))

    entrance_candidates = [(x, hall_z1 - 1) for x in range(door_x0, door_x1 + 1)]
    service = (int(layout["anchors"]["SERVICE_COUNTER"][0]), int(layout["anchors"]["SERVICE_COUNTER"][2]))
    path_options = [
        (shortest_path_length(walkable, start, service), start)
        for start in entrance_candidates
        if start in walkable
    ]
    path_options = [item for item in path_options if item[0] is not None]
    if path_options:
        path_options.sort(key=lambda item: (int(item[0]), item[1]))
        service_distance, entrance = path_options[0]
    else:
        service_distance, entrance = None, entrance_candidates[0]

    opaque: set[tuple[int, int]] = set()
    for x in range(1, hall_width - 1):
        for z in range(hall_z0 + 1, hall_z1):
            cells = [model.cells.get((x, y, z)) for y in (2, 3)]
            if any(cell is not None and cell.role not in {"window", "door", "lighting"} for cell in cells):
                opaque.add((x, z))

    entrance_visible_service = entrance in walkable and service in walkable and visible_2d(opaque, entrance, service)
    reachable = reachable_distances(walkable, entrance) if entrance in walkable else {}
    connected_fraction = len(reachable) / max(len(walkable), 1)
    entrance_closeness = closeness_centrality(walkable, entrance)
    service_closeness = closeness_centrality(walkable, service)
    entrance_visibility = visibility_count(walkable, opaque, entrance)
    stretch = path_stretch(service_distance, entrance, service)

    math_layout = {
        "stage": "constraint_topology_field_optimization",
        "solver": {**optimization, "quality": 1.0 / (1.0 + float(optimization["score"]))},
        "referenceProfile": {
            "profileId": profile.get("profileId"),
            "status": profile.get("status"),
            "provenance": profile.get("provenance"),
        },
        "shapeGrammar": {
            "southWindowGroups": [list(g) for g in south_groups],
            "northWindowGroups": [list(g) for g in north_groups],
            "trace": grammar_trace,
            "descriptionLengthProxy": grammar_mdl,
            "upstreamGeometryMatchesSolvedGrammar": grammar_geometry_matches,
        },
        "facadeLayers": {
            "claimCount": len(claims),
            "conflicts": facade_conflicts,
            "contract": [
                "structure !~ opening_void",
                "envelope !~ opening_void",
                "glazing !~ structure|envelope|hardware|furnishing",
            ],
        },
        "roofField": {
            "type": str(roof_cfg.get("type", "two_eave_wavefront_gable")),
            "generalizationTarget": "straight_skeleton_or_weighted_wavefront_for_nonrectangular_footprints",
            "public": {
                "zRange": [hall_z0, hall_z1],
                "rise": main_rise,
                "baseY": roof_base,
                "pitchRatio": main_field.pitch_ratio,
                "ridgeStations": public_ridge_zs,
                "profile": [list(item) for item in main_field.profile(overhang)],
                "connectedRafterPath": [list(item) for item in public_path_2d],
                "tieStations": public_ties,
            },
            "working": {
                "zRange": [0, hall_z1],
                "rise": working_rise,
                "baseY": roof_base,
                "pitchRatio": working_field.pitch_ratio,
                "ridgeStations": working_ridge_zs,
                "profile": [list(item) for item in working_field.profile(overhang)],
                "connectedRafterPath": [list(item) for item in working_path_2d],
                "tieStations": working_ties,
            },
        },
        "structuralGraph": {
            "nodeCount": len(structural_nodes),
            "supportCount": len(supports),
            "roofTargetCount": len(roof_targets),
            "unsupportedRoofTargets": [list(pos) for pos in sorted(unsupported)],
            "criterion": "6_connected_path_from_roof_frame_to_grounded_support",
            "claim": "structural_legibility_not_engineering_load_capacity",
        },
        "spaceGraph": {
            "nodeCount": len(walkable),
            "entranceNode": list(entrance),
            "serviceNode": list(service),
            "entranceToServiceDistance": service_distance,
            "entranceToServiceStretch": stretch,
            "entranceSeesService": entrance_visible_service,
            "connectedFractionFromEntrance": connected_fraction,
            "entranceCloseness": entrance_closeness,
            "serviceCloseness": service_closeness,
            "entranceVisibilityCount": entrance_visibility,
            "metricModel": "four_neighbor_walkability_plus_grid_line_of_sight",
        },
    }
    layout["mathematicalOptimization"] = math_layout
    layout["resolvedParameters"].update(
        {
            "runtimeCorrectionVersion": "0.12",
            "mathematicalOptimizationStage": "explicit",
            "optimizedMainRoofRise": main_rise,
            "optimizedRoofOverhang": overhang,
            "optimizedPublicWindowWidth": public_window_width,
            "roofFieldTreatment": "shared_wavefront_field_exterior_and_interior",
            "structuralConnectivityTreatment": "six_connected_support_graph",
            "spaceAnalysisTreatment": "walkability_visibility_graph",
            "facadeGrammarTreatment": "attributed_split_grammar_with_layer_contracts",
        }
    )

    issues: list[str] = []
    requested = set(spec.get("requestedAnchors", []))
    missing_anchors = sorted(requested - set(layout["anchors"]))
    if missing_anchors:
        issues.append(f"missing requested anchors: {', '.join(missing_anchors)}")
    if not grammar_geometry_matches:
        issues.append(
            f"upstream window geometry does not match solved grammar: south {current_south}->{south_groups}, north {current_north}->{north_groups}"
        )
    if facade_conflicts:
        issues.append(f"facade layer contract has {len(facade_conflicts)} conflict(s)")
    if unsupported:
        issues.append(f"structural graph has {len(unsupported)} unsupported roof-frame target(s)")
    if service_distance is None:
        issues.append("space graph cannot route public entrance to service counter")
    max_stretch = float(space_cfg.get("maxEntranceServiceStretch", 1.15))
    if stretch > max_stretch:
        issues.append(f"entrance-service path stretch {stretch:.3f} exceeds {max_stretch:.3f}")
    if bool(space_cfg.get("requireEntranceSeesService", True)) and not entrance_visible_service:
        issues.append("entrance does not have line of sight to service-counter approach")
    min_connected = float(space_cfg.get("minConnectedFraction", 0.80))
    if connected_fraction < min_connected:
        issues.append(f"public walkable connected fraction {connected_fraction:.3f} below {min_connected:.3f}")
    if not public_path_2d or not working_path_2d:
        issues.append("roof field produced empty rafter path")
    if not is_four_connected(public_path_2d) or not is_four_connected(working_path_2d):
        issues.append("roof field rafter digitalization is not four-connected")
    if profile.get("provenance", {}).get("externalMasterBuilderMeasurement") is not False:
        issues.append("v0.12 bootstrap specimen expected an explicitly internal working reference profile")

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
    summary.update(
        {
            "schemaVersion": "0.12",
            "compilerVersion": "0.12",
            "assetId": spec["assetId"],
            "layout": layout,
            "blockCount": len(model.cells),
            "materialCounts": dict(sorted(collections.Counter(c.state.canonical() for c in model.cells.values()).items())),
            "roleCounts": dict(sorted(collections.Counter(c.role for c in model.cells.values()).items())),
            "moduleBlockCounts": dict(sorted(collections.Counter(c.module for c in model.cells.values() if c.module).items())),
            "digestSha256": hashlib.sha256(canonical.encode("utf-8")).hexdigest(),
            "validation": {"passed": not issues, "issues": issues},
        }
    )
    return CompiledAsset(summary, model)
