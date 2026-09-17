package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeCarverCursorSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void deferredCarverRetainsDeterministicVanillaNoiseContextForSurfaceRules() throws Exception {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNativeCarverCursor.java"));

        assertTrue(source.contains("targetChunk.getOrCreateNoiseChunk("));
        assertTrue(source.contains("NoiseChunk.forChunk("));
        assertTrue(source.contains("EMPTY_STRUCTURE_DENSITY"));
        assertTrue(source.contains("new DensityFunctions.BeardifierOrMarker()"));
        assertFalse(source.contains("Beardifier.forStructuresInChunk"));
        assertTrue(source.contains("settings.defaultFluid()"));
        assertTrue(source.contains("Blender.empty()"));
        assertFalse(source.contains("targetChunk.getHeightAccessorForGeneration(),\n                null,"));
    }
}
