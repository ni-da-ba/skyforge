package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class WaveC9ComputingResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void pinsComputingSubstrateExactly() throws IOException {
        Properties pins = new Properties();
        try (var input = Files.newInputStream(PROJECT_DIRECTORY.resolve("wave-c9-mods.properties"))) {
            pins.load(input);
        }

        assertEquals("1.21.1", pins.getProperty("minecraft.version"));
        assertEquals("21.1.249", pins.getProperty("neoforge.version"));
        assertEquals("1.119.0", pins.getProperty("cctweaked.version"));
        assertEquals(
                "maven.modrinth:gu7yAYhd:puxJkazX",
                pins.getProperty("cctweaked.coordinate"));
        assertEquals("0.5.2", pins.getProperty("createavionics.version"));
        assertEquals(
                "maven.modrinth:h4nsLvjf:sWhAueMC",
                pins.getProperty("createavionics.coordinate"));
    }

    @Test
    void buildKeepsComputingOptionalAndReusesFlightStack() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("val waveC9Runtime = sourceSets.create(\"waveC9Runtime\")"));
        assertTrue(build.contains("sourceSet.set(waveC9Runtime)"));
        assertTrue(build.contains("waveC9Runtime.runtimeOnlyConfigurationName"));
        assertTrue(build.contains("listOf(\"create\", \"sable\", \"aeronautics\")"));
        assertTrue(build.contains("waveC9Pin(\"cctweaked\", \"coordinate\")"));
        assertTrue(build.contains("waveC9Pin(\"createavionics\", \"coordinate\")"));
    }
}
