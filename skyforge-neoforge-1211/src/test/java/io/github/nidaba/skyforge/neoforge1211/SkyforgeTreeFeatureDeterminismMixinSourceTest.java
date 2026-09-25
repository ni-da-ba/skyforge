package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeTreeFeatureDeterminismMixinSourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(
                    System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath()
            .normalize();

    @Test
    void stableDeferredTreeFinalizationCanonicalizesAllVanillaPositionSets() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/SkyforgeTreeFeatureDeterminismMixin.java"));
        String bridge = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeWorldGenRegionDomainBridge.java"));
        String stage = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgePopulationExecutionStage.java"));
        String config = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));

        assertTrue(source.contains("@Mixin(TreeFeature.class)"));
        assertTrue(source.contains("method = \"updateLeaves\""));
        assertTrue(source.contains("index = 2"));
        assertTrue(source.contains("index = 3"));
        assertTrue(source.contains("index = 4"));
        assertTrue(source.contains("new LinkedHashSet<>(ordered)"));
        assertTrue(source.contains("@Redirect("));
        assertTrue(source.contains("Lcom/google/common/collect/Sets;newHashSet()Ljava/util/HashSet;"));
        assertTrue(source.contains("skyforge$canonicalizeLeafDistanceFrontier"));
        assertTrue(source.contains("new LinkedHashSet<>()"));
        assertTrue(source.contains("SkyforgeWorldGenRegionDomainBridge.deterministicDeferredTreeFinalizationActive()"));
        assertTrue(bridge.contains("public static boolean deterministicDeferredTreeFinalizationActive()"));
        assertTrue(stage.contains("execution.stableDeferredLevel"));
        assertTrue(stage.contains("structuralTreeOperation(execution.operation)"));
        assertTrue(config.contains("\"SkyforgeTreeFeatureDeterminismMixin\""));
    }
}
