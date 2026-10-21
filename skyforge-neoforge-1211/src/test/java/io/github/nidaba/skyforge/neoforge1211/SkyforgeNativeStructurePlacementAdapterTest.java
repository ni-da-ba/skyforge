package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeStructurePlacementAdapterTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void adapterPlacesOneExplicitStartWithoutReplayingDecoration() throws IOException {
        String adapter = adapterSource();

        assertTrue(adapter.contains("StructureStart start"));
        assertTrue(adapter.contains("SkyforgeGenerationDomainStage.requireExactIslandVolume(volumeId);"));
        assertTrue(adapter.contains("SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)"));
        assertTrue(adapter.contains("start.placeInChunk("));
        assertFalse(adapter.contains("applyBiomeDecoration("));
        assertFalse(adapter.contains("startsForStructure("));
        assertFalse(adapter.contains("createStructures("));
    }

    @Test
    void adapterReconstructsVanillaStructurePlacementSeedAndChunkBounds() throws IOException {
        String adapter = adapterSource();

        assertTrue(adapter.contains("structure.step() == start.getStructure().step()"));
        assertTrue(adapter.contains("random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ())"));
        assertTrue(adapter.contains("random.setFeatureSeed(decorationSeed, structureIndex, start.getStructure().step().ordinal())"));
        assertTrue(adapter.contains("BoundingBox writableArea = writableArea(chunk);"));
        assertTrue(adapter.contains("SkyforgeStructurePlacementExecutionStage.open(volumeId, start.getBoundingBox())"));
        assertTrue(adapter.contains("SkyforgeDeferredPopulationPostProcessingBridge.flushIfActive();"));
    }

    private static String adapterSource() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeNoiseBasedChunkGenerator.java"));
        int start = source.indexOf("void placeStructureStartForExactSkyforgeVolume(");
        int end = source.indexOf("\n    private static BoundingBox writableArea", start);
        assertTrue(start >= 0, "post-admission native structure placement adapter must exist");
        assertTrue(end > start, "placement adapter must remain bounded");
        return source.substring(start, end);
    }
}
