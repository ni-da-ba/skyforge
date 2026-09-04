package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-independent semantic position inside an authored island.
 *
 * <p>The horizontal position is expressed in the same island-local world units used by surface
 * authorship. {@code depthFraction} is semantic depth measured downward from the authored upper
 * surface: 0 is immediately beneath the surface and 1 is the deep interior/underside zone. It is
 * deliberately not a Minecraft Y coordinate and does not prescribe a physical cave-carver frame.
 */
public record SkyIslandSubsurfacePosition(
        SkyIslandLocalPosition surfacePosition,
        double depthFraction) {

    public SkyIslandSubsurfacePosition {
        surfacePosition = Objects.requireNonNull(surfacePosition, "surfacePosition");
        if (!Double.isFinite(depthFraction) || depthFraction < 0.0 || depthFraction > 1.0) {
            throw new IllegalArgumentException("depthFraction must be finite and in [0, 1]");
        }
    }

    public SkyIslandSubsurfacePosition(double x, double z, double depthFraction) {
        this(new SkyIslandLocalPosition(x, z), depthFraction);
    }

    public double x() {
        return surfacePosition.x();
    }

    public double z() {
        return surfacePosition.z();
    }
}
