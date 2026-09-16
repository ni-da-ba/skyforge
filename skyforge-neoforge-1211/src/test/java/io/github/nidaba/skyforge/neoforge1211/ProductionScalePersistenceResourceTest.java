package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProductionScalePersistenceResourceTest {
    private static final Path PROJECT_DIRECTORY =
            Path.of(System.getProperty("skyforge.test.projectDirectory", ".")).toAbsolutePath().normalize();

    @Test
    void fixtureUsesExactStackAndProductionFootprintWithoutAircraftMechanisms() throws IOException {
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String pins = Files.readString(PROJECT_DIRECTORY.resolve("wave-c1-mods.properties"));
        String source = fixtureSource();

        assertTrue(build.contains("compilerPlatformProductionScalePersistencePrepareServer"));
        assertTrue(build.contains("compilerPlatformProductionScalePersistenceVerifyServer"));
        assertTrue(build.contains("sourceSet.set(waveC11Runtime)"));
        assertTrue(pins.contains("minecraft.version=1.21.1"));
        assertTrue(pins.contains("neoforge.version=21.1.249"));
        assertTrue(source.contains("EXPECTED_TRANSFER_COUNT = 118"));
        assertTrue(source.contains("MIN_X = 124"));
        assertTrue(source.contains("MAX_X = 134"));
        assertTrue(source.contains("MIN_Z = 12"));
        assertTrue(source.contains("MAX_Z = 22"));
        assertTrue(source.contains("simulated:physics_assembler"));
        assertFalse(source.contains("create:creative_motor"));
        assertFalse(source.contains("SuperGlueEntity"));
        assertFalse(source.contains("PropellerBearing"));
    }

    @Test
    void freshProcessReloadUsesPersistedSableStorageLocatorBeforeUuidSnatch() throws IOException {
        String source = fixtureSource();
        assertTrue(source.contains("getLastSerializationPointer"));
        assertTrue(source.contains("PointerState"));
        assertTrue(source.contains("locatorChunk"));
        assertTrue(source.contains("getHoldingSubLevel"));
        assertTrue(source.contains("snatchAndLoad"));
        assertTrue(source.contains("RELOAD_LOCATOR"));
        assertTrue(source.contains("HOLDING_LOAD_REQUEST"));
        assertTrue(source.contains("samePersistentUuid=true"));
        assertTrue(source.contains("currentPhysicsHandleValid=true"));
        assertTrue(source.contains("payloadRecovered=true"));
    }

    @Test
    void reloadFailureModesAreTypedAndBounded() throws IOException {
        String source = fixtureSource();
        String failures = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeCompilerIntegrationFailure.java"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/compiler-platform-production-scale-persistence.yml").normalize());

        assertTrue(failures.contains("FAIL_PERSISTENCE_NOT_PERSISTED"));
        assertTrue(failures.contains("FAIL_PERSISTENCE_HOLDING_POINTER"));
        assertTrue(failures.contains("FAIL_PERSISTENCE_LOAD_CANONICALIZATION"));
        assertTrue(failures.contains("FAIL_PERSISTENCE_PHYSICS_REHYDRATION"));
        assertTrue(source.contains("HOLDING_DISCOVERY_DEADLINE_TICKS"));
        assertTrue(source.contains("CANONICALIZATION_DEADLINE_TICKS"));
        assertTrue(source.contains("PHYSICS_REHYDRATION_DEADLINE_TICKS"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("PREPARE PASS"));
        assertTrue(workflow.contains("VERIFY PASS"));
    }

    @Test
    void capabilityRemainsUnpublishedUntilExactL2Passes() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        assertFalse(ledger.contains("\"SABLE_PRODUCTION_SCALE_PERSISTENCE_LIFECYCLE\""));
    }

    private static String fixtureSource() throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeProductionScalePersistenceAcceptance.java"));
    }
}
