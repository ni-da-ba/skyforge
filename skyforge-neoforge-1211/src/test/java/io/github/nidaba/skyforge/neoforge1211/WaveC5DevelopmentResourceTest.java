package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class WaveC5DevelopmentResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void pinsReuseFirstSoaringFaunaStackExactly() throws IOException {
        Properties pins = new Properties();
        try (var input = Files.newInputStream(PROJECT_DIRECTORY.resolve("wave-c5-mods.properties"))) {
            pins.load(input);
        }

        assertEquals("1.21.1", pins.getProperty("minecraft.version"));
        assertEquals("21.1.249", pins.getProperty("neoforge.version"));
        assertEquals(
                "maven.modrinth:WpXfePbg:kZyPjCmU",
                pins.getProperty("fowlplay.coordinate"));
        assertEquals(
                "maven.modrinth:PuyPazRT:O5EpeqI3",
                pins.getProperty("smartbrainlib.coordinate"));
        assertEquals(
                "maven.modrinth:1eAoo2KR:XoVxAvc2",
                pins.getProperty("yacl.coordinate"));
        assertEquals(
                "22eb0dfe639f709a6e91972009911c47ed7f9e60",
                pins.getProperty("fowlplay.sourceCommit"));
        assertEquals(
                "1e091c69477f49972d8f434a595a3eb7d115346b",
                pins.getProperty("smartbrainlib.sourceCommit"));
    }

    @Test
    void buildKeepsBirdStackDevelopmentOnlyAndReusesC3AtmosphereAuthority() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("val waveC5Runtime = sourceSets.create(\"waveC5Runtime\")"));
        assertTrue(build.contains("sourceSet.set(waveC5Runtime)"));
        assertTrue(build.contains("waveC5Runtime.runtimeOnlyConfigurationName"));
        assertTrue(build.contains("files(waveC3AeroCoreArtifact)"));
        assertTrue(build.contains("runWaveC5SoaringFaunaServer"));
        assertTrue(build.contains("runWaveC6HawkThermalServer"));
    }
}
