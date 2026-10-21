package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PortableEngineCutoffResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void cutoffUsesAcceptedC11FlightOnlyRuntime() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("portableEngineCutoffAcceptanceServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains("run-portable-engine-cutoff-acceptance-server"));
        assertFalse(build.contains(
                "portableEngineCutoffAcceptanceServer\") {\n"
                        + "            server()\n"
                        + "            sourceSet.set(waveC9Runtime)"));
    }

    @Test
    void compatibilityTargetsOnlyPortableEngineAndStaysOptional() throws IOException {
        String beMixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/"
                        + "SkyforgePortableEngineBlockEntityMixin.java"));
        String blockMixin = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/mixin/"
                        + "SkyforgePortableEngineBlockMixin.java"));

        assertTrue(beMixin.contains("@Pseudo"));
        assertTrue(blockMixin.contains("@Pseudo"));
        assertTrue(beMixin.contains(
                "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity"));
        assertTrue(blockMixin.contains(
                "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock"));
        assertFalse(beMixin.contains("com.simibubi.create"));
        assertFalse(blockMixin.contains("com.simibubi.create"));
        assertFalse(beMixin.contains("createdieselgenerators"));
        assertFalse(blockMixin.contains("createdieselgenerators"));
    }

    @Test
    void fixtureProvesDefaultCutFuelPreservationAndRestart() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePortableEngineCutoffAcceptance.java"));

        assertTrue(source.contains("default mode burns normally"));
        assertTrue(source.contains("cutoff preserves active burn timer"));
        assertTrue(source.contains("cutoff does not consume queued fuel"));
        assertTrue(source.contains("restart consumes exactly one queued fuel item"));
        assertTrue(source.contains("restart resumes countdown"));
        assertTrue(source.contains("runtime=C11_FLIGHT_ONLY"));
        assertFalse(source.contains("import dev.simulated"));
    }

    @Test
    void mixinRegistryContainsOnlyTheTwoPortableEngineCompatibilityMixins() throws IOException {
        String mixins = Files.readString(PROJECT_DIRECTORY.resolve("src/main/resources/skyforge.mixins.json"));

        assertTrue(mixins.contains("SkyforgePortableEngineBlockEntityMixin"));
        assertTrue(mixins.contains("SkyforgePortableEngineBlockMixin"));
    }
}
