package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Bounded affine difference constraint {@code lower <= x[left] - x[right] <= upper}.
 */
public record SkyIslandHydraulicDifferenceConstraint(
        String id,
        int leftIndex,
        int rightIndex,
        double lower,
        double upper) {

    public SkyIslandHydraulicDifferenceConstraint {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("constraint id must not be blank");
        }
        if (leftIndex < 0 || rightIndex < 0 || leftIndex == rightIndex) {
            throw new IllegalArgumentException("difference constraint requires distinct non-negative indices");
        }
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper) {
            throw new IllegalArgumentException("difference bounds must be finite and ordered");
        }
    }
}
