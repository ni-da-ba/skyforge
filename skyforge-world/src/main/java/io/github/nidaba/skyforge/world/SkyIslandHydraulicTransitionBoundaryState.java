package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Head-independent F2B state sampled at one explicit transition boundary. */
public record SkyIslandHydraulicTransitionBoundaryState(
        int reachStartCellIndex,
        int reachEndCellIndex,
        SkyIslandHydraulicTransitionBoundaryRole role,
        double stationFraction,
        double arcLength,
        SkyIslandLocalPosition position,
        double relativeDischarge,
        double bankfullHalfWidth,
        double waterDepthPotential,
        double terrainElevation) {

    public SkyIslandHydraulicTransitionBoundaryState {
        if (reachStartCellIndex < 0
                || reachEndCellIndex < 0
                || reachStartCellIndex == reachEndCellIndex) {
            throw new IllegalArgumentException("transition boundary requires a valid reach identity");
        }
        role = Objects.requireNonNull(role, "role");
        position = Objects.requireNonNull(position, "position");
        requireFraction(stationFraction, "stationFraction");
        if (!Double.isFinite(arcLength) || arcLength < 0.0) {
            throw new IllegalArgumentException("arcLength must be finite and non-negative");
        }
        requireFraction(relativeDischarge, "relativeDischarge");
        requireFinitePositive(bankfullHalfWidth, "bankfullHalfWidth");
        requireFinitePositive(waterDepthPotential, "waterDepthPotential");
        requireFraction(terrainElevation, "terrainElevation");
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
