package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

final class MinecraftNativeSurfaceTopAdapterTest {
    private static final int CHUNK_WIDTH = 16;

    @Test
    void copiesNativeTopOntoEachElevatedExposedSkyforgeSegmentWithoutChangingOccupancy() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        chunk.setBlockState(new BlockPos(0, 64, 0), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(0, 65, 0), Blocks.WATER.defaultBlockState(), false);

        MinecraftChunkMaterialization original = materialization(chunk);
        MinecraftChunkMaterialization adapted =
                new MinecraftNativeSurfaceTopAdapter().adapt(chunk, original);

        ResourceLocation grass = ResourceLocation.withDefaultNamespace("grass_block");
        assertEquals(grass, adapted.blockKeyAt(0, 200, 0));
        assertEquals(
                SkyforgeMinecraftBlockPalette.STONE,
                adapted.blockKeyAt(0, 199, 0),
                "only the exposed top of a contiguous Skyforge segment should inherit the native surface");
        assertEquals(grass, adapted.blockKeyAt(0, 150, 0));
        assertEquals(
                SkyforgeMinecraftBlockPalette.STONE,
                adapted.blockKeyAt(0, 63, 0),
                "Skyforge solids below the native surface are terrain intersections, not floating exposed tops");
        assertEquals(original.solidBlockCount(), adapted.solidBlockCount());
        assertEquals(original.candidateVolumeReferences(), adapted.candidateVolumeReferences());
    }

    @Test
    void preDecorationSnapshotCannotMistakeLaterVegetationForNativeTerrainMaterial() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        chunk.setBlockState(new BlockPos(0, 64, 0), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        MinecraftNativeSurfaceSnapshot snapshot = MinecraftNativeSurfaceSnapshot.capture(chunk);

        // Simulate a feature placed after surface construction but before Skyforge realization.
        chunk.setBlockState(new BlockPos(0, 100, 0), Blocks.OAK_LOG.defaultBlockState(), false);

        MinecraftChunkMaterialization adapted =
                new MinecraftNativeSurfaceTopAdapter().adapt(snapshot, materialization(chunk));
        assertEquals(
                ResourceLocation.withDefaultNamespace("grass_block"),
                adapted.blockKeyAt(0, 200, 0),
                "island surface representation must come from native terrain, not later decoration");
    }

    private static MinecraftChunkMaterialization materialization(ProtoChunk chunk) {
        ResourceLocation[] keys = new ResourceLocation[CHUNK_WIDTH * CHUNK_WIDTH * chunk.getHeight()];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        put(keys, chunk.getMinBuildHeight(), 0, 200, 0, SkyforgeMinecraftBlockPalette.STONE);
        put(keys, chunk.getMinBuildHeight(), 0, 199, 0, SkyforgeMinecraftBlockPalette.STONE);
        put(keys, chunk.getMinBuildHeight(), 0, 150, 0, SkyforgeMinecraftBlockPalette.STONE);
        put(keys, chunk.getMinBuildHeight(), 0, 63, 0, SkyforgeMinecraftBlockPalette.STONE);
        return new MinecraftChunkMaterialization(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight(),
                keys,
                2);
    }

    private static void put(
            ResourceLocation[] keys,
            int minimumY,
            int localX,
            int worldY,
            int localZ,
            ResourceLocation key) {
        int localY = worldY - minimumY;
        keys[localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY)] = key;
    }
}
