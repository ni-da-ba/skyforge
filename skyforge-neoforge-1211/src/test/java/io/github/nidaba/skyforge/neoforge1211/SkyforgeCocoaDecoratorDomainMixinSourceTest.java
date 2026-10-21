package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeCocoaDecoratorDomainMixinSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void emptyTrunkGuardIsScopedToExactVolumePopulation() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeCocoaDecoratorDomainMixin.java"));
        String bridge = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeWorldGenRegionDomainBridge.java"));
        String config = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));

        assertTrue(source.contains("SkyforgeWorldGenRegionDomainBridge.populationActive()"));
        assertTrue(source.contains("context.logs().isEmpty()"));
        assertTrue(source.contains("callback.cancel()"));
        assertTrue(bridge.contains("public static boolean populationActive()"));
        assertTrue(config.contains("SkyforgeCocoaDecoratorDomainMixin"));
    }
}
