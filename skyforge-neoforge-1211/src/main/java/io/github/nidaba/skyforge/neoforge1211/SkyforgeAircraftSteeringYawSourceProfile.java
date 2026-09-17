package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Source-backed production contract for replacing the temporary yaw test source with Simulated Steering Wheel. */
public record SkyforgeAircraftSteeringYawSourceProfile(
        String profileId,
        AircraftBlockspaceIR.LatticePoint steeringWheelCoordinate,
        AircraftBlockspaceIR.LatticePoint driveCogCoordinate,
        String steeringWheelResource,
        Map<String, String> steeringWheelBlockState,
        int expectedSteeringWheelRpmMagnitude,
        double wheelCommandDegrees,
        String platformCapabilityId,
        String simulatedSourceRepository,
        String simulatedSourceCommit,
        List<String> sourceEvidence,
        List<RuntimeObligation> runtimeObligations) {
    public static final String PROFILE_ID = "skyforge.steering_yaw_source.simulated.guild_utility.v0_17";
    public static final String PLATFORM_CAPABILITY = "STEERING_WHEEL_CLIENT_ON_SABLE_LIFECYCLE";
    public static final String SIMULATED_SOURCE_REPOSITORY = "Creators-of-Aeronautics/Simulated-Project";
    public static final String SIMULATED_SOURCE_COMMIT = "50443d00afa06e0982b45f40cd686f7ecf978132";

    public SkyforgeAircraftSteeringYawSourceProfile {
        profileId = requireText("profileId", profileId);
        if (!PROFILE_ID.equals(profileId)) throw new IllegalArgumentException("unsupported Steering Wheel yaw-source profile: " + profileId);
        steeringWheelCoordinate = Objects.requireNonNull(steeringWheelCoordinate, "steeringWheelCoordinate");
        driveCogCoordinate = Objects.requireNonNull(driveCogCoordinate, "driveCogCoordinate");
        if (!point(18, 2, 1).equals(steeringWheelCoordinate) || !point(18, 3, 1).equals(driveCogCoordinate)) {
            throw new IllegalArgumentException("retained Steering Wheel source/drive-cog coordinates changed");
        }
        if (!steeringWheelCoordinate.equals(point(driveCogCoordinate.x(), driveCogCoordinate.y() - 1, driveCogCoordinate.z()))) {
            throw new IllegalArgumentException("Steering Wheel source must remain directly below the accepted Y-axis drive cog");
        }
        steeringWheelResource = requireText("steeringWheelResource", steeringWheelResource);
        if (!"simulated:steering_wheel".equals(steeringWheelResource)) {
            throw new IllegalArgumentException("exact Steering Wheel resource contract changed");
        }
        steeringWheelBlockState = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(steeringWheelBlockState, "steeringWheelBlockState")));
        if (!Map.of("facing", "north", "on_floor", "false", "waterlogged", "false").equals(steeringWheelBlockState)) {
            throw new IllegalArgumentException("exact Steering Wheel block-state contract changed");
        }
        if (expectedSteeringWheelRpmMagnitude != 16) throw new IllegalArgumentException("Steering Wheel RPM authority is exactly 16");
        if (Double.compare(wheelCommandDegrees, 48.0) != 0) throw new IllegalArgumentException("retained bounded Steering Wheel command reference is exactly 48 degrees");
        platformCapabilityId = requireText("platformCapabilityId", platformCapabilityId);
        if (!PLATFORM_CAPABILITY.equals(platformCapabilityId)) throw new IllegalArgumentException("wrong reusable Steering Wheel Platform authority");
        simulatedSourceRepository = requireText("simulatedSourceRepository", simulatedSourceRepository);
        simulatedSourceCommit = requireText("simulatedSourceCommit", simulatedSourceCommit);
        if (!SIMULATED_SOURCE_REPOSITORY.equals(simulatedSourceRepository) || !SIMULATED_SOURCE_COMMIT.equals(simulatedSourceCommit)) {
            throw new IllegalArgumentException("exact Simulated source provenance changed");
        }
        sourceEvidence = List.copyOf(Objects.requireNonNull(sourceEvidence, "sourceEvidence"));
        if (sourceEvidence.stream().noneMatch(value -> value.contains(SIMULATED_SOURCE_COMMIT) && value.contains("hasShaftTowards"))) {
            throw new IllegalArgumentException("profile must retain exact SteeringWheelBlock shaft source evidence");
        }
        if (sourceEvidence.stream().noneMatch(value -> value.contains("RPM=16") && value.contains("updateTargetAngle"))) {
            throw new IllegalArgumentException("profile must retain exact SteeringWheelBlockEntity speed/command source evidence");
        }
        if (sourceEvidence.stream().noneMatch(value -> value.contains(PLATFORM_CAPABILITY))) {
            throw new IllegalArgumentException("profile must reference accepted reusable Steering Wheel Platform authority");
        }
        ArrayList<RuntimeObligation> obligations = new ArrayList<>(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        obligations.sort(java.util.Comparator.comparing(RuntimeObligation::id));
        runtimeObligations = List.copyOf(obligations);
        if (!runtimeObligations.stream().map(RuntimeObligation::id).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of(
                "cockpit_route_probe",
                "pilot_interaction_probe",
                "steering_wheel_rudder_runtime_probe"))) {
            throw new IllegalArgumentException("Steering Wheel source runtime-obligation set changed");
        }
    }

    public static SkyforgeAircraftSteeringYawSourceProfile retainedC11() {
        return new SkyforgeAircraftSteeringYawSourceProfile(
                PROFILE_ID,
                point(18, 2, 1),
                point(18, 3, 1),
                "simulated:steering_wheel",
                Map.of("facing", "north", "on_floor", "false", "waterlogged", "false"),
                16,
                48.0,
                PLATFORM_CAPABILITY,
                SIMULATED_SOURCE_REPOSITORY,
                SIMULATED_SOURCE_COMMIT,
                List.of(
                        "Simulated exact source " + SIMULATED_SOURCE_COMMIT + ": SteeringWheelBlock.getRotationAxis returns Y and hasShaftTowards exposes UP when on_floor=false, so [18,2,1] shafts directly upward into [18,3,1].",
                        "Simulated exact source " + SIMULATED_SOURCE_COMMIT + ": SteeringWheelBlockEntity declares RPM=16 and updateTargetAngle clamps/bounds signed generated rotation until the requested angle completes.",
                        "Accepted Platform capability " + PLATFORM_CAPABILITY + " proves ordinary actual-client Steering Wheel acquisition plus real +/-16 RPM bounded command/release behavior on Sable; this static aircraft tranche references rather than reproduces that fixture.",
                        "Accepted AIRCRAFT-RUNTIME-003 temporary source interface is [18,2,1] below drive cog [18,3,1] beside Swivel [18,3,0]; source substitution does not authorize cockpit routing."),
                List.of(
                        new RuntimeObligation("steering_wheel_rudder_runtime_probe", "replace the temporary source with the real Steering Wheel at the accepted source coordinate and prove the existing aft cog/Swivel/rudder path responds causally"),
                        new RuntimeObligation("cockpit_route_probe", "route a compiler-emitted pilot-adjacent Steering Wheel to the accepted aft drive-cog interface without crossing child or air-gap boundaries"),
                        new RuntimeObligation("pilot_interaction_probe", "prove ordinary actual-client aircraft interaction through the complete production route using the accepted Platform Steering Wheel lifecycle")));
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtimeObligation.id", id);
            method = requireText("runtimeObligation.method", method);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }

    private static AircraftBlockspaceIR.LatticePoint point(int x, int y, int z) {
        return new AircraftBlockspaceIR.LatticePoint(x, y, z);
    }
}
