package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Position in a partially realized island frame.
 *
 * <p>Horizontal coordinates intentionally remain island-local. {@code physicalY} belongs to the
 * realized vertical coordinate system used by the supplied physical column field. A backend may
 * separately translate or rotate the horizontal axes into its world coordinates.
 */
public record SkyIslandRealizedSubsurfacePosition(
        SkyIslandLocalPosition horizontalPosition,
        double physicalY) {

    public SkyIslandRealizedSubsurfacePosition {
        horizontalPosition = Objects.requireNonNull(horizontalPosition, "horizontalPosition");
        if (!Double.isFinite(physicalY)) {
            throw new IllegalArgumentException("physicalY must be finite");
        }
    }

    public SkyIslandRealizedSubsurfacePosition(double localX, double physicalY, double localZ) {
        this(new SkyIslandLocalPosition(localX, localZ), physicalY);
    }

    public double localX() {
        return horizontalPosition.x();
    }

    public double localZ() {
        return horizontalPosition.z();
    }
}
