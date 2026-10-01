package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One backend-neutral retained-waterbody candidate derived from watershed topology. */
public record SkyIslandWaterbodyCandidate(
        SkyIslandWaterbodyKind kind,
        int sinkCellIndex,
        SkyIslandLocalPosition anchor,
        int catchmentCellCount,
        double catchmentFraction,
        double relativeInflow,
        double retentionPotential,
        double saturationPotential,
        double persistence,
        double basinScale) {

    public SkyIslandWaterbodyCandidate {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(anchor, "anchor");
        if (sinkCellIndex < 0 || catchmentCellCount < 1) {
            throw new IllegalArgumentException("invalid waterbody candidate identity");
        }
        requireNormalized("catchmentFraction", catchmentFraction);
        requireNormalized("relativeInflow", relativeInflow);
        requireNormalized("retentionPotential", retentionPotential);
        requireNormalized("saturationPotential", saturationPotential);
        requireNormalized("persistence", persistence);
        requireNormalized("basinScale", basinScale);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
