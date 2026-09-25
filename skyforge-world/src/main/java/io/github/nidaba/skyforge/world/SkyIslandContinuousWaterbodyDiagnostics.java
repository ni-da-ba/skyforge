package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Objective pre-authoring diagnostics for one continuous retained-water basin candidate. */
public record SkyIslandContinuousWaterbodyDiagnostics(
        SkyIslandContinuousWaterbodyBasin basin,
        double equivalentDiameter,
        double maximumDepthWorldUnits,
        double depthToEquivalentDiameterRatio,
        double maximumShorelineGrade,
        double spillHeadroomWorldUnits,
        int matchedTerminalReachCount,
        double maximumChannelDatumMismatchWorldUnits,
        boolean reachesSearchBoundary,
        int shorelineCrossingCount) {

    public SkyIslandContinuousWaterbodyDiagnostics {
        basin = Objects.requireNonNull(basin, "basin");
        requirePositive(equivalentDiameter, "equivalentDiameter");
        requireNonNegative(maximumDepthWorldUnits, "maximumDepthWorldUnits");
        requireNonNegative(depthToEquivalentDiameterRatio, "depthToEquivalentDiameterRatio");
        requireNonNegative(maximumShorelineGrade, "maximumShorelineGrade");
        requireNonNegative(spillHeadroomWorldUnits, "spillHeadroomWorldUnits");
        if (matchedTerminalReachCount < 0) {
            throw new IllegalArgumentException("matchedTerminalReachCount must be non-negative");
        }
        requireNonNegative(maximumChannelDatumMismatchWorldUnits, "maximumChannelDatumMismatchWorldUnits");
        if (shorelineCrossingCount < 0) {
            throw new IllegalArgumentException("shorelineCrossingCount must be non-negative");
        }
    }

    public boolean hasClosedNumericalShoreline() {
        return !reachesSearchBoundary && shorelineCrossingCount > 0;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
