package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * F4C deterministic integer-column quantization evidence.
 *
 * <p>This plan grants no Minecraft mutation authority. It is a backend-neutral proof that an exact
 * AUTH-0046 realization can represent the F4B target with removal-only ceiling quantization.
 */
public record SkyIslandFluvialVoxelQuantizationPlan(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan,
        List<SkyIslandFluvialVoxelColumn> authorizedColumns) {

    public SkyIslandFluvialVoxelQuantizationPlan {
        association = Objects.requireNonNull(association, "association");
        candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        authorizedColumns = List.copyOf(authorizedColumns);
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4C association and F4A candidate must share the same authored descriptor");
        }
        authorizedColumns.forEach(value -> Objects.requireNonNull(value, "authorized column"));
    }

    public List<SkyIslandFluvialVoxelColumn> mutatedColumns() {
        return authorizedColumns.stream()
                .filter(SkyIslandFluvialVoxelColumn::mutatesTerrain)
                .toList();
    }

    public double maximumUndercutResidualWorld() {
        return authorizedColumns.stream()
                .mapToDouble(SkyIslandFluvialVoxelColumn::undercutResidualWorld)
                .max()
                .orElse(0.0);
    }

    public int totalRemovedSolidBlocks() {
        return authorizedColumns.stream()
                .mapToInt(SkyIslandFluvialVoxelColumn::removedSolidBlocks)
                .sum();
    }
}
