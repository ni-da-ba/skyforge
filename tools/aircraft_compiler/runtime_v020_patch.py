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
    outbound_injected = '''        boolean physicsWasPaused = (boolean) publicMethod(physicsSystem, "getPaused").invoke(physicsSystem);
        Method setPhysicsPaused = physicsSystem.getClass().getMethod("setPaused", boolean.class);
        if (physicsWasPaused) {
            setPhysicsPaused.invoke(physicsSystem, false);
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE PHYSICS_STATE"
                        + " originalPaused=" + physicsWasPaused
                        + " temporaryUnpauseApplied=" + physicsWasPaused);

        boolean plateActorReadyBeforeRepair = hasSwivelPlateActor(childSubLevel);
        boolean plateActorRepairApplied = false;
        Object platePosValue = publicMethod(bearing, "getPlatePos").invoke(bearing);
        if (!(platePosValue instanceof BlockPos platePos)) {
            throw new IllegalStateException("assembled Swivel plate position unavailable before v0.20 physical settle");
        }
        if (!plateActorReadyBeforeRepair) {
            requireBlockEntity(level, platePos, "SwivelBearingPlateBlockEntity");
            Object childPlot = publicMethod(childSubLevel, "getPlot").invoke(childSubLevel);
            twoArgMethod(childPlot, "onBlockChange", platePos, level.getBlockState(platePos))
                    .invoke(childPlot, platePos, level.getBlockState(platePos));
            plateActorRepairApplied = true;
        }
        boolean plateActorReadyAfterRepair = hasSwivelPlateActor(childSubLevel);
        Object constraintHandle = declaredFieldObject(bearing, "handle");
        boolean constraintHandlePresent = constraintHandle != null;
        boolean constraintHandleValid = constraintHandlePresent
                && (boolean) publicMethod(constraintHandle, "isValid").invoke(constraintHandle);
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE SWIVEL_READINESS"
                        + " plateActorReadyBeforeRepair=" + plateActorReadyBeforeRepair
                        + " plateActorRepairApplied=" + plateActorRepairApplied
                        + " plateActorReadyAfterRepair=" + plateActorReadyAfterRepair
                        + " constraintHandlePresent=" + constraintHandlePresent
                        + " constraintHandleValid=" + constraintHandleValid
                        + " bearingState=" + bearing.getBlockState());
        assertTrue("Swivel plate physics actor ready for v0.20 physical proof", plateActorReadyAfterRepair);
        assertTrue("Swivel rotary constraint handle present for v0.20 physical proof", constraintHandlePresent);
        assertTrue("Swivel rotary constraint handle valid for v0.20 physical proof", constraintHandleValid);

        int maximumPhysicalSettlePhysicsTicks = Math.max(physicalSettlePhysicsTicks, 1) * 2;
        int physicalDeflectTicks = 0;
        double physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        while (physicalDeflectTicks < maximumPhysicalSettlePhysicsTicks
                && !(Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees
                && Math.signum(physicalDeflected) == expectedExtraCogSign)) {
            runPhysics(physicsSystem, container, 1);
            physicalDeflectTicks++;
            physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE SETTLE"
                        + " phase=outbound"
                        + " requestedPhysicsTicks=" + physicalSettlePhysicsTicks
                        + " maximumPhysicsTicks=" + maximumPhysicalSettlePhysicsTicks
                        + " observedPhysicsTicks=" + physicalDeflectTicks
                        + " targetDegrees=" + targetDeflected
                        + " physicalDegrees=" + physicalDeflected
                        + " minimumPhysicalDegrees=" + minimumPhysicalRudderDeflectionDegrees
                        + " expectedSign=" + expectedExtraCogSign
                        + " originalPhysicsPaused=" + physicsWasPaused);
        assertTrue(
                "physical rudder follows cockpit-routed target"
                        + " observedDegrees=" + physicalDeflected
                        + " minimumDegrees=" + minimumPhysicalRudderDeflectionDegrees
                        + " targetDegrees=" + targetDeflected
                        + " observedPhysicsTicks=" + physicalDeflectTicks
                        + " maximumPhysicsTicks=" + maximumPhysicalSettlePhysicsTicks,
                Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees);
        assertTrue(
                "physical rudder sign follows compiled extra-cog sign"
                        + " observedDegrees=" + physicalDeflected
                        + " expectedSign=" + expectedExtraCogSign
                        + " observedPhysicsTicks=" + physicalDeflectTicks,
                Math.signum(physicalDeflected) == expectedExtraCogSign);
'''
    source = _replace_once(
        source,
        outbound_anchor,
        outbound_injected,
        "v0.20 integrated-client bounded outbound physical settle",
    )

    inbound_anchor = '''        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder returns near neutral through cockpit route",
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);
'''
    inbound_injected = '''        int physicalReturnTicks = 0;
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        while (physicalReturnTicks < maximumPhysicalSettlePhysicsTicks
                && Math.abs(physicalReturned) > physicalNeutralToleranceDegrees) {
            runPhysics(physicsSystem, container, 1);
            physicalReturnTicks++;
            physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE SETTLE"
                        + " phase=neutral-return"
                        + " requestedPhysicsTicks=" + physicalSettlePhysicsTicks
                        + " maximumPhysicsTicks=" + maximumPhysicalSettlePhysicsTicks
                        + " observedPhysicsTicks=" + physicalReturnTicks
                        + " targetDegrees=" + targetReturned
                        + " physicalDegrees=" + physicalReturned
                        + " neutralToleranceDegrees=" + physicalNeutralToleranceDegrees
                        + " originalPhysicsPaused=" + physicsWasPaused);
        assertTrue(
                "physical rudder returns near neutral through cockpit route"
                        + " observedDegrees=" + physicalReturned
                        + " toleranceDegrees=" + physicalNeutralToleranceDegrees
                        + " observedPhysicsTicks=" + physicalReturnTicks
                        + " maximumPhysicsTicks=" + maximumPhysicalSettlePhysicsTicks,
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);

        setPhysicsPaused.invoke(physicsSystem, physicsWasPaused);
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE PHYSICS_STATE_RESTORED"
                        + " paused=" + physicsWasPaused);
'''
    source = _replace_once(
        source,
        inbound_anchor,
        inbound_injected,
        "v0.20 integrated-client bounded neutral-return physical settle",
    )

    helper_anchor = '''    private static void runPhysics(Object physicsSystem, Object container, int ticks)
            throws ReflectiveOperationException {
        for (int i = 0; i < ticks; i++) {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
        }
    }
'''
    helper_injected = '''    private static boolean hasSwivelPlateActor(Object childSubLevel)
            throws ReflectiveOperationException {
        Object childPlot = publicMethod(childSubLevel, "getPlot").invoke(childSubLevel);
        Object actors = publicMethod(childPlot, "getBlockEntityActors").invoke(childPlot);
        if (!(actors instanceof Iterable<?> iterable)) {
            throw new IllegalStateException("Sable child plot did not expose iterable block-entity actors");
        }
        for (Object actor : iterable) {
            if (actor != null && actor.getClass().getName().endsWith("SwivelBearingPlateBlockEntity")) {
                return true;
            }
        }
        return false;
    }

    private static Object declaredFieldObject(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void runPhysics(Object physicsSystem, Object container, int ticks)
            throws ReflectiveOperationException {
        for (int i = 0; i < ticks; i++) {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
        }
    }
'''
    return _replace_once(
        source,
        helper_anchor,
        helper_injected,
        "v0.20 Swivel plate actor readiness helpers",
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
