package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Retained bounded profile for static Create pilot-seat placement only. */
public record SkyforgeAircraftPilotStationProfile(
        String profileId,
        String targetProfileId,
        String airframeAerodynamicProviderId,
        AircraftBlockspaceIR.AnchorType stationType,
        Offset installationOffsetBlocks,
        String seatResourceId,
        Map<String, String> seatBlockState,
        String evidenceLevel,
        ControlBindingContract controlBindingContract,
        List<RuntimeObligation> runtimeObligations) {
    public SkyforgeAircraftPilotStationProfile {
        profileId = requireText("profileId", profileId);
        targetProfileId = requireText("targetProfileId", targetProfileId);
        airframeAerodynamicProviderId = requireText("airframeAerodynamicProviderId", airframeAerodynamicProviderId);
        stationType = Objects.requireNonNull(stationType, "stationType");
        if (stationType != AircraftBlockspaceIR.AnchorType.PILOT_STATION) {
            throw new IllegalArgumentException("bounded pilot profile requires PILOT_STATION");
        }
        installationOffsetBlocks = Objects.requireNonNull(installationOffsetBlocks, "installationOffsetBlocks");
        if (!installationOffsetBlocks.equals(new Offset(0, 1, 0))) {
            throw new IllegalArgumentException("bounded pilot profile requires one-block upward installation offset");
        }
        seatResourceId = requireResourceId(seatResourceId);
        seatBlockState = Map.copyOf(Objects.requireNonNull(seatBlockState, "seatBlockState"));
        if (!seatBlockState.equals(Map.of("waterlogged", "false"))) {
            throw new IllegalArgumentException("bounded pilot profile requires explicit waterlogged=false seat state");
        }
        evidenceLevel = requireText("evidenceLevel", evidenceLevel);
        controlBindingContract = Objects.requireNonNull(controlBindingContract, "controlBindingContract");
        runtimeObligations = List.copyOf(Objects.requireNonNull(runtimeObligations, "runtimeObligations"));
        if (runtimeObligations.stream().map(RuntimeObligation::id).distinct().count() != runtimeObligations.size()) {
            throw new IllegalArgumentException("pilot runtime obligation ids must be unique");
        }
        if (!runtimeObligations.stream().map(RuntimeObligation::id).toList()
                .containsAll(List.of("pilot_occupancy_probe", "control_binding_probe"))) {
            throw new IllegalArgumentException("pilot profile requires occupancy and control-binding obligations");
        }
    }

    public static SkyforgeAircraftPilotStationProfile retainedC11() {
        return new SkyforgeAircraftPilotStationProfile(
                "skyforge.pilot_station.create_1_21_1.v1",
                SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                "create_white_sail_lift_v1",
                AircraftBlockspaceIR.AnchorType.PILOT_STATION,
                new Offset(0, 1, 0),
                "create:brown_seat",
                Map.of("waterlogged", "false"),
                "create_registry_and_seat_movement_source_verified_sable_passenger_behavior_unverified",
                new ControlBindingContract(
                        "runtime_unverified",
                        false,
                        true,
                        "occupancy is a placement concern; pilot input routing to propulsion and movable control surfaces is a separate runtime contract"),
                List.of(
                        new RuntimeObligation(
                                "pilot_occupancy_probe",
                                "assemble exact-stack Sable vehicle and verify player occupy/attach/dismount/save-reload behavior for the compiled Create seat"),
                        new RuntimeObligation(
                                "control_binding_probe",
                                "after one-axis control hardware exists, verify explicit pilot input reaches the intended actuator and returns to neutral without hidden manual setup")));
    }

    public record Offset(int dx, int dy, int dz) {}

    public record ControlBindingContract(
            String status,
            boolean requiredForProbeEmission,
            boolean requiredForFlightQualification,
            String reason) {
        public ControlBindingContract {
            status = requireText("control binding status", status);
            reason = requireText("control binding reason", reason);
            if (!"runtime_unverified".equals(status)) {
                throw new IllegalArgumentException("bounded control binding must remain runtime_unverified");
            }
            if (requiredForProbeEmission) {
                throw new IllegalArgumentException("static probe emission must not require pilot control binding");
            }
            if (!requiredForFlightQualification) {
                throw new IllegalArgumentException("flight qualification must require pilot control binding");
            }
        }
    }

    public record RuntimeObligation(String id, String method) {
        public RuntimeObligation {
            id = requireText("runtime obligation id", id);
            method = requireText("runtime obligation method", method);
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }

    private static String requireResourceId(String value) {
        String resourceId = requireText("resourceId", value);
        if (!resourceId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("resourceId must be namespaced: " + resourceId);
        }
        return resourceId;
    }
}
