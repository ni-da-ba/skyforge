package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC16WirelessEnvelopeResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void buildReusesExactAcceptedComputingRuntime() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("create(\"waveC16WirelessEnvelopeServer\")"));
        assertTrue(build.contains("sourceSet.set(waveC9Runtime)"));
    }

    @Test
    void acceptanceUsesRealCraftOsModemCallsWithoutCcCompileImports() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC16WirelessEnvelopeAcceptance.java"));

        assertTrue(source.contains("peripheral.find(\"modem\")"));
        assertTrue(source.contains("modem.isWireless()"));
        assertTrue(source.contains("modem.open("));
        assertTrue(source.contains("modem.transmit("));
        assertTrue(source.contains("NORMAL_NEAR = new BlockPos(48, 64, 0)"));
        assertTrue(source.contains("NORMAL_FAR = new BlockPos(96, 64, 0)"));
        assertTrue(source.contains("ENDER_REMOTE = new BlockPos(512, 64, 64)"));
        assertTrue(source.contains("nether"));
        assertFalse(source.contains("import dan200.computercraft"));
    }

    @Test
    void productionEntrypointKeepsWirelessFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC16WirelessEnvelopeAcceptance.installFromSystemProperty();"));
    }
}
