package io.github.nidaba.skyforge.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkyIslandTerrainSemanticCorpusTest {
    @TempDir Path temp;

    @Test
    void generatesStudioSemanticVolumesAlongsideHumanEvidence() throws Exception {
        SkyIslandTerrainSemanticCorpusCli.main(new String[] {temp.toString()});

        Path specimen = temp.resolve("specimen");
        Path regional = temp.resolve("regional-hub");
        assertTrue(Files.isRegularFile(temp.resolve("index.html")));
        assertTrue(Files.isRegularFile(specimen.resolve("terrain-semantic-volume.json")));
        assertTrue(Files.isRegularFile(regional.resolve("terrain-semantic-volume.json")));
        assertTrue(Files.isRegularFile(specimen.resolve("top-surface-semantics.png")));
        assertTrue(Files.isRegularFile(regional.resolve("top-surface-semantics.png")));

        String specimenJson = Files.readString(specimen.resolve("terrain-semantic-volume.json"));
        String regionalJson = Files.readString(regional.resolve("terrain-semantic-volume.json"));
        assertTrue(specimenJson.contains(
                "\"artifact_kind\": \"SKYFORGE_TERRAIN_SEMANTIC_VOLUME\""));
        assertTrue(regionalJson.contains(
                "\"artifact_kind\": \"SKYFORGE_TERRAIN_SEMANTIC_VOLUME\""));
        assertTrue(specimenJson.contains("\"minecraft_dependency\": false"));
        assertTrue(regionalJson.contains("\"minecraft_dependency\": false"));

        String index = Files.readString(temp.resolve("index.html"));
        assertTrue(index.contains("specimen/terrain-semantic-volume.json"));
        assertTrue(index.contains("regional-hub/terrain-semantic-volume.json"));
    }
}
