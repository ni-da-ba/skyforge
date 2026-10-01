package io.github.nidaba.skyforge.reference.sampling;

/** An inclusive, uniformly spaced three-dimensional sampling grid. */
public record VolumeGridSpec(
        double minimumX,
        double maximumX,
        double minimumY,
        double maximumY,
        double minimumZ,
        double maximumZ,
        int xSamples,
        int ySamples,
        int zSamples) {
    /**
     * Validates finite increasing bounds, at least two samples per axis, and an addressable sample
     * count.
     */
    public VolumeGridSpec {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumY", minimumY);
        requireFinite("maximumY", maximumY);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        requireIncreasing("x", minimumX, maximumX);
        requireIncreasing("y", minimumY, maximumY);
        requireIncreasing("z", minimumZ, maximumZ);
        if (xSamples < 2 || ySamples < 2 || zSamples < 2) {
            throw new IllegalArgumentException("volume grid dimensions must each be at least two");
        }
        Math.multiplyExact(Math.multiplyExact(xSamples, zSamples), ySamples);
    }

    /** Returns the exact x coordinate represented by an index, including both endpoints. */
    public double xAt(int index) {
        return coordinateAt("xIndex", index, xSamples, minimumX, maximumX);
    }

    /** Returns the exact y coordinate represented by an index, including both endpoints. */
    public double yAt(int index) {
        return coordinateAt("yIndex", index, ySamples, minimumY, maximumY);
    }

    /** Returns the exact z coordinate represented by an index, including both endpoints. */
    public double zAt(int index) {
        return coordinateAt("zIndex", index, zSamples, minimumZ, maximumZ);
    }

    /** Returns constant x spacing. */
    public double spacingX() {
        return (maximumX - minimumX) / (xSamples - 1);
    }

    /** Returns constant y spacing. */
    public double spacingY() {
        return (maximumY - minimumY) / (ySamples - 1);
    }

    /** Returns constant z spacing. */
    public double spacingZ() {
        return (maximumZ - minimumZ) / (zSamples - 1);
    }

    /**
     * Returns the canonical linear index. Traversal increments x first, then z, then y, preserving
     * each horizontal x-z layer as one contiguous row-major block.
     */
    public int linearIndex(int xIndex, int yIndex, int zIndex) {
        checkIndex("xIndex", xIndex, xSamples);
        checkIndex("yIndex", yIndex, ySamples);
        checkIndex("zIndex", zIndex, zSamples);
        return (yIndex * zSamples + zIndex) * xSamples + xIndex;
    }

    /** Returns the canonical sample count. */
    public int sampleCount() {
        return xSamples * zSamples * ySamples;
    }

    private static double coordinateAt(
            String property, int index, int samples, double minimum, double maximum) {
        checkIndex(property, index, samples);
        if (index == samples - 1) {
            return maximum;
        }
        return minimum + (maximum - minimum) * index / (samples - 1);
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requireIncreasing(String axis, double minimum, double maximum) {
        if (!(maximum > minimum)) {
            throw new IllegalArgumentException("maximum" + axis.toUpperCase(java.util.Locale.ROOT)
                    + " must be greater than minimum" + axis.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static void checkIndex(String property, int index, int limit) {
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException(property + " outside [0, " + limit + ")");
        }
    }
}
