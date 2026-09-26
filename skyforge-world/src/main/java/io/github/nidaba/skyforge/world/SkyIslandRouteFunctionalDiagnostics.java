package io.github.nidaba.skyforge.world;

/** Resolution-consistent diagnostics for one terrain-aware search route. */
public record SkyIslandRouteFunctionalDiagnostics(
        double objective,
        double pathLength,
        double maximumGuidanceDeviation,
        double positiveElevationVariation,
        double maximumUphillGrade,
        double ridgeLengthFraction,
        double meanValleyFloorAdvantage) {

    public SkyIslandRouteFunctionalDiagnostics {
        requireFiniteNonNegative(objective, "objective");
        requireFinitePositive(pathLength, "pathLength");
        requireFiniteNonNegative(maximumGuidanceDeviation, "maximumGuidanceDeviation");
        requireFiniteNonNegative(positiveElevationVariation, "positiveElevationVariation");
        requireFiniteNonNegative(maximumUphillGrade, "maximumUphillGrade");
        requireFraction(ridgeLengthFraction, "ridgeLengthFraction");
        if (!Double.isFinite(meanValleyFloorAdvantage)) {
            throw new IllegalArgumentException("meanValleyFloorAdvantage must be finite");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
