package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

final class SkyforgeDeferredTerrainWriteResumeTest {
    @Test
    void saveLoadMidObligationResumesWithoutSkipsDuplicatesOrPrematureTerminalState() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ResourceLocation[] keys = new ResourceLocation[16 * 16 * 2];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        int[] solidLinearIndices = {1, 5, 260, 300, 500};
        for (int index : solidLinearIndices) {
            keys[index] = SkyforgeMinecraftBlockPalette.STONE;
        }
        MinecraftChunkMaterialization materialization =
                new MinecraftChunkMaterialization(chunkPos, 0, 2, keys, 1);
        ProtoChunk packetized = MinecraftTestChunkFactory.protoChunk(chunkPos);
        ProtoChunk monolithic = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkWriter writer =
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        SkyIslandWorldVolumeId volumeId = new SkyIslandWorldVolumeId(17L, "resume", 0, 0, 23L);

        var data = new SkyforgeDeferredTerrainWriteProgressData();
        var progress = data.getOrCreate(volumeId, chunkPos.toLong(), 5);
        var first = writer.writeDeferredSolidOverlayPacket(
                packetized, materialization, progress.cursor(), 2, false);
        assertEquals(2, first.assignedSolidWrites());
        assertFalse(first.complete());
        data.store(progress.advance(first, false));

        CompoundTag saved = data.save(new CompoundTag(), null);
        var reloaded = SkyforgeDeferredTerrainWriteProgressData.load(saved, null);
        var resumed = reloaded.find(volumeId, chunkPos.toLong()).orElseThrow();
        assertEquals(first.cursor(), resumed.cursor());
        assertFalse(resumed.terminal());

        var second = writer.writeDeferredSolidOverlayPacket(
                packetized, materialization, resumed.cursor(), 2, false);
        assertEquals(2, second.assignedSolidWrites());
        assertFalse(second.complete());
        reloaded.store(resumed.advance(second, false));

        var afterSecond = reloaded.find(volumeId, chunkPos.toLong()).orElseThrow();
        var third = writer.writeDeferredSolidOverlayPacket(
                packetized, materialization, afterSecond.cursor(), 2, false);
        assertEquals(1, third.assignedSolidWrites());
        assertTrue(third.complete());
        var terminal = afterSecond.advance(third, true);
        reloaded.store(terminal);
        assertTrue(terminal.terminal());
        assertEquals(5, terminal.cumulativeAssignedSolidWrites());
        assertEquals(5, terminal.expectedSolidBlocks());

        writer.writeSolidOverlay(monolithic, materialization);
        for (int worldY = 0; worldY < 2; worldY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    BlockPos pos = new BlockPos(localX, worldY, localZ);
                    assertEquals(
                            monolithic.getBlockState(pos),
                            packetized.getBlockState(pos),
                            "save/load resumed packet stream diverged at " + pos);
                }
            }
        }
    }
}
