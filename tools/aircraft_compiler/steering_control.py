from __future__ import annotations

import hashlib
import json
from typing import Any


class SteeringControlLoweringError(ValueError):
    pass


def _coord(value: Any, label: str) -> list[int]:
    if not isinstance(value, list) or len(value) != 3 or not all(isinstance(v, int) for v in value):
        raise SteeringControlLoweringError(f"{label} must be a 3-integer coordinate")
    return list(value)


def lower_steering_control(
    yaw_actuation: dict[str, Any],
    yaw_neutral_return: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if yaw_actuation.get("schemaVersion") != "aircraft-yaw-actuation-ir-0.14":
        raise SteeringControlLoweringError("v0.17 requires yaw-actuation IR v0.14")
    if yaw_neutral_return.get("schemaVersion") != "aircraft-yaw-neutral-return-ir-0.16":
        raise SteeringControlLoweringError("v0.17 requires yaw-neutral-return IR v0.16")
    if profile.get("schemaVersion") != "aircraft-steering-control-profile-0.17":
        raise SteeringControlLoweringError("v0.17 requires aircraft-steering-control-profile-0.17")
    if not yaw_actuation.get("validation", {}).get("passed", False):
        raise SteeringControlLoweringError("v0.17 requires accepted v0.14 actuation topology")
    if not yaw_neutral_return.get("validation", {}).get("passed", False):
        raise SteeringControlLoweringError("v0.17 requires accepted v0.16 neutral-return probe contract")

    act_digest = str(yaw_actuation.get("digestSha256", ""))
    neutral_digest = str(yaw_neutral_return.get("digestSha256", ""))
    if yaw_neutral_return.get("sourceYawActuationDigestSha256") != act_digest:
        raise SteeringControlLoweringError("v0.16 artifact is not chained to supplied v0.14 actuation")
    if profile.get("sourceYawActuationDigestSha256") not in (None, "", act_digest):
        raise SteeringControlLoweringError("v0.17 source actuation digest mismatch")
    if profile.get("sourceYawNeutralReturnDigestSha256") not in (None, "", neutral_digest):
        raise SteeringControlLoweringError("v0.17 source neutral-return digest mismatch")

    swivel = _coord(yaw_actuation.get("swivelBearingCoordinate"), "swivelBearingCoordinate")
    drive_cog = _coord(yaw_actuation.get("driveCogCoordinate"), "driveCogCoordinate")
    source_coord = _coord(yaw_actuation.get("creativeMotorCoordinate"), "creativeMotorCoordinate")
    if drive_cog != [source_coord[0], source_coord[1] + 1, source_coord[2]]:
        raise SteeringControlLoweringError("v0.17 source must remain directly below the accepted Y-axis drive cog")

    resource = str(profile.get("steeringWheelResource", ""))
    state = dict(profile.get("steeringWheelBlockState", {}))
    if resource != "simulated:steering_wheel":
        raise SteeringControlLoweringError("v0.17 requires exact simulated:steering_wheel resource")
    if state != {"facing": "north", "on_floor": "false", "waterlogged": "false"}:
        raise SteeringControlLoweringError("v0.17 requires north-facing ceiling-mounted dry Steering Wheel state")

    rpm = int(profile.get("expectedSteeringWheelRpmMagnitude", 0))
    command = float(profile.get("wheelCommandDegrees", 0.0))
    max_ticks = int(profile.get("maximumCommandTicks", 0))
    settle = int(profile.get("physicalSettlePhysicsTicks", 0))
    min_target = float(profile.get("minimumSwivelTargetDeflectionDegrees", 0.0))
    min_physical = float(profile.get("minimumPhysicalRudderDeflectionDegrees", 0.0))
    target_tol = float(profile.get("targetNeutralToleranceDegrees", 0.0))
    physical_tol = float(profile.get("physicalNeutralToleranceDegrees", 0.0))
    if rpm != 16:
        raise SteeringControlLoweringError("v0.17 released Steering Wheel source contract is exactly 16 RPM")
    if not (0.0 < command <= 180.0):
        raise SteeringControlLoweringError("wheelCommandDegrees must be within (0, 180]")
    if min(max_ticks, settle) < 1 or min(min_target, min_physical, target_tol, physical_tol) <= 0:
        raise SteeringControlLoweringError("v0.17 probe timing/tolerances must be positive")

    required = {
        "steering_wheel_source_registration_probe",
        "steering_wheel_swivel_network_probe",
        "steering_wheel_rudder_deflection_probe",
        "steering_wheel_commanded_neutral_return_probe",
    }
    obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    if required - {str(v.get("id")) for v in obligations}:
        raise SteeringControlLoweringError("v0.17 profile missing required runtime obligations")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-steering-control-ir-0.17",
        "assetId": str(yaw_neutral_return["assetId"]).replace("v0_16_yaw_neutral_return", "v0_17_steering_control"),
        "compilerVersion": "aircraft-steering-control-lowerer-0.17.0",
        "sourceYawActuationDigestSha256": act_digest,
        "sourceYawNeutralReturnDigestSha256": neutral_digest,
        "profileId": profile["profileId"],
        "swivelBearingCoordinate": swivel,
        "driveCogCoordinate": drive_cog,
        "steeringWheelCoordinate": source_coord,
        "steeringWheelResource": resource,
        "steeringWheelBlockState": state,
        "runtimeProbe": {
            "expectedSteeringWheelRpmMagnitude": rpm,
            "wheelCommandDegrees": command,
            "maximumCommandTicks": max_ticks,
            "physicalSettlePhysicsTicks": settle,
            "minimumSwivelTargetDeflectionDegrees": min_target,
            "minimumPhysicalRudderDeflectionDegrees": min_physical,
            "targetNeutralToleranceDegrees": target_tol,
            "physicalNeutralToleranceDegrees": physical_tol,
            "acceptance": "replace the temporary Creative Motor source with a real ceiling-mounted Simulated Steering Wheel at the same coordinate, command its public bounded angle mechanism, observe its real 16-RPM kinetic output through the accepted cog/Swivel path, then command the wheel and physical rudder back to neutral",
        },
        "runtimeObligations": obligations,
        "readiness": {
            "steeringWheelSourceStaticTopologyPassed": True,
            "steeringWheelSourceRuntimeProbeReady": True,
            "productionCockpitRoutingReady": False,
            "pilotInteractionBindingReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "steering_wheel_source_runtime_unverified",
                "steering_wheel_swivel_network_unverified",
                "steering_wheel_physical_rudder_response_unverified",
                "steering_wheel_commanded_return_unverified",
                "cockpit_kinetic_route_unresolved",
                "pilot_interaction_binding_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "source_backed_real_simulated_steering_wheel_substitution_for_the_accepted_post_assembly_yaw_test_source",
            "doesNotProve": [
                "cockpit-accessible placement or ergonomics",
                "pilot interaction/network packet binding",
                "production kinetic routing from cockpit to tail",
                "passive self-centering without a command",
                "three-axis control or stable flight",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
