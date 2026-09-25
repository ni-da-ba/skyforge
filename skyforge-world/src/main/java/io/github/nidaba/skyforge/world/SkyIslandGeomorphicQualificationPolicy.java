package io.github.nidaba.skyforge.world;

/**
 * Explicit provisional geomorphic acceptance envelope for post-reset hydrology.
 *
 * <p>Thresholds are procedural-world calibration parameters, not universal fluvial constants.
 * They are intentionally centralized so corpus evidence can tune them without hiding policy inside
 * terrain-authoring code.
 */
public record SkyIslandGeomorphicQualificationPolicy(
        double alluvialMaxCenterlineLoweringPotential,
        double incisedMaxCenterlineLoweringPotential,
        double cascadeMaxCenterlineLoweringPotential,
        double alluvialMaxLateralRecoveryGrade,
        double incisedMaxLateralRecoveryGrade,
        double cascadeMaxLateralRecoveryGrade,
        double alluvialMaxLongitudinalGrade,
        double incisedMaxLongitudinalGrade,
        double cascadeMaxLongitudinalGrade,
        double alluvialMaxCurvatureWidthRatio,
        double incisedMaxCurvatureWidthRatio,
        double cascadeMaxCurvatureWidthRatio,
        double maxNormalizedExcavationBurden,
        double maxRidgeSampleFraction) {

    public SkyIslandGeomorphicQualificationPolicy {
        requirePositive(alluvialMaxCenterlineLoweringPotential, "alluvialMaxCenterlineLoweringPotential");
        requirePositive(incisedMaxCenterlineLoweringPotential, "incisedMaxCenterlineLoweringPotential");
        requirePositive(cascadeMaxCenterlineLoweringPotential, "cascadeMaxCenterlineLoweringPotential");
        requirePositive(alluvialMaxLateralRecoveryGrade, "alluvialMaxLateralRecoveryGrade");
        requirePositive(incisedMaxLateralRecoveryGrade, "incisedMaxLateralRecoveryGrade");
        requirePositive(cascadeMaxLateralRecoveryGrade, "cascadeMaxLateralRecoveryGrade");
        requirePositive(alluvialMaxLongitudinalGrade, "alluvialMaxLongitudinalGrade");
        requirePositive(incisedMaxLongitudinalGrade, "incisedMaxLongitudinalGrade");
        requirePositive(cascadeMaxLongitudinalGrade, "cascadeMaxLongitudinalGrade");
        requirePositive(alluvialMaxCurvatureWidthRatio, "alluvialMaxCurvatureWidthRatio");
        requirePositive(incisedMaxCurvatureWidthRatio, "incisedMaxCurvatureWidthRatio");
        requirePositive(cascadeMaxCurvatureWidthRatio, "cascadeMaxCurvatureWidthRatio");
        requirePositive(maxNormalizedExcavationBurden, "maxNormalizedExcavationBurden");
        requireFraction(maxRidgeSampleFraction, "maxRidgeSampleFraction");
    }

    /**
     * First conservative calibration. It exists to make implausible geometry rejectable now; corpus
     * evidence may revise these values before this layer becomes production authoring authority.
     */
    public static SkyIslandGeomorphicQualificationPolicy provisional() {
        return new SkyIslandGeomorphicQualificationPolicy(
                0.070, 0.110, 0.150,
                0.55, 0.90, 1.40,
                0.12, 0.28, 0.90,
                0.55, 0.70, 0.90,
                0.080,
                0.10);
    }

    public double maxCenterlineLoweringPotential(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> alluvialMaxCenterlineLoweringPotential;
            case INCISED -> incisedMaxCenterlineLoweringPotential;
            case CASCADE -> cascadeMaxCenterlineLoweringPotential;
        };
    }

    public double maxLateralRecoveryGrade(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> alluvialMaxLateralRecoveryGrade;
            case INCISED -> incisedMaxLateralRecoveryGrade;
            case CASCADE -> cascadeMaxLateralRecoveryGrade;
        };
    }

    public double maxLongitudinalGrade(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> alluvialMaxLongitudinalGrade;
            case INCISED -> incisedMaxLongitudinalGrade;
            case CASCADE -> cascadeMaxLongitudinalGrade;
        };
    }

    public double maxCurvatureWidthRatio(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> alluvialMaxCurvatureWidthRatio;
            case INCISED -> incisedMaxCurvatureWidthRatio;
            case CASCADE -> cascadeMaxCurvatureWidthRatio;
        };
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
