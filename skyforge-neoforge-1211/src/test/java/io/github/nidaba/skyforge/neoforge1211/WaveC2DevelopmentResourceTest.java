package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class WaveC2DevelopmentResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();
    private static final Path DEVELOPMENT_RESOURCES =
            PROJECT_DIRECTORY.resolve(Path.of("src", "development", "resources"));
    private static final Path MOBILITY_DATAPACK =
            PROJECT_DIRECTORY.resolve(Path.of("src", "development", "wave-c2-mobility-datapack"));
    private static final Path NETHER_SCALE_DATAPACK =
            PROJECT_DIRECTORY.resolve(Path.of("src", "development", "wave-c2-nether-scale-datapack"));

    @Test
    void earlyGliderPrototypeRemovesPhantomDependencyWithoutChangingUpdraftData() throws IOException {
        for (Path root : new Path[] {DEVELOPMENT_RESOURCES, MOBILITY_DATAPACK}) {
            String recipe = readFrom(
                    root,
                    "data",
                    "reliable_gliders",
                    "recipe",
                    "glider.json");

            assertTrue(
                    recipe.contains("\"type\": \"neoforge:mod_loaded\"")
                            && recipe.contains("\"modid\": \"reliable_gliders\""),
                    "glider override must be inert when Reliable Gliders is absent");
            assertTrue(
                    recipe.contains("\"tag\": \"minecraft:wool\""),
                    "Wave C2 should use ordinary cloth for the bootstrap glider");
            assertTrue(recipe.contains("\"item\": \"minecraft:leather\""));
            assertTrue(recipe.contains("\"item\": \"minecraft:stick\""));
            assertTrue(recipe.contains("\"id\": \"reliable_gliders:glider\""));
            assertFalse(
                    recipe.contains("phantom_membrane"),
                    "Phantom hunting must not gate starter-group personal mobility");
        }

        assertTrue(
                Files.notExists(DEVELOPMENT_RESOURCES.resolve(
                        Path.of("data", "reliable_gliders", "tags", "block", "updraft_blocks.json"))),
                "first C2 pass must leave Reliable Gliders' stock updraft sources untouched");
    }

    @Test
    void standaloneDatapacksTargetMinecraft1211AndKeepNetherScaleIsolated() throws IOException {
        assertPackFormat48(MOBILITY_DATAPACK);
        assertPackFormat48(NETHER_SCALE_DATAPACK);

        Path accidentalGlobalNetherOverride = DEVELOPMENT_RESOURCES.resolve(
                Path.of("data", "minecraft", "dimension_type", "the_nether.json"));
        assertTrue(
                Files.notExists(accidentalGlobalNetherOverride),
                "1:1 Nether must not leak into every development/acceptance run");

        String nether = readFrom(
                NETHER_SCALE_DATAPACK,
                "data",
                "minecraft",
                "dimension_type",
                "the_nether.json");

        assertTrue(nether.contains("\"coordinate_scale\": 1.0"));
        assertTrue(nether.contains("\"effects\": \"minecraft:the_nether\""));
        assertTrue(nether.contains("\"fixed_time\": 18000"));
        assertTrue(nether.contains("\"logical_height\": 128"));
        assertTrue(nether.contains("\"height\": 256"));
        assertTrue(nether.contains("\"ultrawarm\": true"));
        assertTrue(nether.contains("\"has_ceiling\": true"));
        assertTrue(nether.contains("\"respawn_anchor_works\": true"));
    }

    @Test
    void runtimeSpecimenPinsAreExactAndReviewable() throws IOException {
        Properties pins = new Properties();
        try (var input = Files.newInputStream(PROJECT_DIRECTORY.resolve("wave-c2-mods.properties"))) {
            pins.load(input);
        }

        assertEquals("1.21.1", pins.getProperty("minecraft.version"));
        assertEquals("21.1.249", pins.getProperty("neoforge.version"));
        assertPinned(
                pins,
                "reliablegliders",
                "1.4.1",
                "maven.modrinth:pVIWsXir:HFLhMfNC");
        assertPinned(
                pins,
                "noelytraboost",
                "1.0.0",
                "maven.modrinth:uNWWxAv9:G9hL7wPy");
    }

    private static void assertPackFormat48(Path root) throws IOException {
        String metadata = readFrom(root, "pack.mcmeta");
        assertTrue(
                metadata.contains("\"pack_format\": 48"),
                root + " must target Minecraft 1.21.1 data pack format 48");
    }

    private static void assertPinned(
            Properties pins, String mod, String version, String coordinate) {
        assertEquals(version, pins.getProperty(mod + ".version"), mod + " version pin changed");
        assertEquals(
                coordinate,
                pins.getProperty(mod + ".coordinate"),
                mod + " immutable artifact pin changed");
    }

    private static String readFrom(Path root, String... relativePath) throws IOException {
        Path path = root;
        for (String element : relativePath) {
            path = path.resolve(element);
        }
        return Files.readString(path);
    }
}
