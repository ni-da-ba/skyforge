package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Backend-neutral semantic chamber volume associated with one AUTH-0024 cave node. */
public record SkyIslandCaveChamberGeometry(
        int nodeId,
        SkyIslandSubsurfacePosition center,
        double horizontalRadius,
        double depthRadius,
        double irregularity) {

    public SkyIslandCaveChamberGeometry {
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId must be non-negative");
        }
        center = Objects.requireNonNull(center, "center");
        requirePositive("horizontalRadius", horizontalRadius);
        requirePositive("depthRadius", depthRadius);
        if (depthRadius > 0.35) {
            throw new IllegalArgumentException("depthRadius is a semantic depth fraction and must remain local");
        }
        if (!Double.isFinite(irregularity) || irregularity < 0.0 || irregularity > 1.0) {
            throw new IllegalArgumentException("irregularity must be finite and in [0, 1]");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
