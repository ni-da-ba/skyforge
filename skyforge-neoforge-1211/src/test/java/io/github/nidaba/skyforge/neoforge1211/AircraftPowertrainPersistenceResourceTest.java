package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftPowertrainPersistenceResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void consumerRequiresAcceptedPlatformPersistenceAuthority() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        for (String capability : new String[] {
                "SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE",
                "SABLE_PRODUCTION_SCALE_PERSISTENCE_LIFECYCLE"}) {
            int start = ledger.indexOf("\"" + capability + "\"");
            assertTrue(start >= 0, "missing platform capability " + capability);
            String entry = ledger.substring(start, Math.min(ledger.length(), start + 12000));
            assertTrue(entry.contains("\"status\": \"accepted\""), capability + " is not accepted");
            assertTrue(entry.contains("\"verification_level\": \"L2\""), capability + " is not L2");
            assertTrue(entry.contains("\"B\": true"), capability + " does not grant Agent B authority");
        }
    }

    @Test
    void workflowCrossesFreshProcessBoundaryOnOneProductionWorld() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/aircraft-powertrain-persistence.yml").normalize());
        assertTrue(build.contains("aircraftPowertrainPersistencePrepareServer"));
        assertTrue(build.contains("aircraftPowertrainPersistenceVerifyServer"));
        assertTrue(build.contains("run-aircraft-powertrain-persistence"));
        assertTrue(workflow.contains("Boot A"));
        assertTrue(workflow.contains("Boot B"));
        assertTrue(workflow.contains("PREPARE PASS"));
        assertTrue(workflow.contains("VERIFY PASS"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
    }

    @Test
    void aircraftProofPreservesIdentityAndOnlyReactivatesAcceptedLifecycleState() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftPowertrainRuntimeAcceptance.java"));
        assertTrue(source.contains("aircraft-runtime-002.identity"));
        assertTrue(source.contains("SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE"));
        assertTrue(source.contains("SABLE_PRODUCTION_SCALE_PERSISTENCE_LIFECYCLE"));
        assertTrue(source.contains("getLastSerializationPointer"));
        assertTrue(source.contains("LOCATOR_CHUNK_TICKET"));
        assertTrue(source.contains("persistedPointer="));
        assertTrue(source.contains("FAIL_PERSISTENCE_NOT_PERSISTED"));
        assertTrue(source.contains("FAIL_PERSISTENCE_HOLDING_POINTER"));
        assertTrue(source.contains("FAIL_PERSISTENCE_LOAD_CANONICALIZATION"));
        assertTrue(source.contains("FAIL_PERSISTENCE_PHYSICS_REHYDRATION"));
        assertTrue(source.contains("samePersistentUuid=true"));
        assertTrue(source.contains("currentPhysicsHandleValid=true"));
        assertTrue(source.contains("normalizedChildBlocksInPlot=true"));
        assertTrue(source.contains("PRE_SAVE_GLUE_OBSERVED"));
        assertTrue(source.contains("ENTITY_REHYDRATION_GRACE_TICKS = 10L"));
        assertTrue(source.contains("reassembled-from-normalized-plot"));
        assertTrue(source.contains("lifecycleReactivation=portable_engine_burn_plus_governor_target"));
        assertTrue(source.contains("persistenceQualified=true"));
        assertTrue(source.contains("controlAxisQualified=false"));
        assertTrue(source.contains("higherGovernorPointsQualified=false"));
        assertFalse(source.contains("aircraft-specific save subsystem"));
    }
}
