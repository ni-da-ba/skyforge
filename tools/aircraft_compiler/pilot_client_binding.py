from __future__ import annotations

import hashlib
import json
from typing import Any


class PilotClientBindingError(ValueError):
    pass


def _coord(value: Any, label: str) -> list[int]:
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, int) for v in value):
        raise PilotClientBindingError(f"{label} must be a 3-integer coordinate")
    return list(value)


def lower_pilot_client_binding(
    pilot_interaction: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if pilot_interaction.get("schemaVersion") != "aircraft-pilot-interaction-ir-0.19":
        raise PilotClientBindingError("v0.20 requires pilot-interaction IR v0.19")
    if profile.get("schemaVersion") != "aircraft-pilot-client-binding-profile-0.20":
        raise PilotClientBindingError("v0.20 requires aircraft-pilot-client-binding-profile-0.20")
    if not pilot_interaction.get("validation", {}).get("passed", False):
        raise PilotClientBindingError("v0.20 requires accepted v0.19 pilot-interaction contract")

    source_digest = str(pilot_interaction.get("digestSha256", ""))
    if not source_digest:
        raise PilotClientBindingError("v0.20 input must carry a deterministic digest")
    if profile.get("sourcePilotInteractionDigestSha256") not in (None, "", source_digest):
        raise PilotClientBindingError("v0.20 source pilot-interaction digest mismatch")

    readiness = dict(pilot_interaction.get("readiness", {}))
    if not readiness.get("pilotInteractionStaticContractPassed", False):
        raise PilotClientBindingError("v0.20 requires the accepted v0.19 static contract")
    if not readiness.get("realClientPilotProbeReady", False):
        raise PilotClientBindingError("v0.20 requires the v0.19 real-client probe boundary")
    overclaims = (
        "pilotOccupancyRuntimeQualified",
        "playerSableTrackingQualified",
        "steeringPacketRoundTripQualified",
        "flightQualified",
    )
    if any(readiness.get(key, False) for key in overclaims):
        raise PilotClientBindingError("v0.20 source must remain fail-closed before real-client qualification")

    seat_coordinate = _coord(pilot_interaction.get("pilotSeatCoordinate"), "pilot seat coordinate")
    wheel_coordinate = _coord(pilot_interaction.get("steeringWheelCoordinate"), "Steering Wheel coordinate")
    if str(pilot_interaction.get("pilotSeatResource")) != "create:brown_seat":
        raise PilotClientBindingError("v0.20 requires the compiled Create brown pilot seat")
    if str(pilot_interaction.get("steeringWheelResource")) != "simulated:steering_wheel":
        raise PilotClientBindingError("v0.20 requires the compiled Simulated Steering Wheel")

    entry_contract = dict(profile.get("clientEntryContract", {}))
    expected_entry_contract = {
        "blockUseEntryPoint": "net.minecraft.client.multiplayer.MultiPlayerGameMode#useItemOn",
        "quietUseMixinClass": "dev.simulated_team.simulated.mixin.quiet_use.MultiPlayerGameModeMixin",
        "steeringHandlerClass": "dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelHandler",
        "mouseMoveDispatch": "dev.simulated_team.simulated.events.SimulatedCommonClientEvents#onMouseMove",
        "useReleaseDispatch": "dev.simulated_team.simulated.events.SimulatedCommonClientEvents#onBeforeMouseInput",
        "packetClass": "dev.simulated_team.simulated.network.packets.SteeringWheelPacket",
        "packetPayloadId": "simulated:steering_wheel_update",
        "seatBehaviorClass": "com.simibubi.create.content.contraptions.actors.seat.SeatBlock",
        "seatEntityClass": "com.simibubi.create.content.contraptions.actors.seat.SeatEntity",
    }
    if entry_contract != expected_entry_contract:
        raise PilotClientBindingError("v0.20 exact real-client entry contract mismatch")

    command_probe = dict(profile.get("commandProbe", {}))
    mouse_yaw_delta = float(command_probe.get("mouseYawDelta", 0.0))
    min_target = float(command_probe.get("minimumAbsoluteTargetDegrees", 0.0))
    max_target = float(command_probe.get("maximumAbsoluteTargetDegrees", 0.0))
    if not 0.0 < abs(mouse_yaw_delta) <= 720.0:
        raise PilotClientBindingError("v0.20 mouseYawDelta must be finite, nonzero, and bounded to 720 degrees")
    if not 0.0 < min_target < max_target <= 90.0:
        raise PilotClientBindingError("v0.20 target-angle bounds must satisfy 0 < min < max <= 90")
    for field in (
        "activePacketSettleTicks",
        "releasePacketSettleTicks",
        "seatMountSettleTicks",
        "seatDismountSettleTicks",
    ):
        value = command_probe.get(field)
        if not isinstance(value, int) or not 1 <= value <= 100:
            raise PilotClientBindingError(f"v0.20 {field} must be an integer in [1,100]")

    required_obligations = {
        "real_client_steering_hold_acquire",
        "real_client_steering_active_packet_round_trip",
        "real_client_steering_release_packet_round_trip",
        "real_player_seat_mount",
        "real_player_seat_dismount",
    }
    obligations = [dict(item, status="unverified") for item in profile.get("runtimeObligations", [])]
    obligation_ids = {str(item.get("id")) for item in obligations}
    missing = required_obligations - obligation_ids
    if missing:
        raise PilotClientBindingError(f"v0.20 profile missing runtime obligations: {sorted(missing)}")

    probe_modes = dict(profile.get("probeModes", {}))
    if set(probe_modes.get("actualClientRequired", [])) != required_obligations:
        raise PilotClientBindingError("v0.20 actual-client qualification set is incomplete")
    if probe_modes.get("headlessServerCanQualify") != []:
        raise PilotClientBindingError("v0.20 real-client obligations must not be qualified headlessly")
    if probe_modes.get("deferredRealClient") != ["player_sable_tracking_probe"]:
        raise PilotClientBindingError("v0.20 must explicitly defer moving-body player tracking")

    output: dict[str, Any] = {
        "schemaVersion": "aircraft-pilot-client-binding-ir-0.20",
        "assetId": str(pilot_interaction["assetId"]).replace("v0_19_pilot_interaction", "v0_20_pilot_client_binding"),
        "compilerVersion": "aircraft-pilot-client-binding-lowerer-0.20.0",
        "sourcePilotInteractionDigestSha256": source_digest,
        "profileId": profile["profileId"],
        "pilotSeatCoordinate": seat_coordinate,
        "pilotSeatResource": pilot_interaction["pilotSeatResource"],
        "steeringWheelCoordinate": wheel_coordinate,
        "steeringWheelResource": pilot_interaction["steeringWheelResource"],
        "steeringWheelBlockState": dict(pilot_interaction.get("steeringWheelBlockState", {})),
        "clientEntryContract": expected_entry_contract,
        "commandProbe": command_probe,
        "runtimeObligations": obligations,
        "probeModes": probe_modes,
        "readiness": {
            "pilotClientBindingStaticContractPassed": True,
            "assembledCockpitPresencePrerequisite": True,
            "realClientInteractionProbeReady": True,
            "steeringHoldRuntimeQualified": False,
            "steeringPacketRoundTripQualified": False,
            "pilotOccupancyRuntimeQualified": False,
            "playerSableTrackingQualified": False,
            "completedControlsPersistenceQualified": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "real_client_steering_hold_unverified",
                "real_client_active_packet_round_trip_unverified",
                "real_client_release_packet_round_trip_unverified",
                "real_player_seat_mount_unverified",
                "real_player_seat_dismount_unverified",
                "player_sable_tracking_deferred",
                "completed_controls_save_reload_unverified",
                "pitch_roll_controls_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "exact_real_client_entry_and_packet_round_trip_qualification_contract",
            "doesNotProve": [
                "the player remains synchronized while the assembled Sable parent translates or rotates",
                "completed-control save/reload persistence",
                "pitch or roll controls",
                "closed-loop handling quality or stability",
                "stable flight",
            ],
        },
    }
    canonical = json.dumps(output, sort_keys=True, separators=(",", ":"))
    output["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return output
