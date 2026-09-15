package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.aircraft.AircraftBlockspaceIR;
import java.util.Objects;

/** Bounded discrete transform for the regular-sail horizontal/vertical tail junction. */
public record SkyforgeAircraftTailLoweringProfile(
        String profileId,
        String targetProfileId,
        String providerId,
        String resourceId,
        String stateProperty,
        AircraftBlockspaceIR.Role translatedRole,
        AircraftBlockspaceIR.Role junctionRetainedRole,
        String translatedState,
        String junctionRetainedState,
        Translation translationBlocks) {

    public SkyforgeAircraftTailLoweringProfile {
        profileId = requireText("profileId", profileId);
        targetProfileId = requireText("targetProfileId", targetProfileId);
        providerId = requireText("providerId", providerId);
        resourceId = requireResourceId(resourceId);
        stateProperty = requireText("stateProperty", stateProperty);
        translatedRole = Objects.requireNonNull(translatedRole, "translatedRole");
        junctionRetainedRole = Objects.requireNonNull(junctionRetainedRole, "junctionRetainedRole");
        if (translatedRole == junctionRetainedRole) {
            throw new IllegalArgumentException("translatedRole and junctionRetainedRole must differ");
        }
        translatedState = requireText("translatedState", translatedState);
        junctionRetainedState = requireText("junctionRetainedState", junctionRetainedState);
        translationBlocks = Objects.requireNonNull(translationBlocks, "translationBlocks");
        if (!new Translation(0, 1, 0).equals(translationBlocks)) {
            throw new IllegalArgumentException("bounded tail lowering requires one-block upward translation");
        }
    }

    public static SkyforgeAircraftTailLoweringProfile retainedC11() {
        return new SkyforgeAircraftTailLoweringProfile(
                "skyforge.tail_lowering.guild_utility.v1",
                SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                "create_white_sail_lift_v1",
                "create:white_sail",
                "facing",
                AircraftBlockspaceIR.Role.VERTICAL_TAIL_SURFACE_INTENT,
                AircraftBlockspaceIR.Role.HORIZONTAL_TAIL_SURFACE_INTENT,
                "south",
                "up",
                new Translation(0, 1, 0));
    }

    public record Translation(int dx, int dy, int dz) {}

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) {
            throw new IllegalArgumentException(property + " must not be blank");
        }
        return value;
    }

    private static String requireResourceId(String value) {
        String resource = requireText("resourceId", value);
        if (!resource.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid resourceId: " + value);
        }
        return resource;
    }
}
