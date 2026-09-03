package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral semantic dry-land corridor plan around accepted channel topology. */
public record SkyIslandRiparianCorridorPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandRiparianCell> cells) {

    public SkyIslandRiparianCorridorPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "riparian cell"));
    }

    public int cellCount() {
        return cells.size();
    }

    public long count(SkyIslandRiparianKind kind) {
        return cells.stream().filter(cell -> cell.kind() == kind).count();
    }

    public double maxRiparianPotential() {
        return cells.stream().mapToDouble(SkyIslandRiparianCell::riparianPotential).max().orElse(0.0);
    }
}
