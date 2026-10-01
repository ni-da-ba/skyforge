package io.github.nidaba.skyforge.world;

/** Uniform world-space lattice used by backend realization proofs. */
public record WorldSampleGrid(
        double minimumX,
        double minimumY,
        double minimumZ,
        double spacingX,
        double spacingY,
        double spacingZ,
        int xSamples,
        int ySamples,
        int zSamples) {

    /** Validates finite origin, positive spacing, and positive sample counts. */
    public WorldSampleGrid {
        requireFinite("minimumX", minimumX);
        requireFinite("minimumY", minimumY);
        requireFinite("minimumZ", minimumZ);
        requirePositive("spacingX", spacingX);
        requirePositive("spacingY", spacingY);
        requirePositive("spacingZ", spacingZ);
        requirePositive("xSamples", xSamples);
        requirePositive("ySamples", ySamples);
        requirePositive("zSamples", zSamples);
    }

    public double xAt(int index) {
        requireIndex("x", index, xSamples);
        return minimumX + spacingX * index;
    }

    public double yAt(int index) {
        requireIndex("y", index, ySamples);
        return minimumY + spacingY * index;
    }

    public double zAt(int index) {
        requireIndex("z", index, zSamples);
        return minimumZ + spacingZ * index;
    }

    public double maximumX() {
        return xAt(xSamples - 1);
    }

    public double maximumY() {
        return yAt(ySamples - 1);
    }

    public double maximumZ() {
        return zAt(zSamples - 1);
    }

    public int sampleCount() {
        return Math.multiplyExact(Math.multiplyExact(xSamples, ySamples), zSamples);
    }

    public int linearIndex(int x, int y, int z) {
        requireIndex("x", x, xSamples);
        requireIndex("y", y, ySamples);
        requireIndex("z", z, zSamples);
        return Math.addExact(
                x,
                Math.multiplyExact(xSamples, Math.addExact(z, Math.multiplyExact(zSamples, y))));
    }

    public WorldBounds bounds() {
        return new WorldBounds(
                minimumX,
                maximumX(),
                minimumY,
                maximumY(),
                minimumZ,
                maximumZ());
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }

    private static void requirePositive(String property, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(property + " must be finite and positive");
        }
    }

    private static void requirePositive(String property, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }

    private static void requireIndex(String axis, int index, int samples) {
        if (index < 0 || index >= samples) {
            throw new IndexOutOfBoundsException(axis + " index " + index + " outside [0, " + samples + ")");
        }
    }
}
