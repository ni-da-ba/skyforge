package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** F4H subset proof for re-quantized F4G terrain over previously accepted F4C authority. */
public record SkyIslandWorldHeadRefinedVoxelPlan(
        SkyIslandWorldHeadRefinedTerrainPlan refinedTerrain,
        List<SkyIslandWorldHeadRefinedVoxelComponentPlan> components) {

    public SkyIslandWorldHeadRefinedVoxelPlan {
        refinedTerrain = Objects.requireNonNull(refinedTerrain, "refinedTerrain");
        components = List.copyOf(components);
        components.forEach(value -> Objects.requireNonNull(value, "component"));
    }

    public int directRemovedSolidBlocks() {
        return components.stream()
                .mapToInt(SkyIslandWorldHeadRefinedVoxelComponentPlan::directRemovedSolidBlocks)
                .sum();
    }

    public int refinedRemovedSolidBlocks() {
        return components.stream()
                .mapToInt(SkyIslandWorldHeadRefinedVoxelComponentPlan::refinedRemovedSolidBlocks)
                .sum();
    }

    public int returnedSolidBlocks() {
        return directRemovedSolidBlocks() - refinedRemovedSolidBlocks();
    }

    public List<SkyIslandWorldHeadRefinedVoxelColumn> columns() {
        return components.stream().flatMap(component -> component.columns().stream()).toList();
    }
}
