package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC18TurtleFreightResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void freightActionsRemainStockCraftOsTurtleCalls() throws IOException {
        String source = acceptanceSource();

        assertTrue(source.contains("turtle.refuel(2)"));
        assertTrue(source.contains("turtle.forward()"));
        assertTrue(source.contains("turtle.back()"));
        assertTrue(source.contains("turtle.drop()"));
        assertTrue(source.contains("CARGO_STACKS = 15"));
        assertTrue(source.contains("TOTAL_MOVES"));
        assertFalse(source.contains("import dan200.computercraft"));
        assertFalse(source.contains("getAccess()"));
        assertFalse(source.contains("setFuelLevel"));
    }

    @Test
    void fixtureProvesVoidCargoFuelCadenceAndLoadedWorldBoundary() throws IOException {
        String source = acceptanceSource();

        assertTrue(source.contains("Blocks.AIR.defaultBlockState()"));
        assertTrue(source.contains("Blocks.BARREL.defaultBlockState()"));
        assertTrue(source.contains("BOUNDARY_ATTEMPT_DISTANCE = 48"));
        assertTrue(source.contains("BOUNDARY_STALL_TICKS"));
        assertTrue(source.contains("boundaryStalled"));
        assertTrue(source.contains("NO_TICK_PROGRESS"));
        assertTrue(source.contains("boundary turtle stopped for a non-loading reason"));
        assertTrue(source.contains("Cannot leave loaded world"));
        assertTrue(source.contains("fuelStart - fuelEnd != TOTAL_MOVES"));
        assertTrue(source.contains("elapsedTicks="));
        assertTrue(source.contains("EXPECTED_DELIVERED"));
        assertTrue(source.contains("WAVE_C18 PASS"));
    }

    @Test
    void productionEntrypointKeepsTurtleFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC18TurtleFreightAcceptance.installFromSystemProperty();"));
    }

    private static String acceptanceSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC18TurtleFreightAcceptance.java"));
    }
}
