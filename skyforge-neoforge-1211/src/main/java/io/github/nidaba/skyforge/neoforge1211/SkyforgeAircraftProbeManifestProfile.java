package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;

/** Frozen-v0.9-equivalent static probe-manifest policy for the retained C11 target. */
public record SkyforgeAircraftProbeManifestProfile(
        String profileId,
        String targetProfileId,
        String airframeAerodynamicProviderId,
        OriginContract originContract) {
    public SkyforgeAircraftProbeManifestProfile {
        profileId = requireText("profileId", profileId);
        targetProfileId = requireText("targetProfileId", targetProfileId);
        airframeAerodynamicProviderId = requireText("airframeAerodynamicProviderId", airframeAerodynamicProviderId);
        originContract = Objects.requireNonNull(originContract, "originContract");
    }

    public static SkyforgeAircraftProbeManifestProfile retainedC11() {
        return new SkyforgeAircraftProbeManifestProfile(
                "skyforge.probe_manifest.create_aeronautics_1_3_2.v1",
                SkyforgeAircraftTargetProfile.retainedC11().profileId(),
                "create_white_sail_lift_v1",
                new OriginContract(
                        "compiler lattice x-forward y-up z-starboard",
                        "world position chosen for isolated exact-stack probe fixture",
                        true,
                        true,
                        false));
    }

    public record OriginContract(
            String coordinateFrame,
            String executionOrigin,
            boolean negativeCoordinatesAllowed,
            boolean destructivePlacement,
            boolean productionSchematic) {
        public OriginContract {
            coordinateFrame = requireText("coordinateFrame", coordinateFrame);
            executionOrigin = requireText("executionOrigin", executionOrigin);
            if (!"compiler lattice x-forward y-up z-starboard".equals(coordinateFrame)) {
                throw new IllegalArgumentException("bounded probe manifest requires retained compiler coordinate frame");
            }
            if (!negativeCoordinatesAllowed || !destructivePlacement || productionSchematic) {
                throw new IllegalArgumentException("bounded probe manifest requires destructive isolated probe-origin semantics");
            }
        }
    }

    private static String requireText(String property, String value) {
        Objects.requireNonNull(value, property);
        if (value.isBlank()) throw new IllegalArgumentException(property + " must not be blank");
        return value;
    }
}
