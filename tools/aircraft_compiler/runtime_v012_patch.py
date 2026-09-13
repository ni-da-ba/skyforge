#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


class RuntimePatchError(ValueError):
    pass


def _replace_once(source: str, old: str, new: str, label: str) -> str:
    if source.count(old) != 1:
        raise RuntimePatchError(f"{label}: expected exactly one anchor, found {source.count(old)}")
    return source.replace(old, new)


def patch_runtime_source(source: str) -> str:
    replacements = {
        "private static final int EXPECTED_MOVING_MAIN_BODY = 114;": (
            "private static final int EXPECTED_MOVING_MAIN_BODY = 114;\n"
            "    private static final int EXPECTED_V012_MOVING_MAIN_BODY = 118;"
        ),
        "private static final int EXPECTED_PRIMARY_SABLE_PAYLOAD = 123;": "private static final int EXPECTED_PRIMARY_SABLE_PAYLOAD = 127;",
        "private static final int EXPECTED_GLUE_DOMAINS = 4;": "private static final int EXPECTED_GLUE_DOMAINS = 5;",
        'assertInt("encoded glue domain count", EXPECTED_GLUE_DOMAINS, glueDomainsJson.size());': 'assertInt("encoded v0.11 glue domain count", EXPECTED_GLUE_DOMAINS - 1, glueDomainsJson.size());',
        'assertInt("exact moved main-body block count", EXPECTED_MOVING_MAIN_BODY, movedMainCount);': 'assertInt("exact moved v0.12 main-body block count", EXPECTED_V012_MOVING_MAIN_BODY, movedMainCount);',
    }
    for old, new in replacements.items():
        source = _replace_once(source, old, new, old)

    schema_anchor = '        assertEquals("glue schema", "aircraft-glue-encoding-ir-0.11", string(glueEncoding, "schemaVersion"));\n'
    schema_injected = schema_anchor + '''        JsonObject powertrain;
        try {
            powertrain = readJson(requirePathProperty("skyforge.dev.aircraftCompilerPowertrain"));
        } catch (IOException failure) {
            throw new IllegalStateException("could not read v0.12 powertrain artifact", failure);
        }
        assertEquals("powertrain schema", "aircraft-powertrain-ir-0.12", string(powertrain, "schemaVersion"));
'''
    source = _replace_once(source, schema_anchor, schema_injected, "powertrain JSON")

    expected_anchor = '        assertInt("main-body expected block count", EXPECTED_MOVING_MAIN_BODY, mainExpected.size());\n'
    expected_injected = '''        JsonArray powertrainPlacements = powertrain.getAsJsonArray("placements");
        for (JsonElement raw : powertrainPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            BlockPos pos = blockPos(placement.getAsJsonArray("lattice"));
            ExpectedBlock expected = expectedBlock(placement);
            String mode = string(placement, "mode");
            if (mode.equals("replace")) {
                assertTrue("v0.12 replacement targets existing main-body cell " + pos, mainExpected.containsKey(pos));
                mainExpected.put(pos, expected);
            } else if (mode.equals("add")) {
                assertTrue("v0.12 addition coordinate free " + pos,
                        !mainExpected.containsKey(pos) && !propellerPayloadExpected.containsKey(pos));
                mainExpected.put(pos, expected);
                mainCoordinates.add(pos);
            } else {
                fail("unexpected v0.12 placement mode " + mode);
            }
        }
        assertInt("v0.12 main-body expected block count", EXPECTED_V012_MOVING_MAIN_BODY, mainExpected.size());
'''
    source = _replace_once(source, expected_anchor, expected_injected, "powertrain expected map")

    place_anchor = '        level.setBlock(BASE.offset(assemblerRelative), materialize(assemblerExpected), 3);\n'
    place_injected = place_anchor + '''        for (JsonElement raw : powertrainPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            level.setBlock(
                    BASE.offset(blockPos(placement.getAsJsonArray("lattice"))),
                    materialize(expectedBlock(placement)),
                    3);
        }
'''
    source = _replace_once(source, place_anchor, place_injected, "powertrain block placement")

    glue_anchor = '        assertInt("runtime SuperGlueEntity domain insertion count", EXPECTED_GLUE_DOMAINS, insertedGlue.size());\n'
    glue_injected = '''        JsonObject powerplantGlue = powertrain.getAsJsonObject("powerplantGlueDomain");
        BlockPos powerplantFrom = blockPos(powerplantGlue.getAsJsonArray("from"));
        BlockPos powerplantTo = blockPos(powerplantGlue.getAsJsonArray("to"));
        assertSelectionWithinLimit("powerplant", powerplantFrom, powerplantTo);
        Entity powerplantGlueEntity = addGlueDomain(level, "powerplant", powerplantFrom, powerplantTo);
        for (BlockPos child : propellerPayloadCoordinates) {
            assertTrue("powerplant glue excludes propeller payload center " + child,
                    !powerplantGlueEntity.getBoundingBox().contains(Vec3.atCenterOf(BASE.offset(child))));
        }
        insertedGlue.add(powerplantGlueEntity);
''' + glue_anchor
    source = _replace_once(source, glue_anchor, glue_injected, "powerplant glue")

    marker = '"AIRCRAFT_001_RUNTIME_ASSEMBLY PASS"'
    marker_index = source.find(marker)
    if marker_index < 0 or source.find(marker, marker_index + 1) >= 0:
        raise RuntimePatchError("expected exactly one primary runtime PASS marker")
    insert_at = source.rfind("        LOGGER.log(", 0, marker_index)
    if insert_at < 0:
        raise RuntimePatchError("could not locate primary runtime LOGGER.log call")
    propeller_injected = '''        BlockPos movedBearingPos = null;
        for (Map.Entry<BlockPos, ExpectedBlock> entry : mainExpected.entrySet()) {
            if (entry.getValue().id().toString().equals("aeronautics:propeller_bearing")) {
                assertTrue("single moved propeller bearing", movedBearingPos == null);
                movedBearingPos = BASE.offset(entry.getKey()).offset(offset);
            }
        }
        assertTrue("moved propeller bearing located from compiler manifest", movedBearingPos != null);
        List<BlockPos> movedPropellerPayloadPositions = new ArrayList<>();
        for (BlockPos relative : propellerPayloadExpected.keySet()) {
            movedPropellerPayloadPositions.add(BASE.offset(relative).offset(offset));
        }
        SkyforgeAircraftCompilerPropellerRuntimeAcceptance.verify(
                level, movedBearingPos, movedPropellerPayloadPositions);

        Map<String, BlockPos> movedPowertrainRoles = new LinkedHashMap<>();
        for (JsonElement raw : powertrainPlacements) {
            JsonObject placement = raw.getAsJsonObject();
            String role = string(placement, "role");
            BlockPos movedRolePos = BASE.offset(blockPos(placement.getAsJsonArray("lattice"))).offset(offset);
            assertTrue("unique v0.12 powertrain role " + role, movedPowertrainRoles.put(role, movedRolePos) == null);
        }
        String[] requiredPowertrainRoles = {"engine_port", "engine_starboard", "governor", "governor_cog", "prop_shaft"};
        for (String role : requiredPowertrainRoles) {
            assertTrue("moved v0.12 powertrain role present " + role, movedPowertrainRoles.containsKey(role));
        }
        SkyforgeAircraftCompilerPowertrainRuntimeAcceptance.verify(
                level,
                movedPowertrainRoles.get("engine_port"),
                movedPowertrainRoles.get("engine_starboard"),
                movedPowertrainRoles.get("governor"),
                movedPowertrainRoles.get("governor_cog"),
                movedPowertrainRoles.get("prop_shaft"),
                movedBearingPos);

'''
    return source[:insert_at] + propeller_injected + source[insert_at:]


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch AIRCRAFT-001 runtime acceptance to v0.12 recapture/performance semantics")
    parser.add_argument("java_source", type=Path)
    args = parser.parse_args()
    source = args.java_source.read_text(encoding="utf-8")
    patched = patch_runtime_source(source)
    args.java_source.write_text(patched, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
