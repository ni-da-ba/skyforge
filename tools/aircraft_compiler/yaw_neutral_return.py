from __future__ import annotations

import hashlib
import json
from typing import Any


class YawNeutralReturnLoweringError(ValueError):
    pass


def lower_yaw_neutral_return(
    yaw_actuation: dict[str, Any],
    yaw_authority: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if yaw_actuation.get("schemaVersion") != "aircraft-yaw-actuation-ir-0.14":
        raise YawNeutralReturnLoweringError("v0.16 requires yaw-actuation IR v0.14")
    if yaw_authority.get("schemaVersion") != "aircraft-yaw-authority-ir-0.15":
        raise YawNeutralReturnLoweringError("v0.16 requires yaw-authority IR v0.15")
    if profile.get("schemaVersion") != "aircraft-yaw-neutral-return-profile-0.16":
        raise YawNeutralReturnLoweringError("v0.16 requires aircraft-yaw-neutral-return-profile-0.16")
    if not yaw_actuation.get("validation", {}).get("passed", False):
        raise YawNeutralReturnLoweringError("v0.16 requires statically accepted v0.14 actuation")
    if not yaw_authority.get("validation", {}).get("passed", False):
        raise YawNeutralReturnLoweringError("v0.16 requires statically accepted v0.15 authority probe")

    actuation_digest = str(yaw_actuation.get("digestSha256", ""))
    authority_digest = str(yaw_authority.get("digestSha256", ""))
    if yaw_authority.get("sourceYawActuationDigestSha256") != actuation_digest:
        raise YawNeutralReturnLoweringError("v0.15 authority artifact is not chained to supplied v0.14 actuation")
    expected_actuation = str(profile.get("sourceYawActuationDigestSha256", ""))
    expected_authority = str(profile.get("sourceYawAuthorityDigestSha256", ""))
    if expected_actuation and expected_actuation != actuation_digest:
        raise YawNeutralReturnLoweringError("v0.16 source actuation digest mismatch")
    if expected_authority and expected_authority != authority_digest:
        raise YawNeutralReturnLoweringError("v0.16 source authority digest mismatch")

    actuation_probe = yaw_actuation.get("runtimeProbe", {})
    motor_rpm = int(actuation_probe.get("expectedCreativeMotorRpmMagnitude", 0))
    network_ticks = int(actuation_probe.get("networkSettleTicks", 0))
    command_ticks = int(actuation_probe.get("commandTicks", 0))
    stop_ticks = int(actuation_probe.get("stopSettleTicks", 0))
    inverse_ticks = network_ticks + command_ticks
    reverse_sign = int(profile.get("reverseMotorSign", 0))
    physical_settle_ticks = int(profile.get("physicalSettlePhysicsTicks", 0))
    target_tolerance = float(profile.get("targetNeutralToleranceDegrees", 0.0))
    physical_tolerance = float(profile.get("physicalNeutralToleranceDegrees", 0.0))
    minimum_start = float(profile.get("minimumStartingDeflectionDegrees", 0.0))
    if motor_rpm <= 0 or network_ticks < 1 or command_ticks < 1 or stop_ticks < 1:
        raise YawNeutralReturnLoweringError("v0.16 requires positive v0.14 source/timing parameters")
    if reverse_sign != -1:
        raise YawNeutralReturnLoweringError("v0.16 reverseMotorSign must be -1 for the accepted v0.14 fixture")
    if physical_settle_ticks < 1 or target_tolerance <= 0 or physical_tolerance <= 0 or minimum_start <= 0:
        raise YawNeutralReturnLoweringError("v0.16 neutral-return probe parameters must be positive")

    required = {
        "real_kinetic_reverse_command_probe",
        "swivel_target_neutral_return_probe",
        "rudder_physical_neutral_return_probe",
    }
    obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    if required - {str(v.get("id")) for v in obligations}:
        raise YawNeutralReturnLoweringError("v0.16 profile missing required runtime obligations")

    out: dict[str, Any] = {
        "schemaVersion": "aircraft-yaw-neutral-return-ir-0.16",
        "assetId": str(yaw_authority["assetId"]).replace("v0_15_yaw_authority", "v0_16_yaw_neutral_return"),
        "compilerVersion": "aircraft-yaw-neutral-return-lowerer-0.16.0",
        "sourceYawActuationDigestSha256": actuation_digest,
        "sourceYawAuthorityDigestSha256": authority_digest,
        "profileId": profile["profileId"],
        "runtimeProbe": {
            "expectedCreativeMotorRpmMagnitude": motor_rpm,
            "reverseMotorSign": reverse_sign,
            "inverseDriveTicks": inverse_ticks,
            "stopSettleTicks": stop_ticks,
            "physicalSettlePhysicsTicks": physical_settle_ticks,
            "minimumStartingDeflectionDegrees": minimum_start,
            "targetNeutralToleranceDegrees": target_tolerance,
            "physicalNeutralToleranceDegrees": physical_tolerance,
            "acceptance": "reinstall the real Create motor/cog fixture, reverse motor sign for exactly the v0.14 outbound active duration, remove the source, then require both Swivel target and measured child-body yaw to return near neutral",
        },
        "runtimeObligations": obligations,
        "readiness": {
            "neutralReturnProbeReady": True,
            "commandedNeutralReturnQualified": False,
            "passiveSelfCenteringQualified": False,
            "productionControlBindingReady": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "reverse_kinetic_command_unverified",
                "swivel_target_neutral_return_unverified",
                "rudder_physical_neutral_return_unverified",
                "passive_self_centering_unverified",
                "pilot_control_binding_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "source_backed_inverse_create_kinetic_command_contract_for_commanded_rudder_neutral_return",
            "doesNotProve": [
                "passive spring or self-centering behavior",
                "production cockpit or pilot binding",
                "closed-loop handling quality",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
