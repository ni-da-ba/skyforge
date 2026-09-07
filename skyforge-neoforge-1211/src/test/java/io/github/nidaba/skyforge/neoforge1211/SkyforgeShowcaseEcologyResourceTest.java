package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkyforgeShowcaseEcologyResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void showcaseLandPresetKeepsSkyforgeGeneratorWithGrassBearingNativeSource() throws IOException {
        Path preset = PROJECT_DIRECTORY.resolve(Path.of(
                "src",
                "development",
                "resources",
                "data",
                "skyforge",
                "worldgen",
                "world_preset",
                "showcase_land.json"));
        String json = Files.readString(preset);

        assertTrue(json.contains("\"type\": \"skyforge:noise_overlay\""));
        assertTrue(json.contains("\"type\": \"minecraft:fixed\""));
        assertTrue(json.contains("\"biome\": \"minecraft:plains\""));
        assertTrue(json.contains("\"settings\": \"minecraft:overworld\""));
    }

    @Test
    void showcaseRunOptsIntoLegibleEcologyWithoutChangingProductionAcceptancePreset() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("level-type=skyforge:showcase_land"));
        assertTrue(build.contains(
                "systemProperty(\"skyforge.dev.showcaseLegibleEcology\", \"true\")"));
        assertTrue(build.contains(
                "val sfImp0069AcceptanceServerProperties = \"\"\"\n"
                        + "    level-name=acceptance\n"
                        + "    level-seed=600068\n"
                        + "    level-type=skyforge:development"));
    }
}
