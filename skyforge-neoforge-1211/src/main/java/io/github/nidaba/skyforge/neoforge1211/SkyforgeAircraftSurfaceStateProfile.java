package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Source-backed static block-state policy for regular Create aircraft lifting sails. */
public record SkyforgeAircraftSurfaceStateProfile(
        String profileId,
        String targetProfileId,
        String providerId,
        String stateProperty,
        Set<String> legalStates,
        Map<AircraftBlockspaceIR.Role, String> roleStates) {

    public SkyforgeAircraftSurfaceStateProfile {
        profileId = requireText("profileId", profileId);
        targetProfileId = requireText("targetProfileId", targetProfileId);
        providerId = requireText("providerId", providerId);
        stateProperty = requireText("stateProperty", stateProperty);
        legalStates = Set.copyOf(Objects.requireNonNull(legalStates, "legalStates"));
        if (legalStates.isEmpty()) {
            throw new IllegalArgumentException("legalStates must not be empty");
        }
        for (String legalState : legalStates) {
            requireText("legalState", legalState);
        }
        EnumMap<AircraftBlockspaceIR.Role, String> normalized = new EnumMap<>(AircraftBlockspaceIR.Role.class);
        normalized.putAll(Objects.requireNonNull(roleStates, "roleStates"));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("roleStates must not be empty");
        }
        for (Map.Entry<AircraftBlockspaceIR.Role, String> entry : normalized.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "roleStates role");
            String state = requireText("role state", entry.getValue());
            if (!legalStates.contains(state)) {
                throw new IllegalArgumentException(
                        "role state '" + state + "' is not declared in legalStates");
            }
        }
        roleStates = Map.copyOf(new LinkedHashMap<>(normalized));
    }

    public static SkyforgeAircraftSurfaceStateProfile retainedC11() {
        return new SkyforgeAircraftSurfaceStateProfile(
                "skyforge.surface_state.create_regular_sail.guild_utility.v1",
                SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                "create_white_sail_lift_v1",
                "facing",
                Set.of("up", "south"),
                Map.of(
                        AircraftBlockspaceIR.Role.WING_SURFACE_INTENT, "up",
                        AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT, "up",
                        AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT, "south"));
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }
}
