package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class WaveC3DevelopmentResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void runtimeSpecimenPinsAuthorityAndMeasurementArtifactsExactly() throws IOException {
        Properties pins = new Properties();
        try (var input = Files.newInputStream(PROJECT_DIRECTORY.resolve("wave-c3-mods.properties"))) {
            pins.load(input);
        }

        assertEquals("1.21.1", pins.getProperty("minecraft.version"));
        assertEquals("21.1.249", pins.getProperty("neoforge.version"));

        assertPinned(
                pins,
                "aerodynamics4mcCore",
                "0.2.1",
                "maven.modrinth:UnshaaiE:6Z0Z1pfP");
        assertPinned(
                pins,
                "aerodynamics4mcCompat",
                "0.2.1-Aeronaustics-Compat",
                "maven.modrinth:UnshaaiE:L1NGyZ63");
        assertPinned(
                pins,
                "windTunnel",
                "1.1.8",
                "maven.modrinth:EnEqwk7y:IdHA8JDW");
        assertPinned(
                pins,
                "ldlib",
                "2.2.6",
                "maven.modrinth:B1CBVXHX:KX3KmrCS");

        assertEquals(
                "62a52a584e9c65246e50226b29a1f0449e43995e",
                pins.getProperty("aerodynamics4mcCore.sourceCommit"));
        assertEquals(
                pins.getProperty("aerodynamics4mcCore.sourceCommit"),
                pins.getProperty("aerodynamics4mcCompat.sourceCommit"));
        assertNotEquals(
                pins.getProperty("aerodynamics4mcCore.coordinate"),
                pins.getProperty("aerodynamics4mcCompat.coordinate"),
                "core and compat are distinct files even though Modrinth gives them one project identity");
    }

    @Test
    void buildKeepsCoreCompatSeparateAndOfficialContentOutOfAuthorityPrototype() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("waveC3AeroCoreArtifact"));
        assertTrue(build.contains("waveC3AeroCompatArtifact"));
        assertTrue(build.contains("files(waveC3AeroCoreArtifact)"));
        assertTrue(build.contains("files(waveC3AeroCompatArtifact)"));
        assertTrue(build.contains("waveC3WindTunnelClient"));
        assertFalse(
                build.contains("aerodynamics4mc-content"),
                "official A4MC content must not become part of atmosphere-authority selection");
    }

    private static void assertPinned(
            Properties pins, String component, String version, String coordinate) {
        assertEquals(
                version,
                pins.getProperty(component + ".version"),
                component + " version pin changed");
        assertEquals(
                coordinate,
                pins.getProperty(component + ".coordinate"),
                component + " immutable artifact pin changed");
    }
}
