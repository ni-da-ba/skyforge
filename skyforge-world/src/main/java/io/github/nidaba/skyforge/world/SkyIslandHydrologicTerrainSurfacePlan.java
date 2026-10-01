package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral hydrologically adjusted coarse surface for one authored island. */
public record SkyIslandHydrologicTerrainSurfacePlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        double spacing,
        List<SkyIslandHydrologicTerrainSurfaceCell> cells) {

    public SkyIslandHydrologicTerrainSurfacePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 1 || !Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException("invalid surface-plan grid");
        }
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "surface cell"));
    }

    public long changedCellCount() {
        return cells.stream().filter(SkyIslandHydrologicTerrainSurfaceCell::changed).count();
    }

    public long loweredCellCount() {
        return cells.stream().filter(SkyIslandHydrologicTerrainSurfaceCell::lowered).count();
    }

    public long raisedCellCount() {
        return cells.stream().filter(SkyIslandHydrologicTerrainSurfaceCell::raised).count();
    }

    public double maxLowering() {
        return cells.stream().mapToDouble(cell -> Math.max(0.0, -cell.netAdjustment())).max().orElse(0.0);
    }

    public double maxRaising() {
        return cells.stream().mapToDouble(cell -> Math.max(0.0, cell.netAdjustment())).max().orElse(0.0);
    }

    public double meanAbsoluteAdjustment() {
        return cells.stream().mapToDouble(cell -> Math.abs(cell.netAdjustment())).average().orElse(0.0);
    }
}
