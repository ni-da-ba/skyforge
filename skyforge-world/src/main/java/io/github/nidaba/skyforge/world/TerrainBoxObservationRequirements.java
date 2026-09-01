package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral sampling request for observing one finite 3-D world-space box.
 *
 * <p>This is an observation contract, not an eligibility policy. A caller chooses the sampling
 * resolution appropriate to its backend and must not reinterpret unobserved space as proven solid
 * or proven air.
 */
public record TerrainBoxObservationRequirements(
        WorldBounds bounds,
        double sampleSpacing) {

    public TerrainBoxObservationRequirements {
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(sampleSpacing) || sampleSpacing <= 0.0) {
            throw new IllegalArgumentException("sampleSpacing must be finite and greater than zero");
        }
    }
}
