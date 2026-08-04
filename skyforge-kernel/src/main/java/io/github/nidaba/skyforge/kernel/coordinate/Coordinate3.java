package io.github.nidaba.skyforge.kernel.coordinate;

/**
 * An immutable coordinate in Skyforge's right-handed {@code x-y-z} space.
 * The {@code y} axis points upward.
 *
 * @param x the east-west coordinate in abstract world units
 * @param y the vertical coordinate in abstract world units
 * @param z the north-south coordinate in abstract world units
 */
public record Coordinate3(double x, double y, double z) {
    /**
     * Creates a finite spatial coordinate.
     *
     * @throws IllegalArgumentException if any component is not finite
     */
    public Coordinate3 {
        requireFinite("x", x);
        requireFinite("y", y);
        requireFinite("z", z);
    }

    private static void requireFinite(String component, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(component + " must be finite");
        }
    }
}
