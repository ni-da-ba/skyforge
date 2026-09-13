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
    schema_injected = schema_anchor + '''        JsonObject steeringControl;
        try {
            steeringControl = readJson(requirePathProperty("skyforge.dev.aircraftCompilerSteeringControl"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.17 steering-control artifact", failure);
        }
        assertEquals("steering-control schema", "aircraft-steering-control-ir-0.17",
                string(steeringControl, "schemaVersion"));
        assertTrue("v0.17 Steering Wheel source contract accepted",
                steeringControl.getAsJsonObject("validation").get("passed").getAsBoolean());

        JsonObject cockpitRoute;
        try {
            cockpitRoute = readJson(requirePathProperty("skyforge.dev.aircraftCompilerCockpitYawRoute"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.18 cockpit-yaw-route artifact", failure);
        }
        assertEquals("cockpit-yaw-route schema", "aircraft-cockpit-yaw-route-ir-0.18",
                string(cockpitRoute, "schemaVersion"));
        assertEquals("v0.18 source manifest digest", manifest.get("digestSha256").getAsString(),
                cockpitRoute.get("sourceManifestDigestSha256").getAsString());
        assertEquals("v0.18 source powertrain digest", powertrain.get("digestSha256").getAsString(),
                cockpitRoute.get("sourcePowertrainDigestSha256").getAsString());
        assertEquals("v0.18 source yaw digest", yawControl.get("digestSha256").getAsString(),
                cockpitRoute.get("sourceYawControlDigestSha256").getAsString());
        assertEquals("v0.18 source Steering Wheel digest", steeringControl.get("digestSha256").getAsString(),
                cockpitRoute.get("sourceSteeringControlDigestSha256").getAsString());
        assertTrue("v0.18 static route accepted before runtime probe",
                cockpitRoute.getAsJsonObject("readiness").get("cockpitRouteRuntimeProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "v0.18 compiler artifacts")

    expected_anchor = '''        assertInt("v0.13.1 parent-main expected block count",
                EXPECTED_V0131_MOVING_PARENT_MAIN_BODY, mainExpected.size());
        assertInt("v0.13.1 rudder expected block count", EXPECTED_RUDDER_PAYLOAD, rudderExpected.size());
'''
    expected_injected = expected_anchor + '''        JsonArray cockpitRoutePlacements = cockpitRoute.getAsJsonArray("placements");
        for (JsonElement raw : cockpitRoutePlacements) {
            JsonObject placement = raw.getAsJsonObject();
            BlockPos pos = blockPos(placement.getAsJsonArray("lattice"));
            assertEquals("v0.18 route placement mode", "add_main", string(placement, "mode"));
            assertTrue("v0.18 route coordinate free " + pos,
                    !mainExpected.containsKey(pos)
                            && !propellerPayloadExpected.containsKey(pos)
                            && !rudderExpected.containsKey(pos));
            mainExpected.put(pos, expectedBlock(placement));
            assertTrue("v0.18 route coordinate unique " + pos, mainCoordinates.add(pos));
        }
        int expectedCockpitParentMain = cockpitRoute.getAsJsonObject("metrics")
                .get("resultingMovingParentMainBodyPlacementCount").getAsInt();
        int expectedCockpitPrimaryTransfer = cockpitRoute.getAsJsonObject("metrics")
                .get("expectedPrimarySableTransferCount").getAsInt();
        assertInt("v0.18 parent-main expected block count", expectedCockpitParentMain, mainExpected.size());
