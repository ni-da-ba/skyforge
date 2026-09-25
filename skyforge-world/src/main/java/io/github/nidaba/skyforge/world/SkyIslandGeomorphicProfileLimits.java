package io.github.nidaba.skyforge.world;

/** Hard acceptance limits for one channel profile family. */
public record SkyIslandGeomorphicProfileLimits(
        double maximumCenterlineLoweringPotential,
        double maximumLateralRecoveryGrade,
        double maximumBankContainmentDeficitWorldUnits,
        double maximumDepthToBankfullWidthRatio,
        double maximumReliefToValleyWidthRatio,
        double maximumNormalizedExcavationBurden,
        double maximumExcavationVolumeWorldUnitsCubed,
        double maximumCurvatureWidthRatio,
        double maximumRidgeSampleFraction,
        double maximumLongitudinalGrade) {

    public SkyIslandGeomorphicProfileLimits {
        requirePositive(maximumCenterlineLoweringPotential, "maximumCenterlineLoweringPotential");
        requirePositive(maximumLateralRecoveryGrade, "maximumLateralRecoveryGrade");
        requireNonNegative(maximumBankContainmentDeficitWorldUnits, "maximumBankContainmentDeficitWorldUnits");
        requirePositive(maximumDepthToBankfullWidthRatio, "maximumDepthToBankfullWidthRatio");
        requirePositive(maximumReliefToValleyWidthRatio, "maximumReliefToValleyWidthRatio");
        requirePositive(maximumNormalizedExcavationBurden, "maximumNormalizedExcavationBurden");
        requirePositive(maximumExcavationVolumeWorldUnitsCubed, "maximumExcavationVolumeWorldUnitsCubed");
        requirePositive(maximumCurvatureWidthRatio, "maximumCurvatureWidthRatio");
        requireFraction(maximumRidgeSampleFraction, "maximumRidgeSampleFraction");
        requirePositive(maximumLongitudinalGrade, "maximumLongitudinalGrade");
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
