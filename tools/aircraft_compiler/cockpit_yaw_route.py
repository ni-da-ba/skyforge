from __future__ import annotations

import hashlib
import heapq
import json
from typing import Any


class CockpitYawRouteError(ValueError):
    pass


DIR_VECTORS: dict[str, tuple[int, int, int]] = {
    "east": (1, 0, 0),
    "west": (-1, 0, 0),
    "south": (0, 0, 1),
    "north": (0, 0, -1),
    "up": (0, 1, 0),
    "down": (0, -1, 0),
}
OPPOSITE = {"east": "west", "west": "east", "south": "north", "north": "south", "up": "down", "down": "up"}
AXIS = {"east": "x", "west": "x", "up": "y", "down": "y", "south": "z", "north": "z"}
AXIS_DIRECTION = {"east": 1, "up": 1, "south": 1, "west": -1, "down": -1, "north": -1}
HORIZONTAL_DIRECTIONS = ("east", "north", "south", "west")


def _coord(value: Any, label: str = "coordinate") -> tuple[int, int, int]:
    if not isinstance(value, (list, tuple)) or len(value) != 3:
        raise CockpitYawRouteError(f"{label} must be a 3-vector, got {value!r}")
    return tuple(int(v) for v in value)


def _add(a: tuple[int, int, int], b: tuple[int, int, int]) -> tuple[int, int, int]:
    return tuple(a[i] + b[i] for i in range(3))


def _move(a: tuple[int, int, int], direction: str) -> tuple[int, int, int]:
    return _add(a, DIR_VECTORS[direction])


def _direction(a: tuple[int, int, int], b: tuple[int, int, int]) -> str:
    diff = tuple(b[i] - a[i] for i in range(3))
    for name, vector in DIR_VECTORS.items():
        if diff == vector:
            return name
    raise CockpitYawRouteError(f"coordinates are not face-adjacent: {a!r} -> {b!r}")


def _third_axis(a: str, b: str) -> str:
    if a == b:
        raise CockpitYawRouteError(f"gearbox turn requires distinct axes, got {a!r}")
    return ({"x", "y", "z"} - {a, b}).pop()


def _gearbox_modifier(source_direction: str, output_direction: str) -> int:
    """Create 6.0.10 RotationPropagator.getAxisModifier for a GearboxBlockEntity."""
    source_axis = AXIS[source_direction]
    output_axis = AXIS[output_direction]
    if output_axis == source_axis:
        return 1 if output_direction == source_direction else -1
    return -1 if AXIS_DIRECTION[output_direction] == AXIS_DIRECTION[source_direction] else 1


def _wheel_generated_sign(state: dict[str, Any]) -> int:
    facing = str(state.get("facing", ""))
    floor = str(state.get("on_floor", "")).lower() == "true"
    if facing not in {"north", "south", "east", "west"}:
        raise CockpitYawRouteError(f"unsupported Steering Wheel facing {facing!r}")
    # SteeringWheelBlockEntity.updateTargetAngle(): NORTH/WEST == floor negates logical RPM.
    return -1 if ((facing in {"north", "west"}) == floor) else 1


def _placement(kind: str, lattice: tuple[int, int, int], resource: str, state: dict[str, Any], role: str) -> dict[str, Any]:
    return {
        "mode": "add_main",
        "kind": kind,
        "role": role,
        "lattice": list(lattice),
        "resourceId": resource,
        "blockState": dict(state),
    }


