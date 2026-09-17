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
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeLevelChunkPopulationReadMixin.java"));
        assertTrue(source.contains("SkyforgeWorldGenRegionDomainBridge.populationActive()"));
        assertTrue(source.contains("!SkyforgeWorldGenRegionDomainBridge.isVisible(position)"));
        assertTrue(source.contains("SkyforgeWorldGenRegionDomainBridge.hiddenBlockState(position)"));
        assertTrue(source.contains("getBlockState"));
        assertTrue(source.contains("getFluidState"));
    }
}
