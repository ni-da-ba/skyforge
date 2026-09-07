package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC15PortalLinkingResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void buildProvidesRetainedFlightPortalProfileAndC10Datapack() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("val waveC15PortalRuntime = sourceSets.create(\"waveC15PortalRuntime\")"));
        assertTrue(build.contains("create(\"waveC15PortalLinkingAcceptanceServer\")"));
        assertTrue(build.contains("sourceSet.set(waveC15PortalRuntime)"));
        assertTrue(build.contains("waveC15ResolvePinnedMods"));
        assertTrue(build.contains("src/development/wave-c2-nether-scale-datapack"));
    }

    @Test
    void acceptanceExercisesVanillaPortalDestinationLinkAndCreatePaths() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC15PortalLinkingAcceptance.java"));

        assertTrue(source.contains("NetherPortalBlock#getPortalDestination"));
        assertTrue(source.contains(".getPortalDestination(sourceLevel, player, portalPos)"));
        assertTrue(source.contains("VANILLA_EIGHT_TO_ONE_DISTRACTOR_FRAME"));
        assertTrue(source.contains("findClosestPortalPosition("));
        assertTrue(source.contains("beforeCreate.isPresent()"));
        assertTrue(source.contains("WAVE_C15 PASS"));
    }

    @Test
    void productionEntrypointKeepsPortalFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC15PortalLinkingAcceptance.installFromSystemProperty();"));
    }
}
