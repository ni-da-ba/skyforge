package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Immutable diagnostic result for one independently evaluated Skyforge island surface. */
public record SurfaceSupportAssessment(
        SkyIslandWorldVolumeId supportingVolumeId,
        int sampleCount,
        int supportedSampleCount,
        double coverageFraction,
        int clearanceSampleCount,
        int supportedClearanceSampleCount,
        double clearanceCoverageFraction,
        double minimumSurfaceY,
        double maximumSurfaceY,
        double heightSpan,
        boolean crossesSurfaceBoundary,
        int surfaceComponentCount,
        boolean coherentSurface,
        boolean accepted) {

    /** Validates count relationships and diagnostic ranges. */
    public SurfaceSupportAssessment {
        Objects.requireNonNull(supportingVolumeId, "supportingVolumeId");
        requireCounts("interior", sampleCount, supportedSampleCount);
        requireCounts("clearance", clearanceSampleCount, supportedClearanceSampleCount);
        requireFraction("coverageFraction", coverageFraction);
        requireFraction("clearanceCoverageFraction", clearanceCoverageFraction);
        if (surfaceComponentCount < 0) {
            throw new IllegalArgumentException("surfaceComponentCount must be non-negative");
        }
        if (supportedSampleCount == 0) {
            if (!Double.isNaN(minimumSurfaceY)
                    || !Double.isNaN(maximumSurfaceY)
                    || !Double.isNaN(heightSpan)) {
                throw new IllegalArgumentException("unsupported assessments must use NaN surface heights");
            }
        } else {
            requireFinite("minimumSurfaceY", minimumSurfaceY);
            requireFinite("maximumSurfaceY", maximumSurfaceY);
            requireFinite("heightSpan", heightSpan);
            if (maximumSurfaceY < minimumSurfaceY) {
                throw new IllegalArgumentException("maximumSurfaceY must not be below minimumSurfaceY");
            }
            if (heightSpan < 0.0) {
                throw new IllegalArgumentException("heightSpan must be non-negative");
            }
        }
    }

    /** Semantic alias used by structure policy callers. */
    public double interiorCoverage() {
        return coverageFraction;
    }

    /** Returns whether at least one interior sample is supported by this volume. */
    public boolean hasInteriorSupport() {
        return supportedSampleCount > 0;
    }

    private static void requireCounts(String label, int total, int supported) {
        if (total < 0 || supported < 0 || supported > total) {
            throw new IllegalArgumentException(label + " support counts are inconsistent");
        }
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
