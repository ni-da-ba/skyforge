package io.github.nidaba.skyforge.world;

/**
 * Backend-independent two-dimensional position expressed in island-local Skyforge world units.
 *
 * <p>The origin is the authored island center. Positive axes are semantic local axes only; a backend
 * may translate or rotate them when placing an authored island in a realized world.
 */
public record SkyIslandLocalPosition(double x, double z) {
    /** Validates one finite island-local position. */
    public SkyIslandLocalPosition {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("island-local coordinates must be finite");
        }
    }
}
