package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeDeferredExactMaterializationPreparationTest {
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_AREA = CHUNK_WIDTH * CHUNK_WIDTH;

    @Test
    void assemblesBoundedColumnsInHistoricalLocalZThenLocalXOrder() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(17L, "slice", 1, 2, 31L);
        ChunkPos chunkPos = new ChunkPos(3, -4);
        var preparation = new SkyforgeDeferredExactMaterializationPreparation(volumeId, chunkPos, 10, 5);

        var first = preparation.advance(this::syntheticColumns, 16);
        assertEquals(16, first.preparedColumns());
        assertEquals(16, first.nextColumn());
        assertFalse(first.complete());
        assertEquals(16, preparation.nextColumn());

        MinecraftChunkMaterialization completed = null;
        while (!preparation.complete()) {
            var advance = preparation.advance(this::syntheticColumns, 16);
            if (advance.complete()) {
                completed = advance.completedMaterialization().orElseThrow();
            }
        }

        assertEquals(CHUNK_AREA, preparation.nextColumn());
        assertEquals((long) CHUNK_AREA * 5L, preparation.classifiedVoxels());
        assertEquals(0L, preparation.provenAirSkippedVoxels());
        assertEquals(chunkPos, completed.chunkPos());
        assertEquals(10, completed.minimumY());
        assertEquals(5, completed.height());
        assertEquals(1, completed.candidateVolumeReferences());
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int column = localZ * CHUNK_WIDTH + localX;
                for (int worldY = 10; worldY < 15; worldY++) {
                    assertEquals(
                            expectedKey(column, worldY),
                            completed.blockKeyAt(localX, worldY, localZ));
                }
            }
        }
        assertThrows(IllegalStateException.class, () -> preparation.advance(this::syntheticColumns, 16));
    }

    @Test
    void rejectsAnAdvanceWhoseCursorDoesNotMatchTheRequestedColumn() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(23L, "slice", 0, 0, 47L);
        ChunkPos chunkPos = new ChunkPos(0, 0);
        var preparation = new SkyforgeDeferredExactMaterializationPreparation(volumeId, chunkPos, -8, 4);

        assertThrows(IllegalStateException.class, () -> preparation.advance(
                (ignoredVolume, ignoredChunk, minimumY, height, firstColumn, maximumColumns, keys) ->
                        new SkyforgeNeoForge1211ChunkAdapter.ExactColumnAdvance(
                                1,
                                2,
                                8L,
                                0L,
                                false),
                2));
        assertEquals(0, preparation.nextColumn());
    }

    private SkyforgeNeoForge1211ChunkAdapter.ExactColumnAdvance syntheticColumns(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int minimumY,
            int height,
            int firstColumn,
            int maximumColumns,
            ResourceLocation[] blockKeys) {
        int preparedColumns = Math.min(maximumColumns, CHUNK_AREA - firstColumn);
        int maximumColumnExclusive = firstColumn + preparedColumns;
        for (int column = firstColumn; column < maximumColumnExclusive; column++) {
            int localZ = column / CHUNK_WIDTH;
            int localX = column % CHUNK_WIDTH;
            for (int localY = 0; localY < height; localY++) {
                int worldY = minimumY + localY;
                blockKeys[localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY)] =
                        expectedKey(column, worldY);
            }
        }
        return new SkyforgeNeoForge1211ChunkAdapter.ExactColumnAdvance(
                firstColumn,
                preparedColumns,
                (long) preparedColumns * height,
                0L,
                maximumColumnExclusive == CHUNK_AREA);
    }

    private static ResourceLocation expectedKey(int column, int worldY) {
        return Math.floorMod(column + worldY, 2) == 0
                ? SkyforgeMinecraftBlockPalette.STONE
                : SkyforgeMinecraftBlockPalette.DEEPSLATE;
    }
}
