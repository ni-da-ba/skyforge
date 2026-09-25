package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One station in a constrained longitudinal channel bed and free-surface solution. */
public record SkyIslandHydraulicProfileStation(
        SkyIslandLocalPosition position,
        double distanceFromStart,
        double terrainElevation,
        double bedElevation,
        double waterSurfaceElevation) {

    public SkyIslandHydraulicProfileStation {
        position = Objects.requireNonNull(position, "position");
        requireFiniteNonNegative(distanceFromStart, "distanceFromStart");
        requireNormalized(terrainElevation, "terrainElevation");
        requireNormalized(bedElevation, "bedElevation");
        requireNormalized(waterSurfaceElevation, "waterSurfaceElevation");
        if (bedElevation > terrainElevation + 1.0e-12) {
            throw new IllegalArgumentException("bed cannot lie above pre-fluvial terrain");
        }
        if (waterSurfaceElevation + 1.0e-12 < bedElevation) {
            throw new IllegalArgumentException("water surface cannot lie below the bed");
        }
        if (waterSurfaceElevation > terrainElevation + 1.0e-12) {
            throw new IllegalArgumentException("ordinary channel water cannot exceed pre-fluvial centerline terrain");
        }
    }

    public double incisionPotential() {
        return terrainElevation - bedElevation;
    }

    public double waterDepthPotential() {
        return waterSurfaceElevation - bedElevation;
    }

    public double incisionWorldUnits(double verticalReliefScale) {
        requirePositive(verticalReliefScale, "verticalReliefScale");
        return incisionPotential() * verticalReliefScale;
    }

    public double waterDepthWorldUnits(double verticalReliefScale) {
        requirePositive(verticalReliefScale, "verticalReliefScale");
        return waterDepthPotential() * verticalReliefScale;
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requirePositive(double value, String name) {
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
