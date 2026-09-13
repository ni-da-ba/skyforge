package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeDeferredExactMaterializationPreparationTest {
    @Test
    void assemblesBoundedVerticalSlicesIntoTheOriginalYMajorProjection() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(17L, "slice", 1, 2, 31L);
        ChunkPos chunkPos = new ChunkPos(3, -4);
        var preparation = new SkyforgeDeferredExactMaterializationPreparation(volumeId, chunkPos, 10, 5);

        var first = preparation.advance(this::syntheticSlice, 2);
        assertEquals(2, first.preparedHeight());
        assertFalse(first.complete());
        assertEquals(2, preparation.preparedHeight());

        var second = preparation.advance(this::syntheticSlice, 2);
        assertEquals(2, second.preparedHeight());
        assertFalse(second.complete());
        assertEquals(4, preparation.preparedHeight());

        var third = preparation.advance(this::syntheticSlice, 2);
        assertEquals(1, third.preparedHeight());
        assertTrue(third.complete());
        assertEquals(5, preparation.preparedHeight());

        MinecraftChunkMaterialization completed = third.completedMaterialization().orElseThrow();
        assertEquals(chunkPos, completed.chunkPos());
        assertEquals(10, completed.minimumY());
        assertEquals(5, completed.height());
        assertEquals(1, completed.candidateVolumeReferences());
        for (int worldY = 10; worldY < 15; worldY++) {
            ResourceLocation expected = expectedKey(worldY);
            assertEquals(expected, completed.blockKeyAt(0, worldY, 0));
            assertEquals(expected, completed.blockKeyAt(15, worldY, 15));
        }
        assertThrows(IllegalStateException.class, () -> preparation.advance(this::syntheticSlice, 2));
    }

    @Test
    void rejectsASliceWhoseIdentityDoesNotMatchTheRequestedInterval() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(23L, "slice", 0, 0, 47L);
        ChunkPos chunkPos = new ChunkPos(0, 0);
        var preparation = new SkyforgeDeferredExactMaterializationPreparation(volumeId, chunkPos, -8, 4);

        assertThrows(IllegalStateException.class, () -> preparation.advance(
                (ignoredVolume, ignoredChunk, minimumY, height) ->
                        syntheticSlice(ignoredVolume, new ChunkPos(1, 0), minimumY, height),
                2));
        assertEquals(0, preparation.preparedHeight());
    }

    private MinecraftChunkMaterialization syntheticSlice(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int minimumY,
            int height) {
        ResourceLocation[] keys = new ResourceLocation[16 * 16 * height];
        for (int localY = 0; localY < height; localY++) {
            Arrays.fill(
                    keys,
                    localY * 16 * 16,
                    (localY + 1) * 16 * 16,
                    expectedKey(minimumY + localY));
        }
        return new MinecraftChunkMaterialization(chunkPos, minimumY, height, keys, 1);
    }

    private static ResourceLocation expectedKey(int worldY) {
        return Math.floorMod(worldY, 2) == 0
                ? SkyforgeMinecraftBlockPalette.STONE
                : SkyforgeMinecraftBlockPalette.DEEPSLATE;
    }
}
