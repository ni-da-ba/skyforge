package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Objective pre-carving plausibility report for one candidate hydraulic reach. */
public record SkyIslandGeomorphicReachQualification(
        SkyIslandHydraulicReachGeometry hydraulicReach,
        SkyIslandContinuousChannelCenterline centerline,
        List<SkyIslandGeomorphicQualificationFailureKind> failures,
        double ridgeSampleFraction,
        double maximumCenterlineLoweringWorld,
        double maximumIncisionToFullWidthRatio,
        double maximumWaterDepthToFullWidthRatio,
        double impliedMaximumLateralGrade,
        double maximumLongitudinalWaterSurfaceGrade,
        double minimumCurvatureRadiusToFullWidthRatio,
        double normalizedExcavationVolume,
        double maximumRawTerrainAscentWorld) {

    public SkyIslandGeomorphicReachQualification {
        hydraulicReach = Objects.requireNonNull(hydraulicReach, "hydraulicReach");
        centerline = Objects.requireNonNull(centerline, "centerline");
        failures = List.copyOf(failures);
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
        requireFraction(ridgeSampleFraction, "ridgeSampleFraction");
        requireFiniteNonNegative(maximumCenterlineLoweringWorld, "maximumCenterlineLoweringWorld");
        requireFiniteNonNegative(maximumIncisionToFullWidthRatio, "maximumIncisionToFullWidthRatio");
        requireFiniteNonNegative(maximumWaterDepthToFullWidthRatio, "maximumWaterDepthToFullWidthRatio");
        requireFiniteNonNegative(impliedMaximumLateralGrade, "impliedMaximumLateralGrade");
        requireFiniteNonNegative(maximumLongitudinalWaterSurfaceGrade, "maximumLongitudinalWaterSurfaceGrade");
        requireFiniteNonNegative(minimumCurvatureRadiusToFullWidthRatio, "minimumCurvatureRadiusToFullWidthRatio");
        requireFiniteNonNegative(normalizedExcavationVolume, "normalizedExcavationVolume");
        requireFiniteNonNegative(maximumRawTerrainAscentWorld, "maximumRawTerrainAscentWorld");
    }

    public boolean accepted() {
        return failures.isEmpty();
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
