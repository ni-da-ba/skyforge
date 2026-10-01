package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationFeatureLightDomainMixinSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void deferredPopulationUsesDeterministicFeaturesStageBrightnessOnlyInsidePopulationScope()
            throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgePopulationFeatureLightDomainMixin.java"));
        String config = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));

        assertTrue(source.contains("@Mixin(LevelLightEngine.class)"));
        assertTrue(source.contains("method = \"getRawBrightness\""));
        assertTrue(source.contains("SkyforgeWorldGenRegionDomainBridge.populationActive()"));
        assertTrue(source.contains("callback.setReturnValue(0)"));
        assertTrue(config.contains("SkyforgePopulationFeatureLightDomainMixin"));
    }
}
