package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic coarse watershed topology for one authored island. */
public record SkyIslandWatershedPlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        double spacing,
        List<SkyIslandWatershedCell> cells,
        double maxFlowAccumulation) {

    public SkyIslandWatershedPlan {
        Objects.requireNonNull(descriptor, "descriptor");
        cells = List.copyOf(cells);
        if (gridSize < 3 || !Double.isFinite(spacing) || spacing <= 0.0 || !Double.isFinite(maxFlowAccumulation) || maxFlowAccumulation < 0.0) {
            throw new IllegalArgumentException("invalid watershed plan metadata");
        }
    }

    public long outletCount() {
        return cells.stream().filter(SkyIslandWatershedCell::edgeOutlet).count();
    }

    public long retainedSinkCount() {
        return cells.stream().filter(SkyIslandWatershedCell::retainedSink).count();
    }
}
