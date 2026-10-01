package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral footprint and policy thresholds for evaluating one candidate surface.
 *
 * <p>The footprint is a union of world-space X/Z rectangles. Sampling is deterministic and
 * independent of backend block/chunk concepts. The historical rectangular constructor remains as a
 * convenience for callers that do not need piece-aware geometry.
 */
public record SurfaceSupportRequirements(
        SurfaceFootprint footprint,
        double sampleSpacing,
        double clearance,
        double minimumCoverageFraction,
        double minimumClearanceCoverageFraction,
        double maximumHeightSpan) {

    /** Validates finite geometry and normalized policy thresholds. */
    public SurfaceSupportRequirements {
        Objects.requireNonNull(footprint, "footprint");
        requireFinite("sampleSpacing", sampleSpacing);
        requireFinite("clearance", clearance);
        requireFinite("maximumHeightSpan", maximumHeightSpan);
        if (sampleSpacing <= 0.0) {
            throw new IllegalArgumentException("sampleSpacing must be greater than zero");
        }
        if (clearance < 0.0) {
            throw new IllegalArgumentException("clearance must be non-negative");
        }
        requireFraction("minimumCoverageFraction", minimumCoverageFraction);
        requireFraction("minimumClearanceCoverageFraction", minimumClearanceCoverageFraction);
        if (maximumHeightSpan < 0.0) {
            throw new IllegalArgumentException("maximumHeightSpan must be non-negative");
        }
        requireFinite("expandedMinimumX", footprint.minimumX() - clearance);
        requireFinite("expandedMaximumX", footprint.maximumX() + clearance);
        requireFinite("expandedMinimumZ", footprint.minimumZ() - clearance);
        requireFinite("expandedMaximumZ", footprint.maximumZ() + clearance);
    }

    /** Backward-compatible convenience constructor for one rectangular footprint. */
    public SurfaceSupportRequirements(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double sampleSpacing,
            double clearance,
            double minimumCoverageFraction,
            double minimumClearanceCoverageFraction,
            double maximumHeightSpan) {
        this(
                SurfaceFootprint.rectangle(minimumX, maximumX, minimumZ, maximumZ),
                sampleSpacing,
                clearance,
                minimumCoverageFraction,
                minimumClearanceCoverageFraction,
                maximumHeightSpan);
    }

    public double minimumX() {
        return footprint.minimumX();
    }

    public double maximumX() {
        return footprint.maximumX();
    }

    public double minimumZ() {
        return footprint.minimumZ();
    }

    public double maximumZ() {
        return footprint.maximumZ();
    }

    double expandedMinimumX() {
        return footprint.minimumX() - clearance;
    }

    double expandedMaximumX() {
        return footprint.maximumX() + clearance;
    }

    double expandedMinimumZ() {
        return footprint.minimumZ() - clearance;
    }

    double expandedMaximumZ() {
        return footprint.maximumZ() + clearance;
    }

    private static void requireFraction(String property, double value) {
        requireFinite(property, value);
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be in [0, 1]");
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " must be finite");
        }
    }
}