def _reconstruct(
    manifest: dict[str, Any], powertrain: dict[str, Any], yaw: dict[str, Any]
) -> tuple[dict[tuple[int, int, int], dict[str, Any]], set[tuple[int, int, int]], set[tuple[int, int, int]], set[tuple[int, int, int]]]:
    effective: dict[tuple[int, int, int], dict[str, Any]] = {}
    propeller: set[tuple[int, int, int]] = set()
    rudder: set[tuple[int, int, int]] = set()
    for raw in manifest.get("placements", []):
        p = dict(raw)
        c = _coord(p.get("lattice"), "manifest lattice")
        if c in effective:
            raise CockpitYawRouteError(f"duplicate manifest coordinate {c!r}")
        effective[c] = p
        if p.get("kind") in {"propeller_hub", "propeller_sail"}:
            propeller.add(c)

    for raw in powertrain.get("placements", []):
        p = dict(raw)
        c = _coord(p.get("lattice"), "powertrain lattice")
        mode = str(p.get("mode", ""))
        if mode == "replace":
            if c not in effective:
                raise CockpitYawRouteError(f"powertrain replacement source missing at {c!r}")
            effective[c] = p
        elif mode == "add":
            if c in effective:
                raise CockpitYawRouteError(f"powertrain addition collides at {c!r}")
            effective[c] = p
        else:
            raise CockpitYawRouteError(f"unsupported powertrain mode {mode!r}")

    for raw in yaw.get("placements", []):
        p = dict(raw)
        c = _coord(p.get("lattice"), "yaw lattice")
        mode = str(p.get("mode", ""))
        if mode == "remove_main":
            if c not in effective:
                raise CockpitYawRouteError(f"yaw removal source missing at {c!r}")
            effective.pop(c)
        elif mode == "add_main":
            if c in effective:
                raise CockpitYawRouteError(f"yaw parent addition collides at {c!r}")
            effective[c] = p
        elif mode == "add_control_child":
            if c in effective:
                raise CockpitYawRouteError(f"yaw child addition collides at {c!r}")
            effective[c] = p
            rudder.add(c)
        else:
            raise CockpitYawRouteError(f"unsupported yaw mode {mode!r}")

    main = set(effective) - propeller - rudder
    return effective, main, propeller, rudder


def _seat_coordinate(manifest: dict[str, Any]) -> tuple[int, int, int]:
    seats = [_coord(p.get("lattice")) for p in manifest.get("placements", []) if p.get("kind") == "pilot_occupancy_station"]
    if len(seats) != 1:
        raise CockpitYawRouteError(f"expected exactly one pilot occupancy station, got {len(seats)}")
    return seats[0]


def _search_horizontal_route(
    start: tuple[int, int, int],
    goal: tuple[int, int, int],
    obstacles: set[tuple[int, int, int]],
    bounds: dict[str, int],
    wheel_sign: int,
    expected_extra_sign: int,
    turn_penalty: int,
) -> tuple[int, list[tuple[int, int, int]], int, list[dict[str, Any]]] | None:
    if start[1] != goal[1]:
        raise CockpitYawRouteError("v0.18 bounded route search requires start and goal on one horizontal service plane")
    if start in obstacles or goal in obstacles:
        return None

    min_x, max_x = int(bounds["minX"]), int(bounds["maxX"])
    min_z, max_z = int(bounds["minZ"]), int(bounds["maxZ"])

    # State is (current position, incoming travel direction, sign arriving at current).
    # The start gearbox applies its modifier when the first move is chosen.
    heap: list[tuple[Any, ...]] = []
    best: dict[tuple[tuple[int, int, int], str, int], int] = {}
    for first in HORIZONTAL_DIRECTIONS:
        nxt = _move(start, first)
        if nxt in obstacles or not (min_x <= nxt[0] <= max_x and min_z <= nxt[2] <= max_z):
            continue
        if nxt[1] != start[1]:
            continue
        sign = wheel_sign * _gearbox_modifier("up", first)
        path = (start, nxt)
        state = (nxt, first, sign)
        cost = 2
        best[state] = cost
        heapq.heappush(heap, (cost, 0, path, nxt, first, sign, ()))

    while heap:
        cost, turns, path, current, incoming, sign, trace_tuple = heapq.heappop(heap)
        state = (current, incoming, sign)
        if cost != best.get(state):
            continue

        if current == goal:
            final_sign = sign * _gearbox_modifier(OPPOSITE[incoming], "up") * -1  # tail small cog -> Swivel extra cog
            if final_sign == expected_extra_sign:
                trace = list(trace_tuple)
                trace.append({
                    "at": list(goal),
                    "primitive": "terminal_gearbox",
                    "sourceDirection": OPPOSITE[incoming],
                    "outputDirection": "up",
                    "modifier": _gearbox_modifier(OPPOSITE[incoming], "up"),
                    "signAfter": sign * _gearbox_modifier(OPPOSITE[incoming], "up"),
                })
                trace.append({"at": list(_move(goal, "up")), "primitive": "small_cog_to_swivel_mesh", "modifier": -1, "signAfter": final_sign})
                return cost, list(path), final_sign, trace
            continue

        for outgoing in HORIZONTAL_DIRECTIONS:
            if outgoing == OPPOSITE[incoming]:
                continue
            nxt = _move(current, outgoing)
            if nxt in path or nxt in obstacles:
                continue
            if not (min_x <= nxt[0] <= max_x and min_z <= nxt[2] <= max_z) or nxt[1] != start[1]:
                continue
            turned = AXIS[outgoing] != AXIS[incoming]
            next_sign = sign
            trace = list(trace_tuple)
            next_turns = turns
            extra = 1
            if turned:
                modifier = _gearbox_modifier(OPPOSITE[incoming], outgoing)
                next_sign *= modifier
                next_turns += 1
                extra += turn_penalty
                trace.append({
                    "at": list(current),
                    "primitive": "turn_gearbox",
                    "sourceDirection": OPPOSITE[incoming],
                    "outputDirection": outgoing,
                    "modifier": modifier,
                    "signAfter": next_sign,
                })
            next_cost = cost + extra
            next_path = path + (nxt,)
            next_state = (nxt, outgoing, next_sign)
            if next_cost < best.get(next_state, 10**12):
                best[next_state] = next_cost
                heapq.heappush(heap, (next_cost, next_turns, next_path, nxt, outgoing, next_sign, tuple(trace)))
    return None


