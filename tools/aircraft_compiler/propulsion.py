from __future__ import annotations

import hashlib
import json
from collections import deque
from typing import Any


class PropulsionRealizationError(ValueError):
    pass


_FACING_VECTORS = {
    "west": (-1, 0, 0),
    "east": (1, 0, 0),
    "down": (0, -1, 0),
    "up": (0, 1, 0),
    "north": (0, 0, -1),
    "south": (0, 0, 1),
}


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise PropulsionRealizationError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _connected_to_hub(offsets: set[tuple[int, int]]) -> bool:
    if not offsets:
        return False
    allowed = set(offsets) | {(0, 0)}
    seen = {(0, 0)}
    queue = deque([(0, 0)])
    while queue:
        y, z = queue.popleft()
        for nxt in ((y + 1, z), (y - 1, z), (y, z + 1), (y, z - 1)):
            if nxt in allowed and nxt not in seen:
                seen.add(nxt)
                queue.append(nxt)
    return offsets <= seen


def realize_propulsion(
    assembly: dict[str, Any],
    target_preflight: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if assembly.get("schemaVersion") != "aircraft-assembly-plan-ir-0.3":
        raise PropulsionRealizationError("v0.5 requires aircraft-assembly-plan-ir-0.3")
    if target_preflight.get("schemaVersion") != "aircraft-minecraft-realization-preflight-ir-0.4":
        raise PropulsionRealizationError("v0.5 requires aircraft-minecraft-realization-preflight-ir-0.4")
    if profile.get("schemaVersion") != "aircraft-propulsion-profile-0.5":
        raise PropulsionRealizationError("v0.5 requires aircraft-propulsion-profile-0.5")
    if profile.get("targetProfileId") != target_preflight.get("targetProfileId"):
        raise PropulsionRealizationError("propulsion profile targetProfileId does not match target preflight")

    station_name = str(profile.get("stationName", "propeller_axis"))
    stations = [s for s in assembly.get("stations", []) if s.get("name") == station_name]
    if len(stations) != 1:
        raise PropulsionRealizationError(f"expected exactly one {station_name} station")
    bearing_coord = _coord(stations[0]["lattice"])

    bearing = profile.get("bearing", {})
    bearing_resource = str(bearing.get("resourceId", ""))
    facing = str(bearing.get("facing", "")).lower()
    if ":" not in bearing_resource:
        raise PropulsionRealizationError("bearing resourceId must be namespaced")
    if facing not in _FACING_VECTORS:
        raise PropulsionRealizationError(f"invalid bearing facing: {facing!r}")
    forward = _FACING_VECTORS[facing]
    hub_coord = tuple(bearing_coord[i] + forward[i] for i in range(3))

    propeller = profile.get("propeller", {})
    sail_resource = str(propeller.get("sailResourceId", ""))
    hub_resource = str(propeller.get("hubResourceId", ""))
    sail_axis = str(propeller.get("sailAxis", "")).lower()
    if ":" not in sail_resource or ":" not in hub_resource:
        raise PropulsionRealizationError("propeller resource ids must be namespaced")
    axis_for_facing = {"west": "x", "east": "x", "down": "y", "up": "y", "north": "z", "south": "z"}[facing]
    if sail_axis != axis_for_facing:
        raise PropulsionRealizationError(
            f"symmetric-sail axis {sail_axis!r} must match bearing axis {axis_for_facing!r}"
        )

    raw_offsets = propeller.get("bladeOffsets", [])
    offsets = [_coord([0, int(v[0]), int(v[1])])[1:] for v in raw_offsets]
    if not offsets or len(set(offsets)) != len(offsets):
        raise PropulsionRealizationError("bladeOffsets must be non-empty and unique")
    if (0, 0) in offsets:
        raise PropulsionRealizationError("bladeOffsets may not occupy the hub")
    offset_set = set(offsets)

    sail_coords: list[tuple[int, int, int]] = []
    # Offsets are defined in the two coordinates orthogonal to the bearing axis.
    for a, b in offsets:
        if axis_for_facing == "x":
            sail_coords.append((hub_coord[0], hub_coord[1] + a, hub_coord[2] + b))
        elif axis_for_facing == "y":
            sail_coords.append((hub_coord[0] + a, hub_coord[1], hub_coord[2] + b))
        else:
            sail_coords.append((hub_coord[0] + a, hub_coord[1] + b, hub_coord[2]))

    occupied = {(int(s["x"]), int(s["y"]), int(s["z"])) for s in assembly.get("sites", [])}
    generated = {bearing_coord, hub_coord, *sail_coords}
    no_internal_collision = len(generated) == 2 + len(sail_coords)
    source_collisions = sorted(generated & occupied)

    first_moment_a = sum(v[0] for v in offsets)
    first_moment_b = sum(v[1] for v in offsets)
    balanced = first_moment_a == 0 and first_moment_b == 0
    central_symmetry = all((-a, -b) in offset_set for a, b in offsets)
    connected = _connected_to_hub(offset_set)
    radii_squared = sorted(a * a + b * b for a, b in offsets)

    counted_power = int(propeller.get("sailPowerPerBlock", 1)) * len(sail_coords)
    minimum_power = int(propeller.get("minimumSailPower", 2))
    target_power = int(propeller.get("targetSailPower", counted_power))
    minimum_sail_power_satisfied = counted_power >= minimum_power
    target_sail_power_satisfied = counted_power == target_power

    topology_checks = {
        "bearingCoordinateFree": bearing_coord not in occupied,
        "hubCoordinateFree": hub_coord not in occupied,
        "generatedCoordinatesUnique": no_internal_collision,
        "noSourceAssemblyCollision": not source_collisions,
        "allSailsCoplanarNormalToBearingAxis": all(
            c[{"x": 0, "y": 1, "z": 2}[axis_for_facing]] == hub_coord[{"x": 0, "y": 1, "z": 2}[axis_for_facing]]
            for c in sail_coords
        ),
        "bladeGraphConnectedToHub": connected,
        "zeroTransverseFirstMoment": balanced,
        "centralSymmetry": central_symmetry,
        "minimumSailPowerSatisfied": minimum_sail_power_satisfied,
        "targetSailPowerSatisfied": target_sail_power_satisfied,
    }
    static_propulsion_passed = all(topology_checks.values())

    bearing_placement = {
        "kind": "propeller_bearing",
        "lattice": list(bearing_coord),
        "resourceId": bearing_resource,
        "blockState": {"facing": facing},
        "evidenceLevel": bearing.get("evidenceLevel", "source_verified_runtime_unverified"),
    }
    hub_placement = {
        "kind": "propeller_hub",
        "lattice": list(hub_coord),
        "resourceId": hub_resource,
        "blockState": dict(propeller.get("hubBlockState", {})),
        "assemblyContract": "bearing_facing_neighbor; live capture still required",
    }
    sail_placements = [
        {
            "kind": "propeller_sail_intent",
            "lattice": list(coord),
            "resourceId": sail_resource,
            "blockState": {"axis": sail_axis},
            "sailPower": int(propeller.get("sailPowerPerBlock", 1)),
        }
        for coord in sorted(sail_coords)
    ]

    runtime_obligations = []
    for raw in profile.get("runtimeObligations", []):
        obligation = dict(raw)
        obligation["status"] = "unverified"
        runtime_obligations.append(obligation)

    upstream_blockers = list(target_preflight.get("readiness", {}).get("blockers", []))
    resolved_upstream = []
    effective_blockers = []
    for blocker in upstream_blockers:
        if blocker == "unsatisfied_contraption_companion_requirements" and minimum_sail_power_satisfied:
            resolved_upstream.append(blocker)
        else:
            effective_blockers.append(blocker)
    if not static_propulsion_passed:
        effective_blockers.append("propulsion_static_topology_failed")
    if runtime_obligations:
        effective_blockers.append("propulsion_runtime_obligations_unverified")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-propulsion-realization-ir-0.5",
        "assetId": str(assembly["assetId"]).replace("v0_3_assembly", "v0_5_propulsion"),
        "compilerVersion": "aircraft-propulsion-realizer-0.5",
        "sourceAssemblyDigestSha256": assembly["digestSha256"],
        "sourceTargetPreflightDigestSha256": target_preflight["digestSha256"],
        "propulsionProfileId": profile["profileId"],
        "bearing": bearing_placement,
        "hub": hub_placement,
        "sails": sail_placements,
        "geometry": {
            "bearingAxis": axis_for_facing,
            "bearingFacing": facing,
            "hubCoordinate": list(hub_coord),
            "bladeOffsets": [list(v) for v in sorted(offsets)],
            "transverseFirstMoment": [first_moment_a, first_moment_b],
            "radiiSquared": radii_squared,
            "sailPower": counted_power,
            "minimumSailPower": minimum_power,
            "targetSailPower": target_power,
        },
        "topologyChecks": topology_checks,
        "sourceAssemblyCollisions": [list(v) for v in source_collisions],
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "generatedPlacementCount": 2 + len(sail_placements),
            "propellerSailCount": len(sail_placements),
            "propellerSailPower": counted_power,
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "propulsionStaticTopologyPassed": static_propulsion_passed,
            "propulsionRuntimeQualified": False,
            "schematicEmissionReady": bool(static_propulsion_passed and not effective_blockers),
            "flightQualified": False,
            "resolvedUpstreamBlockers": sorted(resolved_upstream),
            "blockers": sorted(set(effective_blockers)),
        },
        "validation": {
            "passed": static_propulsion_passed,
            "scope": "deterministic_propeller_geometry_and_source_backed_static_topology",
            "doesNotProve": [
                "bearing contraption capture in the exact runtime",
                "kinetic connectivity, RPM, or stress margin",
                "thrust sign or magnitude",
                "nested-contraption attachment persistence",
                "vehicle mass or center of mass",
                "control binding",
                "stable flight",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
