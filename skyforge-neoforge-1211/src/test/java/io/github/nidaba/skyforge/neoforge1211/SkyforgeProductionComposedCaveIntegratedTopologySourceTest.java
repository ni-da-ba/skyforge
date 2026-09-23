package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeProductionComposedCaveIntegratedTopologySourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void composedCavesFinalizeTopologyBeforeSurfacePopulationAndPresentation() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeComposedCaveStage.java"));
        String catchup = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgePhysicalVolumeCatchupService.java"));

        assertFalse(stage.contains("pendingBiomePresentationChunks(volumeId)"));
        assertTrue(stage.contains("Cave topology is upstream of final surface representation and population"));
        assertTrue(catchup.contains("caveTopologyReadyForSurface"));
        assertTrue(catchup.contains("caveTopologyReadyForPopulation"));

        int cavePump = catchup.indexOf("PumpResult composedPump = pumpComposedCaveQuanta(");
        int surface = catchup.indexOf("SkyforgeAuthoredNativeSurfaceStage.apply");
        int population = catchup.indexOf("SkyforgeNativeSurfacePopulationStage.populateVolumeDeferred");
        int presentation = catchup.indexOf("SkyforgePersistentBiomePresentationStage.present");
        assertTrue(cavePump >= 0 && surface >= 0 && population >= 0 && presentation >= 0);
        assertTrue(cavePump < surface);
        assertTrue(surface < population);
        assertTrue(population < presentation);
    }

    @Test
    void dr50AllowsDownstreamInteriorOccupancyWithoutWeakeningStandaloneCaveProof() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211ProductionComposedCaveDevRuntime.java"));

        assertTrue(source.contains("SkyforgeDr50IntegratedRegionEvidence.enabled()"));
        assertTrue(source.contains("finalEvidence.finalAuthoredAir() > 0"));
        assertTrue(source.contains("finalEvidence.finalAuthoredHydrologyWater()"));
        assertTrue(source.contains("<= authoredPositive"));
        assertTrue(source.contains("== authoredPositive"));
        assertTrue(source.contains("authoredDownstreamOccupied"));
        assertTrue(source.contains("DR50_FLUID_SETTLE_TICKS = 100"));
        assertTrue(source.contains("dr50FluidSettleStartTick = level.getGameTime()"));
        assertTrue(source.contains("level.getGameTime() - dr50FluidSettleStartTick < DR50_FLUID_SETTLE_TICKS"));
    }
}
