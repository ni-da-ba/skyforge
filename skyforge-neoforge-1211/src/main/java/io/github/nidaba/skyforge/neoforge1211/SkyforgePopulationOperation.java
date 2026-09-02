package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Immutable identity for one island-owned native population attempt. */
record SkyforgePopulationOperation(
        SkyIslandWorldVolumeId volumeId,
        ChunkPos originChunk,
        ResourceLocation nativeDefinitionKey,
        int generationStep,
        int occurrenceIndex,
        long seed) {

    SkyforgePopulationOperation {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(originChunk, "originChunk");
        Objects.requireNonNull(nativeDefinitionKey, "nativeDefinitionKey");
        if (generationStep < 0) {
            throw new IllegalArgumentException("generationStep must be non-negative");
        }
        if (occurrenceIndex < 0) {
            throw new IllegalArgumentException("occurrenceIndex must be non-negative");
        }
        long expectedSeed = SkyforgePopulationSeed.derive(
                volumeId,
                originChunk,
                nativeDefinitionKey,
                generationStep,
                occurrenceIndex);
        if (seed != expectedSeed) {
            throw new IllegalArgumentException("seed does not match exact population operation identity");
        }
    }

    static SkyforgePopulationOperation create(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos originChunk,
            ResourceLocation nativeDefinitionKey,
            int generationStep,
            int occurrenceIndex) {
        long seed = SkyforgePopulationSeed.derive(
                volumeId,
                originChunk,
                nativeDefinitionKey,
                generationStep,
                occurrenceIndex);
        return new SkyforgePopulationOperation(
                volumeId,
                originChunk,
                nativeDefinitionKey,
                generationStep,
                occurrenceIndex,
                seed);
    }
}
