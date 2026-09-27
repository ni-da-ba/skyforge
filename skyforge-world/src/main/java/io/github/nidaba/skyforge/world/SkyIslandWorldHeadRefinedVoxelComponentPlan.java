package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** F4H re-quantization evidence for one F4C-qualified terminal component. */
public record SkyIslandWorldHeadRefinedVoxelComponentPlan(
        SkyIslandFluvialVoxelComponentPlan directComponent,
        List<SkyIslandWorldHeadRefinedVoxelColumn> columns) {

    public SkyIslandWorldHeadRefinedVoxelComponentPlan {
        directComponent = Objects.requireNonNull(directComponent, "directComponent");
        if (directComponent.status() != SkyIslandFluvialVoxelComponentStatus.QUALIFIED) {
            throw new IllegalArgumentException(
                    "F4H consumes only F4C-qualified component authority");
        }
        columns = List.copyOf(columns);
        columns.forEach(value -> Objects.requireNonNull(value, "refined column"));
        if (columns.size() != directComponent.columns().size()) {
            throw new IllegalArgumentException(
                    "F4H must re-quantize exactly the accepted F4C authority columns");
        }
    }

    public int terminalCellIndex() {
        return directComponent.terminalCellIndex();
    }

    public int directRemovedSolidBlocks() {
        return directComponent.columns().stream()
                .mapToInt(SkyIslandFluvialVoxelColumn::removedSolidBlocks)
                .sum();
    }

    public int refinedRemovedSolidBlocks() {
        return columns.stream()
                .mapToInt(SkyIslandWorldHeadRefinedVoxelColumn::refinedRemovedSolidBlocks)
                .sum();
    }

    public int returnedSolidBlocks() {
        return directRemovedSolidBlocks() - refinedRemovedSolidBlocks();
    }
}
