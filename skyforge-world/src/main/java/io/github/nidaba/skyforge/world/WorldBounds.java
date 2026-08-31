package io.github.nidaba.skyforge.world;

/** Closed finite world-space axis-aligned bounds used for conservative backend queries. */
public record WorldBounds(
        double minimumX,
        double maximumX,
        double minimumY,
        double maximumY,
        double minimumZ,
        double maximumZ) {

    /** Validates finite ordered bounds. Zero thickness is allowed for conservative boundary queries. */
    public WorldBounds {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumY", minimumY);
        requireFinite("maximumY", maximumY);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        requireOrdered("x", minimumX, maximumX);
        requireOrdered("y", minimumY, maximumY);
        requireOrdered("z", minimumZ, maximumZ);
    }

    /** Returns true when these closed bounds overlap or touch the supplied bounds. */
    public boolean intersects(WorldBounds other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return maximumX >= other.minimumX
                && minimumX <= other.maximumX
                && maximumY >= other.minimumY
                && minimumY <= other.maximumY
                && maximumZ >= other.minimumZ
                && minimumZ <= other.maximumZ;
    }

    /** Returns true when the supplied point lies inside or on these closed bounds. */
    public boolean contains(double x, double y, double z) {
        return x >= minimumX && x <= maximumX
                && y >= minimumY && y <= maximumY
                && z >= minimumZ && z <= maximumZ;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requireOrdered(String axis, double minimum, double maximum) {
        if (maximum < minimum) {
            throw new IllegalArgumentException(
                    "maximum" + axis.toUpperCase(java.util.Locale.ROOT)
                            + " must be greater than or equal to minimum"
                            + axis.toUpperCase(java.util.Locale.ROOT));
        }
    }
}
