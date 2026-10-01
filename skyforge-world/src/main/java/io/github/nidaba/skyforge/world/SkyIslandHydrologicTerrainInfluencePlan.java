package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral hydrology-to-terrain influence plan for one authored island. */
public record SkyIslandHydrologicTerrainInfluencePlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandHydrologicTerrainCell> cells) {

    public SkyIslandHydrologicTerrainInfluencePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "terrain influence cell"));
    }

    public long count(SkyIslandHydrologicTerrainResponseKind kind) {
        return cells.stream().filter(cell -> cell.dominantResponse() == kind).count();
    }

    public double maxIncisionPotential() {
        return cells.stream().mapToDouble(SkyIslandHydrologicTerrainCell::incisionPotential).max().orElse(0.0);
    }

    public double maxDepositionPotential() {
        return cells.stream().mapToDouble(SkyIslandHydrologicTerrainCell::depositionPotential).max().orElse(0.0);
    }

    public double maxFloodplainPotential() {
        return cells.stream().mapToDouble(SkyIslandHydrologicTerrainCell::floodplainPotential).max().orElse(0.0);
    }

    public double maxDropShapingPotential() {
        return cells.stream().mapToDouble(SkyIslandHydrologicTerrainCell::dropShapingPotential).max().orElse(0.0);
    }
}
