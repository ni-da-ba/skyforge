from __future__ import annotations

import hashlib
import json
from typing import Any


class YawAuthorityLoweringError(ValueError):
    pass


def lower_yaw_authority(yaw_actuation: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if yaw_actuation.get("schemaVersion") != "aircraft-yaw-actuation-ir-0.14":
        raise YawAuthorityLoweringError("v0.15 requires yaw-actuation IR v0.14")
    if profile.get("schemaVersion") != "aircraft-yaw-authority-profile-0.15":
        raise YawAuthorityLoweringError("v0.15 requires aircraft-yaw-authority-profile-0.15")
    if not yaw_actuation.get("validation", {}).get("passed", False):
        raise YawAuthorityLoweringError("v0.15 requires statically accepted v0.14 actuation fixture")
    if not yaw_actuation.get("readiness", {}).get("actuationRuntimeProbeReady", False):
        raise YawAuthorityLoweringError("v0.15 requires v0.14 runtime-probe readiness")

    expected = str(profile.get("sourceYawActuationDigestSha256", ""))
    actual = str(yaw_actuation.get("digestSha256", ""))
    if expected and actual != expected:
        raise YawAuthorityLoweringError(f"v0.15 source actuation digest mismatch: actual={actual} expected={expected}")

    forward_speed = float(profile.get("forwardSpeedMps", 0.0))
    settle_ticks = int(profile.get("servoSettlePhysicsTicks", 0))
    min_angle = float(profile.get("minimumPhysicalDeflectionDegrees", 0.0))
    min_lateral = float(profile.get("minimumLateralImpulseMagnitude", 0.0))
    min_yaw = float(profile.get("minimumYawImpulseMagnitude", 0.0))
    if forward_speed <= 0 or settle_ticks < 1 or min_angle <= 0 or min_lateral <= 0 or min_yaw <= 0:
        raise YawAuthorityLoweringError("v0.15 probe parameters must be positive")

    required = {"rudder_physical_pose_response_probe", "rudder_lateral_drag_probe", "rudder_yaw_moment_probe"}
    obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    if required - {str(v.get("id")) for v in obligations}:
        raise YawAuthorityLoweringError("v0.15 profile missing required runtime obligations")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-yaw-authority-ir-0.15",
        "assetId": str(yaw_actuation["assetId"]).replace("v0_14_yaw_actuation", "v0_15_yaw_authority"),
        "compilerVersion": "aircraft-yaw-authority-lowerer-0.15.0",
        "sourceYawActuationDigestSha256": actual,
        "profileId": profile["profileId"],
        "runtimeProbe": {
            "forwardSpeedMps": forward_speed,
            "servoSettlePhysicsTicks": settle_ticks,
            "minimumPhysicalDeflectionDegrees": min_angle,
            "minimumLateralImpulseMagnitude": min_lateral,
            "minimumYawImpulseMagnitude": min_yaw,
            "flowDirectionAircraftLocal": [-1.0, 0.0, 0.0],
            "yawAxisAircraftLocal": [0.0, 1.0, 0.0],
            "acceptance": "actual child pose must deflect; Sable-recorded rudder drag must have nonzero lateral component and nonzero yaw impulse about live parent COM; aft-rudder yaw impulse sign must oppose measured rudder-body yaw sign",
        },
        "runtimeObligations": obligations,
        "readiness": {
            "yawAuthorityProbeReady": True,
            "yawAuthorityQualified": False,
            "neutralReturnQualified": False,
            "productionControlBindingReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "rudder_physical_pose_response_unverified",
                "rudder_lateral_drag_unverified",
                "rudder_yaw_moment_unverified",
                "rudder_neutral_return_unverified",
                "pilot_control_binding_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "exact_stack_sable_physics_pose_and_recorded_drag_force_yaw_authority_probe_contract",
            "doesNotProve": [
                "neutral-return behavior",
                "production cockpit or pilot binding",
                "closed-loop handling quality",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
