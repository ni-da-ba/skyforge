package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One finite face-derived patch used to continuously realize an AUTH-0034 semantic contact.
 *
 * <p>Horizontal spans are normalized by nominal island radius. Depth span and width are expressed
 * in semantic-depth units. The metric is therefore backend-neutral and independent of voxel scale.
 */
public record SkyIslandLithologicContactPatch(
        int contactId,
        int firstAssemblageId,
        int secondAssemblageId,
        SkyIslandLithologicContactAxis axis,
        SkyIslandSubsurfacePosition center,
        boolean firstAssemblageOnNegativeSide,
        double horizontalHalfSpanNormalized,
        double depthHalfSpan,
        double normalizedHalfWidth,
        double transitionSharpness,
        double localStructuralInfluence,
        double caveExposureInfluence) {

    public SkyIslandLithologicContactPatch {
        if (contactId < 0 || firstAssemblageId < 0 || secondAssemblageId < 0) {
            throw new IllegalArgumentException("contact and assemblage ids must be non-negative");
        }
        if (firstAssemblageId >= secondAssemblageId) {
            throw new IllegalArgumentException("contact assemblage ids must be ordered");
        }
        axis = Objects.requireNonNull(axis, "axis");
        center = Objects.requireNonNull(center, "center");
        requirePositive("horizontalHalfSpanNormalized", horizontalHalfSpanNormalized);
        requirePositive("depthHalfSpan", depthHalfSpan);
        requirePositive("normalizedHalfWidth", normalizedHalfWidth);
        requireNormalized("transitionSharpness", transitionSharpness);
        requireNormalized("localStructuralInfluence", localStructuralInfluence);
        requireNormalized("caveExposureInfluence", caveExposureInfluence);
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
