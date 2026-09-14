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
    schema_anchor = '''        assertTrue("v0.20 must not pre-qualify flight",
                !pilotClientBinding.getAsJsonObject("readiness").get("flightQualified").getAsBoolean());
'''
    schema_injected = schema_anchor + '''
        JsonObject pilotTracking;
        try {
            pilotTracking = readJson(requirePathProperty("skyforge.dev.aircraftCompilerPilotTrackingArtifact"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.21 pilot-tracking artifact", failure);
        }
        assertEquals("pilot-tracking schema", "aircraft-pilot-tracking-ir-0.21",
                string(pilotTracking, "schemaVersion"));
        assertEquals("v0.21 source pilot-client-binding digest", pilotClientBinding.get("digestSha256").getAsString(),
                pilotTracking.get("sourcePilotClientBindingDigestSha256").getAsString());
        assertTrue("v0.21 static pilot tracking contract accepted",
                pilotTracking.getAsJsonObject("readiness").get("pilotTrackingStaticContractPassed").getAsBoolean());
        assertTrue("v0.21 records accepted v0.20 actual-client prerequisite",
                pilotTracking.getAsJsonObject("readiness").get("v020ActualClientRuntimePrerequisiteAccepted").getAsBoolean());
        assertTrue("v0.21 runtime tracking probe ready",
                pilotTracking.getAsJsonObject("readiness").get("pilotTrackingRuntimeProbeReady").getAsBoolean());
        assertTrue("v0.21 artifact remains fail-closed before runtime tracking proof",
                !pilotTracking.getAsJsonObject("readiness").get("playerSableTrackingQualified").getAsBoolean());
        assertTrue("v0.21 artifact must not pre-qualify flight",
                !pilotTracking.getAsJsonObject("readiness").get("flightQualified").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.21 pilot-tracking artifact")

    bind_anchor = '''        SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);
'''
    bind_injected = bind_anchor + '''        JsonObject v021Probe = pilotTracking.getAsJsonObject("motionProbe");
        SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);
        SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.configure(
                v021Probe.get("translationVelocityMetersPerSecond").getAsDouble(),
                v021Probe.get("minimumParentTranslationBlocks").getAsDouble(),
                v021Probe.get("serverPlayerDeltaToleranceBlocks").getAsDouble(),
                v021Probe.get("trackingAcquireSettleTicks").getAsInt(),
                v021Probe.get("translationSettleTicks").getAsInt());
        SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.installFromSystemProperty();
'''
    source = _replace_once(source, bind_anchor, bind_injected, "v0.21 tracking runtime binding")
    return source


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 v0.21 pilot tracking proof into disposable runtime source")
    parser.add_argument("runtime_source", type=Path)
    args = parser.parse_args()
    source = args.runtime_source.read_text(encoding="utf-8")
    args.runtime_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
