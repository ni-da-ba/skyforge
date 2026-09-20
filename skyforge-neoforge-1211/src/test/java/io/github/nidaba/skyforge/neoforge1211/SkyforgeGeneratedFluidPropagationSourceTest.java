package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeGeneratedFluidPropagationSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void persistedGeneratedFluidTicksFreezeWhenTerrainBindingIsAbsent() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeGeneratedFluidPropagationStage.java"));
        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeFlowingFluidDomainMixin.java"));
        assertTrue(stage.contains("if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding())"));
        assertTrue(stage.contains("return false;"));
        assertTrue(mixin.contains("cancellable = true"));
        assertTrue(mixin.contains("callback.cancel();"));
    }

    @Test
    void authoredVisibleWaterTicksUseExactAuthoredHydrologyDomain() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeGeneratedFluidPropagationStage.java"));
        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeFlowingFluidDomainMixin.java"));
        assertTrue(stage.contains("authoredVisibleHydrologyVolumeId(position)"));
        assertTrue(stage.contains("BoundaryPolicy.AUTHORED_HYDROLOGY"));
        assertTrue(stage.contains(".filter(volumeId::equals)"));
        assertTrue(stage.contains("return true;"));
        assertTrue(mixin.contains("if (!SkyforgeGeneratedFluidPropagationStage.beginFluidTick"));
        assertTrue(mixin.contains("callback.cancel();"));
    }

    @Test
    void nativeFluidSpringsRetainInteriorShellFenceForIncidentalEdgeDischarge() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeGeneratedFluidPropagationStage.java"));
        assertTrue(stage.contains("GenerationStep.Decoration.FLUID_SPRINGS.ordinal()"));
        assertTrue(stage.contains("? BoundaryPolicy.INTERIOR_SHELL"));
        assertTrue(stage.contains("SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell"));
    }
    @Test
    void dr50SettledEvidenceReconcilesStaleFluidProvenanceWithoutChunkTickets() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeGeneratedFluidPropagationStage.java"));
        String evidence = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeDr50IntegratedRegionEvidence.java"));
        assertTrue(stage.contains("static int reconcileSettledFluids("));
        assertTrue(stage.contains("getChunkNow(position.getX() >> 4, position.getZ() >> 4)"));
        assertTrue(stage.contains("stale.forEach(data::remove);"));
        assertTrue(evidence.contains("SkyforgeGeneratedFluidPropagationStage.reconcileSettledFluids(level, volumeId);"));
    }

    @Test
    void nativeLakeAdmissionFailsClosedWithoutMoltenSemantics() throws IOException {
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNativeLakeAdmissionStage.java"));
        String mixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeLakeFeatureAdmissionMixin.java"));
        assertTrue(stage.contains("configuration.fluid() instanceof SimpleStateProvider"));
        assertTrue(stage.contains("fluidState.getType() == net.minecraft.world.level.material.Fluids.WATER"));
        assertTrue(mixin.contains("context.config()"));
    }

}
