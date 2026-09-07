package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC21CreateResourceAuthorityResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void productionResourcesDoNotGloballyOverrideCreateResourceModifiers() {
        Path mainResources = PROJECT_DIRECTORY.resolve("src/main/resources");
        assertFalse(Files.exists(mainResources.resolve(
                "data/create/neoforge/biome_modifier/zinc_ore.json")));
        assertFalse(Files.exists(mainResources.resolve(
                "data/create/neoforge/biome_modifier/striated_ores_overworld.json")));
    }

    @Test
    void controlPackIsBuiltOnlyInsideDisposableC21RunWorld() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("run-wave-c21-resource-suppressed-server"));
        assertTrue(build.contains("wave-c21-control"));
        assertTrue(build.contains("data/create/neoforge/biome_modifier/zinc_ore.json"));
        assertTrue(build.contains("data/create/neoforge/biome_modifier/striated_ores_overworld.json"));
        assertTrue(build.contains("\\\"type\\\": \\\"neoforge:none\\\""));
        assertTrue(build.contains("skyforge.dev.waveC21CreateResourceAuthority"));
    }

    @Test
    void fixtureChecksModifierTypesAndRetainedAssetsWithoutCreateCompileImports() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC21CreateResourceAuthorityAcceptance.java"));

        assertTrue(source.contains("create\", \"zinc_ore"));
        assertTrue(source.contains("create\", \"striated_ores_overworld"));
        assertTrue(source.contains("AddFeaturesBiomeModifier"));
        assertTrue(source.contains("create\", \"deepslate_zinc_ore"));
        assertTrue(source.contains("create\", \"crimsite"));
        assertFalse(source.contains("com.simibubi.create"));
    }

    @Test
    void productionEntrypointKeepsC21FixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains(
                "SkyforgeWaveC21CreateResourceAuthorityAcceptance.installFromSystemProperty();"));
    }
}
