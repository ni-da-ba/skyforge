package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC11FirstFlightRecipeResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void c11RuntimeIsIsolatedAndReusesExactC1Pins() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("val waveC11Runtime = sourceSets.create(\"waveC11Runtime\")"));
        assertTrue(build.contains("runWaveC11FirstFlightRecipeServer"));
        assertTrue(build.contains("waveC11ResolvePinnedMods"));
        assertTrue(build.contains("waveC11Runtime.runtimeOnlyConfigurationName"));
        assertTrue(build.contains("waveC1Pin(mod, \"coordinate\")"));
    }

    @Test
    void liveFixtureChecksRequiredAircraftWorkshopOutputsAndAdvancedFamilies() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeWaveC11FirstFlightRecipeAcceptance.java"));

        for (String output : new String[] {
                "simulated:physics_assembler",
                "simulated:engine_assembly",
                "simulated:red_portable_engine",
                "aeronautics:andesite_propeller",
                "simulated:steering_wheel",
                "simulated:swivel_bearing",
                "simulated:white_symmetric_sail",
                "create:mechanical_press",
                "create:mechanical_saw"
        }) {
            assertTrue(source.contains(output), output);
        }

        for (String forbidden : new String[] {
                "brass",
                "petroleum",
                "crude_oil",
                "diesel",
                "netherite",
                "levitite",
                "electric_motor",
                "capacitor"
        }) {
            assertTrue(source.contains("\"" + forbidden + "\""), forbidden);
        }

        assertTrue(source.contains("getRecipeManager().getRecipes()"));
        assertTrue(source.contains("getIngredient"));
        assertFalse(source.contains("import com.simibubi.create"));
        assertFalse(source.contains("import dev.simulated"));
    }

    @Test
    void productionEntrypointKeepsC11FixtureOptIn() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeNeoForge1211Mod.java"));
        assertTrue(source.contains(
                "SkyforgeWaveC11FirstFlightRecipeAcceptance.installFromSystemProperty();"));
    }
}
