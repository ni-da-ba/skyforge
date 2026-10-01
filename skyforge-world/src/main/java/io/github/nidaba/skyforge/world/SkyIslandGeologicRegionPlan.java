package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic mesoscale geological region plan for one authored island interior. */
public record SkyIslandGeologicRegionPlan(
        SkyIslandDescriptor descriptor,
        int gridSize,
        int depthSamples,
        double horizontalSpacing,
        double depthSpacing,
        int structuralCorridorCount,
        List<SkyIslandGeologicRegion> regions) {

    public SkyIslandGeologicRegionPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (gridSize < 2 || depthSamples < 2) {
            throw new IllegalArgumentException("geologic region plan requires at least a 2 x 2 x 2 grid");
        }
        if (!Double.isFinite(horizontalSpacing) || horizontalSpacing <= 0.0
                || !Double.isFinite(depthSpacing) || depthSpacing <= 0.0) {
            throw new IllegalArgumentException("geologic region plan spacing must be positive and finite");
        }
        if (structuralCorridorCount < 1) {
            throw new IllegalArgumentException("structuralCorridorCount must be positive");
        }
        regions = List.copyOf(regions);
        regions.forEach(region -> Objects.requireNonNull(region, "geologic region"));
    }

    public long regionCount(SkyIslandGeologicRegionKind kind) {
        return regions.stream().filter(region -> region.kind() == kind).count();
    }

    public int cellCount(SkyIslandGeologicRegionKind kind) {
        return regions.stream()
                .filter(region -> region.kind() == kind)
                .mapToInt(SkyIslandGeologicRegion::cellCount)
                .sum();
    }

    public int largestRegionCellCount(SkyIslandGeologicRegionKind kind) {
        return regions.stream()
                .filter(region -> region.kind() == kind)
                .mapToInt(SkyIslandGeologicRegion::cellCount)
                .max()
                .orElse(0);
    }
}
