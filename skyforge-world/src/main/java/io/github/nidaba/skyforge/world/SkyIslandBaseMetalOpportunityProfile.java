package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0093 deterministic island-scale base-metal opportunity over exact AUTH-0033 planning
 * provenance.
 *
 * <p>Opportunity values are normalized geological suitability evidence. They are not ore counts,
 * grades, deposit volumes, rarity tiers, or backend placement policy.
 */
public final class SkyIslandBaseMetalOpportunityProfile {
    private final SkyIslandMaterialFamilyPlan sourcePlan;
    private final List<SkyIslandBaseMetalOpportunityCell> cells;

    SkyIslandBaseMetalOpportunityProfile(
            SkyIslandMaterialFamilyPlan sourcePlan,
            List<SkyIslandBaseMetalOpportunityCell> cells) {
        this.sourcePlan = Objects.requireNonNull(sourcePlan, "sourcePlan");
        Objects.requireNonNull(cells, "cells");
        this.cells = List.copyOf(cells);

        if (this.cells.size() != sourcePlan.cells().size()) {
            throw new IllegalArgumentException(
                    "base-metal opportunity must cover every exact AUTH-0033 host cell");
        }
        for (int index = 0; index < this.cells.size(); index++) {
            if (!this.cells.get(index).sourceCell().equals(sourcePlan.cells().get(index))) {
                throw new IllegalArgumentException(
                        "base-metal opportunity must preserve exact AUTH-0033 cell order/provenance");
            }
        }
    }

    public SkyIslandDescriptor descriptor() {
        return sourcePlan.descriptor();
    }

    public SkyIslandMaterialFamilyPlan sourcePlan() {
        return sourcePlan;
    }

    public int gridSize() {
        return sourcePlan.gridSize();
    }

    public int depthSamples() {
        return sourcePlan.depthSamples();
    }

    public int activeHostCells() {
        return sourcePlan.activeHostCells();
    }

    public List<SkyIslandBaseMetalOpportunityCell> cells() {
        return cells;
    }

    public int mineralBearingCellCount() {
        return (int) cells.stream()
                .filter(cell -> cell.sourceCell().mineralBearingStructuralHost() > 0.0)
                .count();
    }

    public double meanOpportunity(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cells.stream().mapToDouble(cell -> cell.opportunity(kind)).average().orElse(0.0);
    }

    public double peakOpportunity(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return cells.stream().mapToDouble(cell -> cell.opportunity(kind)).max().orElse(0.0);
    }

    public double relativeOpportunityShare(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        double total = 0.0;
        for (SkyIslandBaseMetalKind candidate : SkyIslandBaseMetalKind.values()) {
            total += meanOpportunity(candidate);
        }
        if (total == 0.0) {
            return 0.0;
        }
        return meanOpportunity(kind) / total;
    }
}
