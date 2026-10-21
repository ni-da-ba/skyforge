package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC14AvionicsCapabilityResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void buildProvidesExplicitFlightOnlyAndComputingCapabilityProfiles() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("val waveC14FlightBaselineRuntime = sourceSets.create(\"waveC14FlightBaselineRuntime\")"));
        assertTrue(build.contains("create(\"waveC14FlightBaselineServer\")"));
        assertTrue(build.contains("sourceSet.set(waveC14FlightBaselineRuntime)"));
        assertTrue(build.contains("create(\"waveC14AvionicsCapabilityServer\")"));
        assertTrue(build.contains("sourceSet.set(waveC9Runtime)"));
        assertTrue(build.contains("waveC14ResolvePinnedMods"));
    }

    @Test
    void acceptanceUsesRealCraftOsPeripheralCallsWithoutOptionalCompileImports() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC14AvionicsCapabilityAcceptance.java"));

        assertTrue(source.contains("peripheral.find(\"altitude_sensor\")"));
        assertTrue(source.contains("altitude.getHeight()"));
        assertTrue(source.contains("peripheral.find(\"throttle_lever\")"));
        assertTrue(source.contains("throttle.setSignal(9)"));
        assertTrue(source.contains("throttle.setSignal(-4)"));
        assertTrue(source.contains("throttle.setSignal(99)"));
        assertTrue(source.contains("level.getSignal("));
        assertFalse(source.contains("import dan200.computercraft"));
        assertFalse(source.contains("import ink.astrius.create_avionics"));
        assertFalse(source.contains("import dev.simulated_team.simulated"));
    }

    @Test
    void productionEntrypointKeepsCapabilityFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC14AvionicsCapabilityAcceptance.installFromSystemProperty();"));
    }
}
