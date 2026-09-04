package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One sampled semantic centerline point and local corridor radius for a cave passage. */
public record SkyIslandCavePassagePoint(
        SkyIslandSubsurfacePosition position,
        double radius) {

    public SkyIslandCavePassagePoint {
        position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("passage radius must be positive and finite");
        }
    }
}
