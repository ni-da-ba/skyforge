package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Normalized hydrology-driven terrain-response potentials for one coarse planning cell. */
public record SkyIslandHydrologicTerrainCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        double incisionPotential,
        double depositionPotential,
        double floodplainPotential,
        double dropShapingPotential) {

    private static final double EPSILON = 1.0e-12;

    public SkyIslandHydrologicTerrainCell {
        if (watershedCellIndex < 0) {
            throw new IllegalArgumentException("watershedCellIndex must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        requireNormalized("incisionPotential", incisionPotential);
        requireNormalized("depositionPotential", depositionPotential);
        requireNormalized("floodplainPotential", floodplainPotential);
        requireNormalized("dropShapingPotential", dropShapingPotential);
        if (incisionPotential <= EPSILON
                && depositionPotential <= EPSILON
                && floodplainPotential <= EPSILON
                && dropShapingPotential <= EPSILON) {
            throw new IllegalArgumentException("at least one terrain-response potential must be positive");
        }
    }

    public SkyIslandHydrologicTerrainResponseKind dominantResponse() {
        SkyIslandHydrologicTerrainResponseKind kind = SkyIslandHydrologicTerrainResponseKind.INCISION;
        double strongest = incisionPotential;
        if (depositionPotential > strongest + EPSILON) {
            kind = SkyIslandHydrologicTerrainResponseKind.DEPOSITION;
            strongest = depositionPotential;
        }
        if (floodplainPotential > strongest + EPSILON) {
            kind = SkyIslandHydrologicTerrainResponseKind.FLOODPLAIN;
            strongest = floodplainPotential;
        }
        if (dropShapingPotential > strongest + EPSILON) {
            kind = SkyIslandHydrologicTerrainResponseKind.DROP_SHAPING;
        }
        return kind;
    }

    public double dominantPotential() {
        return Math.max(
                Math.max(incisionPotential, depositionPotential),
                Math.max(floodplainPotential, dropShapingPotential));
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
