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
    schema_anchor = '''        assertTrue("v0.19 must not pre-qualify Steering Wheel packet round trip",
                !pilotInteraction.getAsJsonObject("readiness").get("steeringPacketRoundTripQualified").getAsBoolean());
'''
    schema_injected = schema_anchor + '''
        JsonObject pilotClientBinding;
        try {
            pilotClientBinding = readJson(requirePathProperty("skyforge.dev.aircraftCompilerPilotClientBinding"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.20 pilot-client-binding artifact", failure);
        }
        assertEquals("pilot-client-binding schema", "aircraft-pilot-client-binding-ir-0.20",
                string(pilotClientBinding, "schemaVersion"));
        assertEquals("v0.20 source pilot-interaction digest", pilotInteraction.get("digestSha256").getAsString(),
                pilotClientBinding.get("sourcePilotInteractionDigestSha256").getAsString());
        assertTrue("v0.20 static pilot client binding contract accepted",
                pilotClientBinding.getAsJsonObject("readiness").get("pilotClientBindingStaticContractPassed").getAsBoolean());
        assertTrue("v0.20 actual-client probe ready",
                pilotClientBinding.getAsJsonObject("readiness").get("realClientInteractionProbeReady").getAsBoolean());
        assertTrue("v0.20 must not pre-qualify packet round trip",
                !pilotClientBinding.getAsJsonObject("readiness").get("steeringPacketRoundTripQualified").getAsBoolean());
        assertTrue("v0.20 must not pre-qualify pilot occupancy",
                !pilotClientBinding.getAsJsonObject("readiness").get("pilotOccupancyRuntimeQualified").getAsBoolean());
        assertTrue("v0.20 must defer moving-body player tracking",
                !pilotClientBinding.getAsJsonObject("readiness").get("playerSableTrackingQualified").getAsBoolean());
        assertTrue("v0.20 must not pre-qualify flight",
                !pilotClientBinding.getAsJsonObject("readiness").get("flightQualified").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.20 pilot-client-binding artifact")

    invocation_anchor = '''                string(v019WheelState, "facing"),
                string(v019WheelState, "on_floor"),
                string(v019WheelState, "waterlogged"));

        SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(
'''
    invocation_injected = '''                string(v019WheelState, "facing"),
                string(v019WheelState, "on_floor"),
                string(v019WheelState, "waterlogged"));

        JsonObject v020Probe = pilotClientBinding.getAsJsonObject("commandProbe");
        SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.bindAssembledParentSubLevel(subLevel);
        SkyforgeAircraftCompilerPilotClientBridge.publish(
                movedPilotSeatPos,
                movedCockpitWheelPos,
                v020Probe.get("mouseYawDelta").getAsDouble(),
                v020Probe.get("minimumAbsoluteTargetDegrees").getAsDouble(),
                v020Probe.get("maximumAbsoluteTargetDegrees").getAsDouble(),
                v020Probe.get("activePacketSettleTicks").getAsInt(),
                v020Probe.get("releasePacketSettleTicks").getAsInt(),
                v020Probe.get("seatMountSettleTicks").getAsInt(),
                v020Probe.get("seatDismountSettleTicks").getAsInt());

        SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(
'''
    source = _replace_once(source, invocation_anchor, invocation_injected, "v0.20 actual-client bridge publication")

    return source


def patch_cockpit_settle_diagnostics(source: str) -> str:
    outbound_anchor = '''        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder follows cockpit-routed target",
                Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees);
        assertTrue("physical rudder sign follows compiled extra-cog sign",
                Math.signum(physicalDeflected) == expectedExtraCogSign);
'''
    outbound_injected = '''        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE SETTLE"
                        + " phase=outbound"
                        + " physicsTicks=" + physicalSettlePhysicsTicks
                        + " targetDegrees=" + targetDeflected
                        + " physicalDegrees=" + physicalDeflected
                        + " minimumPhysicalDegrees=" + minimumPhysicalRudderDeflectionDegrees
                        + " expectedSign=" + expectedExtraCogSign);
        assertTrue(
                "physical rudder follows cockpit-routed target"
                        + " observedDegrees=" + physicalDeflected
                        + " minimumDegrees=" + minimumPhysicalRudderDeflectionDegrees
                        + " targetDegrees=" + targetDeflected,
                Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees);
        assertTrue(
                "physical rudder sign follows compiled extra-cog sign"
                        + " observedDegrees=" + physicalDeflected
                        + " expectedSign=" + expectedExtraCogSign,
                Math.signum(physicalDeflected) == expectedExtraCogSign);
'''
    source = _replace_once(
        source,
        outbound_anchor,
        outbound_injected,
        "v0.20 integrated-client outbound physical-settle diagnostics",
    )

    inbound_anchor = '''        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder returns near neutral through cockpit route",
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);
'''
    inbound_injected = '''        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE SETTLE"
                        + " phase=neutral-return"
                        + " physicsTicks=" + physicalSettlePhysicsTicks
                        + " targetDegrees=" + targetReturned
                        + " physicalDegrees=" + physicalReturned
                        + " neutralToleranceDegrees=" + physicalNeutralToleranceDegrees);
        assertTrue(
                "physical rudder returns near neutral through cockpit route"
                        + " observedDegrees=" + physicalReturned
                        + " toleranceDegrees=" + physicalNeutralToleranceDegrees,
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);
'''
    return _replace_once(
        source,
        inbound_anchor,
        inbound_injected,
        "v0.20 integrated-client neutral-return physical-settle diagnostics",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.20 actual-client handoff")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")

    cockpit_source = args.java_source.with_name("SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.java")
    if not cockpit_source.is_file():
        raise RuntimePatchError(f"v0.20 cockpit runtime source missing: {cockpit_source}")
    cockpit_source.write_text(
        patch_cockpit_settle_diagnostics(cockpit_source.read_text(encoding="utf-8")),
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
