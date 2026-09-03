package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Semantic dry-margin cells associated with one accepted retained-waterbody footprint. */
public record SkyIslandWaterbodyMargin(
        SkyIslandWaterbodyFootprint footprint,
        List<SkyIslandWaterbodyMarginCell> cells) {

    public SkyIslandWaterbodyMargin {
        Objects.requireNonNull(footprint, "footprint");
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "margin cell"));
    }

    public long count(SkyIslandWaterbodyMarginKind kind) {
        return cells.stream().filter(cell -> cell.kind() == kind).count();
    }

    public double maxMarginPotential() {
        return cells.stream().mapToDouble(SkyIslandWaterbodyMarginCell::marginPotential).max().orElse(0.0);
    }
}
