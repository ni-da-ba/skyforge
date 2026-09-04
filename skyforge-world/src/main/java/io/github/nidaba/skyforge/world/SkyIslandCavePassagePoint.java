package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One sampled semantic centerline point and local corridor thickness for a cave passage. */
public record SkyIslandCavePassagePoint(
        SkyIslandSubsurfacePosition position,
        double horizontalRadius,
        double depthRadius) {

    public SkyIslandCavePassagePoint {
        position = Objects.requireNonNull(position, "position");
        requirePositive("horizontalRadius", horizontalRadius);
        requirePositive("depthRadius", depthRadius);
        if (depthRadius > 0.25) {
            throw new IllegalArgumentException("depthRadius is a semantic depth fraction and must remain local");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
