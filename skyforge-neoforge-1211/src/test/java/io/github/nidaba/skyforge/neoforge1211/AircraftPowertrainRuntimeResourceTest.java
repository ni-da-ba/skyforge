package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftPowertrainRuntimeResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void retainedProductionChainReachesTheBoundedV012RuntimeBoundary() {
        var fixture = SkyforgeAircraftRetainedGuildUtilityFixture.compileV012();

        assertTrue(fixture.powertrain().validation().passed());
        assertTrue(fixture.powertrain().readiness().governor128RuntimeProbeReady());
        assertFalse(fixture.powertrain().readiness().runtimeQualificationReady());
        assertEquals(122, fixture.manifest().placements().size());
        assertEquals(114, fixture.assemblyFixture().metrics().mainBodyPlacementCount());
        assertEquals(9, fixture.assemblyFixture().metrics().nestedChildPlacementCount());
        assertEquals(4, fixture.glue().metrics().glueDomainCount());
        assertEquals(118, fixture.powertrain().metrics().resultingMovingMainBodyPlacementCount());
        assertEquals(127, fixture.powertrain().metrics().expectedPrimarySableTransferCount());
        assertEquals(128, fixture.powertrain().governor().firstAcceptedTargetRpm());
    }

    @Test
    void runtimeGateConsumesAcceptedPlatformAuthorityWithoutWideningIt() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        for (String capability : new String[] {
                "SABLE_PRIMARY_ASSEMBLY_LIFECYCLE",
                "SUPER_GLUE_ASSEMBLY_DOMAIN_LIFECYCLE",
                "CREATE_KINETIC_ON_SABLE_LIFECYCLE",
                "NESTED_PROPELLER_BEARING_LIFECYCLE"}) {
            int start = ledger.indexOf("\"" + capability + "\"");
            assertTrue(start >= 0, "missing platform capability " + capability);
            String entry = ledger.substring(start, Math.min(ledger.length(), start + 7000));
            assertTrue(entry.contains("\"status\": \"accepted\""), capability + " is not accepted");
            assertTrue(entry.contains("\"B\": true"), capability + " does not grant Agent B authority");
        }
    }

    @Test
    void legacyRuntimeGateRemainsBoundedTo128RpmWithPersistenceModeIsolated() throws IOException {
        String source = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftPowertrainRuntimeAcceptance.java"));
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/aircraft-powertrain-runtime-128.yml").normalize());

        assertTrue(source.contains("TARGET_RPM = 128"));
        assertTrue(source.contains("higherGovernorPointsQualified=false"));
        assertTrue(source.contains("persistenceQualified=false"));
        assertTrue(source.contains("controlAxisQualified=false"));
        assertTrue(source.contains("analyticalAuthorityIndependent=true"));
        assertTrue(source.contains("compiler-emitted propeller child did not re-form without hidden child glue"));
        assertTrue(source.contains("RunMode.LEGACY"));
        assertTrue(source.contains("System.getProperty(PERSISTENCE_PROPERTY"));
        assertTrue(source.contains("runMode == RunMode.PREPARE"));
        assertTrue(source.contains("runMode == RunMode.VERIFY"));
        assertFalse(source.contains("steering_wheel"));
        assertFalse(source.contains("swivel_bearing"));
        assertTrue(build.contains("aircraftPowertrainRuntime128Server"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("waveC11ResolvePinnedMods"));
        assertTrue(workflow.contains("runAircraftPowertrainRuntime128Server"));
        assertFalse(workflow.contains("runAircraftPowertrainPersistence"));
    }
}
