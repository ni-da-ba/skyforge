package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral requirements for a fill-only foundation beneath a structure footprint.
 *
 * <p>The nested support requirements describe the surface that may receive the foundation.
 * {@code foundationTopY} is the continuous boundary up to which added support is required;
 * {@code maximumSurfaceY} is the highest existing natural surface the caller can accept without
 * excavation. Keeping those planes distinct allows a backend to represent native occupied/free
 * coordinate conventions without inflating required fill depth. Accommodation never authorizes
 * excavation or mutation of the compiled island field.
 */
public record SurfaceFoundationRequirements(
        SurfaceSupportRequirements supportRequirements,
        double foundationTopY,
        double maximumSurfaceY,
        double maximumFillDepth) {

    /** Validates finite fill geometry and freezes the nested support policy. */
    public SurfaceFoundationRequirements {
        Objects.requireNonNull(supportRequirements, "supportRequirements");
        if (!Double.isFinite(foundationTopY)) {
            throw new IllegalArgumentException("foundationTopY must be finite");
        }
        if (!Double.isFinite(maximumSurfaceY) || maximumSurfaceY < foundationTopY) {
            throw new IllegalArgumentException(
                    "maximumSurfaceY must be finite and greater than or equal to foundationTopY");
        }
        if (!Double.isFinite(maximumFillDepth) || maximumFillDepth <= 0.0) {
            throw new IllegalArgumentException("maximumFillDepth must be finite and greater than zero");
        }
    }
}
