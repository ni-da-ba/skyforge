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
    schema_anchor = '''        assertTrue("v0.13.1 static topology accepted before runtime probe",
                yawControl.getAsJsonObject("readiness").get("rudderChildCaptureProbeReady").getAsBoolean());
'''
    schema_injected = schema_anchor + '''        JsonObject yawActuation;
        try {
            yawActuation = readJson(requirePathProperty("skyforge.dev.aircraftCompilerYawActuation"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.14 yaw-actuation artifact", failure);
        }
        assertEquals("yaw-actuation schema", "aircraft-yaw-actuation-ir-0.14", string(yawActuation, "schemaVersion"));
        assertEquals("v0.14 source yaw digest", yawControl.get("digestSha256").getAsString(),
                yawActuation.get("sourceYawControlDigestSha256").getAsString());
        assertTrue("v0.14 actuation fixture is statically probe-ready",
                yawActuation.getAsJsonObject("readiness").get("actuationRuntimeProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.14 actuation JSON")

    yaw_call = '''        SkyforgeAircraftCompilerYawRuntimeAcceptance.verify(
                level,
                movedSwivelBearingPos,
                movedRudderPayloadPositions,
                movedRetainedParentMainPositions);

'''
    actuation_call = yaw_call + '''        JsonObject yawActuationProbe = yawActuation.getAsJsonObject("runtimeProbe");
        BlockPos movedDriveCogPos = BASE.offset(blockPos(yawActuation.getAsJsonArray("driveCogCoordinate"))).offset(offset);
        BlockPos movedCreativeMotorPos = BASE.offset(blockPos(yawActuation.getAsJsonArray("creativeMotorCoordinate"))).offset(offset);
        SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.verify(
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
    source = _replace_once(source, yaw_call, actuation_call, "v0.14 actuation invocation")
    return source


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 v0.13.1 runtime harness with v0.14 real-kinetic yaw actuation")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    patched = patch_runtime_source(source)
    args.java_source.write_text(patched, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
