package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One coarse authored-surface sample after deterministic hydrologic terrain shaping. */
public record SkyIslandHydrologicTerrainSurfaceCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        double baseElevationPotential,
        double adjustedElevationPotential,
        double incisionLowering,
        double depositionRaising,
        double floodplainAdjustment,
        double dropLowering) {

    private static final double EPSILON = 1.0e-12;

    public SkyIslandHydrologicTerrainSurfaceCell {
        if (watershedCellIndex < 0) {
            throw new IllegalArgumentException("watershedCellIndex must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        requireNormalized("baseElevationPotential", baseElevationPotential);
        requireNormalized("adjustedElevationPotential", adjustedElevationPotential);
        requireNormalized("incisionLowering", incisionLowering);
        requireNormalized("depositionRaising", depositionRaising);
        requireSignedNormalized("floodplainAdjustment", floodplainAdjustment);
        requireNormalized("dropLowering", dropLowering);
    }

    /** Signed change in normalized authored surface potential. */
    public double netAdjustment() {
        return adjustedElevationPotential - baseElevationPotential;
    }

    public boolean changed() {
        return Math.abs(netAdjustment()) > EPSILON;
    }

    public boolean lowered() {
        return netAdjustment() < -EPSILON;
    }

    public boolean raised() {
        return netAdjustment() > EPSILON;
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireSignedNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < -1.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [-1, 1]");
        }
    }
}
