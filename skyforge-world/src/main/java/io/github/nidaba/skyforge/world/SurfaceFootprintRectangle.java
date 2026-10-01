package io.github.nidaba.skyforge.world;

/** One axis-aligned world-space X/Z rectangle participating in a surface footprint. */
public record SurfaceFootprintRectangle(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ) {

    /** Validates finite non-inverted rectangle coordinates. */
    public SurfaceFootprintRectangle {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        if (maximumX < minimumX) {
            throw new IllegalArgumentException("maximumX must be greater than or equal to minimumX");
        }
        if (maximumZ < minimumZ) {
            throw new IllegalArgumentException("maximumZ must be greater than or equal to minimumZ");
        }
    }

    /** Returns whether this rectangle contains the supplied world-space sample. */
    public boolean contains(double x, double z) {
        return x >= minimumX && x <= maximumX && z >= minimumZ && z <= maximumZ;
    }

    /** Returns whether the rectangle expanded uniformly in X/Z contains the supplied sample. */
    public boolean expandedContains(double x, double z, double expansion) {
        requireFinite("expansion", expansion);
        if (expansion < 0.0) {
            throw new IllegalArgumentException("expansion must be non-negative");
        }
        return x >= minimumX - expansion
                && x <= maximumX + expansion
                && z >= minimumZ - expansion
                && z <= maximumZ + expansion;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
