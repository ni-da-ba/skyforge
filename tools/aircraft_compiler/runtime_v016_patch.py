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
    schema_anchor = '''        assertTrue("v0.15 yaw-authority probe is statically ready",
                yawAuthority.getAsJsonObject("readiness").get("yawAuthorityProbeReady").getAsBoolean());
'''
    schema_injected = schema_anchor + '''        JsonObject yawNeutralReturn;
        try {
            yawNeutralReturn = readJson(requirePathProperty("skyforge.dev.aircraftCompilerYawNeutralReturn"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.16 yaw-neutral-return artifact", failure);
        }
        assertEquals("yaw-neutral-return schema", "aircraft-yaw-neutral-return-ir-0.16",
                string(yawNeutralReturn, "schemaVersion"));
        assertEquals("v0.16 source actuation digest", yawActuation.get("digestSha256").getAsString(),
                yawNeutralReturn.get("sourceYawActuationDigestSha256").getAsString());
        assertEquals("v0.16 source authority digest", yawAuthority.get("digestSha256").getAsString(),
                yawNeutralReturn.get("sourceYawAuthorityDigestSha256").getAsString());
        assertTrue("v0.16 neutral-return probe is statically ready",
                yawNeutralReturn.getAsJsonObject("readiness").get("neutralReturnProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.16 neutral-return JSON")

    authority_call = '''        JsonObject yawAuthorityProbe = yawAuthority.getAsJsonObject("runtimeProbe");
        SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance.verify(
                level,
                subLevel,
                movedSwivelBearingPos,
                yawAuthorityProbe.get("forwardSpeedMps").getAsDouble(),
                yawAuthorityProbe.get("servoSettlePhysicsTicks").getAsInt(),
                yawAuthorityProbe.get("minimumPhysicalDeflectionDegrees").getAsDouble(),
                yawAuthorityProbe.get("minimumLateralImpulseMagnitude").getAsDouble(),
                yawAuthorityProbe.get("minimumYawImpulseMagnitude").getAsDouble());

'''
    neutral_call = authority_call + '''        JsonObject yawNeutralReturnProbe = yawNeutralReturn.getAsJsonObject("runtimeProbe");
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
    return _replace_once(source, authority_call, neutral_call, "v0.16 neutral-return invocation")


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.16 commanded neutral-return probe")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
