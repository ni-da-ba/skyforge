package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

final class MinecraftAdditionalSurfaceIndexTest {
    private static final int MINIMUM_Y = -64;
    private static final int HEIGHT = 384;

    @Test
    void indexesPreservedGroundAndLowerSkyforgeSurfaceButExcludesHighestSurface() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 64, 64, Blocks.GRASS_BLOCK.defaultBlockState());
        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);
        List<BlockPos> positions = index.positions(0, 0);

        assertEquals(List.of(new BlockPos(0, 65, 0), new BlockPos(0, 105, 0)), positions);
        assertFalse(positions.contains(new BlockPos(0, 205, 0)), "vanilla owns the highest surface");
    }

    @Test
    void dryOpenIsStrictSubsetWithHeadroomAndSupportThickness() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 60, 64, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertEquals(
                List.of(new BlockPos(0, 65, 0), new BlockPos(0, 105, 0)),
                index.positions(0, 0, MinecraftSurfaceSuitability.DRY_OPEN));
    }

    @Test
    void dryOpenRejectsLowCeilingWhileDryLandRemainsReachable() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        chunk.setBlockState(new BlockPos(0, 110, 0), Blocks.STONE.defaultBlockState(), false);
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertTrue(index.positions(0, 0).contains(new BlockPos(0, 105, 0)));
        assertFalse(index.positions(0, 0, MinecraftSurfaceSuitability.DRY_OPEN)
                .contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void dryOpenRejectsThinCarvedShelfWhileDryLandRemainsReachable() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        chunk.setBlockState(new BlockPos(0, 104, 0), Blocks.STONE.defaultBlockState(), false);
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertTrue(index.positions(0, 0).contains(new BlockPos(0, 105, 0)));
        assertFalse(index.positions(0, 0, MinecraftSurfaceSuitability.DRY_OPEN)
                .contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void submergedWaterFloorIsAddressableWithoutBecomingDryLand() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 60, 63, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 64, 70, Blocks.WATER.defaultBlockState());
        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertTrue(index.positions(0, 0, MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR)
                .contains(new BlockPos(0, 64, 0)));
        assertFalse(index.positions(0, 0).contains(new BlockPos(0, 64, 0)));
    }

    @Test
    void openWaterFloorRequiresWaterColumnToReachAir() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 105, 111, Blocks.WATER.defaultBlockState());
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertTrue(index.positions(0, 0, MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR)
                .contains(new BlockPos(0, 105, 0)));
        assertTrue(index.positions(0, 0, MinecraftSurfaceSuitability.OPEN_WATER_FLOOR)
                .contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void openWaterFloorRejectsSolidCeilingButSubmergedFloorRemainsReachable() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 105, 111, Blocks.WATER.defaultBlockState());
        chunk.setBlockState(new BlockPos(0, 112, 0), Blocks.STONE.defaultBlockState(), false);
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        MinecraftAdditionalSurfaceIndex index = MinecraftAdditionalSurfaceIndex.from(chunk, materialization);

        assertTrue(index.positions(0, 0, MinecraftSurfaceSuitability.SUBMERGED_WATER_FLOOR)
                .contains(new BlockPos(0, 105, 0)));
        assertFalse(index.positions(0, 0, MinecraftSurfaceSuitability.OPEN_WATER_FLOOR)
                .contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void carvedAwaySkyforgeTopIsNotReturned() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        setColumnRange(chunk, 0, 0, 64, 64, Blocks.GRASS_BLOCK.defaultBlockState());
        // The lower Skyforge interval exists in accepted occupancy but is absent from the live
        // post-carver chunk, simulating a carver that removed its exposed top.
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        List<BlockPos> positions = MinecraftAdditionalSurfaceIndex.from(chunk, materialization)
                .positions(0, 0);

        assertTrue(positions.contains(new BlockPos(0, 65, 0)));
        assertFalse(positions.contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void nativeFluidSurfaceIsNotReturnedAsLandVegetationTarget() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        MinecraftChunkMaterialization materialization = stackedMaterialization(chunk.getPos());

        chunk.setBlockState(new BlockPos(0, 64, 0), Blocks.WATER.defaultBlockState(), false);
        setColumnRange(chunk, 0, 0, 100, 104, Blocks.STONE.defaultBlockState());
        setColumnRange(chunk, 0, 0, 200, 204, Blocks.STONE.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        List<BlockPos> positions = MinecraftAdditionalSurfaceIndex.from(chunk, materialization)
                .positions(0, 0);

        assertFalse(positions.contains(new BlockPos(0, 65, 0)));
        assertTrue(positions.contains(new BlockPos(0, 105, 0)));
    }

    @Test
    void columnsWithoutSkyforgeDoNotSupplementOrdinaryNativeGround() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        ResourceLocation[] keys = airKeys();
        MinecraftChunkMaterialization materialization =
                new MinecraftChunkMaterialization(chunk.getPos(), MINIMUM_Y, HEIGHT, keys, 0);
        setColumnRange(chunk, 1, 1, 64, 64, Blocks.GRASS_BLOCK.defaultBlockState());
        primeWorldSurfaceWorldgen(chunk);

        assertTrue(MinecraftAdditionalSurfaceIndex.from(chunk, materialization)
                .positions(1, 1)
                .isEmpty());
    }

    private static void primeWorldSurfaceWorldgen(ProtoChunk chunk) {
        Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.WORLD_SURFACE_WG));
    }

    private static MinecraftChunkMaterialization stackedMaterialization(ChunkPos chunkPos) {
        ResourceLocation[] keys = airKeys();
        setMaterializedRange(keys, 0, 0, 100, 104, SkyforgeMinecraftBlockPalette.STONE);
        setMaterializedRange(keys, 0, 0, 200, 204, SkyforgeMinecraftBlockPalette.STONE);
        return new MinecraftChunkMaterialization(chunkPos, MINIMUM_Y, HEIGHT, keys, 2);
    }

    private static ResourceLocation[] airKeys() {
        ResourceLocation[] keys = new ResourceLocation[16 * 16 * HEIGHT];
        java.util.Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        return keys;
    }

    private static void setMaterializedRange(
            ResourceLocation[] keys,
            int localX,
            int localZ,
            int minimumWorldY,
            int maximumWorldY,
            ResourceLocation key) {
        for (int worldY = minimumWorldY; worldY <= maximumWorldY; worldY++) {
            int localY = worldY - MINIMUM_Y;
            keys[localX + 16 * (localZ + 16 * localY)] = key;
        }
    }

    private static void setColumnRange(
            ProtoChunk chunk,
            int localX,
            int localZ,
            int minimumWorldY,
            int maximumWorldY,
            net.minecraft.world.level.block.state.BlockState state) {
        int worldX = chunk.getPos().getMinBlockX() + localX;
        int worldZ = chunk.getPos().getMinBlockZ() + localZ;
        for (int worldY = minimumWorldY; worldY <= maximumWorldY; worldY++) {
            chunk.setBlockState(new BlockPos(worldX, worldY, worldZ), state, false);
        }
    }
}
