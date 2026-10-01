package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC19TurtleMiningResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void miningActionsRemainStockCraftOsTurtleCalls() throws IOException {
        String source = acceptanceSource();

        assertTrue(source.contains("turtle.refuel(3)"));
        assertTrue(source.contains("turtle.equipLeft()"));
        assertTrue(source.contains("turtle.dig()"));
        assertTrue(source.contains("turtle.forward()"));
        assertTrue(source.contains("turtle.back()"));
        assertTrue(source.contains("turtle.drop()"));
        assertTrue(source.contains("new ItemStack(Items.DIAMOND_PICKAXE, 1)"));
        assertFalse(source.contains("import dan200.computercraft"));
        assertFalse(source.contains("getAccess()"));
        assertFalse(source.contains("setFuelLevel"));
    }

    @Test
    void fixtureProvesExtractionDeliveryFuelAndLoadedWorldEnvelope() throws IOException {
        String source = acceptanceSource();

        assertTrue(source.contains("MINE_DISTANCE = 96"));
        assertTrue(source.contains("BOUNDARY_ATTEMPT_DISTANCE = 48"));
        assertTrue(source.contains("Blocks.IRON_ORE.defaultBlockState()"));
        assertTrue(source.contains("Items.RAW_IRON"));
        assertTrue(source.contains("BOUNDARY_STALL_TICKS"));
        assertTrue(source.contains("NO_TICK_PROGRESS"));
        assertTrue(source.contains("Cannot leave loaded world"));
        assertTrue(source.contains("fuelStart - fuelEnd != TOTAL_MOVES"));
        assertTrue(source.contains("boundaryFuelStart - boundaryFuelEnd != boundaryMoves"));
        assertTrue(source.contains("elapsedTicks="));
        assertTrue(source.contains("WAVE_C19 PASS"));
    }

    @Test
    void productionEntrypointKeepsMiningFixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));

        assertTrue(source.contains("SkyforgeWaveC19TurtleMiningAcceptance.installFromSystemProperty();"));
    }

    private static String acceptanceSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC19TurtleMiningAcceptance.java"));
    }
}
