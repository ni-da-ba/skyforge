package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeStructureRuntimeOperationSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void runtimeOperationUsesAuthoritativeMinecraftContextAndAcceptedSeams() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNativeStructureRuntimeOperation.java"));
        assertTrue(source.contains("chunkSource.getGeneratorState()"));
        assertTrue(source.contains("level.structureManager()"));
        assertTrue(source.contains("level.getStructureManager()"));
        assertTrue(source.contains("createStructuresForExactSkyforgeVolume("));
        assertTrue(source.contains("placeStructureStartForExactSkyforgeVolume("));
        assertTrue(source.contains("saved.complete(identity);"));
        assertFalse(source.contains("applyBiomeDecoration("));
    }

    @Test
    void catchupOrdersNativeStructuresBeforeSurfacePopulation() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgePhysicalVolumeCatchupService.java"));
        int structures = source.indexOf("SkyforgeNativeStructureRuntimeOperation.execute(");
        int surface = source.indexOf("SkyforgeNativeSurfacePopulationStage.populateDeferred(");
        assertTrue(structures >= 0);
        assertTrue(surface > structures);
    }
}
