package io.github.nidaba.skyforge.reference.sampling;

/** An inclusive, uniformly spaced horizontal sampling grid. */
public record GridSpec(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ,
        int width,
        int height) {
    /** Validates that this grid has finite, increasing bounds and at least two samples per axis. */
    public GridSpec {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        if (!(maximumX > minimumX)) {
            throw new IllegalArgumentException("maximumX must be greater than minimumX");
        }
        if (!(maximumZ > minimumZ)) {
            throw new IllegalArgumentException("maximumZ must be greater than minimumZ");
        }
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("grid dimensions must each be at least two");
        }
        Math.multiplyExact(width, height);
    }

    /** Returns the exact coordinate represented by a column, including both declared endpoints. */
    public double xAt(int xIndex) {
        checkIndex("xIndex", xIndex, width);
        if (xIndex == width - 1) {
            return maximumX;
        }
        return minimumX + (maximumX - minimumX) * xIndex / (width - 1);
    }

    /** Returns the exact coordinate represented by a row, including both declared endpoints. */
    public double zAt(int zIndex) {
        checkIndex("zIndex", zIndex, height);
        if (zIndex == height - 1) {
            return maximumZ;
        }
        return minimumZ + (maximumZ - minimumZ) * zIndex / (height - 1);
    }

    /** Returns the constant horizontal spacing between adjacent columns. */
    public double spacingX() {
        return (maximumX - minimumX) / (width - 1);
    }

    /** Returns the constant horizontal spacing between adjacent rows. */
    public double spacingZ() {
        return (maximumZ - minimumZ) / (height - 1);
    }

    /** Returns the row-major sample count, rejecting integer overflow during construction. */
    public int sampleCount() {
        return width * height;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void checkIndex(String property, int index, int limit) {
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException(property + " outside [0, " + limit + ")");
        }
    }
}
