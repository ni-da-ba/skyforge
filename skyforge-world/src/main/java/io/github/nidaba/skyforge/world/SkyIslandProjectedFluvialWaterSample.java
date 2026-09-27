package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/** One F4E world-space water-surface projection coupled to an F4B terrain sample. */
public record SkyIslandProjectedFluvialWaterSample(
        SkyIslandProjectedFluvialTerrainSample terrainProjection,
        OptionalDouble waterSurfaceWorldY,
        double waterDepthWorldUnits) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandProjectedFluvialWaterSample {
        terrainProjection = Objects.requireNonNull(terrainProjection, "terrainProjection");
        waterSurfaceWorldY = Objects.requireNonNull(waterSurfaceWorldY, "waterSurfaceWorldY");
        if (!Double.isFinite(waterDepthWorldUnits) || waterDepthWorldUnits < -EPSILON) {
            throw new IllegalArgumentException(
                    "waterDepthWorldUnits must be finite and nonnegative");
        }

        boolean semanticWet = terrainProjection.semanticSample().wet();
        if (semanticWet != waterSurfaceWorldY.isPresent()) {
            throw new IllegalArgumentException(
                    "F4E water authority must exist exactly for F4A-wet samples");
        }
        if (!semanticWet) {
            if (Math.abs(waterDepthWorldUnits) > EPSILON) {
                throw new IllegalArgumentException(
                        "dry F4A samples cannot carry world-space water depth");
            }
            waterDepthWorldUnits = 0.0;
            return;
        }

        double water = waterSurfaceWorldY.orElseThrow();
        if (!Double.isFinite(water)) {
            throw new IllegalArgumentException("water surface must be finite");
        }
        double expectedDepth = water - terrainProjection.targetUpperSurfaceWorldY();
        if (!(expectedDepth > EPSILON)) {
            throw new IllegalArgumentException(
                    "F4A-wet sample must project positive world-space water depth");
        }
        if (Math.abs(expectedDepth - waterDepthWorldUnits) > EPSILON) {
            throw new IllegalArgumentException(
                    "world-space water depth must equal water surface minus F4B terrain target");
        }
    }

    public boolean wet() {
        return waterSurfaceWorldY.isPresent();
    }
}
