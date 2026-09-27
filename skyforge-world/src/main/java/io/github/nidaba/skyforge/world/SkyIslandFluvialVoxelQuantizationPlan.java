package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * F4C deterministic integer-column quantization evidence.
 *
 * <p>This plan grants no Minecraft mutation authority. Component outcomes preserve the F3E/F4A
 * atomicity boundary: a rejected component exposes zero columns while unrelated components may
 * remain independently qualified.
 */
public record SkyIslandFluvialVoxelQuantizationPlan(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan,
        List<SkyIslandFluvialVoxelComponentPlan> components) {

    public SkyIslandFluvialVoxelQuantizationPlan {
        association = Objects.requireNonNull(association, "association");
        candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        components = List.copyOf(components);
        if (!association.authoredDescriptor().equals(candidatePlan.descriptor())) {
            throw new IllegalArgumentException(
                    "F4C association and F4A candidate must share the same authored descriptor");
        }
        components.forEach(value -> Objects.requireNonNull(value, "component plan"));
        long distinctTerminals = components.stream()
                .map(SkyIslandFluvialVoxelComponentPlan::terminalCellIndex)
                .distinct()
                .count();
        if (distinctTerminals != components.size()) {
            throw new IllegalArgumentException("F4C component terminal classified more than once");
        }
        if (components.size() != candidatePlan.realizedComponents().size()) {
            throw new IllegalArgumentException(
                    "every F4A-realized component must receive exactly one F4C outcome");
        }
    }

    public List<SkyIslandFluvialVoxelColumn> authorizedColumns() {
        return components.stream()
                .filter(component ->
                        component.status() == SkyIslandFluvialVoxelComponentStatus.QUALIFIED)
                .flatMap(component -> component.columns().stream())
                .toList();
    }

    public List<SkyIslandFluvialVoxelColumn> mutatedColumns() {
        return authorizedColumns().stream()
                .filter(SkyIslandFluvialVoxelColumn::mutatesTerrain)
                .toList();
    }

    public List<SkyIslandFluvialVoxelComponentPlan> rejectedComponents() {
        return components.stream()
                .filter(component ->
                        component.status() == SkyIslandFluvialVoxelComponentStatus.PHYSICAL_REJECTION)
                .toList();
    }

    public double maximumUndercutResidualWorld() {
        return authorizedColumns().stream()
                .mapToDouble(SkyIslandFluvialVoxelColumn::undercutResidualWorld)
                .max()
                .orElse(0.0);
    }

    public int totalRemovedSolidBlocks() {
        return authorizedColumns().stream()
                .mapToInt(SkyIslandFluvialVoxelColumn::removedSolidBlocks)
                .sum();
    }
}
