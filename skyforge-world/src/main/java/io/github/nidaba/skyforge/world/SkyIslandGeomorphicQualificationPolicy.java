package io.github.nidaba.skyforge.world;

/**
 * Explicit hard-safety envelope for pre-authoring hydromorphology.
 *
 * <p>These values are procedural-world acceptance policy, not claims of universal river constants.
 * D2 keeps policy separate from measurement so calibration can be reviewed and changed without
 * altering the diagnostic mathematics.
 */
public record SkyIslandGeomorphicQualificationPolicy(
        double maxCenterlineLoweringPotential,
        double maxIncisionToBankfullWidthRatio,
        double maxLateralRecoveryGrade,
        double maxContainmentDeficitToWaterDepthRatio,
        double maxDepthToBankfullWidthRatio,
        double maxNormalizedExcavationBurden,
        double maxCurvatureWidthRatio,
        double maxRidgeSampleFraction,
        double maxLongitudinalGrade,
        double maxRawTerrainUphillStepPotential) {

    public SkyIslandGeomorphicQualificationPolicy {
        requirePositive(maxCenterlineLoweringPotential, "maxCenterlineLoweringPotential");
        requirePositive(maxIncisionToBankfullWidthRatio, "maxIncisionToBankfullWidthRatio");
        requirePositive(maxLateralRecoveryGrade, "maxLateralRecoveryGrade");
        requirePositive(maxContainmentDeficitToWaterDepthRatio, "maxContainmentDeficitToWaterDepthRatio");
        requirePositive(maxDepthToBankfullWidthRatio, "maxDepthToBankfullWidthRatio");
        requirePositive(maxNormalizedExcavationBurden, "maxNormalizedExcavationBurden");
        requirePositive(maxCurvatureWidthRatio, "maxCurvatureWidthRatio");
        requireFraction(maxRidgeSampleFraction, "maxRidgeSampleFraction");
        requirePositive(maxLongitudinalGrade, "maxLongitudinalGrade");
        requirePositive(maxRawTerrainUphillStepPotential, "maxRawTerrainUphillStepPotential");
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
