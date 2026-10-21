package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationOperationTest {
    @Test
    void factoryBindsSeedToExactOperationIdentity() {
        var volumeId = new SkyIslandWorldVolumeId(17L, "operation-test", 2, 5, 23L);
        var chunk = new ChunkPos(-4, 9);
        var key = ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains");

        var operation = SkyforgePopulationOperation.create(volumeId, chunk, key, 9, 3);

        assertEquals(volumeId, operation.volumeId());
        assertEquals(chunk, operation.originChunk());
        assertEquals(key, operation.nativeDefinitionKey());
        assertEquals(9, operation.generationStep());
        assertEquals(3, operation.occurrenceIndex());
        assertEquals(
                SkyforgePopulationSeed.derive(volumeId, chunk, key, 9, 3),
                operation.seed());
    }

    @Test
    void constructorRejectsSeedDetachedFromOperationIdentity() {
        var volumeId = new SkyIslandWorldVolumeId(17L, "operation-test", 2, 5, 23L);
        var chunk = new ChunkPos(-4, 9);
        var key = ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains");
        long expected = SkyforgePopulationSeed.derive(volumeId, chunk, key, 9, 3);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyforgePopulationOperation(volumeId, chunk, key, 9, 3, expected + 1));
    }
}
