package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211SurfaceStageTest {
    @Test
    void inactivePostSurfaceStageLeavesChunkUntouched() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        BlockPos sentinel = new BlockPos(0, 224, 0);
        chunk.setBlockState(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), false);

        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
        assertTrue(SkyforgeNeoForge1211SurfaceStage.realize(chunk).isEmpty());
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), chunk.getBlockState(sentinel));
    }

    @Test
    void activePostSurfaceStagePreservesNativeAirAndFeedsFinalHeightmapPriming() throws Exception {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        MaterializedPosition highestSolid = highestSolid(materialization);

        BlockPos nativeSentinel = new BlockPos(0, 0, 0);
        assertEquals(
                SkyforgeMinecraftBlockPalette.AIR,
                materialization.blockKeyAt(0, nativeSentinel.getY(), 0));
        chunk.setBlockState(nativeSentinel, Blocks.GOLD_BLOCK.defaultBlockState(), false);

        Optional<MinecraftChunkWriteResult> result;
        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
            assertFalse(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
            result = SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }

        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().solidBlockCount() > 0);
        assertEquals(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                chunk.getBlockState(nativeSentinel),
                "post-surface Skyforge AIR must preserve terrain already built by Minecraft");

        BlockPos highestSolidPosition = new BlockPos(
                chunkPos.getMinBlockX() + highestSolid.localX(),
                highestSolid.worldY(),
                chunkPos.getMinBlockZ() + highestSolid.localZ());
        assertFalse(chunk.getBlockState(highestSolidPosition).isAir());

        // ChunkStatusTasks primes these immediately before biome decoration. Performing the same
        // vanilla operation after the Skyforge post-surface write proves that later feature-stage
        // height queries can observe the elevated island.
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
        int finalSurfaceHeight = chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE,
                highestSolid.localX(),
                highestSolid.localZ());
        assertTrue(
                finalSurfaceHeight >= highestSolid.worldY(),
                "final world-surface heightmap must include the elevated Skyforge solid");
    }

    @Test
    void finalHeightmapSelectsUpperSkyforgeSurfaceOverPreservedNativeGround() throws Exception {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        MaterializedPosition highestSolid = highestSolid(materialization);

        int worldX = chunkPos.getMinBlockX() + highestSolid.localX();
        int worldZ = chunkPos.getMinBlockZ() + highestSolid.localZ();
        BlockPos nativeGround = new BlockPos(worldX, 64, worldZ);
        chunk.setBlockState(nativeGround, Blocks.GRASS_BLOCK.defaultBlockState(), false);

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.realize(chunk).isPresent());
        }

        assertEquals(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                chunk.getBlockState(nativeGround),
                "native lower terrain must remain physically present under the floating island");

        Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
        int finalSurfaceHeight = chunk.getHeight(
                Heightmap.Types.WORLD_SURFACE,
                highestSolid.localX(),
                highestSolid.localZ());

        assertTrue(
                finalSurfaceHeight >= highestSolid.worldY(),
                "a vanilla single-valued surface heightmap should target the upper Skyforge surface");
        assertTrue(
                finalSurfaceHeight > nativeGround.getY(),
                "the preserved lower ground cannot simultaneously be the column's world-surface heightmap target");
    }

    @Test
    void nativeSurfaceAdaptedBindingCopiesMinecraftSurfaceOntoElevatedSkyforgeTop() throws Exception {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        MaterializedPosition highestSolid = highestSolid(materialization);

        int worldX = chunkPos.getMinBlockX() + highestSolid.localX();
        int worldZ = chunkPos.getMinBlockZ() + highestSolid.localZ();
        chunk.setBlockState(new BlockPos(worldX, 64, worldZ), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(worldX, 65, worldZ), Blocks.WATER.defaultBlockState(), false);

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
            assertTrue(SkyforgeNeoForge1211SurfaceStage.realize(chunk).isPresent());
        }

        BlockPos highestSolidPosition = new BlockPos(worldX, highestSolid.worldY(), worldZ);
        assertEquals(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                chunk.getBlockState(highestSolidPosition),
                "the exposed Skyforge top should inherit Minecraft's already-built native surface material");
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
    }

    @Test
    void activeStageSkipsProjectionForChunkWithoutSkyforgeCandidates() throws Exception {
        ChunkPos chunkPos = new ChunkPos(100, 100);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        BlockPos sentinel = new BlockPos(chunkPos.getMinBlockX(), 64, chunkPos.getMinBlockZ());
        chunk.setBlockState(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), false);

        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        Optional<MinecraftChunkWriteResult> result;
        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            assertFalse(SkyforgeNeoForge1211SurfaceStage.hasCandidateVolume(chunk));
            result = SkyforgeNeoForge1211SurfaceStage.realize(chunk);
        }

        assertTrue(result.isPresent());
        assertEquals(0, result.orElseThrow().assignedBlockCount());
        assertEquals(0, result.orElseThrow().solidBlockCount());
        assertEquals(0, result.orElseThrow().candidateVolumeReferences());
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), chunk.getBlockState(sentinel));
    }

    private static MaterializedPosition highestSolid(MinecraftChunkMaterialization materialization) {
        for (int worldY = materialization.minimumY() + materialization.height() - 1;
                worldY >= materialization.minimumY();
                worldY--) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    if (!SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        return new MaterializedPosition(localX, worldY, localZ, key);
                    }
                }
            }
        }
        throw new AssertionError("expected at least one Skyforge solid sample");
    }

    private record MaterializedPosition(
            int localX,
            int worldY,
            int localZ,
            ResourceLocation blockKey) {}
}
