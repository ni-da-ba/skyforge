package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One connected mesoscale geological region of a single semantic kind. */
public record SkyIslandGeologicRegion(
        int regionId,
        SkyIslandGeologicRegionKind kind,
        List<SkyIslandGeologicRegionCell> cells) {

    public SkyIslandGeologicRegion {
        if (regionId < 0) {
            throw new IllegalArgumentException("regionId must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        cells = List.copyOf(cells);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("geologic region must contain at least one cell");
        }
        cells.forEach(cell -> Objects.requireNonNull(cell, "region cell"));
    }

    public int cellCount() {
        return cells.size();
    }

    public double peakMembership() {
        return cells.stream().mapToDouble(SkyIslandGeologicRegionCell::membership).max().orElse(0.0);
    }

    public double meanMembership() {
        return cells.stream().mapToDouble(SkyIslandGeologicRegionCell::membership).average().orElse(0.0);
    }

    public SkyIslandSubsurfacePosition centroid() {
        double x = cells.stream().mapToDouble(cell -> cell.position().x()).average().orElse(0.0);
        double z = cells.stream().mapToDouble(cell -> cell.position().z()).average().orElse(0.0);
        double depth = cells.stream()
                .mapToDouble(cell -> cell.position().depthFraction())
                .average()
                .orElse(0.0);
        return new SkyIslandSubsurfacePosition(x, z, depth);
    }
}
