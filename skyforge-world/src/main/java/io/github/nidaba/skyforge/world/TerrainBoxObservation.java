package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Sampled relationship between one independently compiled Skyforge volume and one finite 3-D box.
 *
 * <p>The four sample categories are mutually exclusive and exhaustive. This record deliberately
 * reports observations rather than an accept/reject decision: sparse sampling can establish what
 * was observed, but does not prove anything about unsampled space.
 */
public record TerrainBoxObservation(
        SkyIslandWorldVolumeId observedVolumeId,
        int sampleCount,
        int solidSampleCount,
        int atOrAboveUpperSurfaceSampleCount,
        int atOrBelowUndersideSurfaceSampleCount,
        int openBetweenSurfacesSampleCount) {

    public TerrainBoxObservation {
        Objects.requireNonNull(observedVolumeId, "observedVolumeId");
        requireNonNegative("sampleCount", sampleCount);
        requireNonNegative("solidSampleCount", solidSampleCount);
        requireNonNegative("atOrAboveUpperSurfaceSampleCount", atOrAboveUpperSurfaceSampleCount);
        requireNonNegative("atOrBelowUndersideSurfaceSampleCount", atOrBelowUndersideSurfaceSampleCount);
        requireNonNegative("openBetweenSurfacesSampleCount", openBetweenSurfacesSampleCount);
        if (sampleCount == 0) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
        int categorized = Math.addExact(
                Math.addExact(solidSampleCount, atOrAboveUpperSurfaceSampleCount),
                Math.addExact(atOrBelowUndersideSurfaceSampleCount, openBetweenSurfacesSampleCount));
        if (categorized != sampleCount) {
            throw new IllegalArgumentException("terrain observation categories must sum to sampleCount");
        }
    }

    public boolean allSamplesSolid() {
        return solidSampleCount == sampleCount;
    }

    public boolean allSamplesAtOrAboveUpperSurface() {
        return atOrAboveUpperSurfaceSampleCount == sampleCount;
    }

    public boolean allSamplesAtOrBelowUndersideSurface() {
        return atOrBelowUndersideSurfaceSampleCount == sampleCount;
    }

    public boolean hasOpenBetweenSurfacesSamples() {
        return openBetweenSurfacesSampleCount > 0;
    }

    public boolean mixed() {
        int nonEmptyCategories = 0;
        nonEmptyCategories += solidSampleCount > 0 ? 1 : 0;
        nonEmptyCategories += atOrAboveUpperSurfaceSampleCount > 0 ? 1 : 0;
        nonEmptyCategories += atOrBelowUndersideSurfaceSampleCount > 0 ? 1 : 0;
        nonEmptyCategories += openBetweenSurfacesSampleCount > 0 ? 1 : 0;
        return nonEmptyCategories > 1;
    }

    private static void requireNonNegative(String property, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(property + " must be non-negative");
        }
    }
}
