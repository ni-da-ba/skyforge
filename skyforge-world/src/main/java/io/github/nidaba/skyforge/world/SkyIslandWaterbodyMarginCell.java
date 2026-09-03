package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One dry coarse planning cell influenced by an adjacent retained-waterbody footprint. */
public record SkyIslandWaterbodyMarginCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        SkyIslandWaterbodyMarginKind kind,
        int latticeDistance,
        double proximityPotential,
        double saturationPotential,
        double retentionPotential,
        double elevationHeadPotential,
        double marginPotential) {

    public SkyIslandWaterbodyMarginCell {
        if (watershedCellIndex < 0 || latticeDistance < 1 || latticeDistance > 2) {
            throw new IllegalArgumentException("invalid waterbody margin cell identity");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(kind, "kind");
        requireNormalized("proximityPotential", proximityPotential);
        requireNormalized("saturationPotential", saturationPotential);
        requireNormalized("retentionPotential", retentionPotential);
        requireNormalized("elevationHeadPotential", elevationHeadPotential);
        requireNormalized("marginPotential", marginPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
