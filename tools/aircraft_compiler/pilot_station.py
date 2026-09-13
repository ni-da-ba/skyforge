from __future__ import annotations

import hashlib
import json
from typing import Any


class PilotStationError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise PilotStationError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def realize_pilot_station(
    target: dict[str, Any],
    propulsion: dict[str, Any],
    tail_lowering: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if target.get("schemaVersion") != "aircraft-minecraft-realization-preflight-ir-0.4":
        raise PilotStationError("v0.8 requires target preflight v0.4")
    if propulsion.get("schemaVersion") != "aircraft-propulsion-realization-ir-0.5":
        raise PilotStationError("v0.8 requires propulsion realization v0.5")
    if tail_lowering.get("schemaVersion") != "aircraft-tail-lowering-ir-0.7":
        raise PilotStationError("v0.8 requires tail lowering v0.7")
    if profile.get("schemaVersion") != "aircraft-pilot-station-profile-0.8":
        raise PilotStationError("v0.8 requires aircraft-pilot-station-profile-0.8")
    if not tail_lowering.get("validation", {}).get("passed", False):
        raise PilotStationError("refusing pilot realization from invalid tail lowering")

    station_name = str(profile.get("stationName", "pilot_station"))
    stations = [s for s in target.get("stations", []) if s.get("name") == station_name]
    if len(stations) != 1:
        raise PilotStationError(f"expected exactly one {station_name} station")
    anchor = _coord(stations[0]["lattice"])
    offset = _coord(profile.get("installationOffsetBlocks", [0, 1, 0]))
    placement = tuple(anchor[i] + offset[i] for i in range(3))

    seat = profile.get("seat", {})
    resource_id = str(seat.get("resourceId", ""))
    if ":" not in resource_id:
        raise PilotStationError("seat resourceId must be namespaced")
    state = dict(seat.get("blockState", {}))
    if state != {"waterlogged": False}:
        raise PilotStationError("bounded v0.8 proof requires explicit non-waterlogged Create seat state")

    aero_provider = str(profile["airframeAerodynamicProviderId"])
    structural = [p for p in target.get("placements", []) if p.get("providerId") != aero_provider]
    structural_coords = {_coord(p["lattice"]) for p in structural}
    aero_coords = {_coord(p["lattice"]) for p in tail_lowering.get("resolvedAerodynamicPlacements", [])}
    propulsion_coords = {
        _coord(propulsion["bearing"]["lattice"]),
        _coord(propulsion["hub"]["lattice"]),
        *(_coord(p["lattice"]) for p in propulsion.get("sails", [])),
    }
    occupied = structural_coords | aero_coords | propulsion_coords

    below = (placement[0], placement[1] - 1, placement[2])
    expected_support = anchor if offset == (0, 1, 0) else below
    checks = {
        "installationOffsetIsOneBlockUp": offset == (0, 1, 0),
        "pilotAnchorIsStructuralAirframeSite": anchor in structural_coords,
        "seatCoordinateFree": placement not in occupied,
        "seatSupportedByPilotAnchor": below == expected_support and below in structural_coords,
        "tailLoweringPassed": bool(tail_lowering.get("readiness", {}).get("tailJunctionLoweringPassed", False)),
        "surfaceStateResolutionComplete": bool(tail_lowering.get("readiness", {}).get("surfaceStateResolutionComplete", False)),
    }
    static_passed = all(checks.values())

    control_contract = dict(profile.get("controlBindingContract", {}))
    if control_contract.get("status") != "runtime_unverified":
        raise PilotStationError("v0.8 control binding must remain runtime_unverified")
    if control_contract.get("requiredForProbeEmission") is not False:
        raise PilotStationError("probe emission must not require flight-control binding")
    if control_contract.get("requiredForFlightQualification") is not True:
        raise PilotStationError("flight qualification must require control binding")

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    runtime_ids = {v.get("id") for v in runtime_obligations}
    if {"pilot_occupancy_probe", "control_binding_probe"} - runtime_ids:
        raise PilotStationError("v0.8 requires pilot_occupancy_probe and control_binding_probe")

    static_blockers = [] if static_passed else ["pilot_station_static_placement_failed"]
    runtime_blockers = list(tail_lowering.get("readiness", {}).get("runtimeBlockers", []))
    runtime_blockers += ["pilot_occupancy_runtime_unverified", "control_binding_runtime_unverified"]

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-pilot-station-realization-ir-0.8",
        "assetId": str(tail_lowering["assetId"]).replace("v0_7_tail_lowering", "v0_8_pilot_station"),
        "compilerVersion": "aircraft-pilot-station-realizer-0.8",
        "sourceTargetPreflightDigestSha256": target["digestSha256"],
        "sourcePropulsionDigestSha256": propulsion["digestSha256"],
        "sourceTailLoweringDigestSha256": tail_lowering["digestSha256"],
        "profileId": profile["profileId"],
        "semanticStation": {
            "name": station_name,
            "anchorLattice": list(anchor),
            "installationOffsetBlocks": list(offset),
        },
        "placement": {
            "kind": "pilot_occupancy_station",
            "lattice": list(placement),
            "resourceId": resource_id,
            "blockState": state,
            "staticCapability": "moving_occupant_anchor_candidate",
            "runtimeBehaviorStatus": "unverified_on_sable_physics_body",
        },
        "controlBindingContract": control_contract,
        "topologyChecks": checks,
        "runtimeObligations": runtime_obligations,
        "metrics": {
            "pilotAnchorOccupiedByStructure": anchor in structural_coords,
            "pilotSeatPlacementCount": 1,
            "runtimeObligationCount": len(runtime_obligations),
            "runtimeObligationVerifiedCount": 0,
        },
        "readiness": {
            "pilotStationStaticPlacementPassed": static_passed,
            "probeSchematicEmissionReady": bool(static_passed),
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "resolvedUpstreamBlockers": ["unresolved_required_station_providers"] if static_passed else [],
            "staticBlockers": static_blockers,
            "runtimeBlockers": sorted(set(runtime_blockers)),
        },
        "validation": {
            "passed": static_passed,
            "scope": "pilot_occupancy_block_placement_and_station_decomposition_only",
            "doesNotProve": [
                "Create seat passenger behavior on the assembled Sable physics body",
                "pilot input binding",
                "pitch yaw or roll control authority",
                "throttle or propeller governor binding",
                "runtime mass or center of mass",
                "flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
