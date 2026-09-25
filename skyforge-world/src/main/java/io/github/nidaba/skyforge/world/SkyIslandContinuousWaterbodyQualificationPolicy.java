package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit retained-water qualification policy.
 *
 * <p>No calibrated default is defined until the E1 fixed basin corpus is reviewed.
 */
public record SkyIslandContinuousWaterbodyQualificationPolicy(
        SkyIslandContinuousWaterbodyQualificationLimits pond,
        SkyIslandContinuousWaterbodyQualificationLimits lake) {

    public SkyIslandContinuousWaterbodyQualificationPolicy {
        pond = Objects.requireNonNull(pond, "pond");
        lake = Objects.requireNonNull(lake, "lake");
    }

    public SkyIslandContinuousWaterbodyQualificationLimits limits(
            SkyIslandWaterbodyKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case POND -> pond;
            case LAKE -> lake;
            case WETLAND -> throw new IllegalArgumentException(
                    "wetlands are saturated-margin semantics, not open-water basin qualification");
        };
    }
}
