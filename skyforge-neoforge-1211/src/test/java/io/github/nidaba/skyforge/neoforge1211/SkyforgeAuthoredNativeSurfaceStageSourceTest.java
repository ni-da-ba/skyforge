package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeAuthoredNativeSurfaceStageSourceTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("skyforge.test.projectDirectory", "."));

    @Test
    void nativeSurfaceRulesAreDrivenByExactIslandEcologyWithoutDonorTerrain() throws Exception {
        String stage = Files.readString(PROJECT.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeAuthoredNativeSurfaceStage.java"));

        assertTrue(stage.contains("SkyforgeNativeSurfacePopulationStage.planForVolume"));
        assertTrue(stage.contains("plan.biomeResolver()"));
        assertTrue(stage.contains("withDifferentSource"));
        assertTrue(stage.contains("NoiseGeneratorSettings.OVERWORLD"));
        assertTrue(stage.contains("new ProtoChunk("));
        assertTrue(stage.contains("SkyIslandTerrainSemantic.SURFACE_MANTLE"));
        assertTrue(stage.contains("Hydrology owns occupancy and geometry, not the final material palette."));
        assertTrue(stage.contains("instanceof FallingBlock"));
        assertTrue(stage.contains("stableWhenCopied"));
        assertFalse(stage.contains("must not repaint"));
        assertFalse(stage.contains("dr70_surface_donor"));
    }

    @Test
    void catchupSurfacesBeforeNativePopulation() throws Exception {
        String catchup = Files.readString(PROJECT.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePhysicalVolumeCatchupService.java"));
        int surface = catchup.indexOf("SkyforgeAuthoredNativeSurfaceStage.apply");
        int readiness = catchup.indexOf("candidate -> authoredSurfaceReadyForVolume", surface);
        int population = catchup.indexOf("SkyforgeNativeSurfacePopulationStage.populateVolumeDeferred", readiness);
        assertTrue(surface >= 0);
        assertTrue(readiness > surface);
        assertTrue(population > readiness);
        assertTrue(catchup.contains("SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId).isPresent()"));
    }
}
