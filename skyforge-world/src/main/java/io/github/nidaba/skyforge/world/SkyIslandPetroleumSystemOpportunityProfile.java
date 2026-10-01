package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * AUTH-0098 deterministic petroleum-system geological opportunity over exact AUTH-0033 provenance.
 */
public final class SkyIslandPetroleumSystemOpportunityProfile {
    private final SkyIslandMaterialFamilyPlan sourcePlan;
    private final List<SkyIslandPetroleumSystemOpportunityCell> cells;

    SkyIslandPetroleumSystemOpportunityProfile(
            SkyIslandMaterialFamilyPlan sourcePlan,
            List<SkyIslandPetroleumSystemOpportunityCell> cells) {
        this.sourcePlan = Objects.requireNonNull(sourcePlan, "sourcePlan");
        this.cells = List.copyOf(Objects.requireNonNull(cells, "cells"));

        if (this.cells.size() != sourcePlan.cells().size()) {
            throw new IllegalArgumentException(
                    "petroleum-system opportunity must cover every exact AUTH-0033 host cell");
        }
        for (int ordinal = 0; ordinal < this.cells.size(); ordinal++) {
            if (!this.cells.get(ordinal).sourceCell().equals(sourcePlan.cells().get(ordinal))) {
                throw new IllegalArgumentException(
                        "petroleum-system opportunity must preserve exact AUTH-0033 cell order/provenance");
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

    public List<SkyIslandPetroleumSystemOpportunityCell> cells() {
        return cells;
    }

    public double meanSourcePotential() {
        return cells.stream()
                .mapToDouble(SkyIslandPetroleumSystemOpportunityCell::sourcePotential)
                .average()
                .orElse(0.0);
    }

    public double meanReservoirPotential() {
        return cells.stream()
                .mapToDouble(SkyIslandPetroleumSystemOpportunityCell::reservoirPotential)
                .average()
                .orElse(0.0);
    }

    public double meanSealPotential() {
        return cells.stream()
                .mapToDouble(SkyIslandPetroleumSystemOpportunityCell::sealPotential)
                .average()
                .orElse(0.0);
    }

    public double meanSystemOpportunity() {
        return cells.stream()
                .mapToDouble(SkyIslandPetroleumSystemOpportunityCell::systemOpportunity)
                .average()
                .orElse(0.0);
    }

    public double peakSystemOpportunity() {
        return cells.stream()
                .mapToDouble(SkyIslandPetroleumSystemOpportunityCell::systemOpportunity)
                .max()
                .orElse(0.0);
    }

    public long nonzeroSystemCellCount() {
        return cells.stream()
                .filter(cell -> cell.systemOpportunity() > 0.0)
                .count();
    }
}
