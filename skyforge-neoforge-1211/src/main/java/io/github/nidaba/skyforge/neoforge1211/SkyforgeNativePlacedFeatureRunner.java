package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Executes one registered native placed feature inside one exact Skyforge terrain domain. */
final class SkyforgeNativePlacedFeatureRunner {
    private SkyforgeNativePlacedFeatureRunner() {}

    static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        return place(
                level,
                generator,
                placedFeature,
                Optional.empty(),
                operation,
                origin,
                maximumAttachmentDepth);
    }

    static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            Holder<Biome> domainBiome,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        return place(
                level,
                generator,
                placedFeature,
                Optional.of(Objects.requireNonNull(domainBiome, "domainBiome")),
                operation,
                origin,
                maximumAttachmentDepth);
    }

    private static Result place(
            WorldGenLevel level,
            ChunkGenerator generator,
            Holder<PlacedFeature> placedFeature,
            Optional<Holder<Biome>> domainBiome,
            SkyforgePopulationOperation operation,
            BlockPos origin,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(placedFeature, "placedFeature");
        Objects.requireNonNull(domainBiome, "domainBiome");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(origin, "origin");

        ResourceLocation registryLocation = placedFeature.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> level.registryAccess()
                        .registryOrThrow(Registries.PLACED_FEATURE)
                        .getKey(placedFeature.value()));
        if (registryLocation == null) {
            throw new IllegalArgumentException(
                    "island population requires a PlacedFeature present in the final registry");
        }
        if (!registryLocation.equals(operation.nativeDefinitionKey())) {
            throw new IllegalArgumentException("population operation key does not match PlacedFeature registry identity");
        }

        try (var domain = SkyforgeGenerationDomainStage.openIsland(operation.volumeId());
                var execution = openExecution(operation, domainBiome, maximumAttachmentDepth)) {
            domain.requireActive();
            execution.requireActive();
            boolean placed = placedFeature.value().place(
                    level,
                    generator,
                    RandomSource.create(operation.seed()),
                    origin);
            return new Result(placed, execution.execution().attachmentCount());
        }
    }

    private static SkyforgePopulationExecutionStage.Scope openExecution(
            SkyforgePopulationOperation operation,
            Optional<Holder<Biome>> domainBiome,
            int maximumAttachmentDepth) {
        return domainBiome.isPresent()
                ? SkyforgePopulationExecutionStage.open(
                        operation,
                        domainBiome.orElseThrow(),
                        maximumAttachmentDepth)
                : SkyforgePopulationExecutionStage.open(operation, maximumAttachmentDepth);
    }

    record Result(boolean placed, int attachmentWrites) {
        Result {
            if (attachmentWrites < 0) {
                throw new IllegalArgumentException("attachmentWrites must be non-negative");
            }
        }
    }
}
