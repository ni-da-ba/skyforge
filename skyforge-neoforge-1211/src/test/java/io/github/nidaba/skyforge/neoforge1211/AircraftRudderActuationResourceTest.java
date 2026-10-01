package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftRudderActuationResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void retainedProductionChainReachesCorrectedStaticYawBoundary() {
        var fixture = SkyforgeAircraftRetainedGuildUtilityFixture.compileV0131();
        var yaw = fixture.yawControl();
        assertTrue(yaw.validation().passed());
        assertTrue(yaw.readiness().yawControlStaticTopologyPassed());
        assertFalse(yaw.readiness().runtimeQualificationReady());
        assertEquals(118, yaw.metrics().v0131MovingParentMainBodyPlacementCount());
        assertEquals(9, yaw.metrics().nestedPropellerChildPlacementCount());
        assertEquals(4, yaw.metrics().yawControlChildPlacementCount());
        assertEquals(131, yaw.metrics().expectedPrimarySableTransferCount());
        assertEquals(7, yaw.metrics().runtimeGlueDomainCount());
    }

    @Test
    void runtimeConsumesAcceptedSwivelPlatformAuthority() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start, Math.min(ledger.length(), start + 7000));
        assertTrue(entry.contains("\"status\": \"accepted\""));
        assertTrue(entry.contains("\"B\": true"));
        assertTrue(entry.contains("targetAngleDegrees remained observation-only"));
    }

    @Test
    void runtimeGateUsesRealKineticsAndRemainsYawForceUnqualified() throws IOException {
        String harness = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftPowertrainRuntimeAcceptance.java"));
        String consumer = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftRudderActuationRuntimeAcceptance.java"));
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/aircraft-rudder-actuation.yml").normalize());

        assertTrue(harness.contains("skyforge.dev.aircraftRudderActuation"));
        assertTrue(harness.contains("compileV0131"));
        assertTrue(harness.contains("SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE"));
        assertTrue(consumer.contains("create:creative_motor"));
        assertTrue(consumer.contains("getExtraKinetics"));
        assertTrue(consumer.contains("directTargetMutation=false"));
        assertTrue(consumer.contains("passiveSelfCenteringQualified=false"));
        assertTrue(consumer.contains("yawForceQualified=\" + qualifyYawAuthority"));
        assertTrue(harness.contains("yawForceQualified=\" + yawForceMode"));
        assertFalse(consumer.contains("setTargetAngle"));
        assertFalse(consumer.contains("targetAngleDegrees.set"));
        assertTrue(build.contains("aircraftRudderActuationServer"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("runAircraftRudderActuationServer"));
    }
}
