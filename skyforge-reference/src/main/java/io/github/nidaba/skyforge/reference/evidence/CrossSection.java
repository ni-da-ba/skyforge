package io.github.nidaba.skyforge.reference.evidence;

import java.util.Objects;

/** Immutable exact-coordinate samples through an island centerline. */
public final class CrossSection {
    /** Orientation of the varying coordinate. */
    public enum Axis {
        /** X varies while z remains at the descriptor center. */
        EAST_WEST("east-west", "x"),
        /** Z varies while x remains at the descriptor center. */
        NORTH_SOUTH("north-south", "z");

        private final String identifier;
        private final String coordinateLabel;

        Axis(String identifier, String coordinateLabel) {
            this.identifier = identifier;
            this.coordinateLabel = coordinateLabel;
        }

        /** Stable external identifier. */
        public String identifier() {
            return identifier;
        }

        /** Coordinate column name used by canonical CSV. */
        public String coordinateLabel() {
            return coordinateLabel;
        }
    }

    private final Axis axis;
    private final double fixedCoordinate;
    private final double[] coordinates;
    private final double[] heights;

    /** Creates a defensive finite cross-section. */
    public CrossSection(Axis axis, double fixedCoordinate, double[] coordinates, double[] heights) {
        this.axis = Objects.requireNonNull(axis, "axis");
        if (!Double.isFinite(fixedCoordinate)) {
            throw new IllegalArgumentException("fixedCoordinate must be finite");
        }
        Objects.requireNonNull(coordinates, "coordinates");
        Objects.requireNonNull(heights, "heights");
        if (coordinates.length < 2 || coordinates.length != heights.length) {
            throw new IllegalArgumentException("cross-section arrays must have equal length of at least two");
        }
        this.fixedCoordinate = fixedCoordinate;
        this.coordinates = coordinates.clone();
        this.heights = heights.clone();
        for (int index = 0; index < this.coordinates.length; index++) {
            if (!Double.isFinite(this.coordinates[index]) || !Double.isFinite(this.heights[index])) {
                throw new IllegalArgumentException("cross-section values must be finite");
            }
            if (index > 0 && !(this.coordinates[index] > this.coordinates[index - 1])) {
                throw new IllegalArgumentException("cross-section coordinates must strictly increase");
            }
        }
    }

    /** Returns the section orientation. */
    public Axis axis() {
        return axis;
    }

    /** Returns the non-varying horizontal coordinate. */
    public double fixedCoordinate() {
        return fixedCoordinate;
    }

    /** Returns the number of samples. */
    public int size() {
        return coordinates.length;
    }

    /** Returns one varying coordinate. */
    public double coordinateAt(int index) {
        return coordinates[index];
    }

    /** Returns one height. */
    public double heightAt(int index) {
        return heights[index];
    }

    /** Returns a defensive height copy. */
    public double[] heights() {
        return heights.clone();
    }

    /** Returns canonical UTF-8-compatible CSV using exact hexadecimal binary64 text. */
    public String canonicalCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append(axis.coordinateLabel()).append(",height\n");
        for (int index = 0; index < coordinates.length; index++) {
            csv.append(Double.toHexString(coordinates[index]))
                    .append(',')
                    .append(Double.toHexString(heights[index]))
                    .append('\n');
        }
        return csv.toString();
    }
}
