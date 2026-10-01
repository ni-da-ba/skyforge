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
        double minimumTerminalChannelDatumOffsetWorldUnits,
        double maximumTerminalChannelDatumOffsetWorldUnits,
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
        requireFinite(minimumTerminalChannelDatumOffsetWorldUnits,
                "minimumTerminalChannelDatumOffsetWorldUnits");
        requireFinite(maximumTerminalChannelDatumOffsetWorldUnits,
                "maximumTerminalChannelDatumOffsetWorldUnits");
        requireNonNegative(
                maximumChannelDatumMismatchWorldUnits, "maximumChannelDatumMismatchWorldUnits");
        if (matchedTerminalReachCount == 0) {
            if (minimumTerminalChannelDatumOffsetWorldUnits != 0.0
                    || maximumTerminalChannelDatumOffsetWorldUnits != 0.0
                    || maximumChannelDatumMismatchWorldUnits != 0.0) {
                throw new IllegalArgumentException("unmatched terminal has no channel datum offset");
            }
        } else {
            if (minimumTerminalChannelDatumOffsetWorldUnits
                    > maximumTerminalChannelDatumOffsetWorldUnits + 1.0e-9) {
                throw new IllegalArgumentException("terminal channel datum offset range is reversed");
            }
            double rangeMaximum =
                    Math.max(Math.abs(minimumTerminalChannelDatumOffsetWorldUnits),
                            Math.abs(maximumTerminalChannelDatumOffsetWorldUnits));
            if (Math.abs(rangeMaximum - maximumChannelDatumMismatchWorldUnits) > 1.0e-9) {
                throw new IllegalArgumentException("absolute datum mismatch must match signed offset range");
            }
        }
        if (shorelineCrossingCount < 0) {
            throw new IllegalArgumentException("shorelineCrossingCount must be non-negative");
        }
    }

    public boolean hasClosedNumericalShoreline() {
        return !reachesSearchBoundary
                && shorelineCrossingCount > 0
                && basin.hasClosedNumericalShoreline();
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
