package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ComposedMechanismPersistenceResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
                    .toAbsolutePath()
                    .normalize();

    @Test
    void fixtureUsesExactAcceptedComposedRuntimeSeams() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformComposedMechanismPersistencePrepareServer"));
        assertTrue(build.contains("compilerPlatformComposedMechanismPersistenceVerifyServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(source.contains("simulated:physics_assembler"));
        assertTrue(source.contains("create:creative_motor"));
        assertTrue(source.contains("aeronautics:propeller_bearing"));
    }

    @Test
    void persistenceRequiresFreshProcessIdentityRecovery() throws IOException {
        String source = fixtureSource();
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-composed-mechanism-persistence.yml").normalize());

        assertTrue(source.contains("platform-007-composed-persistence.identity"));
        assertTrue(source.contains("getHoldingSubLevel"));
        assertTrue(source.contains("snatchAndLoad"));
        assertTrue(source.contains("samePersistentUuid=true"));
        assertTrue(source.contains("currentPhysicsHandleValid=true"));
        assertTrue(source.contains("staleChildDuplicate=false"));
        assertTrue(source.contains("glueDuplicate=false"));
        assertTrue(source.contains("normalizedChildBlocksInPlot=true"));
        assertTrue(source.contains("recreated-from-topology"));
        assertTrue(source.contains("reassembled-from-normalized-plot"));
        assertTrue(workflow.contains("runCompilerPlatformComposedMechanismPersistencePrepareServer"));
        assertTrue(workflow.contains("runCompilerPlatformComposedMechanismPersistenceVerifyServer"));
        assertTrue(workflow.contains("PREPARE PASS"));
        assertTrue(workflow.contains("VERIFY PASS"));
    }

    @Test
    void recoveredNetworkMustBeLiveAndBounded() throws IOException {
        String source = fixtureSource();
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));

        assertTrue(source.contains("KINETIC_DISCONNECT"));
        assertTrue(source.contains("KINETIC_REBUILD"));
        assertTrue(source.contains("severedObserved=true rebuiltObserved=true"));
        assertTrue(source.contains("TIMEOUT_PERSISTENCE_RELOAD"));
        assertTrue(failures.contains("TIMEOUT_PERSISTENCE_RELOAD"));
        assertTrue(source.contains("fixtureLivenessTicket=sable:command_forced(released)"));
        assertFalse(source.contains("steering_wheel"));
        assertFalse(source.contains("aircraft"));
    }

    @Test
    void ledgerRemainsFailClosedUntilExactStackAcceptance() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start);

        assertTrue(entry.contains("\"status\": \"qualification_pending\""));
        assertTrue(entry.contains("\"latest_accepted_evidence\": null"));
        assertTrue(entry.contains("\"B\": false"));
        assertTrue(entry.contains("\"C\": false"));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/"
                        + "SkyforgeComposedMechanismPersistenceAcceptance.java"));
    }
}
