package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(placedFeature, "placedFeature");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(origin, "origin");

        var registryKey = placedFeature.unwrapKey().orElseThrow(() ->
                new IllegalArgumentException("island population requires a registered PlacedFeature holder"));
        if (!registryKey.location().equals(operation.nativeDefinitionKey())) {
            throw new IllegalArgumentException("population operation key does not match PlacedFeature registry identity");
        }

        try (var domain = SkyforgeGenerationDomainStage.openIsland(operation.volumeId());
                var execution = SkyforgePopulationExecutionStage.open(operation, maximumAttachmentDepth)) {
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

    record Result(boolean placed, int attachmentWrites) {
        Result {
            if (attachmentWrites < 0) {
                throw new IllegalArgumentException("attachmentWrites must be non-negative");
            }
        }
    }
}
