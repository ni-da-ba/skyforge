package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral footprint and policy thresholds for evaluating one candidate surface.
 *
 * <p>The footprint and clearance are expressed in world-space X/Z coordinates. Sampling is
 * deterministic and independent of backend block/chunk concepts.
 */
public record SurfaceSupportRequirements(
        double minimumX,
        double maximumX,
        double minimumZ,
        double maximumZ,
        double sampleSpacing,
        double clearance,
        double minimumCoverageFraction,
        double minimumClearanceCoverageFraction,
        double maximumHeightSpan) {

    /** Validates finite geometry and normalized policy thresholds. */
    public SurfaceSupportRequirements {
        requireFinite("minimumX", minimumX);
        requireFinite("maximumX", maximumX);
        requireFinite("minimumZ", minimumZ);
        requireFinite("maximumZ", maximumZ);
        requireFinite("sampleSpacing", sampleSpacing);
        requireFinite("clearance", clearance);
        requireFinite("maximumHeightSpan", maximumHeightSpan);
        if (maximumX < minimumX) {
            throw new IllegalArgumentException("maximumX must be greater than or equal to minimumX");
        }
        if (maximumZ < minimumZ) {
            throw new IllegalArgumentException("maximumZ must be greater than or equal to minimumZ");
        }
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
        requireFinite("expandedMinimumX", minimumX - clearance);
        requireFinite("expandedMaximumX", maximumX + clearance);
        requireFinite("expandedMinimumZ", minimumZ - clearance);
        requireFinite("expandedMaximumZ", maximumZ + clearance);
    }

    double expandedMinimumX() {
        return minimumX - clearance;
    }

    double expandedMaximumX() {
        return maximumX + clearance;
    }

    double expandedMinimumZ() {
        return minimumZ - clearance;
    }

    double expandedMaximumZ() {
        return maximumZ + clearance;
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
