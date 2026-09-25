package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One pre-carving hydraulic-geometry sample along a candidate geomorphic route. */
public record SkyIslandHydraulicGeometrySample(
        SkyIslandLocalPosition position,
        double stationFraction,
        double relativeDischarge,
        double bankfullHalfWidth,
        double waterDepthPotential,
        double terrainElevation,
        double waterSurfacePotential,
        double bedElevationPotential,
        double requiredCenterlineLowering) {

    public SkyIslandHydraulicGeometrySample {
        position = Objects.requireNonNull(position, "position");
        requireFraction(stationFraction, "stationFraction");
        requireFraction(relativeDischarge, "relativeDischarge");
        requireFinitePositive(bankfullHalfWidth, "bankfullHalfWidth");
        requireFinitePositive(waterDepthPotential, "waterDepthPotential");
        requireFraction(terrainElevation, "terrainElevation");
        requireFraction(waterSurfacePotential, "waterSurfacePotential");
        requireFraction(bedElevationPotential, "bedElevationPotential");
        requireFiniteNonNegative(requiredCenterlineLowering, "requiredCenterlineLowering");
        if (bedElevationPotential >= waterSurfacePotential) {
            throw new IllegalArgumentException("bed must remain below water surface");
        }
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

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