def _route_placements(
    path: list[tuple[int, int, int]],
    wheel: tuple[int, int, int],
    wheel_state: dict[str, Any],
    drive_cog: tuple[int, int, int],
    resources: dict[str, str],
) -> list[dict[str, Any]]:
    if len(path) < 2:
        raise CockpitYawRouteError("route path must contain at least start and terminal gearbox")
    placements: list[dict[str, Any]] = []
    placements.append(_placement("cockpit_yaw_control_source", wheel, resources["steeringWheel"], wheel_state, "cockpit_steering_wheel"))

    first_dir = _direction(path[0], path[1])
    start_axis = _third_axis("y", AXIS[first_dir])
    placements.append(_placement("cockpit_yaw_route_gearbox", path[0], resources["gearbox"], {"axis": start_axis}, "cockpit_drop_gearbox"))

    for i in range(1, len(path) - 1):
        prev_dir = _direction(path[i - 1], path[i])
        next_dir = _direction(path[i], path[i + 1])
        if AXIS[prev_dir] == AXIS[next_dir]:
            placements.append(_placement("cockpit_yaw_route_shaft", path[i], resources["shaft"], {"axis": AXIS[next_dir]}, f"route_shaft_{i:02d}"))
        else:
            axis = _third_axis(AXIS[prev_dir], AXIS[next_dir])
            placements.append(_placement("cockpit_yaw_route_gearbox", path[i], resources["gearbox"], {"axis": axis}, f"route_turn_gearbox_{i:02d}"))

    incoming = _direction(path[-2], path[-1])
    terminal_axis = _third_axis(AXIS[incoming], "y")
    placements.append(_placement("cockpit_yaw_route_gearbox", path[-1], resources["gearbox"], {"axis": terminal_axis}, "tail_rise_gearbox"))
    placements.append(_placement("cockpit_yaw_route_drive_cog", drive_cog, resources["cogwheel"], {"axis": "y"}, "tail_swivel_drive_cog"))
    return placements


