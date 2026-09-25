package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit geomorphic qualification policy.
 *
 * <p>No calibrated default is defined until the post-C1 D1 reach-level corpus is reviewed.
 */
public record SkyIslandGeomorphicQualificationPolicy(
        SkyIslandGeomorphicProfileLimits alluvial,
        SkyIslandGeomorphicProfileLimits incised,
        SkyIslandGeomorphicProfileLimits cascade) {

    public SkyIslandGeomorphicQualificationPolicy {
        alluvial = Objects.requireNonNull(alluvial, "alluvial");
        incised = Objects.requireNonNull(incised, "incised");
        cascade = Objects.requireNonNull(cascade, "cascade");
    }

    public SkyIslandGeomorphicProfileLimits limits(SkyIslandChannelProfileKind kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case ALLUVIAL -> alluvial;
            case INCISED -> incised;
            case CASCADE -> cascade;
        };
    }

    /**
     * For mixed-profile macro reaches, use the most restrictive limit for each metric.
     *
     * <p>This prevents a short cascade subsection from laundering an otherwise alluvial reach into
     * a permissive envelope.
     */
    public SkyIslandGeomorphicProfileLimits limits(SkyIslandSemanticChannelReach reach) {
        Objects.requireNonNull(reach, "reach");
        SkyIslandGeomorphicProfileLimits result = null;
        for (SkyIslandChannelProfile profile : reach.profiles()) {
            SkyIslandGeomorphicProfileLimits candidate = limits(profile.kind());
            result = result == null ? candidate : restrictive(result, candidate);
        }
        return Objects.requireNonNull(result, "semantic reach has no profiles");
    }

    private static SkyIslandGeomorphicProfileLimits restrictive(
            SkyIslandGeomorphicProfileLimits a,
            SkyIslandGeomorphicProfileLimits b) {
        return new SkyIslandGeomorphicProfileLimits(
                Math.min(a.maximumCenterlineLoweringPotential(), b.maximumCenterlineLoweringPotential()),
                Math.min(a.maximumLateralRecoveryGrade(), b.maximumLateralRecoveryGrade()),
                Math.min(a.maximumBankContainmentDeficitWorldUnits(), b.maximumBankContainmentDeficitWorldUnits()),
                Math.min(a.maximumDepthToBankfullWidthRatio(), b.maximumDepthToBankfullWidthRatio()),
                Math.min(a.maximumReliefToValleyWidthRatio(), b.maximumReliefToValleyWidthRatio()),
                Math.min(a.maximumNormalizedExcavationBurden(), b.maximumNormalizedExcavationBurden()),
                Math.min(a.maximumExcavationVolumeWorldUnitsCubed(), b.maximumExcavationVolumeWorldUnitsCubed()),
                Math.min(a.maximumCurvatureWidthRatio(), b.maximumCurvatureWidthRatio()),
                Math.min(a.maximumRidgeSampleFraction(), b.maximumRidgeSampleFraction()),
                Math.min(a.maximumLongitudinalGrade(), b.maximumLongitudinalGrade()));
    }
}
