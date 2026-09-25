package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit geomorphic qualification policy for aggregate D1 reach diagnostics.
 *
 * <p>Mixed-profile macro reaches have their own calibration class because aggregate maxima cannot
 * safely be attributed to one constituent profile.
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

    /**
     * First post-C1 evidence-backed envelope.
     *
     * <p>The envelope is deliberately conservative. It separates the low-surgery tableland/stress
     * controls from the extreme basin-family excavation cluster in the fixed D1 reach corpus. It is
     * not a claim of universal natural-river constants.
     *
     * <p>Pure incised reaches are underrepresented in the initial fixed corpus, so the incised
     * envelope is a conservative interpolation between alluvial and cascade behavior and must be
     * expanded with a dedicated incised control before final production freeze.
     */
    public static SkyIslandGeomorphicQualificationPolicy firstEvidenceBacked() {
        return new SkyIslandGeomorphicQualificationPolicy(
                // alluvial
                new SkyIslandGeomorphicProfileLimits(
                        0.050, 0.75, 1.0, 0.35, 0.35, 0.040, 3.0, 0.15, 0.50),
                // incised (provisional until dedicated pure-incised corpus evidence is added)
                new SkyIslandGeomorphicProfileLimits(
                        0.080, 1.50, 2.0, 0.45, 0.75, 0.050, 3.5, 0.25, 1.50),
                // cascade
                new SkyIslandGeomorphicProfileLimits(
                        0.050, 2.00, 2.0, 0.35, 1.00, 0.050, 3.0, 0.40, 3.50),
                // mixed aggregate
                new SkyIslandGeomorphicProfileLimits(
                        0.080, 2.00, 3.0, 0.35, 0.80, 0.040, 4.0, 0.35, 3.25));
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
