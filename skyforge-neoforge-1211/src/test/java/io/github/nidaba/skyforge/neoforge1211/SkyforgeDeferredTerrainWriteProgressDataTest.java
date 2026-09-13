package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeDeferredTerrainWriteProgressDataTest {
    @Test
    void roundTripsPartialCursorAndTerminalStateDeterministically() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(17L, "test", 2, 3, 41L);
        long chunkKey = 123456789L;
        var data = new SkyforgeDeferredTerrainWriteProgressData();
        var initial = data.getOrCreate(volumeId, chunkKey, 2048);
        assertFalse(initial.terminal());

        var partialAdvance = new SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteAdvance(
                new SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteCursor(7000, 1024, 1024),
                1024,
                1024,
                false,
                false);
        data.store(initial.advance(partialAdvance, false));

        CompoundTag firstEncoding = data.save(new CompoundTag(), null);
        var reloaded = SkyforgeDeferredTerrainWriteProgressData.load(firstEncoding, null);
        var partial = reloaded.find(volumeId, chunkKey).orElseThrow();
        assertEquals(7000, partial.nextLinearIndex());
        assertEquals(1024, partial.cumulativeAssignedSolidWrites());
        assertEquals(2048, partial.expectedSolidBlocks());
        assertFalse(partial.terminal());

        var finalAdvance = new SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteAdvance(
                new SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteCursor(12000, 2048, 2048),
                1024,
                1024,
                true,
                false);
        reloaded.store(partial.advance(finalAdvance, true));
        CompoundTag secondEncoding = reloaded.save(new CompoundTag(), null);
        var terminal = SkyforgeDeferredTerrainWriteProgressData.load(secondEncoding, null)
                .find(volumeId, chunkKey)
                .orElseThrow();
        assertTrue(terminal.terminal());
        assertEquals(2048, terminal.cumulativeAssignedSolidWrites());
        assertEquals(2048, terminal.expectedSolidBlocks());
    }

    @Test
    void partialPureMaterializationPreparationRestartsFromZeroAfterReload() {
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(29L, "prepare", 1, 4, 53L);
        ChunkPos chunkPos = new ChunkPos(2, -3);
        var data = new SkyforgeDeferredTerrainWriteProgressData();
        var preparation = data.getOrCreatePreparation(volumeId, chunkPos, -64, 96);

        var advance = preparation.advance(
                (ignoredVolume, requestedChunk, minimumY, height) -> airSlice(requestedChunk, minimumY, height),
                32);
        assertEquals(32, advance.preparedHeight());
        assertEquals(32, preparation.preparedHeight());
        assertFalse(preparation.complete());

        CompoundTag encoding = data.save(new CompoundTag(), null);
        var reloaded = SkyforgeDeferredTerrainWriteProgressData.load(encoding, null);
        var restarted = reloaded.getOrCreatePreparation(volumeId, chunkPos, -64, 96);
        assertEquals(0, restarted.preparedHeight());
        assertFalse(restarted.complete());
    }

    private static MinecraftChunkMaterialization airSlice(
            ChunkPos chunkPos,
            int minimumY,
            int height) {
        ResourceLocation[] keys = new ResourceLocation[16 * 16 * height];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        return new MinecraftChunkMaterialization(chunkPos, minimumY, height, keys, 1);
    }
}
