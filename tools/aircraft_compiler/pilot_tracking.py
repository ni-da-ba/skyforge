from __future__ import annotations

import hashlib
import json
from typing import Any


class PilotTrackingError(ValueError):
    pass


def lower_pilot_tracking(pilot_client_binding: dict[str, Any], profile: dict[str, Any]) -> dict[str, Any]:
    if pilot_client_binding.get("schemaVersion") != "aircraft-pilot-client-binding-ir-0.20":
        raise PilotTrackingError("v0.21 requires pilot-client-binding IR v0.20")
    if profile.get("schemaVersion") != "aircraft-pilot-tracking-profile-0.21":
        raise PilotTrackingError("v0.21 requires aircraft-pilot-tracking-profile-0.21")
    if not pilot_client_binding.get("validation", {}).get("passed", False):
        raise PilotTrackingError("v0.21 requires accepted v0.20 static binding contract")

    source_digest = str(pilot_client_binding.get("digestSha256", ""))
    if not source_digest:
        raise PilotTrackingError("v0.21 input must carry a deterministic digest")
    if profile.get("sourcePilotClientBindingDigestSha256") not in (None, "", source_digest):
        raise PilotTrackingError("v0.21 source pilot-client-binding digest mismatch")

    readiness = dict(pilot_client_binding.get("readiness", {}))
    if not readiness.get("pilotClientBindingStaticContractPassed", False):
        raise PilotTrackingError("v0.21 requires the accepted v0.20 static contract")
    if not readiness.get("realClientInteractionProbeReady", False):
        raise PilotTrackingError("v0.21 requires the v0.20 actual-client probe boundary")
    if readiness.get("playerSableTrackingQualified", False) or readiness.get("flightQualified", False):
        raise PilotTrackingError("v0.21 source must remain fail-closed for tracking and flight")

    prerequisites = dict(profile.get("acceptedPrerequisites", {}))
    if prerequisites.get("v020ActualClientRuntimeEvidence") is not True:
        raise PilotTrackingError("v0.21 requires explicit acceptance of the v0.20 actual-client runtime boundary")

    tracking = dict(profile.get("trackingContract", {}))
    expected_tracking = {
        "acquisition": "natural_create_seat_vehicle_containment",
        "inspection": "sable_helper_get_tracking_sub_level",
        "parentIdentityMethod": "sable_persistent_sublevel_uuid",
        "measurementBeginsAfterDismount": True,
        "trackingSetterAllowed": False,
        "playerHarnessMutationDuringMeasurementAllowed": False,
    }
    if tracking != expected_tracking:
        raise PilotTrackingError("v0.21 natural tracking contract mismatch")

    probe = dict(profile.get("motionProbe", {}))
    velocity = float(probe.get("translationVelocityMetersPerSecond", 0.0))
    minimum_translation = float(probe.get("minimumParentTranslationBlocks", 0.0))
    tolerance = float(probe.get("serverPlayerDeltaToleranceBlocks", 0.0))
    if not 0.1 <= abs(velocity) <= 5.0:
        raise PilotTrackingError("v0.21 translation velocity must be bounded to [0.1,5.0] m/s magnitude")
    if not 0.05 <= minimum_translation <= 1.0:
        raise PilotTrackingError("v0.21 minimum parent translation must be in [0.05,1.0] blocks")
    if not 0.001 <= tolerance <= 0.25:
        raise PilotTrackingError("v0.21 server/player delta tolerance must be in [0.001,0.25] blocks")
    for field in ("trackingAcquireSettleTicks", "translationSettleTicks"):
        value = probe.get(field)
        if not isinstance(value, int) or not 1 <= value <= 100:
            raise PilotTrackingError(f"v0.21 {field} must be an integer in [1,100]")

    required_obligations = {
        "natural_player_sable_tracking",
        "tracking_parent_identity",
        "post_dismount_inherited_parent_translation",
    }
    obligations = [dict(item, status="unverified") for item in profile.get("runtimeObligations", [])]
    obligation_ids = {str(item.get("id")) for item in obligations}
    missing = required_obligations - obligation_ids
    if missing:
        raise PilotTrackingError(f"v0.21 profile missing runtime obligations: {sorted(missing)}")

    output: dict[str, Any] = {
        "schemaVersion": "aircraft-pilot-tracking-ir-0.21",
        "assetId": str(pilot_client_binding["assetId"]).replace("v0_20_pilot_client_binding", "v0_21_pilot_tracking"),
        "compilerVersion": "aircraft-pilot-tracking-lowerer-0.21.0",
        "sourcePilotClientBindingDigestSha256": source_digest,
        "profileId": profile["profileId"],
        "acceptedPrerequisites": prerequisites,
        "pilotSeatCoordinate": list(pilot_client_binding["pilotSeatCoordinate"]),
        "steeringWheelCoordinate": list(pilot_client_binding["steeringWheelCoordinate"]),
        "trackingContract": expected_tracking,
        "motionProbe": probe,
        "runtimeObligations": obligations,
        "readiness": {
            "pilotTrackingStaticContractPassed": True,
            "v020ActualClientRuntimePrerequisiteAccepted": True,
            "pilotTrackingRuntimeProbeReady": True,
            "playerSableTrackingQualified": False,
            "inheritedParentTranslationQualified": False,
            "completedControlsPersistenceQualified": False,
            "pitchRollQualified": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "natural_player_sable_tracking_unverified",
                "tracking_parent_identity_unverified",
                "post_dismount_inherited_parent_translation_unverified",
                "completed_controls_save_reload_unverified",
                "pitch_roll_controls_unverified",
            ],
        },
        "validation": {
            "passed": True,
            "scope": "natural_sable_player_tracking_and_inherited_parent_translation_contract",
            "doesNotProve": [
                "completed-control save/reload persistence",
                "pitch or roll controls",
                "closed-loop handling quality or stability",
                "sustained powered flight",
                "stable flight",
            ],
        },
    }
    canonical = json.dumps(output, sort_keys=True, separators=(",", ":"))
    output["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return output
