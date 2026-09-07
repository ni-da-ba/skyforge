package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.MultifaceGrowthFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Lifecycle route for biome-owned native features that share Minecraft's VEGETAL_DECORATION step. */
enum SkyforgeNativeVegetalFeatureRoute {
    ALL,
    SURFACE_ECOLOGY,
    POST_CAVE;

    boolean accepts(
            GenerationStep.Decoration generationStep,
            PlacedFeature placedFeature) {
        Objects.requireNonNull(generationStep, "generationStep");
        Objects.requireNonNull(placedFeature, "placedFeature");
        if (generationStep != GenerationStep.Decoration.VEGETAL_DECORATION) {
            if (this != ALL) {
                throw new IllegalArgumentException(
                        "vegetal feature routing is valid only for VEGETAL_DECORATION");
            }
            return true;
        }

        boolean caveDependent = placedFeature.feature().value().feature() instanceof MultifaceGrowthFeature;
        return switch (this) {
            case ALL -> true;
            case SURFACE_ECOLOGY -> !caveDependent;
            case POST_CAVE -> caveDependent;
        };
    }
}
