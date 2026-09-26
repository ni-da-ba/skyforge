package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * One deterministic fine candidate route and its pre-carving geomorphic diagnostics.
 *
 * <p>`uphillStepFraction` and `ridgeSampleFraction` retain their historical component names for
 * source compatibility, but C3 defines them as arc-length occupancy fractions rather than raw sample
 * counts. `positiveAscentPotential` is directed positive terrain variation; `maximumUphillGrade`
 * is rise per world-space run. `maxUphillStep` remains discretization-sensitive evidence only.
 */
public record SkyIslandGeomorphicCandidateRoute(
        List<SkyIslandLocalPosition> points,
        double totalCost,
        double pathLength,
        double maxGuidanceDeviation,
        double uphillStepFraction,
        double ridgeSampleFraction,
        double meanValleyFloorAdvantage,
        double positiveAscentPotential,
        double maximumUphillGrade,
        double maxUphillStep) {

    public SkyIslandGeomorphicCandidateRoute {
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new IllegalArgumentException("geomorphic route requires at least two points");
        }
        points.forEach(point -> Objects.requireNonNull(point, "point"));
        requireFiniteNonNegative(totalCost, "totalCost");
        requireFinitePositive(pathLength, "pathLength");
        requireFiniteNonNegative(maxGuidanceDeviation, "maxGuidanceDeviation");
        requireFraction(uphillStepFraction, "uphillStepFraction");
        requireFraction(ridgeSampleFraction, "ridgeSampleFraction");
        if (!Double.isFinite(meanValleyFloorAdvantage)) {
            throw new IllegalArgumentException("meanValleyFloorAdvantage must be finite");
        }
        requireFiniteNonNegative(positiveAscentPotential, "positiveAscentPotential");
        requireFiniteNonNegative(maximumUphillGrade, "maximumUphillGrade");
        requireFiniteNonNegative(maxUphillStep, "maxUphillStep");
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
