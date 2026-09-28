package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioBoundHydrologySemanticCorpusTest {
    @TempDir Path temp;

    @Test
    void bindsHydrologyToTheExactTerrainSemanticSpecimen() throws Exception {
        StudioBoundHydrologySemanticCorpusCli.main(new String[] {temp.toString()});

        Path terrain = temp.resolve("terrain").resolve("terrain-semantic-volume.json");
        Path hydrology = temp.resolve("hydrology-semantic-layer.json");
        Path index = temp.resolve("index.html");

        assertTrue(Files.isRegularFile(terrain));
        assertTrue(Files.isRegularFile(hydrology));
        assertTrue(Files.isRegularFile(index));

        String terrainJson = Files.readString(terrain);
        String hydrologyJson = Files.readString(hydrology);

        assertTrue(terrainJson.contains(
                "\"artifact_kind\": \"SKYFORGE_TERRAIN_SEMANTIC_VOLUME\""));
        assertTrue(hydrologyJson.contains("\"hydrology_causes\": ["));
        assertTrue(hydrologyJson.contains("\"runoff_potential\":"));
        assertTrue(hydrologyJson.contains("\"retention_potential\":"));
        assertTrue(hydrologyJson.contains("\"drainage_potential\":"));
        assertTrue(hydrologyJson.contains("\"outflow_potential\":"));
        assertTrue(hydrologyJson.contains("\"flow_x\":"));
        assertTrue(hydrologyJson.contains("\"flow_z\":"));
        assertTrue(hydrologyJson.contains("\"cause_stride\": 2"));
        assertTrue(hydrologyJson.contains("\"cause_sample_count\": "));
        assertTrue(hydrologyJson.contains("\"minimum_x\": "));
        assertTrue(hydrologyJson.contains("\"world_frame\": {"));
        String terrainSha = stringValue(terrainJson, "semantic_sha256");
        String boundTerrainSha = stringValue(hydrologyJson, "terrain_semantic_sha256");
        assertEquals(terrainSha, boundTerrainSha);
        assertEquals(64, terrainSha.length());

        String association = stringValue(hydrologyJson, "association_token");
        assertTrue(association.startsWith("sfassoc:v1:"));
        assertTrue(hydrologyJson.contains("\"island_key_hex\": \"000000000000004d\""));
        assertTrue(hydrologyJson.contains("\"group_identifier\": \"studio-s2\""));

        int reaches = integerValue(hydrologyJson, "accepted_reaches");
        int affected = integerValue(hydrologyJson, "affected_grid_samples");
        assertTrue(reaches > 0, "bound specimen should expose at least one accepted F4B reach");
        assertTrue(affected > 0, "bound specimen should expose affected semantic grid samples");
        assertTrue(hydrologyJson.contains("\"reaches\": ["));
        assertTrue(hydrologyJson.contains("\"field_samples\": ["));
        assertTrue(hydrologyJson.contains("\"target_upper_y\":"));
        assertTrue(hydrologyJson.contains("\"terrain_delta_world\":"));
        assertTrue(hydrologyJson.contains("\"water_surface_y\":"));

        assertFalse(hydrologyJson.contains("minecraft:"));
        assertFalse(hydrologyJson.contains("BlockPos"));
    }

    private static String stringValue(String json, String key) {
        String marker = "\"" + key + "\": \"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("missing string property " + key);
        }
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        if (end < 0) {
            throw new AssertionError("unterminated string property " + key);
        }
        return json.substring(valueStart, end);
    }

    private static int integerValue(String json, String key) {
        String marker = "\"" + key + "\": ";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("missing integer property " + key);
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Integer.parseInt(json.substring(valueStart, end));
    }
}
