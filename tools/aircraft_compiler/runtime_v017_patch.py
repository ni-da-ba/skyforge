#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


class RuntimePatchError(ValueError):
    pass


def _replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimePatchError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


def patch_runtime_source(source: str) -> str:
    schema_anchor = '''        assertTrue("v0.16 neutral-return probe is statically ready",
                yawNeutralReturn.getAsJsonObject("readiness").get("neutralReturnProbeReady").getAsBoolean());
'''
    schema_injected = schema_anchor + '''        JsonObject steeringControl;
        try {
            steeringControl = readJson(requirePathProperty("skyforge.dev.aircraftCompilerSteeringControl"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.17 steering-control artifact", failure);
        }
        assertEquals("steering-control schema", "aircraft-steering-control-ir-0.17",
                string(steeringControl, "schemaVersion"));
        assertEquals("v0.17 source actuation digest", yawActuation.get("digestSha256").getAsString(),
                steeringControl.get("sourceYawActuationDigestSha256").getAsString());
        assertEquals("v0.17 source neutral-return digest", yawNeutralReturn.get("digestSha256").getAsString(),
                steeringControl.get("sourceYawNeutralReturnDigestSha256").getAsString());
        assertTrue("v0.17 Steering Wheel source probe is statically ready",
                steeringControl.getAsJsonObject("readiness").get("steeringWheelSourceRuntimeProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.17 steering-control JSON")

    neutral_call = '''        JsonObject yawNeutralReturnProbe = yawNeutralReturn.getAsJsonObject("runtimeProbe");
        SkyforgeAircraftCompilerYawNeutralReturnRuntimeAcceptance.verify(
                level,
                subLevel,
                movedSwivelBearingPos,
                movedDriveCogPos,
                movedCreativeMotorPos,
                yawNeutralReturnProbe.get("expectedCreativeMotorRpmMagnitude").getAsInt(),
                yawNeutralReturnProbe.get("reverseMotorSign").getAsInt(),
                yawNeutralReturnProbe.get("inverseDriveTicks").getAsInt(),
                yawNeutralReturnProbe.get("stopSettleTicks").getAsInt(),
                yawNeutralReturnProbe.get("physicalSettlePhysicsTicks").getAsInt(),
                yawNeutralReturnProbe.get("minimumStartingDeflectionDegrees").getAsDouble(),
                yawNeutralReturnProbe.get("targetNeutralToleranceDegrees").getAsDouble(),
                yawNeutralReturnProbe.get("physicalNeutralToleranceDegrees").getAsDouble());

'''
    steering_call = neutral_call + '''        JsonObject steeringProbe = steeringControl.getAsJsonObject("runtimeProbe");
        SkyforgeAircraftCompilerSteeringControlRuntimeAcceptance.verify(
                level,
                subLevel,
                movedSwivelBearingPos,
                movedDriveCogPos,
                movedCreativeMotorPos,
                steeringProbe.get("wheelCommandDegrees").getAsDouble(),
                steeringProbe.get("expectedSteeringWheelRpmMagnitude").getAsInt(),
                steeringProbe.get("maximumCommandTicks").getAsInt(),
                steeringProbe.get("physicalSettlePhysicsTicks").getAsInt(),
                steeringProbe.get("minimumSwivelTargetDeflectionDegrees").getAsDouble(),
                steeringProbe.get("minimumPhysicalRudderDeflectionDegrees").getAsDouble(),
                steeringProbe.get("targetNeutralToleranceDegrees").getAsDouble(),
                steeringProbe.get("physicalNeutralToleranceDegrees").getAsDouble());

'''
    return _replace_once(source, neutral_call, steering_call, "v0.17 steering-control invocation")


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.17 real Steering Wheel source probe")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
