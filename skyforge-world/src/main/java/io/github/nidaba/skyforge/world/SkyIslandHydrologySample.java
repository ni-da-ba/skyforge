package io.github.nidaba.skyforge.world;

/** Backend-neutral hydrological planning values at one island-local position. */
public record SkyIslandHydrologySample(
        double runoffPotential,
        double retentionPotential,
        double drainagePotential,
        double outflowPotential,
        double flowX,
        double flowZ) {
    public SkyIslandHydrologySample {
        requireNormalized("runoffPotential", runoffPotential);
        requireNormalized("retentionPotential", retentionPotential);
        requireNormalized("drainagePotential", drainagePotential);
        requireNormalized("outflowPotential", outflowPotential);
        if (!Double.isFinite(flowX) || !Double.isFinite(flowZ)) {
            throw new IllegalArgumentException("flow direction must be finite");
        }
        double magnitude = Math.hypot(flowX, flowZ);
        if (magnitude > 1.0000001) {
            throw new IllegalArgumentException("flow direction magnitude must not exceed one");
        }
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
