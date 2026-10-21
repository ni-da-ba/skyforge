package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SkyforgeAircraftSteeringYawSourceLowererTest {
    @Test
    void retainedSteeringWheelSourceIsDeterministicSourceBackedAndRuntimeUnqualified() {
        SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture compiled =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileSteeringYawSource();
        SkyforgeAircraftRetainedGuildUtilityFixture.SteeringYawSourceFixture repeated =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileSteeringYawSource();
        SkyforgeAircraftSteeringYawSourceIR source = compiled.steeringYawSource();
        SkyforgeAircraftRudderControlAuthority authority = compiled.rudderAuthority();

        assertAll(
                () -> assertTrue(source.validation().passed()),
                () -> assertTrue(source.topologyChecks().passed()),
                () -> assertTrue(source.readiness().steeringWheelSourceStaticTopologyPassed()),
                () -> assertTrue(source.readiness().platformSteeringWheelRuntimeAuthorityReferenced()),
                () -> assertFalse(source.readiness().aircraftTailSourceRuntimeReady()),
                () -> assertFalse(source.readiness().cockpitRoutingReady()),
                () -> assertFalse(source.readiness().pilotInteractionBindingReady()),
                () -> assertFalse(source.readiness().flightQualified()),
                () -> assertEquals(compiled.v0131().yawControl().sha256(), source.sourceYawControlDigestSha256()),
                () -> assertEquals(authority.actuationDigestSha256(), source.sourceRudderActuationAuthorityDigestSha256()),
                () -> assertEquals(authority.neutralReturnDigestSha256(), source.sourceRudderNeutralReturnAuthorityDigestSha256()),
                () -> assertEquals(point(18, 3, 0), source.swivelBearingCoordinate()),
                () -> assertEquals(point(18, 3, 1), source.driveCogCoordinate()),
                () -> assertEquals(point(18, 2, 1), source.steeringWheelCoordinate()),
                () -> assertEquals("simulated:steering_wheel", source.steeringWheelResource()),
                () -> assertEquals(Map.of("facing", "north", "on_floor", "false", "waterlogged", "false"), source.steeringWheelBlockState()),
                () -> assertEquals(16, source.expectedSteeringWheelRpmMagnitude()),
                () -> assertEquals(48.0, source.wheelCommandDegrees()),
                () -> assertEquals(SkyforgeAircraftSteeringYawSourceProfile.PLATFORM_CAPABILITY, source.platformCapabilityId()),
                () -> assertEquals(SkyforgeAircraftSteeringYawSourceProfile.SIMULATED_SOURCE_COMMIT, source.simulatedSourceCommit()),
                () -> assertEquals(3, source.runtimeObligations().size()),
                () -> assertTrue(source.runtimeObligations().stream().allMatch(value -> "unverified".equals(value.status()))),
                () -> assertTrue(source.readiness().runtimeBlockers().contains("cockpit_kinetic_route_unresolved")),
                () -> assertTrue(source.readiness().runtimeBlockers().contains("pilot_interaction_binding_unverified")),
                () -> assertEquals(source, repeated.steeringYawSource()),
                () -> assertEquals(source.sha256(), repeated.steeringYawSource().sha256()),
                () -> assertEquals(64, source.sha256().length()));
    }

    @Test
    void currentRuntimeAuthorityDigestsAreDerivedFromCurrentYawIrNotFrozenSchemaStrings() {
        SkyforgeAircraftRetainedGuildUtilityFixture.YawFixture current =
                SkyforgeAircraftRetainedGuildUtilityFixture.compileV0131();
        SkyforgeAircraftRudderControlAuthority currentAuthority =
                SkyforgeAircraftRudderControlAuthority.accepted(current.yawControl());
        SkyforgeAircraftRetainedGuildUtilityFixture.Fixture v012 = current.v012();
        SkyforgeAircraftYawControlIR alternateYaw = new SkyforgeAircraftYawControlLowerer().lower(
                v012.manifest(), v012.assemblyFixture(), v012.glue(), v012.powertrain(),
                "skyforge.aircraft.guild_utility_monoplane.yaw_control.alternate",
                SkyforgeAircraftYawControlProfile.retainedC11());
        SkyforgeAircraftRudderControlAuthority alternateAuthority =
                SkyforgeAircraftRudderControlAuthority.accepted(alternateYaw);

        assertAll(
                () -> assertNotEquals(current.yawControl().sha256(), alternateYaw.sha256()),
                () -> assertNotEquals(currentAuthority.actuationDigestSha256(), alternateAuthority.actuationDigestSha256()),
                () -> assertNotEquals(currentAuthority.neutralReturnDigestSha256(), alternateAuthority.neutralReturnDigestSha256()),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkyforgeAircraftSteeringYawSourceLowerer().lower(
                        current.yawControl(), alternateAuthority, "mixed-authority", SkyforgeAircraftSteeringYawSourceProfile.retainedC11())));
    }

    @Test
    void sourceCoordinateMustRemainDirectlyBelowAcceptedDriveCog() {
        SkyforgeAircraftSteeringYawSourceProfile retained = SkyforgeAircraftSteeringYawSourceProfile.retainedC11();
        assertThrows(IllegalArgumentException.class, () -> copyProfile(
                retained, point(18, 2, 0), retained.driveCogCoordinate(), retained.steeringWheelResource(),
                retained.steeringWheelBlockState(), retained.expectedSteeringWheelRpmMagnitude(),
                retained.platformCapabilityId(), retained.simulatedSourceCommit()));
    }

    @Test
    void sourceResourceStateAndRpmDriftFailClosed() {
        SkyforgeAircraftSteeringYawSourceProfile retained = SkyforgeAircraftSteeringYawSourceProfile.retainedC11();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> copyProfile(
                        retained, retained.steeringWheelCoordinate(), retained.driveCogCoordinate(), "create:creative_motor",
                        retained.steeringWheelBlockState(), retained.expectedSteeringWheelRpmMagnitude(),
                        retained.platformCapabilityId(), retained.simulatedSourceCommit())),
                () -> assertThrows(IllegalArgumentException.class, () -> copyProfile(
                        retained, retained.steeringWheelCoordinate(), retained.driveCogCoordinate(), retained.steeringWheelResource(),
                        Map.of("facing", "north", "on_floor", "true", "waterlogged", "false"), retained.expectedSteeringWheelRpmMagnitude(),
                        retained.platformCapabilityId(), retained.simulatedSourceCommit())),
                () -> assertThrows(IllegalArgumentException.class, () -> copyProfile(
                        retained, retained.steeringWheelCoordinate(), retained.driveCogCoordinate(), retained.steeringWheelResource(),
                        retained.steeringWheelBlockState(), 32, retained.platformCapabilityId(), retained.simulatedSourceCommit())));
    }

    @Test
    void platformAndExactSimulatedSourceProvenanceDriftFailClosed() {
        SkyforgeAircraftSteeringYawSourceProfile retained = SkyforgeAircraftSteeringYawSourceProfile.retainedC11();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> copyProfile(
                        retained, retained.steeringWheelCoordinate(), retained.driveCogCoordinate(), retained.steeringWheelResource(),
                        retained.steeringWheelBlockState(), retained.expectedSteeringWheelRpmMagnitude(),
                        "CREATE_KINETIC_ON_SABLE_LIFECYCLE", retained.simulatedSourceCommit())),
                () -> assertThrows(IllegalArgumentException.class, () -> copyProfile(
                        retained, retained.steeringWheelCoordinate(), retained.driveCogCoordinate(), retained.steeringWheelResource(),
                        retained.steeringWheelBlockState(), retained.expectedSteeringWheelRpmMagnitude(),
                        retained.platformCapabilityId(), "0".repeat(40))));
    }

    private static SkyforgeAircraftSteeringYawSourceProfile copyProfile(
            SkyforgeAircraftSteeringYawSourceProfile source,
            AircraftBlockspaceIR.LatticePoint steeringWheelCoordinate,
            AircraftBlockspaceIR.LatticePoint driveCogCoordinate,
            String resource,
            Map<String, String> state,
            int rpm,
            String platformCapability,
            String sourceCommit) {
        return new SkyforgeAircraftSteeringYawSourceProfile(
                source.profileId(), steeringWheelCoordinate, driveCogCoordinate, resource, state, rpm,
                source.wheelCommandDegrees(), platformCapability, source.simulatedSourceRepository(), sourceCommit,
                source.sourceEvidence(), source.runtimeObligations());
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
