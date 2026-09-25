package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit geomorphic qualification policy for aggregate D1 reach diagnostics.
 *
 * <p>No calibrated default is defined until the post-C1 D1 reach-level corpus is reviewed.
 *
 * <p>Mixed-profile macro reaches have their own calibration class. Aggregate maxima cannot safely
 * be attributed to a specific constituent profile, so applying the most restrictive constituent
 * limit would create false profile-specific conclusions.
 */
public record SkyIslandGeomorphicQualificationPolicy(
        SkyIslandGeomorphicProfileLimits alluvial,
        SkyIslandGeomorphicProfileLimits incised,
        SkyIslandGeomorphicProfileLimits cascade,
        SkyIslandGeomorphicProfileLimits mixed) {

    public SkyIslandGeomorphicQualificationPolicy {
        alluvial = Objects.requireNonNull(alluvial, "alluvial");
        incised = Objects.requireNonNull(incised, "incised");
        cascade = Objects.requireNonNull(cascade, "cascade");
        mixed = Objects.requireNonNull(mixed, "mixed");
    }

    public SkyIslandGeomorphicProfileLimits limits(
            SkyIslandGeomorphicQualificationClass classification) {
        return switch (Objects.requireNonNull(classification, "classification")) {
            case ALLUVIAL -> alluvial;
            case INCISED -> incised;
            case CASCADE -> cascade;
            case MIXED -> mixed;
        };
    }

    public SkyIslandGeomorphicProfileLimits limits(SkyIslandSemanticChannelReach reach) {
        return limits(SkyIslandGeomorphicQualificationClass.classify(
                Objects.requireNonNull(reach, "reach")));
    }
}
