package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** AIRCRAFT-PROD-013 static lowering of the accepted temporary yaw source into a real Simulated Steering Wheel. */
public final class SkyforgeAircraftSteeringYawSourceLowerer {
    public SkyforgeAircraftSteeringYawSourceIR lower(
            SkyforgeAircraftYawControlIR yawControl,
            SkyforgeAircraftRudderControlAuthority rudderAuthority,
            String assetId,
            SkyforgeAircraftSteeringYawSourceProfile profile) {
        Objects.requireNonNull(yawControl, "yawControl");
        Objects.requireNonNull(rudderAuthority, "rudderAuthority");
        Objects.requireNonNull(profile, "profile");
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        if (!yawControl.validation().passed() || !yawControl.topologyChecks().passed() || !yawControl.countChecks().passed()) {
            throw new IllegalArgumentException("Steering Wheel source lowering requires accepted current yaw-control IR");
        }

        String yawDigest = yawControl.sha256();
        if (!yawDigest.equals(rudderAuthority.sourceYawControlDigestSha256())) {
            throw new IllegalArgumentException("rudder runtime authority does not belong to supplied yaw-control IR");
        }
        SkyforgeAircraftRudderControlAuthority currentAuthority = SkyforgeAircraftRudderControlAuthority.accepted(yawControl);
        if (!currentAuthority.actuationDigestSha256().equals(rudderAuthority.actuationDigestSha256())) {
            throw new IllegalArgumentException("rudder actuation authority digest is stale or mixed");
        }
        if (!currentAuthority.neutralReturnDigestSha256().equals(rudderAuthority.neutralReturnDigestSha256())) {
            throw new IllegalArgumentException("rudder neutral-return authority digest is stale or mixed");
        }
        if (!yawControl.swivelBearingCoordinate().equals(rudderAuthority.swivelBearingCoordinate())) {
            throw new IllegalArgumentException("rudder authority Swivel coordinate does not match current yaw-control IR");
        }
        if (!profile.driveCogCoordinate().equals(rudderAuthority.driveCogCoordinate())
                || !profile.steeringWheelCoordinate().equals(rudderAuthority.temporarySourceCoordinate())) {
            throw new IllegalArgumentException("Steering Wheel source profile does not preserve accepted AIRCRAFT-RUNTIME-003 source interface");
        }

        AircraftBlockspaceIR.LatticePoint expectedBelow = point(
                profile.driveCogCoordinate().x(), profile.driveCogCoordinate().y() - 1, profile.driveCogCoordinate().z());
        boolean sourceDirectlyBelow = expectedBelow.equals(profile.steeringWheelCoordinate());
        boolean exactState = Map.of("facing", "north", "on_floor", "false", "waterlogged", "false")
                .equals(profile.steeringWheelBlockState());
        boolean exactResource = "simulated:steering_wheel".equals(profile.steeringWheelResource());
        boolean sourceEvidenceValid = profile.sourceEvidence().stream().anyMatch(value ->
                value.contains(SkyforgeAircraftSteeringYawSourceProfile.SIMULATED_SOURCE_COMMIT)
                        && value.contains("hasShaftTowards") && value.contains("on_floor=false"));
        boolean platformAuthority = SkyforgeAircraftSteeringYawSourceProfile.PLATFORM_CAPABILITY.equals(profile.platformCapabilityId());
        boolean sourcePinned = SkyforgeAircraftSteeringYawSourceProfile.SIMULATED_SOURCE_REPOSITORY.equals(profile.simulatedSourceRepository())
                && SkyforgeAircraftSteeringYawSourceProfile.SIMULATED_SOURCE_COMMIT.equals(profile.simulatedSourceCommit());

        SkyforgeAircraftSteeringYawSourceIR.TopologyChecks checks = new SkyforgeAircraftSteeringYawSourceIR.TopologyChecks(
                yawDigest.equals(rudderAuthority.sourceYawControlDigestSha256()),
                currentAuthority.actuationDigestSha256().equals(rudderAuthority.actuationDigestSha256()),
                currentAuthority.neutralReturnDigestSha256().equals(rudderAuthority.neutralReturnDigestSha256()),
                point(18, 3, 1).equals(profile.driveCogCoordinate())
                        && point(18, 3, 0).equals(yawControl.swivelBearingCoordinate()),
                sourceDirectlyBelow,
                exactResource,
                exactState,
                sourceEvidenceValid,
                profile.expectedSteeringWheelRpmMagnitude() == rudderAuthority.sourceRpmMagnitude()
                        && profile.expectedSteeringWheelRpmMagnitude() == 16,
                Double.compare(profile.wheelCommandDegrees(), 48.0) == 0,
                platformAuthority,
                sourcePinned);
        if (!checks.passed()) throw new IllegalArgumentException("Steering Wheel yaw-source topology/source checks failed: " + checks);

        List<SkyforgeAircraftSteeringYawSourceIR.RuntimeObligation> obligations = profile.runtimeObligations().stream()
                .map(value -> new SkyforgeAircraftSteeringYawSourceIR.RuntimeObligation(value.id(), value.method(), "unverified"))
                .toList();
        return new SkyforgeAircraftSteeringYawSourceIR(
                SkyforgeAircraftSteeringYawSourceIR.SCHEMA_VERSION,
                assetId,
                SkyforgeAircraftSteeringYawSourceIR.COMPILER_VERSION,
                yawDigest,
                rudderAuthority.actuationDigestSha256(),
                rudderAuthority.neutralReturnDigestSha256(),
                profile.profileId(),
                yawControl.swivelBearingCoordinate(),
                profile.driveCogCoordinate(),
                profile.steeringWheelCoordinate(),
                profile.steeringWheelResource(),
                profile.steeringWheelBlockState(),
                profile.expectedSteeringWheelRpmMagnitude(),
                profile.wheelCommandDegrees(),
                profile.platformCapabilityId(),
                profile.simulatedSourceRepository(),
                profile.simulatedSourceCommit(),
                profile.sourceEvidence(),
                checks,
                obligations,
                new SkyforgeAircraftSteeringYawSourceIR.Readiness(
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        List.of(
                                "aircraft_steering_wheel_tail_runtime_unverified",
                                "cockpit_kinetic_route_unresolved",
                                "pilot_interaction_binding_unverified")),
                new SkyforgeAircraftSteeringYawSourceIR.Validation(
                        true,
                        "source_backed_static_simulated_steering_wheel_substitution_at_the_accepted_rudder_test_source_coordinate",
                        List.of(
                                "that the production aircraft tail network responds to a real Steering Wheel source",
                                "cockpit-accessible Steering Wheel placement or route",
                                "ordinary actual-client interaction on the production aircraft",
                                "passive self-centering",
                                "pitch/roll or multi-axis control",
                                "handling, stable flight, or human flight feel")));
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
