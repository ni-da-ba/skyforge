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
    schema_anchor = '''        assertTrue("v0.18 static route accepted before runtime probe",
                cockpitRoute.getAsJsonObject("readiness").get("cockpitRouteRuntimeProbeReady").getAsBoolean());
'''
    schema_injected = schema_anchor + '''
        JsonObject pilotInteraction;
        try {
            pilotInteraction = readJson(requirePathProperty("skyforge.dev.aircraftCompilerPilotInteraction"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.19 pilot-interaction artifact", failure);
        }
        assertEquals("pilot-interaction schema", "aircraft-pilot-interaction-ir-0.19",
                string(pilotInteraction, "schemaVersion"));
        assertEquals("v0.19 source cockpit-route digest", cockpitRoute.get("digestSha256").getAsString(),
                pilotInteraction.get("sourceCockpitYawRouteDigestSha256").getAsString());
        assertTrue("v0.19 static pilot interaction contract accepted",
                pilotInteraction.getAsJsonObject("readiness").get("pilotInteractionStaticContractPassed").getAsBoolean());
        assertTrue("v0.19 assembled cockpit presence probe ready",
                pilotInteraction.getAsJsonObject("readiness").get("assembledCockpitPresenceProbeReady").getAsBoolean());
        assertTrue("v0.19 must not pre-qualify pilot occupancy",
                !pilotInteraction.getAsJsonObject("readiness").get("pilotOccupancyRuntimeQualified").getAsBoolean());
        assertTrue("v0.19 must not pre-qualify Steering Wheel packet round trip",
                !pilotInteraction.getAsJsonObject("readiness").get("steeringPacketRoundTripQualified").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.19 pilot-interaction artifact")

    invocation_anchor = '''        JsonObject cockpitSign = cockpitRoute.getAsJsonObject("signContract");
        JsonObject steeringProbe = steeringControl.getAsJsonObject("runtimeProbe");
        SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(
'''
    invocation_injected = '''        JsonObject cockpitSign = cockpitRoute.getAsJsonObject("signContract");
        JsonObject steeringProbe = steeringControl.getAsJsonObject("runtimeProbe");
        BlockPos movedPilotSeatPos = BASE.offset(
                blockPos(pilotInteraction.getAsJsonArray("pilotSeatCoordinate"))).offset(offset);
        assertEquals(
                "v0.19 Steering Wheel coordinate chains to v0.18",
                blockPos(cockpitRoute.getAsJsonArray("steeringWheelCoordinate")),
                blockPos(pilotInteraction.getAsJsonArray("steeringWheelCoordinate")));
        JsonObject v019WheelState = pilotInteraction.getAsJsonObject("steeringWheelBlockState");
        SkyforgeAircraftCompilerPilotInteractionRuntimeAcceptance.verifyAssembledCockpitPresence(
                level,
                movedPilotSeatPos,
                movedCockpitWheelPos,
                string(pilotInteraction, "pilotSeatResource"),
                string(pilotInteraction, "steeringWheelResource"),
                string(v019WheelState, "facing"),
                string(v019WheelState, "on_floor"),
                string(v019WheelState, "waterlogged"));

        SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(
'''
    source = _replace_once(source, invocation_anchor, invocation_injected, "v0.19 assembled cockpit presence invocation")

    return source


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.19 headless cockpit presence proof")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
