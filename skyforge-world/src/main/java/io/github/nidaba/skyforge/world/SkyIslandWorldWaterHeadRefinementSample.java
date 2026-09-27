package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One F4F depth-preserving centerline refinement sample in world units. */
public record SkyIslandWorldWaterHeadRefinementSample(
        SkyIslandLocalPosition localPosition,
        double directWaterHeadWorld,
        double refinedWaterHeadWorld,
        double waterDepthWorld,
        double directTerrainTargetWorld,
        double refinedTerrainTargetWorld,
        double terrainRaiseWorld,
        double maximumTerrainRaiseWorld,
        double bankContainmentUpperHeadWorld) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandWorldWaterHeadRefinementSample {
        localPosition = Objects.requireNonNull(localPosition, "localPosition");
        requireFinite(directWaterHeadWorld, "directWaterHeadWorld");
        requireFinite(refinedWaterHeadWorld, "refinedWaterHeadWorld");
        requireFinitePositive(waterDepthWorld, "waterDepthWorld");
        requireFinite(directTerrainTargetWorld, "directTerrainTargetWorld");
        requireFinite(refinedTerrainTargetWorld, "refinedTerrainTargetWorld");
        requireFiniteNonNegative(terrainRaiseWorld, "terrainRaiseWorld");
        requireFiniteNonNegative(maximumTerrainRaiseWorld, "maximumTerrainRaiseWorld");
        requireFinite(bankContainmentUpperHeadWorld, "bankContainmentUpperHeadWorld");

        if (refinedWaterHeadWorld + EPSILON < directWaterHeadWorld) {
            throw new IllegalArgumentException("F4F may not lower world-space water head");
        }
        if (terrainRaiseWorld > maximumTerrainRaiseWorld + EPSILON) {
            throw new IllegalArgumentException("F4F terrain raise exceeds pre-hydrologic carve margin");
        }
        if (refinedWaterHeadWorld > bankContainmentUpperHeadWorld + EPSILON) {
            throw new IllegalArgumentException("F4F refined head exceeds physical bank containment bound");
        }
        if (Math.abs(refinedWaterHeadWorld - directWaterHeadWorld - terrainRaiseWorld) > EPSILON) {
            throw new IllegalArgumentException("water-head and bed refinement must shift together");
        }
        if (Math.abs(refinedTerrainTargetWorld - directTerrainTargetWorld - terrainRaiseWorld) > EPSILON) {
            throw new IllegalArgumentException("refined terrain target must equal direct target plus raise");
        }
        if (Math.abs(refinedWaterHeadWorld - refinedTerrainTargetWorld - waterDepthWorld) > EPSILON) {
            throw new IllegalArgumentException("F4F must preserve accepted hydraulic depth exactly");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
