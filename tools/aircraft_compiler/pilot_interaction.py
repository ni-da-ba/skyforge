from __future__ import annotations

import hashlib
import json
from typing import Any


class PilotInteractionError(ValueError):
    pass


def _coord(value: Any, label: str) -> list[int]:
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, int) for v in value):
        raise PilotInteractionError(f"{label} must be a 3-integer coordinate")
    return list(value)


def lower_pilot_interaction(
    pilot_station: dict[str, Any],
    cockpit_route: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if pilot_station.get("schemaVersion") != "aircraft-pilot-station-realization-ir-0.8":
        raise PilotInteractionError("v0.19 requires pilot-station IR v0.8")
    if cockpit_route.get("schemaVersion") != "aircraft-cockpit-yaw-route-ir-0.18":
        raise PilotInteractionError("v0.19 requires cockpit-yaw-route IR v0.18")
    if profile.get("schemaVersion") != "aircraft-pilot-interaction-profile-0.19":
        raise PilotInteractionError("v0.19 requires aircraft-pilot-interaction-profile-0.19")
    if not pilot_station.get("validation", {}).get("passed", False):
        raise PilotInteractionError("v0.19 requires accepted v0.8 pilot-station placement")
    if not cockpit_route.get("validation", {}).get("passed", False):
        raise PilotInteractionError("v0.19 requires accepted v0.18 cockpit route")

    pilot_digest = str(pilot_station.get("digestSha256", ""))
    route_digest = str(cockpit_route.get("digestSha256", ""))
    if not pilot_digest or not route_digest:
        raise PilotInteractionError("v0.19 inputs must carry deterministic digests")
    if profile.get("sourcePilotStationDigestSha256") not in (None, "", pilot_digest):
        raise PilotInteractionError("v0.19 source pilot-station digest mismatch")
    if profile.get("sourceCockpitYawRouteDigestSha256") not in (None, "", route_digest):
        raise PilotInteractionError("v0.19 source cockpit-route digest mismatch")

    seat_placement = dict(pilot_station.get("placement", {}))
    seat_coordinate = _coord(seat_placement.get("lattice"), "pilot seat coordinate")
    seat_resource = str(seat_placement.get("resourceId", ""))
    wheel_coordinate = _coord(cockpit_route.get("steeringWheelCoordinate"), "Steering Wheel coordinate")
    route_seat_coordinate = _coord(cockpit_route.get("pilotSeatCoordinate"), "v0.18 pilot seat coordinate")

    wheel_placements = [
        dict(placement)
        for placement in cockpit_route.get("placements", [])
        if str(placement.get("role", "")) == "cockpit_steering_wheel"
    ]
    if len(wheel_placements) != 1:
        raise PilotInteractionError(
            f"v0.19 requires exactly one v0.18 cockpit Steering Wheel placement, got {len(wheel_placements)}"
        )
    wheel_placement = wheel_placements[0]
    if _coord(wheel_placement.get("lattice"), "v0.18 Steering Wheel placement") != wheel_coordinate:
        raise PilotInteractionError("v0.19 Steering Wheel coordinate disagrees with the v0.18 placement record")
    wheel_resource = str(wheel_placement.get("resourceId", ""))
    wheel_state = dict(wheel_placement.get("blockState", {}))
    if wheel_state != dict(cockpit_route.get("steeringWheelBlockState", {})):
        raise PilotInteractionError("v0.19 Steering Wheel blockstate disagrees with the v0.18 placement record")

    if seat_coordinate != route_seat_coordinate:
        raise PilotInteractionError("v0.19 pilot seat must be the same station consumed by v0.18")
    if seat_resource != "create:brown_seat":
        raise PilotInteractionError("v0.19 bounded pilot proof requires exact create:brown_seat")
    if wheel_resource != "simulated:steering_wheel":
        raise PilotInteractionError("v0.19 bounded control proof requires exact simulated:steering_wheel")
    if str(wheel_state.get("on_floor", "")).lower() != "true":
        raise PilotInteractionError("v0.19 production cockpit Steering Wheel must remain floor-mounted")
    if str(wheel_state.get("waterlogged", "")).lower() != "false":
        raise PilotInteractionError("v0.19 production cockpit Steering Wheel must remain dry")

    dx = wheel_coordinate[0] - seat_coordinate[0]
    dy = wheel_coordinate[1] - seat_coordinate[1]
    dz = wheel_coordinate[2] - seat_coordinate[2]
    if dy != 0 or abs(dx) + abs(dz) != 1:
        raise PilotInteractionError("v0.19 Steering Wheel must remain horizontally adjacent to the pilot seat")

    source_contract = dict(profile.get("sourceContract", {}))
    expected_source_contract = {
        "seatBehaviorClass": "com.simibubi.create.content.contraptions.actors.seat.SeatBlock",
        "steeringHandlerClass": "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelHandler",
        "steeringPacketClass": "dev.simulated_team.simulated.network.packets.SteeringWheelPacket",
        "steeringPacketPayloadId": "simulated:steering_wheel_update",
    }
    if any(source_contract.get(k) != v for k, v in expected_source_contract.items()):
        raise PilotInteractionError("v0.19 exact source-class contract mismatch")

    packet_contract = dict(profile.get("steeringPacketContract", {}))
    if packet_contract != {
        "activePacketShouldStop": False,
        "releasePacketShouldStop": True,
        "activePacketServerAction": "startHolding",
        "releasePacketServerAction": "stopHolding",
        "targetAngleField": "targetAngleToUpdate",
        "positionField": "pos",
    }:
        raise PilotInteractionError("v0.19 SteeringWheelPacket contract must match released Simulated semantics")

    required_obligations = {
        "assembled_cockpit_presence_probe",
        "player_seat_interaction_probe",
        "player_sable_tracking_probe",
        "steering_wheel_client_hold_interaction_probe",
        "steering_wheel_packet_round_trip_probe",
    }
    obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    present_ids = {str(v.get("id")) for v in obligations}
    missing = required_obligations - present_ids
    if missing:
        raise PilotInteractionError(f"v0.19 profile missing runtime obligations: {sorted(missing)}")

    probe_modes = dict(profile.get("probeModes", {}))
    if probe_modes.get("headlessServerCanQualify") != ["assembled_cockpit_presence_probe"]:
        raise PilotInteractionError("v0.19 must not overclaim player/client qualification from a headless server")
    if set(probe_modes.get("realClientRequired", [])) != {
        "player_seat_interaction_probe",
        "player_sable_tracking_probe",
        "steering_wheel_client_hold_interaction_probe",
        "steering_wheel_packet_round_trip_probe",
    }:
        raise PilotInteractionError("v0.19 real-client qualification set is incomplete")

    output: dict[str, Any] = {
        "schemaVersion": "aircraft-pilot-interaction-ir-0.19",
        "assetId": str(cockpit_route["assetId"]).replace("v0_18_cockpit_yaw_route", "v0_19_pilot_interaction"),
        "compilerVersion": "aircraft-pilot-interaction-lowerer-0.19.0",
        "sourcePilotStationDigestSha256": pilot_digest,
        "sourceCockpitYawRouteDigestSha256": route_digest,
        "profileId": profile["profileId"],
        "pilotSeatCoordinate": seat_coordinate,
        "pilotSeatResource": seat_resource,
        "steeringWheelCoordinate": wheel_coordinate,
        "steeringWheelResource": wheel_resource,
        "steeringWheelBlockState": wheel_state,
        "cockpitGeometry": {
            "wheelOffsetFromSeat": [dx, dy, dz],
            "horizontalManhattanDistanceBlocks": abs(dx) + abs(dz),
            "sameElevation": dy == 0,
        },
        "sourceContract": expected_source_contract,
        "steeringPacketContract": packet_contract,
        "runtimeObligations": obligations,
        "probeModes": probe_modes,
        "readiness": {
            "pilotInteractionStaticContractPassed": True,
            "assembledCockpitPresenceProbeReady": True,
            "realClientPilotProbeReady": True,
            "pilotOccupancyRuntimeQualified": False,
            "playerSableTrackingQualified": False,
            "pilotInteractionBindingReady": False,
            "steeringPacketRoundTripQualified": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "assembled_cockpit_presence_unverified",
                "real_player_seat_interaction_unverified",
                "real_player_sable_tracking_unverified",
                "real_client_steering_hold_interaction_unverified",
                "real_client_steering_packet_round_trip_unverified",
                "completed_controls_save_reload_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "source_backed_pilot_seat_and_real_client_steering_interaction_qualification_contract",
            "doesNotProve": [
                "a real player can mount or remain attached to the assembled moving Sable aircraft",
                "Sable player tracking on the production aircraft",
                "a real client can acquire and hold the Steering Wheel interaction while the aircraft is assembled",
                "SteeringWheelPacket round-trip delivery to the assembled moving-body block entity",
                "completed-control save/reload persistence",
                "pitch or roll controls",
                "stable flight",
            ],
        },
    }
    canonical = json.dumps(output, sort_keys=True, separators=(",", ":"))
    output["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return output
