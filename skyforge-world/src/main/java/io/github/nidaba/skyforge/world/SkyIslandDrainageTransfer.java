package io.github.nidaba.skyforge.world;

/** One classified physical interpretation of a routed watershed edge. */
public record SkyIslandDrainageTransfer(
        int sourceCellIndex,
        int downstreamCellIndex,
        SkyIslandDrainageTransferKind kind,
        int depressionAnchorCellIndex,
        double rawDropPotential,
        double requiredCutPotential,
        double sourceFillDepthPotential,
        double spillSurfacePotential) {

    public SkyIslandDrainageTransfer {
        if (sourceCellIndex < 0
                || downstreamCellIndex < 0
                || sourceCellIndex == downstreamCellIndex
                || kind == null
                || depressionAnchorCellIndex < -1) {
            throw new IllegalArgumentException("invalid drainage transfer identity");
        }
        requireFinite(rawDropPotential, "rawDropPotential");
        requireFiniteNonNegative(requiredCutPotential, "requiredCutPotential");
        requireFiniteNonNegative(sourceFillDepthPotential, "sourceFillDepthPotential");
        requireFiniteNonNegative(spillSurfacePotential, "spillSurfacePotential");
        if (requiredCutPotential + 1.0e-12 < Math.max(0.0, -rawDropPotential)) {
            throw new IllegalArgumentException("required cut cannot understate raw uphill transfer");
        }
        if (kind == SkyIslandDrainageTransferKind.BASIN_INTERIOR
                && depressionAnchorCellIndex < 0) {
            throw new IllegalArgumentException("basin-interior transfer requires a depression anchor");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
