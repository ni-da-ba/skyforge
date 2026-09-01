package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral requirements for a fill-only foundation beneath a structure footprint.
 *
 * <p>The nested support requirements describe the surface that may receive the foundation. The
 * foundation top and fill depth are world-space vertical values. Accommodation never authorizes
 * excavation or mutation of the compiled island field.
 */
public record SurfaceFoundationRequirements(
        SurfaceSupportRequirements supportRequirements,
        double foundationTopY,
        double maximumFillDepth) {

    /** Validates finite fill geometry and freezes the nested support policy. */
    public SurfaceFoundationRequirements {
        Objects.requireNonNull(supportRequirements, "supportRequirements");
        if (!Double.isFinite(foundationTopY)) {
            throw new IllegalArgumentException("foundationTopY must be finite");
        }
        if (!Double.isFinite(maximumFillDepth) || maximumFillDepth <= 0.0) {
            throw new IllegalArgumentException("maximumFillDepth must be finite and greater than zero");
        }
    }
}
