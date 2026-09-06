package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class WaveC1DevelopmentResourceTest {
    private static final Path DEVELOPMENT_RESOURCES =
            Path.of("src", "development", "resources");

    @Test
    void accumulatorPrototypeUsesExistingGoldOrElectrumWireTag() throws IOException {
        String recipe = read(
                "data",
                "createaddition",
                "recipe",
                "crafting",
                "modular_accumulator.json");

        assertTrue(
                recipe.contains("\"tag\": \"createaddition:modular_accumulator_usable_wires\""),
                "Wave C1 must not hard-require Electrum/Silver for baseline accumulator crafting");
    }

    @Test
    void rejectedOreBiomeModifiersAreNoOps() throws IOException {
        for (String modifier : new String[] {
            "add_ore_platinum.json",
            "add_ore_platinum_buried.json",
            "add_ore_platinum_large.json",
            "add_ore_platinum_medium.json"
        }) {
            String json = read(
                    "data",
                    "createpropulsion",
                    "neoforge",
                    "biome_modifier",
                    modifier);
            assertTrue(
                    json.contains("\"type\": \"neoforge:none\""),
                    modifier + " must be a no-op in the Wave C1 prototype");
        }

        String wolframite = read(
                "data",
                "createmetallurgy",
                "neoforge",
                "biome_modifier",
                "wolframite_ore.json");
        assertTrue(
                wolframite.contains("\"type\": \"neoforge:none\""),
                "Metallurgy A/B must not inject Wolframite into Nether geography");
    }

    private static String read(String... relativePath) throws IOException {
        Path path = DEVELOPMENT_RESOURCES;
        for (String element : relativePath) {
            path = path.resolve(element);
        }
        return Files.readString(path);
    }
}
