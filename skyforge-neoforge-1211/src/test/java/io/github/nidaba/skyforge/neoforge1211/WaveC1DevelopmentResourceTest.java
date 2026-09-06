package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class WaveC1DevelopmentResourceTest {
    private static final Path DEVELOPMENT_RESOURCES =
            Path.of("src", "development", "resources");
    private static final Path STANDALONE_DATAPACK =
            Path.of("src", "development", "wave-c1-datapack");

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

    @Test
    void standaloneDatapackUsesMinecraft1211FormatAndMirrorsCoreOverrides() throws IOException {
        String packMetadata = readFrom(STANDALONE_DATAPACK, "pack.mcmeta");
        assertTrue(
                packMetadata.contains("\"pack_format\": 48"),
                "Wave C1 standalone datapack must target Minecraft 1.21.1 data pack format 48");

        String accumulator = readFrom(
                STANDALONE_DATAPACK,
                "data",
                "createaddition",
                "recipe",
                "crafting",
                "modular_accumulator.json");
        assertTrue(
                accumulator.contains("\"tag\": \"createaddition:modular_accumulator_usable_wires\""),
                "standalone Wave C1 datapack must preserve the Silver-free accumulator route");

        String platinum = readFrom(
                STANDALONE_DATAPACK,
                "data",
                "createpropulsion",
                "neoforge",
                "biome_modifier",
                "add_ore_platinum.json");
        assertTrue(
                platinum.contains("\"type\": \"neoforge:none\""),
                "standalone Wave C1 datapack must disable Platinum biome injection");

        String wolframite = readFrom(
                STANDALONE_DATAPACK,
                "data",
                "createmetallurgy",
                "neoforge",
                "biome_modifier",
                "wolframite_ore.json");
        assertTrue(
                wolframite.contains("\"type\": \"neoforge:none\""),
                "standalone Wave C1 datapack must disable Wolframite biome injection");
    }

    @Test
    void metallurgyIndustrialCrucibleUsesRetainedFoundryMaterials() throws IOException {
        for (Path root : new Path[] {DEVELOPMENT_RESOURCES, STANDALONE_DATAPACK}) {
            String recipe = readFrom(
                    root,
                    "data",
                    "createmetallurgy",
                    "recipe",
                    "sequenced_assembly",
                    "industrial_crucible.json");

            assertTrue(
                    recipe.contains("\"type\": \"neoforge:mod_loaded\"")
                            && recipe.contains("\"modid\": \"createmetallurgy\""),
                    "Industrial Crucible override must be ignored when Metallurgy is absent");
            assertTrue(
                    recipe.contains("\"item\": \"create:sturdy_sheet\""),
                    "Industrial Crucible must use retained reinforced structure");
            assertTrue(
                    recipe.contains("\"amount\": 90")
                            && recipe.contains("\"fluid\": \"createmetallurgy:molten_steel\""),
                    "Industrial Crucible must become a Steel-fed foundry upgrade");
            assertTrue(
                    !recipe.contains("tungsten") && !recipe.contains("obdurium"),
                    "Wave C1 Industrial Crucible must not require rejected Tungsten/Obdurium");
        }
    }

    @Test
    void runtimeSpecimenPinsAreExactAndReviewable() throws IOException {
        Properties pins = new Properties();
        try (var input = Files.newInputStream(Path.of("wave-c1-mods.properties"))) {
            pins.load(input);
        }

        assertEquals("1.21.1", pins.getProperty("minecraft.version"));
        assertEquals("21.1.249", pins.getProperty("neoforge.version"));
        assertPinned(pins, "create", "6.0.10+mc1.21.1", "maven.modrinth:LNytGWDc:UjX6dr61");
        assertPinned(pins, "rpl", "2.1.2", "maven.modrinth:B3pb093D:hZ6B2Z0x");
        assertPinned(pins, "createbigcannons", "5.11.7", "maven.modrinth:GWp4jCJj:bOiDu0LS");
        assertPinned(pins, "createaddition", "1.6.0", "maven.modrinth:kU1G12Nn:qPr8V4G2");
        assertPinned(pins, "createmetallurgy", "1.0.3-1.21.1", "maven.modrinth:Soft45xC:4RhIMmaJ");
        assertPinned(pins, "sable", "2.0.5+mc1.21.1", "maven.modrinth:T9PomCSv:U678xqle");
        assertPinned(pins, "aeronautics", "1.3.2+mc1.21.1", "maven.modrinth:oWaK0Q19:44pLdPGg");
        assertPinned(pins, "createpropulsion", "1.1.5", "maven.modrinth:ApkoHNO9:H13U56dc");
        assertPinned(pins, "jei", "19.50.0.414", "maven.modrinth:u6dRKJwZ:zKog3N6a");
    }

    private static void assertPinned(
            Properties pins, String mod, String version, String coordinate) {
        assertEquals(version, pins.getProperty(mod + ".version"), mod + " version pin changed");
        assertEquals(
                coordinate,
                pins.getProperty(mod + ".coordinate"),
                mod + " immutable artifact pin changed");
    }

    private static String read(String... relativePath) throws IOException {
        return readFrom(DEVELOPMENT_RESOURCES, relativePath);
    }

    private static String readFrom(Path root, String... relativePath) throws IOException {
        Path path = root;
        for (String element : relativePath) {
            path = path.resolve(element);
        }
        return Files.readString(path);
    }
}
