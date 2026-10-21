package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeLevelChunkPopulationReadMixinSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void directStableChunkReadsUseThePopulationVirtualNeighborView() throws IOException {
        String blockFluidSource = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeLevelChunkPopulationReadMixin.java"));
        assertTrue(blockFluidSource.contains("SkyforgeWorldGenRegionDomainBridge.populationActive()"));
        assertTrue(blockFluidSource.contains("!SkyforgeWorldGenRegionDomainBridge.isVisible(position)"));
        assertTrue(blockFluidSource.contains("SkyforgeWorldGenRegionDomainBridge.hiddenBlockState(position)"));
        assertTrue(blockFluidSource.contains("getBlockState"));
        assertTrue(blockFluidSource.contains("getFluidState"));

        String heightSource = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeChunkAccessPopulationHeightMixin.java"));
        assertTrue(heightSource.contains("@Mixin(ChunkAccess.class)"));
        assertTrue(heightSource.contains("SkyforgeCarverExecutionStage.virtualFirstFreeHeight"));
        assertTrue(heightSource.contains("SkyforgeWorldGenRegionDomainBridge.populationHeightVirtualizationActive()"));
        assertTrue(heightSource.contains("SkyforgeWorldGenRegionDomainBridge.exactHeight("));
        assertTrue(heightSource.contains("firstFreeHeight.getAsInt() - 1"));

        String bridge = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeWorldGenRegionDomainBridge.java"));
        assertTrue(bridge.contains("public static boolean populationHeightVirtualizationActive()"));
        assertTrue(bridge.contains("GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()"));

        String mixins = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));
        assertTrue(mixins.contains("\"SkyforgeChunkAccessPopulationHeightMixin\""));
    }
}
