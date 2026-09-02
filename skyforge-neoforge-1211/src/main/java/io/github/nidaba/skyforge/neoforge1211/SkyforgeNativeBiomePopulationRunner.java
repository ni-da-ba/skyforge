package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Executes one native biome generation step inside one exact Skyforge world volume. */
final class SkyforgeNativeBiomePopulationRunner {
    private SkyforgeNativeBiomePopulationRunner() {}

    static Result populateStep(
            WorldGenLevel level,
            ChunkGenerator generator,
            SkyforgeExactVolumeBiomeResolver biomeResolver,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            int sampleY,
            GenerationStep.Decoration generationStep,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(biomeResolver, "biomeResolver");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(originChunk, "originChunk");
        Objects.requireNonNull(generationStep, "generationStep");

        int sampleX = originChunk.getMiddleBlockX();
        int sampleZ = originChunk.getMiddleBlockZ();
        ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                biomeResolver.resolve(volumeId, sampleX, sampleY, sampleZ),
                "biome resolver returned null");
        Holder<Biome> biome = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(biomeKey)
                .orElseThrow(() -> new IllegalStateException(
                        "exact-volume biome is absent from final registry: " + biomeKey.location()));

        var featureSteps = biome.value().getGenerationSettings().features();
        int stepIndex = generationStep.ordinal();
        if (stepIndex >= featureSteps.size()) {
            return new Result(biomeKey, generationStep, 0, 0, 0, List.of());
        }

        var placedFeatureRegistry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        BlockPos nativeChunkOrigin = new BlockPos(
                originChunk.getMinBlockX(),
                level.getMinBuildHeight(),
                originChunk.getMinBlockZ());
        List<ResourceLocation> featureKeys = new ArrayList<>();
        int attempted = 0;
        int successful = 0;
        int attachmentWrites = 0;

        for (Holder<PlacedFeature> placedFeature : featureSteps.get(stepIndex)) {
            ResourceLocation featureKey = placedFeature.unwrapKey()
                    .map(key -> key.location())
                    .orElseGet(() -> placedFeatureRegistry.getKey(placedFeature.value()));
            if (featureKey == null) {
                throw new IllegalStateException(
                        "biome generation settings contain a PlacedFeature absent from the final registry");
            }

            var operation = SkyforgePopulationOperation.create(
                    volumeId,
                    originChunk,
                    featureKey,
                    stepIndex,
                    attempted);
            var result = SkyforgeNativePlacedFeatureRunner.place(
                    level,
                    generator,
                    placedFeature,
                    biome,
                    operation,
                    nativeChunkOrigin,
                    maximumAttachmentDepth);
            featureKeys.add(featureKey);
            attempted++;
            if (result.placed()) {
                successful++;
            }
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
        }

        return new Result(
                biomeKey,
                generationStep,
                attempted,
                successful,
                attachmentWrites,
                List.copyOf(featureKeys));
    }

    record Result(
            ResourceKey<Biome> biomeKey,
            GenerationStep.Decoration generationStep,
            int attemptedFeatures,
            int successfulFeatures,
            int attachmentWrites,
            List<ResourceLocation> featureKeys) {
        Result {
            Objects.requireNonNull(biomeKey, "biomeKey");
            Objects.requireNonNull(generationStep, "generationStep");
            Objects.requireNonNull(featureKeys, "featureKeys");
            if (attemptedFeatures < 0 || successfulFeatures < 0 || attachmentWrites < 0) {
                throw new IllegalArgumentException("population result counts must be non-negative");
            }
            if (successfulFeatures > attemptedFeatures || featureKeys.size() != attemptedFeatures) {
                throw new IllegalArgumentException("population result counts are inconsistent");
            }
            featureKeys = List.copyOf(featureKeys);
        }
    }
}
