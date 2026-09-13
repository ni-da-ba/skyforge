from __future__ import annotations

import hashlib
import json
from typing import Any


class YawActuationLoweringError(ValueError):
    pass


def _coord(values: list[int] | tuple[int, int, int]) -> tuple[int, int, int]:
    if len(values) != 3:
        raise YawActuationLoweringError(f"expected 3-vector, got {values!r}")
    return tuple(int(v) for v in values)


def _state_matches(actual: dict[str, Any], expected: dict[str, Any]) -> bool:
    return all(str(actual.get(k)) == str(v) for k, v in expected.items())


def lower_yaw_actuation(
    manifest: dict[str, Any],
    powertrain: dict[str, Any],
    yaw_control: dict[str, Any],
    profile: dict[str, Any],
) -> dict[str, Any]:
    if manifest.get("schemaVersion") != "aircraft-probe-placement-manifest-ir-0.9":
        raise YawActuationLoweringError("v0.14 requires probe manifest v0.9")
    if powertrain.get("schemaVersion") != "aircraft-powertrain-ir-0.12":
        raise YawActuationLoweringError("v0.14 requires powertrain IR v0.12")
    if yaw_control.get("schemaVersion") != "aircraft-yaw-control-ir-0.13.1":
        raise YawActuationLoweringError("v0.14 requires corrected yaw-control IR v0.13.1")
    if profile.get("schemaVersion") != "aircraft-yaw-actuation-profile-0.14":
        raise YawActuationLoweringError("v0.14 requires aircraft-yaw-actuation-profile-0.14")
    if not yaw_control.get("validation", {}).get("passed", False):
        raise YawActuationLoweringError("v0.14 requires statically accepted v0.13.1 yaw topology")
    if not yaw_control.get("readiness", {}).get("rudderChildCaptureProbeReady", False):
        raise YawActuationLoweringError("v0.14 requires rudder child capture probe readiness")

    expected_yaw_digest = str(profile.get("sourceYawControlDigestSha256", ""))
    if expected_yaw_digest and str(yaw_control.get("digestSha256")) != expected_yaw_digest:
        raise YawActuationLoweringError(
            f"v0.14 source yaw digest mismatch: actual={yaw_control.get('digestSha256')} expected={expected_yaw_digest}"
        )

    effective: dict[tuple[int, int, int], dict[str, Any]] = {}
    for raw in manifest.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        if c in effective:
            raise YawActuationLoweringError(f"duplicate manifest coordinate {c!r}")
        effective[c] = p

    for raw in powertrain.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        mode = str(p.get("mode", ""))
        if mode == "replace":
            if c not in effective:
                raise YawActuationLoweringError(f"v0.12 replacement source missing {c!r}")
            effective[c] = p
        elif mode == "add":
            if c in effective:
                raise YawActuationLoweringError(f"v0.12 addition collision {c!r}")
            effective[c] = p
        else:
            raise YawActuationLoweringError(f"unsupported v0.12 placement mode {mode!r}")

    for raw in yaw_control.get("placements", []):
        p = dict(raw)
        c = _coord(p["lattice"])
        mode = str(p.get("mode", ""))
        if mode == "add_main" or mode == "add_control_child":
            if c in effective:
                raise YawActuationLoweringError(f"v0.13.1 addition collision while reconstructing {c!r}")
            effective[c] = p
        elif mode == "remove_main":
            if c not in effective:
                raise YawActuationLoweringError(f"v0.13.1 removal source missing while reconstructing {c!r}")
            effective.pop(c)
        else:
            raise YawActuationLoweringError(f"unsupported v0.13.1 placement mode {mode!r}")

    swivel = _coord(yaw_control["swivelBearingCoordinate"])
    fixture = list(profile.get("runtimeFixturePlacements", []))
    if len(fixture) != 2:
        raise YawActuationLoweringError("v0.14 requires exactly two post-assembly actuator fixture placements")

    roles: dict[str, dict[str, Any]] = {}
    absolute: dict[str, tuple[int, int, int]] = {}
    for raw in fixture:
        p = dict(raw)
        role = str(p.get("role", "")).strip()
        if not role or role in roles:
            raise YawActuationLoweringError(f"runtime fixture requires unique non-empty role: {role!r}")
        roles[role] = p
        offset = _coord(p["offsetFromSwivel"])
        c = tuple(swivel[i] + offset[i] for i in range(3))
        if c in effective:
            raise YawActuationLoweringError(f"runtime fixture collides with aircraft placement at {c!r}")
        if c in absolute.values():
            raise YawActuationLoweringError(f"runtime fixture coordinate collision at {c!r}")
        absolute[role] = c

    required_roles = {"yaw_test_drive_cog", "yaw_test_creative_motor"}
    if set(roles) != required_roles:
        raise YawActuationLoweringError(f"v0.14 fixture roles must be exactly {sorted(required_roles)}")

    cog = roles["yaw_test_drive_cog"]
    motor = roles["yaw_test_creative_motor"]
    cog_coord = absolute["yaw_test_drive_cog"]
    motor_coord = absolute["yaw_test_creative_motor"]

    expected_source_rpm = int(profile.get("expectedCreativeMotorRpm", 16))
    settle_ticks = int(profile.get("networkSettleTicks", 8))
    command_ticks = int(profile.get("commandTicks", 5))
    stop_settle_ticks = int(profile.get("stopSettleTicks", 8))
    hold_ticks = int(profile.get("holdTicks", 4))
    if expected_source_rpm <= 0 or settle_ticks < 1 or command_ticks < 1 or stop_settle_ticks < 1 or hold_ticks < 1:
        raise YawActuationLoweringError("v0.14 runtime timing and source RPM must be positive")

    topology_checks = {
        "creativeMotorResourceExact": motor.get("resourceId") == "create:creative_motor",
        "creativeMotorFacesUp": _state_matches(dict(motor.get("blockState", {})), {"facing": "up"}),
        "driveCogResourceExact": cog.get("resourceId") == "create:cogwheel",
        "driveCogAxisMatchesYawAxis": _state_matches(dict(cog.get("blockState", {})), {"axis": "y"}),
        "driveCogAdjacentToSwivelAcrossZ": cog_coord == (swivel[0], swivel[1], swivel[2] + 1),
        "creativeMotorDirectlyBelowDriveCog": motor_coord == (cog_coord[0], cog_coord[1] - 1, cog_coord[2]),
        "fixtureCoordinatesFreeBeforeRuntimeInjection": all(c not in effective for c in absolute.values()),
        "fixtureDoesNotOccupyRudderChild": not any(c in {_coord(v) for v in yaw_control.get("rudderChildCoordinates", [])} for c in absolute.values()),
        "fixtureDoesNotOccupyAirGap": not any(c in {_coord(v) for v in yaw_control.get("airGapCoordinates", [])} for c in absolute.values()),
    }

    runtime_obligations = [dict(v, status="unverified") for v in profile.get("runtimeObligations", [])]
    required_obligations = {
        "yaw_test_fixture_network_probe",
        "rudder_signed_target_angle_probe",
        "rudder_command_stop_hold_probe",
    }
    if required_obligations - {str(v.get("id")) for v in runtime_obligations}:
        raise YawActuationLoweringError("v0.14 profile missing required runtime obligations")

    passed = all(topology_checks.values())
    out: dict[str, Any] = {
        "schemaVersion": "aircraft-yaw-actuation-ir-0.14",
        "assetId": str(yaw_control["assetId"]).replace("v0_13_1_yaw_control", "v0_14_yaw_actuation"),
        "compilerVersion": "aircraft-yaw-actuation-lowerer-0.14.0",
        "sourceYawControlDigestSha256": yaw_control["digestSha256"],
        "profileId": profile["profileId"],
        "swivelBearingCoordinate": list(swivel),
        "driveCogCoordinate": list(cog_coord),
        "creativeMotorCoordinate": list(motor_coord),
        "runtimeFixturePlacements": [
            dict(cog, lattice=list(cog_coord)),
            dict(motor, lattice=list(motor_coord)),
        ],
        "runtimeProbe": {
            "expectedCreativeMotorRpmMagnitude": expected_source_rpm,
            "networkSettleTicks": settle_ticks,
            "commandTicks": command_ticks,
            "stopSettleTicks": stop_settle_ticks,
            "holdTicks": hold_ticks,
            "expectedCreateAngularDegreesPerTickPerRpm": 0.3,
            "expectedSmallCogMeshRatioMagnitude": 1.0,
            "note": "signed target response is accepted from observed Swivel extra-cog RPM; the compiler does not infer aircraft yaw force from this kinematic probe",
        },
        "topologyChecks": topology_checks,
        "runtimeObligations": runtime_obligations,
        "readiness": {
            "yawActuationStaticTopologyPassed": passed,
            "actuationRuntimeProbeReady": passed,
            "productionControlBindingReady": False,
            "neutralReturnQualified": False,
            "yawForceQualified": False,
            "runtimeQualificationReady": False,
            "flightQualified": False,
            "runtimeBlockers": [
                "yaw_test_fixture_network_unverified",
                "rudder_signed_target_angle_unverified",
                "rudder_command_stop_hold_unverified",
                "rudder_neutral_return_unverified",
                "rudder_yaw_force_unverified",
                "pilot_control_binding_unverified",
            ],
        },
        "validation": {
            "passed": passed,
            "scope": "post_assembly_source_backed_create_creative_motor_plus_small_cog_fixture_for_swivel_extra_cog_actuation_probe",
            "doesNotProve": [
                "a production aircraft control mechanism",
                "pilot or cockpit input binding",
                "neutral-return behavior",
                "rudder side force or yaw moment",
                "stable flight or flight qualification",
            ],
        },
    }
    canonical = json.dumps(out, sort_keys=True, separators=(",", ":"))
    out["digestSha256"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return out
