package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One head-independent hydraulic-geometry sample along a C2 centerline. */
public record SkyIslandHydraulicGeometrySkeletonSample(
        SkyIslandLocalPosition position,
        double arcLength,
        double stationFraction,
        double relativeDischarge,
        double bankfullHalfWidth,
        double waterDepthPotential,
        double terrainElevation) {

    public SkyIslandHydraulicGeometrySkeletonSample {
        position = Objects.requireNonNull(position, "position");
        if (!Double.isFinite(arcLength) || arcLength < 0.0) {
            throw new IllegalArgumentException("arcLength must be finite and non-negative");
        }
        requireFraction(stationFraction, "stationFraction");
        requireFraction(relativeDischarge, "relativeDischarge");
        requireFinitePositive(bankfullHalfWidth, "bankfullHalfWidth");
        requireFinitePositive(waterDepthPotential, "waterDepthPotential");
        requireFraction(terrainElevation, "terrainElevation");
    }

    public double preferredWaterSurfacePotential() {
        return Math.max(
                waterDepthPotential,
                terrainElevation
                        - SkyIslandHydraulicGeometryCalibration.freeboardPotential(relativeDischarge));
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
