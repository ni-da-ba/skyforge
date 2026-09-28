package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import io.github.nidaba.skyforge.world.WorldRegionTerrain;
import io.github.nidaba.skyforge.world.WorldSampleGrid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkyIslandTerrainSemanticEvidenceWriterTest {
    private static final Pattern SEMANTIC_SHA =
            Pattern.compile("\\"semantic_sha256\\": \\"([0-9a-f]{64})\\"");
    private static final Pattern SEMANTIC_PAYLOAD =
            Pattern.compile("\\"semantics_base64\\": \\"([^\\"]+)\\"");

    @TempDir Path temp;

    @Test
    void studioVolumeCarriesExactAuthoritativeSemanticBytes() throws Exception {
        WorldSampleGrid grid = new WorldSampleGrid(
                -4.0,
                100.0,
                8.0,
                4.0,
                2.0,
                4.0,
                2,
                3,
                2);
        byte[] semantics = {
            ordinal(SkyIslandTerrainSemantic.AIR),
            ordinal(SkyIslandTerrainSemantic.EDGE_SHELL),
            ordinal(SkyIslandTerrainSemantic.SURFACE_MANTLE),
            ordinal(SkyIslandTerrainSemantic.UNDERSIDE_SHELL),
            ordinal(SkyIslandTerrainSemantic.SHALLOW_INTERIOR),
            ordinal(SkyIslandTerrainSemantic.DEEP_MASS),
            ordinal(SkyIslandTerrainSemantic.DEEP_MASS),
            ordinal(SkyIslandTerrainSemantic.SHALLOW_INTERIOR),
            ordinal(SkyIslandTerrainSemantic.UNDERSIDE_SHELL),
            ordinal(SkyIslandTerrainSemantic.SURFACE_MANTLE),
            ordinal(SkyIslandTerrainSemantic.EDGE_SHELL),
            ordinal(SkyIslandTerrainSemantic.AIR),
        };
        WorldRegionTerrain terrain = new WorldRegionTerrain(grid, semantics, 12, 2);
        SkyIslandTerrainSemanticEvidenceWriter writer =
                new SkyIslandTerrainSemanticEvidenceWriter();

        Path first = temp.resolve("first");
        Path second = temp.resolve("second");
        writer.write(terrain, first, "studio specimen", "test-version");
        writer.write(terrain, second, "studio specimen", "test-version");

        Path firstArtifact = first.resolve("terrain-semantic-volume.json");
        Path secondArtifact = second.resolve("terrain-semantic-volume.json");
        assertTrue(Files.isRegularFile(firstArtifact));
        assertEquals(Files.readString(firstArtifact), Files.readString(secondArtifact));

        String json = Files.readString(firstArtifact);
        assertTrue(json.contains("\"schema_version\": 1"));
        assertTrue(json.contains(
                "\"artifact_kind\": \"SKYFORGE_TERRAIN_SEMANTIC_VOLUME\""));
        assertTrue(json.contains("\"kind\": \"BASE64_UINT8_ORDINAL\""));
        assertTrue(json.contains(
                "\"linear_index\": \"x + x_samples * (z + z_samples * y)\""));
        assertTrue(json.contains("\"sample_count\": 12"));
        assertTrue(json.contains("\"backend_neutral_semantics\": true"));
        assertTrue(json.contains("\"minecraft_dependency\": false"));

        Matcher sha = SEMANTIC_SHA.matcher(json);
        assertTrue(sha.find());
        assertEquals(terrain.sha256(), sha.group(1));

        Matcher payload = SEMANTIC_PAYLOAD.matcher(json);
        assertTrue(payload.find());
        assertArrayEquals(semantics, Base64.getDecoder().decode(payload.group(1)));

        int previous = -1;
        for (SkyIslandTerrainSemantic semantic : SkyIslandTerrainSemantic.values()) {
            int current = json.indexOf("\"name\": \"" + semantic.name() + "\"");
            assertTrue(current > previous, "semantic legend must preserve enum ordinal order");
            previous = current;
        }
    }

    private static byte ordinal(SkyIslandTerrainSemantic semantic) {
        return (byte) semantic.ordinal();
    }
}
