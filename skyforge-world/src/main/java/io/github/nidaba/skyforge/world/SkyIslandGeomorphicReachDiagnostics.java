package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Objective pre-carving geomorphic measurements for one hydraulic reach candidate. */
public record SkyIslandGeomorphicReachDiagnostics(
        SkyIslandHydraulicReachGeometry hydraulicReach,
        double maximumCenterlineLoweringPotential,
        double maximumCenterlineLoweringWorldUnits,
        double maximumLateralRecoveryGrade,
        double maximumBankContainmentDeficitWorldUnits,
        double maximumContainmentDeficitToWaterDepthRatio,
        double maximumDepthToBankfullWidthRatio,
        double maximumIncisionToBankfullWidthRatio,
        double maximumReliefToValleyWidthRatio,
        double normalizedExcavationBurden,
        double excavationVolumeProxyWorldUnitsCubed,
        double maximumCurvatureWidthRatio,
        double ridgeSampleFraction,
        double maximumLongitudinalGrade,
        double maximumRawTerrainUphillStepPotential) {

    public SkyIslandGeomorphicReachDiagnostics {
        hydraulicReach = Objects.requireNonNull(hydraulicReach, "hydraulicReach");
        requireNonNegative(maximumCenterlineLoweringPotential, "maximumCenterlineLoweringPotential");
        requireNonNegative(maximumCenterlineLoweringWorldUnits, "maximumCenterlineLoweringWorldUnits");
        requireNonNegative(maximumLateralRecoveryGrade, "maximumLateralRecoveryGrade");
        requireNonNegative(maximumBankContainmentDeficitWorldUnits, "maximumBankContainmentDeficitWorldUnits");
        requireNonNegative(maximumContainmentDeficitToWaterDepthRatio, "maximumContainmentDeficitToWaterDepthRatio");
        requireNonNegative(maximumDepthToBankfullWidthRatio, "maximumDepthToBankfullWidthRatio");
        requireNonNegative(maximumIncisionToBankfullWidthRatio, "maximumIncisionToBankfullWidthRatio");
        requireNonNegative(maximumReliefToValleyWidthRatio, "maximumReliefToValleyWidthRatio");
        requireNonNegative(normalizedExcavationBurden, "normalizedExcavationBurden");
        requireNonNegative(excavationVolumeProxyWorldUnitsCubed, "excavationVolumeProxyWorldUnitsCubed");
        requireNonNegative(maximumCurvatureWidthRatio, "maximumCurvatureWidthRatio");
        requireFraction(ridgeSampleFraction, "ridgeSampleFraction");
        requireNonNegative(maximumLongitudinalGrade, "maximumLongitudinalGrade");
        requireNonNegative(maximumRawTerrainUphillStepPotential, "maximumRawTerrainUphillStepPotential");
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
