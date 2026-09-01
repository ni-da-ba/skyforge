package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Immutable diagnostic result for one independently evaluated fill-only foundation candidate. */
public record SurfaceFoundationAssessment(
        SurfaceSupportAssessment supportAssessment,
        int fillSampleCount,
        int surfaceAboveFoundationSampleCount,
        double maximumRequiredFillDepth,
        boolean accepted) {

    /** Validates diagnostic counts and bounded fill depth. */
    public SurfaceFoundationAssessment {
        Objects.requireNonNull(supportAssessment, "supportAssessment");
        if (fillSampleCount < 0) {
            throw new IllegalArgumentException("fillSampleCount must be non-negative");
        }
        if (surfaceAboveFoundationSampleCount < 0) {
            throw new IllegalArgumentException("surfaceAboveFoundationSampleCount must be non-negative");
        }
        if (!Double.isFinite(maximumRequiredFillDepth) || maximumRequiredFillDepth < 0.0) {
            throw new IllegalArgumentException("maximumRequiredFillDepth must be finite and non-negative");
        }
    }

    /** Stable identity of the independently evaluated Skyforge surface. */
    public SkyIslandWorldVolumeId supportingVolumeId() {
        return supportAssessment.supportingVolumeId();
    }

    /** True when accommodation requires adding at least one foundation column. */
    public boolean requiresFill() {
        return fillSampleCount > 0;
    }
}
