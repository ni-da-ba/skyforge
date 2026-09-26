package io.github.nidaba.skyforge.world;

/** Reach-independent D1 geomorphic measurement vector used by whole reaches and partitioned spans. */
public record SkyIslandGeomorphicMeasurements(
        double maximumCenterlineLoweringPotential,
        double maximumCenterlineLoweringWorldUnits,
        double maximumLateralRecoveryGrade,
        double maximumBankContainmentDeficitWorldUnits,
        double maximumDepthToBankfullWidthRatio,
        double maximumReliefToValleyWidthRatio,
        double normalizedExcavationBurden,
        double excavationVolumeProxyWorldUnitsCubed,
        double maximumCurvatureWidthRatio,
        double ridgeLengthFraction,
        double maximumLongitudinalGrade) {

    public SkyIslandGeomorphicMeasurements {
        requireNonNegative(maximumCenterlineLoweringPotential, "maximumCenterlineLoweringPotential");
        requireNonNegative(maximumCenterlineLoweringWorldUnits, "maximumCenterlineLoweringWorldUnits");
        requireNonNegative(maximumLateralRecoveryGrade, "maximumLateralRecoveryGrade");
        requireNonNegative(maximumBankContainmentDeficitWorldUnits, "maximumBankContainmentDeficitWorldUnits");
        requireNonNegative(maximumDepthToBankfullWidthRatio, "maximumDepthToBankfullWidthRatio");
        requireNonNegative(maximumReliefToValleyWidthRatio, "maximumReliefToValleyWidthRatio");
        requireNonNegative(normalizedExcavationBurden, "normalizedExcavationBurden");
        requireNonNegative(excavationVolumeProxyWorldUnitsCubed, "excavationVolumeProxyWorldUnitsCubed");
        requireNonNegative(maximumCurvatureWidthRatio, "maximumCurvatureWidthRatio");
        requireFraction(ridgeLengthFraction, "ridgeLengthFraction");
        requireNonNegative(maximumLongitudinalGrade, "maximumLongitudinalGrade");
    }

    public static SkyIslandGeomorphicMeasurements from(
            SkyIslandGeomorphicReachDiagnostics diagnostics) {
        return new SkyIslandGeomorphicMeasurements(
                diagnostics.maximumCenterlineLoweringPotential(),
                diagnostics.maximumCenterlineLoweringWorldUnits(),
                diagnostics.maximumLateralRecoveryGrade(),
                diagnostics.maximumBankContainmentDeficitWorldUnits(),
                diagnostics.maximumDepthToBankfullWidthRatio(),
                diagnostics.maximumReliefToValleyWidthRatio(),
                diagnostics.normalizedExcavationBurden(),
                diagnostics.excavationVolumeProxyWorldUnitsCubed(),
                diagnostics.maximumCurvatureWidthRatio(),
                diagnostics.ridgeSampleFraction(),
                diagnostics.maximumLongitudinalGrade());
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
