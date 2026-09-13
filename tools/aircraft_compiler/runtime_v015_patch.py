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
    schema_anchor = '''        assertTrue("v0.14 actuation fixture is statically probe-ready",
                yawActuation.getAsJsonObject("readiness").get("actuationRuntimeProbeReady").getAsBoolean());
'''
    schema_injected = schema_anchor + '''        JsonObject yawAuthority;
        try {
            yawAuthority = readJson(requirePathProperty("skyforge.dev.aircraftCompilerYawAuthority"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.15 yaw-authority artifact", failure);
        }
        assertEquals("yaw-authority schema", "aircraft-yaw-authority-ir-0.15", string(yawAuthority, "schemaVersion"));
        assertEquals("v0.15 source actuation digest", yawActuation.get("digestSha256").getAsString(),
                yawAuthority.get("sourceYawActuationDigestSha256").getAsString());
        assertTrue("v0.15 yaw-authority probe is statically ready",
                yawAuthority.getAsJsonObject("readiness").get("yawAuthorityProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.15 authority JSON")

    actuation_call = '''        SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify(
                level,
                movedSwivelBearingPos,
                movedDriveCogPos,
                movedCreativeMotorPos,
                yawActuationProbe.get("expectedCreativeMotorRpmMagnitude").getAsInt(),
                yawActuationProbe.get("networkSettleTicks").getAsInt(),
                yawActuationProbe.get("commandTicks").getAsInt(),
                yawActuationProbe.get("stopSettleTicks").getAsInt(),
                yawActuationProbe.get("holdTicks").getAsInt());

'''
    authority_call = actuation_call + '''        JsonObject yawAuthorityProbe = yawAuthority.getAsJsonObject("runtimeProbe");
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
    return _replace_once(source, actuation_call, authority_call, "v0.15 authority invocation")


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.15 genuine Sable yaw-authority probe")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
