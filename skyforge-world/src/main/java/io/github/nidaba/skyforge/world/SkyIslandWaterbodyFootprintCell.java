package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One connected coarse planning cell inside a retained-waterbody footprint. */
public record SkyIslandWaterbodyFootprintCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        double surfacePotential,
        double waterDepthPotential,
        boolean shoreline) {

    public SkyIslandWaterbodyFootprintCell {
        if (watershedCellIndex < 0) {
            throw new IllegalArgumentException("watershedCellIndex must be non-negative");
        }
        Objects.requireNonNull(position, "position");
        requireNormalized("surfacePotential", surfacePotential);
        requireNormalized("waterDepthPotential", waterDepthPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
