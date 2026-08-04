package io.github.nidaba.skyforge.reference.evidence;

import java.util.Objects;

/** Exact signed-density samples for one vertical center slice. */
public final class VolumeSlice {
    /** Horizontal direction traversed by the slice. */
    public enum Axis {
        EAST_WEST("x"),
        NORTH_SOUTH("z");

        private final String coordinateName;

        Axis(String coordinateName) {
            this.coordinateName = coordinateName;
        }

        /** Returns the world-coordinate label used by canonical CSV. */
        public String coordinateName() {
            return coordinateName;
        }
    }

    private final Axis axis;
    private final double fixedCoordinate;
    private final double[] horizontalCoordinates;
    private final double[] verticalCoordinates;
    private final double[] densities;

    /** Creates a finite y-row, horizontal-column density slice. */
    public VolumeSlice(
            Axis axis,
            double fixedCoordinate,
            double[] horizontalCoordinates,
            double[] verticalCoordinates,
            double[] densities) {
        this.axis = Objects.requireNonNull(axis, "axis");
        requireFinite("fixedCoordinate", fixedCoordinate);
        this.fixedCoordinate = fixedCoordinate;
        this.horizontalCoordinates = finiteCopy("horizontalCoordinates", horizontalCoordinates);
        this.verticalCoordinates = finiteCopy("verticalCoordinates", verticalCoordinates);
        this.densities = finiteCopy("densities", densities);
        int expected = Math.multiplyExact(
                this.horizontalCoordinates.length, this.verticalCoordinates.length);
        if (this.horizontalCoordinates.length < 2 || this.verticalCoordinates.length < 2) {
            throw new IllegalArgumentException("slice dimensions must each be at least two");
        }
        if (this.densities.length != expected) {
            throw new IllegalArgumentException("density count does not match slice dimensions");
        }
    }

    /** Returns the horizontal direction. */
    public Axis axis() {
        return axis;
    }

    /** Returns the orthogonal world coordinate held constant. */
    public double fixedCoordinate() {
        return fixedCoordinate;
    }

    /** Returns the number of horizontal samples. */
    public int width() {
        return horizontalCoordinates.length;
    }

    /** Returns the number of vertical samples. */
    public int height() {
        return verticalCoordinates.length;
    }

    /** Returns a horizontal world coordinate. */
    public double horizontalCoordinateAt(int index) {
        return horizontalCoordinates[index];
    }

    /** Returns a vertical world coordinate. */
    public double verticalCoordinateAt(int index) {
        return verticalCoordinates[index];
    }

    /** Returns signed density at one y-row, horizontal-column sample. */
    public double densityAt(int horizontalIndex, int verticalIndex) {
        return densities[verticalIndex * width() + horizontalIndex];
    }

    /** Returns canonical hexadecimal CSV with the strict positive-inside classification. */
    public String canonicalCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(axis.coordinateName()).append(",y,density,solid\n");
        for (int y = 0; y < height(); y++) {
            for (int horizontal = 0; horizontal < width(); horizontal++) {
                double density = densityAt(horizontal, y);
                csv.append(Double.toHexString(horizontalCoordinateAt(horizontal))).append(',');
                csv.append(Double.toHexString(verticalCoordinateAt(y))).append(',');
                csv.append(Double.toHexString(density)).append(',');
                csv.append(density > 0.0 ? '1' : '0').append('\n');
            }
        }
        return csv.toString();
    }

    private static double[] finiteCopy(String property, double[] source) {
        Objects.requireNonNull(source, property);
        double[] result = source.clone();
        for (double value : result) {
            requireFinite(property, value);
        }
        return result;
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must contain only finite values");
        }
    }
}
