package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Objective pre-carving geomorphic qualification for one hydraulic reach candidate. */
public record SkyIslandGeomorphicReachQualification(
        SkyIslandHydraulicReachGeometry hydraulicReach,
        double maximumCenterlineLoweringPotential,
        double maximumCenterlineLoweringWorldUnits,
        double maximumLateralRecoveryGrade,
        double normalizedExcavationBurden,
        double maximumCurvatureWidthRatio,
        double ridgeSampleFraction,
        double maximumLongitudinalGrade,
        double excavationVolumeProxyWorldUnitsCubed,
        List<SkyIslandGeomorphicQualificationViolation> violations) {

    public SkyIslandGeomorphicReachQualification {
        hydraulicReach = Objects.requireNonNull(hydraulicReach, "hydraulicReach");
        requireNonNegative(maximumCenterlineLoweringPotential, "maximumCenterlineLoweringPotential");
        requireNonNegative(maximumCenterlineLoweringWorldUnits, "maximumCenterlineLoweringWorldUnits");
        requireNonNegative(maximumLateralRecoveryGrade, "maximumLateralRecoveryGrade");
        requireNonNegative(normalizedExcavationBurden, "normalizedExcavationBurden");
        requireNonNegative(maximumCurvatureWidthRatio, "maximumCurvatureWidthRatio");
        requireFraction(ridgeSampleFraction, "ridgeSampleFraction");
        requireNonNegative(maximumLongitudinalGrade, "maximumLongitudinalGrade");
        requireNonNegative(excavationVolumeProxyWorldUnitsCubed, "excavationVolumeProxyWorldUnitsCubed");
        violations = List.copyOf(violations);
        violations.forEach(violation -> Objects.requireNonNull(violation, "violation"));
    }

    public boolean accepted() {
        return violations.isEmpty();
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
