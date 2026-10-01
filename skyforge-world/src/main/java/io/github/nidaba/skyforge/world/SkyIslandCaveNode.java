package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One chamber-scale semantic anchor inside an authored cave system. */
public record SkyIslandCaveNode(
        int nodeId,
        int sourceVoidRegionId,
        SkyIslandSubsurfacePosition position,
        double chamberPotential,
        double groundwaterPotential) {

    public SkyIslandCaveNode {
        if (nodeId < 0 || sourceVoidRegionId < 0) {
            throw new IllegalArgumentException("cave node identifiers must be non-negative");
        }
        position = Objects.requireNonNull(position, "position");
        requireNormalized("chamberPotential", chamberPotential);
        requireNormalized("groundwaterPotential", groundwaterPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
