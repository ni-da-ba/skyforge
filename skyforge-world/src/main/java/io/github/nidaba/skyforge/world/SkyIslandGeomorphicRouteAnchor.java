package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * A bounded region in which a continuous hydrologic route may choose a physical anchor position.
 *
 * <p>The semantic graph relationship is authoritative; the exact coarse planning-cell center is not.
 */
public record SkyIslandGeomorphicRouteAnchor(
        SkyIslandLocalPosition center,
        double radius) {

    public SkyIslandGeomorphicRouteAnchor {
        center = Objects.requireNonNull(center, "center");
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("anchor radius must be finite and non-negative");
        }
    }

    public boolean contains(SkyIslandLocalPosition position, double tolerance) {
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative");
        }
        return Math.hypot(position.x() - center.x(), position.z() - center.z())
                <= radius + tolerance;
    }
}
