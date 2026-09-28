package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
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

        Path hydrologyView = temp.resolve("terrain").resolve("hydrology-plan-view.png");
        assertTrue(Files.isRegularFile(terrain));
        assertTrue(Files.isRegularFile(hydrology));
        assertTrue(Files.isRegularFile(index));
        assertTrue(Files.isRegularFile(hydrologyView));

        String terrainJson = Files.readString(terrain);
        String hydrologyJson = Files.readString(hydrology);
        String indexHtml = Files.readString(index);
        for (String view : new String[] {
                "legend.png",
                "hydrology-plan-view.png",
                "top-surface-semantics.png",
                "east-west-section.png",
                "north-south-section.png",
                "isometric-top-semantics.png"
        }) {
            assertTrue(Files.isRegularFile(temp.resolve("terrain").resolve(view)), "missing terrain view " + view);
            assertTrue(indexHtml.contains("terrain/" + view), "review index must expose " + view);
        }
        assertTrue(indexHtml.contains("Review in Studio"));
        assertTrue(indexHtml.contains("bound hydrology semantic overlay JSON"));
        assertTrue(indexHtml.contains("Blue cells are F4E wet samples"));
        assertTrue(indexHtml.contains("not voxelized or Minecraft-realized water"));
        assertTrue(indexHtml.contains("Human gate"));

        BufferedImage overlay = ImageIO.read(hydrologyView.toFile());
        int wetPixels = 0;
        int reachPixels = 0;
        for (int y = 0; y < overlay.getHeight(); y++) {
            for (int x = 0; x < overlay.getWidth(); x++) {
                int rgb = overlay.getRGB(x, y) & 0x00ffffff;
                if (rgb == (SkyIslandHydrologySemanticEvidenceWriter.WET_SAMPLE_RGB & 0x00ffffff)) {
                    wetPixels++;
                }
                if (rgb == (SkyIslandHydrologySemanticEvidenceWriter.REACH_CENTERLINE_RGB & 0x00ffffff)) {
                    reachPixels++;
                }
            }
        }
        assertTrue(wetPixels > 0, "rendered view must visibly contain F4E wet samples");
        assertTrue(reachPixels > 0, "rendered view must visibly contain accepted reach centerlines");

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
