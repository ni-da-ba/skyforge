package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AircraftRudderYawAuthorityResourceTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("skyforge.test.projectDirectory", "."))
            .toAbsolutePath().normalize();

    @Test
    void consumesAcceptedPlatformForceObservationAuthority() throws IOException {
        String ledger = Files.readString(PROJECT_DIRECTORY.resolve(
                "../docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json").normalize());
        int start = ledger.indexOf("\"SABLE_AERODYNAMIC_FORCE_OBSERVATION_LIFECYCLE\"");
        assertTrue(start >= 0);
        String entry = ledger.substring(start, Math.min(ledger.length(), start + 8000));
        assertTrue(entry.contains("\"status\": \"accepted\""));
        assertTrue(entry.contains("\"verification_level\": \"L2\""));
        assertTrue(entry.contains("\"B\": true"));
    }

    @Test
    void aircraftGateOwnsOnlyGeometrySignAndNumericalNonzeroPolicy() throws IOException {
        String observer = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAerodynamicForceObservationAcceptance.java"));
        String yaw = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftRudderYawAuthorityRuntimeAcceptance.java"));
        String harness = Files.readString(PROJECT_DIRECTORY.resolve(
                "src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftPowertrainRuntimeAcceptance.java"));
        String workflow = Files.readString(PROJECT_DIRECTORY.resolve(
                "../.github/workflows/aircraft-rudder-yaw-authority.yml").normalize());
        String build = Files.readString(PROJECT_DIRECTORY.resolve("build.gradle.kts"));

        assertTrue(observer.contains("static Observation observe"));
        assertFalse(observer.contains("aggregate DRAG moment is zero"));
        assertTrue(yaw.contains("SkyforgeAerodynamicForceObservationAcceptance.observe"));
        assertTrue(yaw.contains("FORWARD_SPEED_MPS = 10.0"));
        assertTrue(yaw.contains("PHYSICAL_SETTLE_TICKS = 80"));
        assertTrue(yaw.contains("MINIMUM_LATERAL_FORCE_MAGNITUDE = 1.0e-4"));
        assertTrue(yaw.contains("SIGN_PROBE_ORTHOGONAL_LIMIT_DEGREES = 90.0"));
        assertTrue(yaw.contains("MINIMUM_YAW_MOMENT_MAGNITUDE = 1.0e-4"));
        assertTrue(yaw.contains("Math.signum(yawMomentY) == -Math.signum(physicalYawDegrees)"));
        assertTrue(yaw.contains("aerodynamicModelFitted=false analyticalAuthorityIndependent=true"));
        assertFalse(yaw.contains("setTargetAngle"));
        assertTrue(harness.contains("skyforge.dev.aircraftRudderYawAuthority"));
        assertTrue(harness.contains("yawForceMode ? 3 : 10"));
        assertTrue(harness.contains("yawForceQualified=\" + yawForceMode"));
        assertTrue(build.contains("aircraftRudderYawAuthorityServer"));
        assertTrue(workflow.contains("l1-contract"));
        assertTrue(workflow.contains("l2-exact-stack"));
        assertTrue(workflow.contains("runAircraftRudderYawAuthorityServer"));
    }
}
