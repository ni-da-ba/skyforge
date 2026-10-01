package io.github.nidaba.skyforge.kernel.coordinate;

/**
 * An immutable coordinate in Skyforge's horizontal {@code x-z} plane.
 *
 * @param x the east-west coordinate in abstract world units
 * @param z the north-south coordinate in abstract world units
 */
public record Coordinate2(double x, double z) {
    /**
     * Creates a finite horizontal coordinate.
     *
     * @throws IllegalArgumentException if either component is not finite
     */
    public Coordinate2 {
        requireFinite("x", x);
        requireFinite("z", z);
    }

    private static void requireFinite(String component, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(component + " must be finite");
        }
    }
}
