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


def _replace_region(source: str, start: str, end: str, replacement: str, label: str) -> str:
    start_index = source.find(start)
    if start_index < 0 or source.find(start, start_index + 1) >= 0:
        raise RuntimePatchError(f"{label}: expected exactly one start anchor")
    end_index = source.find(end, start_index)
    if end_index < 0:
        raise RuntimePatchError(f"{label}: end anchor not found")
    if source.find(end, end_index + len(end)) >= 0:
        raise RuntimePatchError(f"{label}: expected exactly one end anchor")
    end_index += len(end)
    return source[:start_index] + replacement + source[end_index:]


def patch_runtime_source(source: str) -> str:
    replacements = {
        "private static final int EXPECTED_V012_MOVING_MAIN_BODY = 118;": (
            "private static final int EXPECTED_V012_MOVING_MAIN_BODY = 118;\n"
            "    private static final int EXPECTED_V013_MOVING_PARENT_MAIN_BODY = 117;\n"
            "    private static final int EXPECTED_RUDDER_PAYLOAD = 4;"
        ),
        "private static final int EXPECTED_PRIMARY_SABLE_PAYLOAD = 127;":
            "private static final int EXPECTED_PRIMARY_SABLE_PAYLOAD = 130;",
        "private static final int EXPECTED_GLUE_DOMAINS = 5;":
            "private static final int EXPECTED_GLUE_DOMAINS = 6;",
    }
    for old, new in replacements.items():
        source = _replace_once(source, old, new, old)

    schema_anchor = (
        '        assertEquals("powertrain schema", "aircraft-powertrain-ir-0.12", '
        'string(powertrain, "schemaVersion"));\n'
    )
    schema_injected = schema_anchor + '''        JsonObject yawControl;
        try {
            yawControl = readJson(requirePathProperty("skyforge.dev.aircraftCompilerYawControl"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.13 yaw-control artifact", failure);
        }
        assertEquals("yaw-control schema", "aircraft-yaw-control-ir-0.13", string(yawControl, "schemaVersion"));
        assertTrue("v0.13 static topology accepted before runtime probe",
                yawControl.getAsJsonObject("readiness").get("rudderChildCaptureProbeReady").getAsBoolean());
'''
    source = _replace_once(source, schema_anchor, schema_injected, "yaw-control JSON")

    expected_anchor = (
        '        assertInt("v0.12 main-body expected block count", '
        'EXPECTED_V012_MOVING_MAIN_BODY, mainExpected.size());\n'
    )
    expected_injected = expected_anchor + '''        LinkedHashMap<BlockPos, ExpectedBlock> rudderExpected = new LinkedHashMap<>();
        JsonArray yawPlacements = yawControl.getAsJsonArray("placements");
        for (JsonElement raw : yawPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            BlockPos pos = blockPos(placement.getAsJsonArray("lattice"));
            ExpectedBlock expected = expectedBlock(placement);
            String mode = string(placement, "mode");
            if (mode.equals("replace_main")) {
                assertTrue("v0.13 parent replacement targets existing main-body cell " + pos,
                        mainExpected.containsKey(pos));
                mainExpected.put(pos, expected);
            } else if (mode.equals("reclassify_main_to_control_child")) {
                ExpectedBlock removed = mainExpected.remove(pos);
                assertTrue("v0.13 reclassified rudder seed existed on parent " + pos, removed != null);
                assertTrue("v0.13 reclassified rudder seed removed from main coordinate set " + pos,
                        mainCoordinates.remove(pos));
                assertTrue("unique v0.13 rudder payload coordinate " + pos,
                        rudderExpected.put(pos, expected) == null);
            } else if (mode.equals("add_control_child")) {
                assertTrue("v0.13 rudder addition coordinate free " + pos,
                        !mainExpected.containsKey(pos)
                                && !propellerPayloadExpected.containsKey(pos)
                                && !rudderExpected.containsKey(pos));
                rudderExpected.put(pos, expected);
            } else {
                fail("unexpected v0.13 placement mode " + mode);
            }
        }
        assertInt("v0.13 parent-main expected block count",
                EXPECTED_V013_MOVING_PARENT_MAIN_BODY, mainExpected.size());
        assertInt("v0.13 rudder expected block count", EXPECTED_RUDDER_PAYLOAD, rudderExpected.size());
'''
    source = _replace_once(source, expected_anchor, expected_injected, "v0.13 expected maps")

    old_primary = '''        assertInt("primary Sable payload count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                mainExpected.size() + propellerPayloadExpected.size());'''
    new_primary = '''        assertInt("primary Sable payload count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                mainExpected.size() + propellerPayloadExpected.size() + rudderExpected.size());'''
    source = _replace_once(source, old_primary, new_primary, "v0.13 primary expected count")

    old_prepare = (
        "        prepareAirspace(level, mainExpected.keySet(), propellerPayloadExpected.keySet());"
    )
    new_prepare = '''        Set<BlockPos> primaryAirframeCoordinates = new LinkedHashSet<>(mainExpected.keySet());
        primaryAirframeCoordinates.addAll(rudderExpected.keySet());
        prepareAirspace(level, primaryAirframeCoordinates, propellerPayloadExpected.keySet());'''
    source = _replace_once(source, old_prepare, new_prepare, "v0.13 airspace preparation")

    powertrain_place_end = '''        for (JsonElement raw : powertrainPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            level.setBlock(
                    BASE.offset(blockPos(placement.getAsJsonArray("lattice"))),
                    materialize(expectedBlock(placement)),
                    3);
        }
'''
    place_injected = powertrain_place_end + '''        for (JsonElement raw : yawPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            level.setBlock(
                    BASE.offset(blockPos(placement.getAsJsonArray("lattice"))),
                    materialize(expectedBlock(placement)),
                    3);
        }
'''
    source = _replace_once(
        source, powertrain_place_end, place_injected, "v0.13 block placement"
    )

    glue_start = '        JsonArray glueDomainsJson = glueEncoding.getAsJsonArray("glueDomains");\n'
    glue_end = (
        '        assertInt("runtime SuperGlueEntity domain insertion count", '
        'EXPECTED_GLUE_DOMAINS, insertedGlue.size());'
    )
    glue_replacement = '''        JsonArray runtimeGlueDomainsJson = yawControl.getAsJsonArray("runtimeGlueDomains");
        assertInt("encoded v0.13 runtime glue domain count", EXPECTED_GLUE_DOMAINS, runtimeGlueDomainsJson.size());
        List<Entity> insertedGlue = new ArrayList<>();
        BlockPos yawSwivelRelative = blockPos(yawControl.getAsJsonArray("swivelBearingCoordinate"));
        int rudderChildGlueDomains = 0;
        for (JsonElement raw : runtimeGlueDomainsJson) {
            JsonObject domain = raw.getAsJsonObject();
            String name = string(domain, "name");
            String classification = string(domain, "classification");
            BlockPos from = blockPos(domain.getAsJsonArray("from"));
            BlockPos to = blockPos(domain.getAsJsonArray("to"));
            assertSelectionWithinLimit(name, from, to);
            Entity glue = addGlueDomain(level, name, from, to);
            for (BlockPos child : propellerPayloadCoordinates) {
                assertTrue("glue domain " + name + " excludes propeller payload center " + child,
                        !glue.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(child))));
            }
            if (classification.equals("parent_main")) {
                for (BlockPos rudder : rudderExpected.keySet()) {
                    assertTrue("parent glue domain " + name + " excludes rudder payload center " + rudder,
                            !glue.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(rudder))));
                }
            } else if (classification.equals("yaw_control_child")) {
                rudderChildGlueDomains++;
                assertTrue("rudder child glue excludes Swivel Bearing center",
                        !glue.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(yawSwivelRelative))));
                for (BlockPos rudder : rudderExpected.keySet()) {
                    assertTrue("rudder child glue contains rudder payload center " + rudder,
                            glue.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(rudder))));
                }
            } else {
                fail("unexpected v0.13 glue classification " + classification);
            }
            insertedGlue.add(glue);
        }
        assertInt("exactly one rudder-child glue domain", 1, rudderChildGlueDomains);
        assertInt("runtime SuperGlueEntity domain insertion count", EXPECTED_GLUE_DOMAINS, insertedGlue.size());'''
    source = _replace_region(
        source, glue_start, glue_end, glue_replacement, "v0.13 runtime glue domains"
    )

    transfer_old = '''        int movedMainCount = assertTransferred(level, mainExpected, offset, "main-body");
        int movedPropellerPayloadCount =
                assertTransferred(level, propellerPayloadExpected, offset, "propeller-payload");
        assertInt("exact moved v0.12 main-body block count", EXPECTED_V012_MOVING_MAIN_BODY, movedMainCount);
        assertInt("exact transferred propeller payload count", EXPECTED_PROPELLER_PAYLOAD, movedPropellerPayloadCount);
        assertInt("exact primary Sable transferred block count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                movedMainCount + movedPropellerPayloadCount);'''
    transfer_new = '''        int movedMainCount = assertTransferred(level, mainExpected, offset, "parent-main-body");
        int movedPropellerPayloadCount =
                assertTransferred(level, propellerPayloadExpected, offset, "propeller-payload");
        int movedRudderPayloadCount =
                assertTransferred(level, rudderExpected, offset, "rudder-payload");
        assertInt("exact moved v0.13 parent-main block count",
                EXPECTED_V013_MOVING_PARENT_MAIN_BODY, movedMainCount);
        assertInt("exact transferred propeller payload count", EXPECTED_PROPELLER_PAYLOAD, movedPropellerPayloadCount);
        assertInt("exact transferred rudder payload count", EXPECTED_RUDDER_PAYLOAD, movedRudderPayloadCount);
        assertInt("exact primary Sable transferred block count", EXPECTED_PRIMARY_SABLE_PAYLOAD,
                movedMainCount + movedPropellerPayloadCount + movedRudderPayloadCount);'''
    source = _replace_once(source, transfer_old, transfer_new, "v0.13 transfer counts")

    transfer_bounds_anchor = "        transferredCoordinates.addAll(propellerPayloadCoordinates);\n"
    transfer_bounds_injected = (
        transfer_bounds_anchor
        + "        transferredCoordinates.addAll(rudderExpected.keySet());\n"
    )
    source = _replace_once(
        source, transfer_bounds_anchor, transfer_bounds_injected, "v0.13 COM bounds"
    )

    propeller_call_anchor = "        SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify(\n"
    yaw_call = '''        BlockPos movedSwivelBearingPos = BASE.offset(yawSwivelRelative).offset(offset);
        List<BlockPos> movedRudderPayloadPositions = new ArrayList<>();
        for (BlockPos relative : rudderExpected.keySet()) {
            movedRudderPayloadPositions.add(BASE.offset(relative).offset(offset));
        }
        List<BlockPos> movedRetainedParentMainPositions = new ArrayList<>();
        for (BlockPos relative : mainExpected.keySet()) {
            movedRetainedParentMainPositions.add(BASE.offset(relative).offset(offset));
        }
        SkyforgeAircraftCompilerYawRuntimeAcceptance.verify(
                level,
                movedSwivelBearingPos,
                movedRudderPayloadPositions,
                movedRetainedParentMainPositions);

'''
    source = _replace_once(
        source,
        propeller_call_anchor,
        yaw_call + propeller_call_anchor,
        "v0.13 yaw child invocation",
    )

    old_log = (
        '+ " primarySablePayload=" + (movedMainCount + movedPropellerPayloadCount)'
    )
    new_log = (
        '+ " primarySablePayload=" + '
        '(movedMainCount + movedPropellerPayloadCount + movedRudderPayloadCount)'
    )
    source = _replace_once(source, old_log, new_log, "v0.13 assembly PASS count")

    return source


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Patch AIRCRAFT-001 v0.12 runtime harness to v0.13 yaw-child recapture semantics"
    )
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    patched = patch_runtime_source(source)
    args.java_source.write_text(patched, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
