package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0097 raw directional support/edge evidence from one physically supported surface anchor.
 *
 * <p>No field is a runway, dock, cliff, walkability, or concrete-clearance threshold.
 */
public record SkyIslandSurfaceAccessRay(
        SkyIslandSurfaceAccessDirection direction,
        int availableStepCount,
        double stepDistanceNormalized,
        int consecutiveSupportedStepCount,
        OptionalDouble firstOpenDistanceNormalized,
        double furthestSupportedDistanceNormalized,
        int boundaryOpenTailStepCount,
        double boundaryOpenTailDistanceNormalized,
        int supportTransitionCount,
        double maximumRiseFromAnchorNormalized,
        double maximumFallFromAnchorNormalized,
        OptionalDouble meanAdjacentSupportedGrade) {

    private static final double CONSISTENCY_EPSILON = 1.0e-10;

    public SkyIslandSurfaceAccessRay {
        direction = Objects.requireNonNull(direction, "direction");
        firstOpenDistanceNormalized =
                Objects.requireNonNull(firstOpenDistanceNormalized, "firstOpenDistanceNormalized");
        meanAdjacentSupportedGrade =
                Objects.requireNonNull(meanAdjacentSupportedGrade, "meanAdjacentSupportedGrade");

        if (availableStepCount < 0) {
            throw new IllegalArgumentException("availableStepCount must be non-negative");
        }
        requirePositive("stepDistanceNormalized", stepDistanceNormalized);
        requireCount("consecutiveSupportedStepCount", consecutiveSupportedStepCount, availableStepCount);
        requireCount("boundaryOpenTailStepCount", boundaryOpenTailStepCount, availableStepCount);
        requireCount("supportTransitionCount", supportTransitionCount, availableStepCount);
        requireNonNegative("furthestSupportedDistanceNormalized", furthestSupportedDistanceNormalized);
        requireNonNegative("boundaryOpenTailDistanceNormalized", boundaryOpenTailDistanceNormalized);
        requireNonNegative("maximumRiseFromAnchorNormalized", maximumRiseFromAnchorNormalized);
        requireNonNegative("maximumFallFromAnchorNormalized", maximumFallFromAnchorNormalized);

        double maximumDistance = availableStepCount * stepDistanceNormalized;
        if (furthestSupportedDistanceNormalized > maximumDistance + CONSISTENCY_EPSILON
                || boundaryOpenTailDistanceNormalized > maximumDistance + CONSISTENCY_EPSILON) {
            throw new IllegalArgumentException("surface-access distance exceeds available lattice ray");
        }
        double expectedOpenTail = boundaryOpenTailStepCount * stepDistanceNormalized;
        if (Math.abs(expectedOpenTail - boundaryOpenTailDistanceNormalized) > CONSISTENCY_EPSILON) {
            throw new IllegalArgumentException(
                    "boundaryOpenTailDistanceNormalized must equal open-tail steps times step distance");
        }

        if (firstOpenDistanceNormalized.isPresent()) {
            double firstOpen = firstOpenDistanceNormalized.orElseThrow();
            requirePositive("firstOpenDistanceNormalized", firstOpen);
            double expectedFirstOpen = (consecutiveSupportedStepCount + 1) * stepDistanceNormalized;
            if (Math.abs(firstOpen - expectedFirstOpen) > CONSISTENCY_EPSILON) {
                throw new IllegalArgumentException(
                        "first open distance must immediately follow consecutive supported steps");
            }
        } else if (consecutiveSupportedStepCount != availableStepCount) {
            throw new IllegalArgumentException(
                    "missing first-open distance requires support through the lattice boundary");
        }

        if (meanAdjacentSupportedGrade.isPresent()) {
            requireNonNegative(
                    "meanAdjacentSupportedGrade", meanAdjacentSupportedGrade.orElseThrow());
        }
    }

    public boolean observedOpenSample() {
        return firstOpenDistanceNormalized.isPresent();
    }

    private static void requireCount(String name, int value, int maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [0, availableStepCount]");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
