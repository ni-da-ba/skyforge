package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0092 raw nearest-neighbor distance evidence for one exact regional association.
 *
 * <p>The radial gap uses accepted matching nominal radii and is not a physical terrain-edge
 * distance. It is clamped at zero if nominal radial envelopes overlap.
 */
public record SkyIslandRegionalIsolationNeighbor(
        SkyIslandAuthoredRealizationAssociation association,
        double centerDistance,
        double nominalRadialGap) {

    public SkyIslandRegionalIsolationNeighbor {
        association = Objects.requireNonNull(association, "association");
        requireFiniteNonNegative("centerDistance", centerDistance);
        requireFiniteNonNegative("nominalRadialGap", nominalRadialGap);
        if (nominalRadialGap > centerDistance) {
            throw new IllegalArgumentException(
                    "nominalRadialGap cannot exceed centerDistance");
        }
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
