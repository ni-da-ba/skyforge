package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC17GpsInfrastructureResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void acceptanceUsesOnlyStockCraftOsGpsAndModemSurface() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC17GpsInfrastructureAcceptance.java"));

        assertTrue(source.contains("shell.run(\"gps\", \"host\""));
        assertTrue(source.contains("gps.locate(2, false)"));
        assertTrue(source.contains("wireless_modem_normal"));
        assertTrue(source.contains("wireless_modem_advanced"));
        assertTrue(source.contains("WAVE_C17 PASS"));
        assertFalse(source.contains("import dan200.computercraft"));
        assertFalse(source.contains("import ink.astrius.create_avionics"));
    }

    @Test
    void acceptanceProvesNoHostLocalRangeAndEnderRangeCases() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC17GpsInfrastructureAcceptance.java"));

        assertTrue(source.contains("NO_HOST_LOCATOR"));
        assertTrue(source.contains("NORMAL_NEAR_LOCATOR"));
        assertTrue(source.contains("NORMAL_FAR_LOCATOR"));
        assertTrue(source.contains("ENDER_REMOTE_LOCATOR"));
        assertTrue(source.contains("GPS unexpectedly worked without hosts"));
        assertTrue(source.contains("ordinary GPS escaped the normal wireless envelope"));
    }

    @Test
    void productionEntrypointKeepsGpsFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC17GpsInfrastructureAcceptance.installFromSystemProperty();"));
    }
}