def lower_cockpit_yaw_route(
    manifest: dict[str, Any],
    powertrain: dict[str, Any],
    yaw: dict[str, Any],
    steering: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise CockpitYawRouteError("v0.18 requires probe placement manifest v0.9")
    if powertrain.get("schemaVersion") != "aircraft-powertrain-ir-0.12":
        raise CockpitYawRouteError("v0.18 requires powertrain IR v0.12")
    if yaw.get("schemaVersion") != "aircraft-yaw-control-ir-0.13.1":
        raise CockpitYawRouteError("v0.18 requires corrected yaw-control IR v0.13.1")
    if steering.get("schemaVersion") != "aircraft-steering-control-ir-0.17":
        raise CockpitYawRouteError("v0.18 requires Steering Wheel control IR v0.17")
    if profile.get("schemaVersion") != "aircraft-cockpit-yaw-route-profile-0.18":
        raise CockpitYawRouteError("v0.18 requires aircraft-cockpit-yaw-route-profile-0.18")
    expected_steering_digest = str(profile.get("sourceSteeringControlDigestSha256", ""))
    if expected_steering_digest and steering.get("digestSha256") != expected_steering_digest:
        raise CockpitYawRouteError("v0.18 source Steering Wheel digest mismatch")
    if not steering.get("validation", {}).get("passed", False):
        raise CockpitYawRouteError("v0.18 requires statically accepted v0.17 Steering Wheel contract")

    effective, main, propeller, rudder = _reconstruct(manifest, powertrain, yaw)
    seat = _seat_coordinate(manifest)
    drive_cog = _coord(steering.get("driveCogCoordinate"), "v0.17 drive cog")
    swivel = _coord(steering.get("swivelBearingCoordinate"), "v0.17 swivel")
    if drive_cog != (swivel[0], swivel[1], swivel[2] + 1):
        raise CockpitYawRouteError("v0.18 preserves the accepted +Z small-cog Swivel interface")
    if drive_cog in effective:
        raise CockpitYawRouteError(f"production tail drive-cog coordinate is already occupied: {drive_cog!r}")

    resources = dict(profile.get("resources", {}))
    exact_resources = {
        "steeringWheel": "simulated:steering_wheel",
        "gearbox": "create:gearbox",
        "shaft": "create:shaft",
        "cogwheel": "create:cogwheel",
    }
    if resources != exact_resources:
        raise CockpitYawRouteError(f"v0.18 exact route resources mismatch: {resources!r}")

    forbidden_empty = {_coord(v, "forbidden coordinate") for v in profile.get("forbiddenCoordinates", [])}
    forbidden_empty.update(_coord(v, "air-gap coordinate") for v in yaw.get("airGapCoordinates", []))
    obstacles = set(effective) | forbidden_empty | propeller | rudder
    bounds = dict(profile.get("routeBounds", {}))
    required_bound_keys = {"minX", "maxX", "minZ", "maxZ"}
    if set(bounds) != required_bound_keys:
        raise CockpitYawRouteError("v0.18 routeBounds must contain minX/maxX/minZ/maxZ")
    turn_penalty = int(profile.get("turnPenalty", 0))
    if turn_penalty < 0:
        raise CockpitYawRouteError("turnPenalty must be nonnegative")
    expected_extra_sign = int(profile.get("expectedSwivelExtraCogSignForPositiveWheelCommand", 0))
    if expected_extra_sign not in {-1, 1}:
        raise CockpitYawRouteError("expected Swivel extra-cog sign must be +/-1")

    candidates = list(profile.get("pilotWheelCandidates", []))
    if not candidates:
        raise CockpitYawRouteError("v0.18 requires pilot Wheel candidates")
    accepted: list[tuple[Any, ...]] = []
    rejected: list[dict[str, Any]] = []
    for index, raw in enumerate(candidates):
        candidate = dict(raw)
        name = str(candidate.get("name", f"candidate_{index}"))
        offset = _coord(candidate.get("offsetFromPilotSeat"), f"{name} offset")
        wheel = _add(seat, offset)
        state = dict(candidate.get("blockState", {}))
        if str(state.get("on_floor", "")).lower() != "true":
            rejected.append({"name": name, "reason": "production route candidates must be floor-mounted for a downward shaft"})
            continue
        if str(state.get("waterlogged", "")).lower() != "false":
            rejected.append({"name": name, "reason": "production cockpit Steering Wheel must be dry"})
            continue
        toward_seat = tuple(seat[i] - wheel[i] for i in range(3))
        facing_vector = DIR_VECTORS.get(str(state.get("facing", "")))
        if facing_vector is None or toward_seat != facing_vector:
            rejected.append({"name": name, "reason": "Steering Wheel facing must point directly toward the pilot seat"})
            continue
        start = _move(wheel, "down")
        goal = _move(drive_cog, "down")
        if wheel in obstacles:
            rejected.append({"name": name, "reason": f"wheel collision at {list(wheel)}"})
            continue
        if start in obstacles:
            rejected.append({"name": name, "reason": f"drop gearbox collision at {list(start)}"})
            continue
        if goal in obstacles:
            rejected.append({"name": name, "reason": f"tail rise gearbox collision at {list(goal)}"})
            continue
        result = _search_horizontal_route(
            start, goal, obstacles | {wheel, drive_cog}, bounds,
            _wheel_generated_sign(state), expected_extra_sign, turn_penalty,
        )
        if result is None:
            rejected.append({"name": name, "reason": "no collision-free sign-correct horizontal route"})
            continue
        search_cost, path, extra_sign, sign_trace = result
        placements = _route_placements(path, wheel, state, drive_cog, resources)
        coords = [_coord(p["lattice"]) for p in placements]
        if len(coords) != len(set(coords)) or any(c in effective for c in coords):
            raise CockpitYawRouteError(f"route materialization collision for {name}")
        pilot_distance = sum(abs(v) for v in offset)
        score = search_cost + int(profile.get("pilotDistancePenalty", 0)) * pilot_distance + int(candidate.get("preferencePenalty", 0))
        accepted.append((score, tuple(path), name, index, candidate, placements, path, extra_sign, sign_trace))

    if not accepted:
        raise CockpitYawRouteError(f"no cockpit-to-rudder route accepted; rejected={rejected!r}")
    accepted.sort(key=lambda row: (row[0], row[1], row[2], row[3]))
    score, _, candidate_name, _, chosen_candidate, placements, path, extra_sign, sign_trace = accepted[0]

    route_coords = {_coord(p["lattice"]) for p in placements}
    if route_coords & set(effective):
        raise CockpitYawRouteError("selected route overlaps existing aircraft placements")
    if route_coords & propeller or route_coords & rudder or route_coords & forbidden_empty:
        raise CockpitYawRouteError("selected route crosses a forbidden dynamic/air-gap boundary")

    prior_main = int(yaw.get("metrics", {}).get("v0131MovingParentMainBodyPlacementCount", 0))
    if prior_main != len(main) + 1:  # Physics Assembler is not in the manifest reconstruction.
        raise CockpitYawRouteError(f"v0.13.1 parent-main metric mismatch: metric={prior_main} reconstructed={len(main)+1}")
    resulting_main = prior_main + len(placements)
    prop_count = int(yaw.get("metrics", {}).get("nestedPropellerChildPlacementCount", 0))
    rudder_count = int(yaw.get("metrics", {}).get("yawControlChildPlacementCount", 0))
    primary_count = resulting_main + prop_count + rudder_count

    glue_points = route_coords | {seat}
    lo = tuple(min(c[i] for c in glue_points) for i in range(3))
    hi = tuple(max(c[i] for c in glue_points) for i in range(3))
    glue_domain = {
        "name": "cockpit_yaw_route",
        "classification": "parent_main",
        "from": list(lo),
        "to": list(hi),
        "selectionCellBounds": {"min": list(lo), "max": list(hi)},
        "selectionSizeBlocks": [hi[i] - lo[i] + 1 for i in range(3)],
    }
    max_dimension = int(profile.get("maxGlueSelectionDimensionBlocks", 24))
    def contains(c: tuple[int, int, int]) -> bool:
        return all(lo[i] <= c[i] <= hi[i] for i in range(3))
    if any(v > max_dimension for v in glue_domain["selectionSizeBlocks"]):
        raise CockpitYawRouteError("cockpit route glue domain exceeds Create selection bound")
    if any(contains(c) for c in propeller | rudder | forbidden_empty):
        raise CockpitYawRouteError("cockpit route glue domain crosses forbidden child/air-gap boundary")

    kinds: dict[str, int] = {}
    for p in placements:
        kinds[p["kind"]] = kinds.get(p["kind"], 0) + 1
    wheel_coord = next(_coord(p["lattice"]) for p in placements if p["role"] == "cockpit_steering_wheel")
    wheel_state = next(dict(p["blockState"]) for p in placements if p["role"] == "cockpit_steering_wheel")
    expected_tail_cog_sign = extra_sign * -1

    obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_obligations = {
        "cockpit_route_primary_recapture_probe",
        "cockpit_steering_network_probe",
        "cockpit_steering_command_return_probe",
        "post_route_mass_com_probe",
    }
    if required_obligations - {str(v.get("id")) for v in obligations}:
        raise CockpitYawRouteError("v0.18 profile missing required runtime obligations")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-cockpit-yaw-route-ir-0.18",
        "assetId": str(steering["assetId"]).replace("v0_17_steering_control", "v0_18_cockpit_yaw_route"),
        "compilerVersion": "aircraft-cockpit-yaw-route-lowerer-0.18.0",
        "sourceManifestDigestSha256": manifest["digestSha256"],
        "sourcePowertrainDigestSha256": powertrain["digestSha256"],
        "sourceYawControlDigestSha256": yaw["digestSha256"],
        "sourceSteeringControlDigestSha256": steering["digestSha256"],
        "profileId": profile["profileId"],
        "pilotSeatCoordinate": list(seat),
        "selectedCandidate": candidate_name,
        "selectedCandidateScore": score,
        "steeringWheelCoordinate": list(wheel_coord),
        "steeringWheelBlockState": wheel_state,
        "driveCogCoordinate": list(drive_cog),
        "swivelBearingCoordinate": list(swivel),
        "routePlaneY": path[0][1],
        "routePath": [list(c) for c in path],
        "placements": placements,
        "placementKindCounts": kinds,
        "routeGlueDomain": glue_domain,
        "rejectedCandidates": rejected,
        "signContract": {
            "positiveWheelCommandGeneratedSign": _wheel_generated_sign(wheel_state),
            "expectedTailDriveCogSign": expected_tail_cog_sign,
            "expectedSwivelExtraCogSign": extra_sign,
            "expectedRpmMagnitude": int(steering.get("runtimeProbe", {}).get("expectedSteeringWheelRpmMagnitude", 16)),
            "trace": sign_trace,
        },
        "metrics": {
            "routePlacementCount": len(placements),
            "routePlaneCellCount": len(path),
            "routeTurnCount": sum(1 for p in placements if p["kind"] == "cockpit_yaw_route_gearbox") - 2,
            "gearboxCount": kinds.get("cockpit_yaw_route_gearbox", 0),
            "shaftCount": kinds.get("cockpit_yaw_route_shaft", 0),
            "priorMovingParentMainBodyPlacementCount": prior_main,
            "resultingMovingParentMainBodyPlacementCount": resulting_main,
            "nestedPropellerChildPlacementCount": prop_count,
            "yawControlChildPlacementCount": rudder_count,
            "expectedPrimarySableTransferCount": primary_count,
        },
        "runtimeObligations": obligations,
        "readiness": {
            "cockpitRouteStaticSearchPassed": True,
            "cockpitRoutePlacementPatchReady": True,
            "cockpitRouteRuntimeProbeReady": True,
            "cockpitRouteRuntimeQualified": False,
            "pilotInteractionBindingReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "cockpit_route_primary_sable_recapture_unverified",
                "cockpit_route_network_sign_rpm_unverified",
                "cockpit_route_command_return_unverified",
                "post_route_mass_com_unverified",
                "pilot_interaction_binding_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "deterministic_collision_aware_sign_aware_discrete_create_kinetic_route_search_from_pilot_adjacent_steering_wheel_to_accepted_aft_swivel_interface",
            "doesNotProve": [
                "that the 0.18-modified aircraft recaptures as one Sable primary body",
                "that the routed Create kinetic network propagates the predicted sign/RPM in the exact stack",
                "post-route runtime mass or center of mass",
                "actual player interaction/network packet binding",
                "pilot occupancy or ergonomics under live gameplay",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
