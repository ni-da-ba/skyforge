package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0096 local surface-site evidence at one exact accepted watershed anchor.
 *
 * <p>Every value is descriptive. This record intentionally contains no structure role, buildability,
 * walkability, settlement, or aesthetic classification.
 */
public record SkyIslandSurfaceSiteCapabilityCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        boolean physicalSurfacePresent,
        OptionalDouble upperSurfaceOffsetNormalized,
        double authoredInteriority,
        double support3x3Fraction,
        double support5x5Fraction,
        double support9x9Fraction,
        OptionalDouble relief3x3Normalized,
        OptionalDouble relief5x5Normalized,
        OptionalDouble relief9x9Normalized,
        OptionalDouble meanCardinalGrade,
        double normalizedFlowAccumulation,
        boolean retainedWaterbody,
        boolean shoreline,
        double waterDepthPotential,
        double waterbodyMarginPotential,
        double riparianPotential,
        double channelRelativeDischarge,
        double hydrologicAdjustmentMagnitude) {

    public SkyIslandSurfaceSiteCapabilityCell {
        if (watershedCellIndex < 0) {
            throw new IllegalArgumentException("watershedCellIndex must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        upperSurfaceOffsetNormalized =
                Objects.requireNonNull(upperSurfaceOffsetNormalized, "upperSurfaceOffsetNormalized");
        relief3x3Normalized = Objects.requireNonNull(relief3x3Normalized, "relief3x3Normalized");
        relief5x5Normalized = Objects.requireNonNull(relief5x5Normalized, "relief5x5Normalized");
        relief9x9Normalized = Objects.requireNonNull(relief9x9Normalized, "relief9x9Normalized");
        meanCardinalGrade = Objects.requireNonNull(meanCardinalGrade, "meanCardinalGrade");

        requireNormalized("authoredInteriority", authoredInteriority);
        requireNormalized("support3x3Fraction", support3x3Fraction);
        requireNormalized("support5x5Fraction", support5x5Fraction);
        requireNormalized("support9x9Fraction", support9x9Fraction);
        requireNormalized("normalizedFlowAccumulation", normalizedFlowAccumulation);
        requireNormalized("waterDepthPotential", waterDepthPotential);
        requireNormalized("waterbodyMarginPotential", waterbodyMarginPotential);
        requireNormalized("riparianPotential", riparianPotential);
        requireNormalized("channelRelativeDischarge", channelRelativeDischarge);
        requireNormalized("hydrologicAdjustmentMagnitude", hydrologicAdjustmentMagnitude);

        if (physicalSurfacePresent) {
            if (upperSurfaceOffsetNormalized.isEmpty()
                    || relief3x3Normalized.isEmpty()
                    || relief5x5Normalized.isEmpty()
                    || relief9x9Normalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "present physical surface requires offset and local relief evidence");
            }
            requireFinite(
                    "upperSurfaceOffsetNormalized", upperSurfaceOffsetNormalized.orElseThrow());
            requireNonNegative("relief3x3Normalized", relief3x3Normalized.orElseThrow());
            requireNonNegative("relief5x5Normalized", relief5x5Normalized.orElseThrow());
            requireNonNegative("relief9x9Normalized", relief9x9Normalized.orElseThrow());
            if (support3x3Fraction <= 0.0
                    || support5x5Fraction <= 0.0
                    || support9x9Fraction <= 0.0) {
                throw new IllegalArgumentException(
                        "present physical surface must contribute to every local support window");
            }
        } else if (upperSurfaceOffsetNormalized.isPresent()
                || relief3x3Normalized.isPresent()
                || relief5x5Normalized.isPresent()
                || relief9x9Normalized.isPresent()
                || meanCardinalGrade.isPresent()) {
            throw new IllegalArgumentException(
                    "absent physical surface cannot carry physical elevation/relief/grade evidence");
        }

        if (meanCardinalGrade.isPresent()) {
            requireNonNegative("meanCardinalGrade", meanCardinalGrade.orElseThrow());
        }
        if (!retainedWaterbody && (shoreline || waterDepthPotential != 0.0)) {
            throw new IllegalArgumentException(
                    "shoreline/depth evidence requires retained-waterbody membership");
        }
    }

    private static void requireNormalized(String name, double value) {
        requireFinite(name, value);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }

    private static void requireNonNegative(String name, double value) {
        requireFinite(name, value);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
