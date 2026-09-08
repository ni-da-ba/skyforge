package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PortableEngineCutoffPersistenceResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void threeBootsUseSameC11FlightOnlyWorld() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(build.contains("portableEngineCutoffPersistencePrepareServer"));
        assertTrue(build.contains("portableEngineCutoffPersistenceVerifyCutServer"));
        assertTrue(build.contains("portableEngineCutoffPersistenceVerifyRunServer"));
        assertTrue(build.contains(
                "gameDirectory = layout.projectDirectory.dir(\"run-portable-engine-cutoff-persistence\").asFile"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(build.contains(
                "persistence verify requires the world from the preceding server boot"));
    }

    @Test
    void verifyTasksDoNotDeletePrecedingWorld() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        int verifyIndex = build.indexOf(
                "runPortableEngineCutoffPersistenceVerifyCutServer");
        assertTrue(verifyIndex >= 0);
        int nextSection = build.indexOf("val waveC14ServerProperties", verifyIndex);
        String verifyBlock = build.substring(verifyIndex, nextSection);

        assertFalse(verifyBlock.contains("delete(directory)"));
        assertTrue(verifyBlock.contains("runPortableEngineCutoffPersistenceVerifyRunServer"));
        assertTrue(verifyBlock.contains(
                "directory.resolve(\"portable-engine-cutoff-persistence\").isDirectory"));
    }

    @Test
    void acceptanceRequiresRealCutAndRunSavedState() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgePortableEngineCutoffPersistenceAcceptance.java"));

        assertTrue(source.contains("saveEverything(false, true, true)"));
        assertTrue(source.contains("CUT_BURN_TIME = 600"));
        assertTrue(source.contains("RUN_BURN_TIME = 599"));
        assertTrue(source.contains("RUN_AFTER_TICK_BURN_TIME = 598"));
        assertTrue(source.contains("SAVED_FUEL_COUNT = 1"));
        assertTrue(source.contains("CUT_COMPARATOR = 12"));
        assertTrue(source.contains("RUN_COMPARATOR = 11"));
        assertTrue(source.contains("reloaded CUT mode enabled"));
        assertTrue(source.contains("reloaded RUN mode remains configured"));
        assertTrue(source.contains("reloaded RUN generated output"));
        assertTrue(source.contains("getAnalogOutputSignal"));
        assertTrue(source.contains("PORTABLE_ENGINE_CUTOFF_PERSISTENCE CUT VERIFY PASS"));
        assertTrue(source.contains("PORTABLE_ENGINE_CUTOFF_PERSISTENCE RUN VERIFY PASS"));
        assertFalse(source.contains("import dev.simulated"));
    }
}