'''
    source = _replace_once(source, expected_anchor, expected_injected, "v0.18 expected maps")

    old_primary = '''        assertInt("primary Sable payload count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                mainExpected.size() + propellerPayloadExpected.size() + rudderExpected.size());'''
    new_primary = '''        assertInt("v0.18 primary Sable payload count", expectedCockpitPrimaryTransfer,
                mainExpected.size() + propellerPayloadExpected.size() + rudderExpected.size());'''
    source = _replace_once(source, old_primary, new_primary, "v0.18 primary expected count")

    yaw_place_end = '''        for (JsonElement raw : yawPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            BlockPos pos = blockPos(placement.getAsJsonArray("lattice"));
            String mode = string(placement, "mode");
            if (mode.equals("remove_main")) {
                level.setBlock(BASE.offset(pos), Blocks.AIR.defaultBlockState(), 3);
            } else {
                level.setBlock(BASE.offset(pos), materialize(expectedBlock(placement)), 3);
            }
        }
'''
    yaw_place_injected = yaw_place_end + '''        for (JsonElement raw : cockpitRoutePlacements) {
            JsonObject placement = raw.getAsJsonObject();
            level.setBlock(
                    BASE.offset(blockPos(placement.getAsJsonArray("lattice"))),
                    materialize(expectedBlock(placement)),
                    3);
        }
'''
    source = _replace_once(source, yaw_place_end, yaw_place_injected, "v0.18 route block placement")

    glue_anchor = '''        assertInt("runtime SuperGlueEntity domain insertion count", EXPECTED_GLUE_DOMAINS, insertedGlue.size());'''
    glue_injected = glue_anchor + '''
        JsonObject cockpitGlue = cockpitRoute.getAsJsonObject("routeGlueDomain");
        BlockPos cockpitGlueFrom = blockPos(cockpitGlue.getAsJsonArray("from"));
        BlockPos cockpitGlueTo = blockPos(cockpitGlue.getAsJsonArray("to"));
        assertSelectionWithinLimit("cockpit_yaw_route", cockpitGlueFrom, cockpitGlueTo);
        Entity cockpitGlueEntity = addGlueDomain(level, "cockpit_yaw_route", cockpitGlueFrom, cockpitGlueTo);
        for (BlockPos child : propellerPayloadCoordinates) {
            assertTrue("cockpit route glue excludes propeller payload center " + child,
                    !cockpitGlueEntity.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(child))));
        }
        for (BlockPos rudder : rudderExpected.keySet()) {
            assertTrue("cockpit route glue excludes rudder payload center " + rudder,
                    !cockpitGlueEntity.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(rudder))));
        }
        for (BlockPos gap : airGapCoordinates) {
            assertTrue("cockpit route glue excludes air-gap center " + gap,
                    !cockpitGlueEntity.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(gap))));
        }
        insertedGlue.add(cockpitGlueEntity);
        assertInt("v0.18 runtime SuperGlueEntity domain insertion count", EXPECTED_GLUE_DOMAINS + 1, insertedGlue.size());'''
    source = _replace_once(source, glue_anchor, glue_injected, "v0.18 route glue")

    transfer_old = '''        assertInt("exact moved v0.13.1 parent-main block count",
                EXPECTED_V0131_MOVING_PARENT_MAIN_BODY, movedMainCount);
        assertInt("exact transferred propeller payload count", EXPECTED_PROPELLER_PAYLOAD, movedPropellerPayloadCount);
        assertInt("exact transferred rudder payload count", EXPECTED_RUDDER_PAYLOAD, movedRudderPayloadCount);
        assertInt("exact primary Sable transferred block count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                movedMainCount + movedPropellerPayloadCount + movedRudderPayloadCount);'''
    transfer_new = '''        assertInt("exact moved v0.18 parent-main block count", expectedCockpitParentMain, movedMainCount);
        assertInt("exact transferred propeller payload count", EXPECTED_PROPELLER_PAYLOAD, movedPropellerPayloadCount);
        assertInt("exact transferred rudder payload count", EXPECTED_RUDDER_PAYLOAD, movedRudderPayloadCount);
        assertInt("exact v0.18 primary Sable transferred block count", expectedCockpitPrimaryTransfer,
                movedMainCount + movedPropellerPayloadCount + movedRudderPayloadCount);'''
    source = _replace_once(source, transfer_old, transfer_new, "v0.18 transfer counts")

    yaw_call_old = '''        SkyforgeAircraftCompilerYawRuntimeAcceptance.verify(
                level,
                movedSwivelBearingPos,
                movedRudderPayloadPositions,
                movedRetainedParentMainPositions);
'''
    yaw_call_new = '''        SkyforgeAircraftCompilerYawRuntimeAcceptance.verify(
                level,
                movedSwivelBearingPos,
                movedRudderPayloadPositions,
                movedRetainedParentMainPositions,
                expectedCockpitParentMain);
'''
    source = _replace_once(source, yaw_call_old, yaw_call_new, "v0.18 expanded yaw-parent invariant")

    propeller_call_anchor = '''        SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify(
'''
    cockpit_call = '''        BlockPos movedCockpitWheelPos = BASE.offset(
                blockPos(cockpitRoute.getAsJsonArray("steeringWheelCoordinate"))).offset(offset);
        BlockPos movedCockpitDriveCogPos = BASE.offset(
                blockPos(cockpitRoute.getAsJsonArray("driveCogCoordinate"))).offset(offset);
        JsonObject cockpitSign = cockpitRoute.getAsJsonObject("signContract");
        JsonObject steeringProbe = steeringControl.getAsJsonObject("runtimeProbe");
        SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.verify(
                level,
                subLevel,
                movedSwivelBearingPos,
                movedCockpitDriveCogPos,
                movedCockpitWheelPos,
                steeringProbe.get("wheelCommandDegrees").getAsDouble(),
                cockpitSign.get("positiveWheelCommandGeneratedSign").getAsInt(),
                cockpitSign.get("expectedTailDriveCogSign").getAsInt(),
                cockpitSign.get("expectedSwivelExtraCogSign").getAsInt(),
                cockpitSign.get("expectedRpmMagnitude").getAsInt(),
                steeringProbe.get("maximumCommandTicks").getAsInt(),
                steeringProbe.get("physicalSettlePhysicsTicks").getAsInt(),
                steeringProbe.get("minimumSwivelTargetDeflectionDegrees").getAsDouble(),
                steeringProbe.get("minimumPhysicalRudderDeflectionDegrees").getAsDouble(),
                steeringProbe.get("targetNeutralToleranceDegrees").getAsDouble(),
                steeringProbe.get("physicalNeutralToleranceDegrees").getAsDouble());

'''
    source = _replace_once(source, propeller_call_anchor, cockpit_call + propeller_call_anchor, "v0.18 cockpit route invocation")

    log_anchor = '+ " primarySablePayload=" + (movedMainCount + movedPropellerPayloadCount + movedRudderPayloadCount)'
    log_injected = log_anchor + '\n                        + " cockpitRouteParentMain=" + movedMainCount\n                        + " cockpitRouteGlueDomains=" + insertedGlue.size()'
    source = _replace_once(source, log_anchor, log_injected, "v0.18 assembly log")

    return source


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime harness with v0.18 production cockpit-to-rudder route")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    args.java_source.write_text(patch_runtime_source(source), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
