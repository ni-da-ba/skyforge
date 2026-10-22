package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void providerReliefUsesBoundedLinearMappingAtIslandScale() {
        assertEquals(3, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(7));
        assertEquals(6, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(15));
        assertEquals(-6, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(-15));
        assertEquals(25, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(62));
        assertEquals(40, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(100));
        assertEquals(64, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(159));
        assertEquals(64, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(160));
        assertEquals(-64, MinecraftNativeSurfaceTopAdapter.projectProviderRelief(-160));
    }

    @Test
    void nativeReliefRaisesOnlyTheExactIslandTopCapAndClaimsAddedCells() {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = catalog.volumes().getFirst();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        MinecraftChunkMaterialization original = adapter.materialize(
                volume.id(),
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        var support = adapter.integerSolidRange(volume.id(), 8, 8).orElseThrow();
        int providerSurfaceY = 223;
        chunk.setBlockState(
                new BlockPos(8, providerSurfaceY, 8),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                false);

        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                original,
                adapter);

        int authoredTopMaximumY = (int) Math.floor(volume.bounds().maximumY());
        int projectedRise = Math.toIntExact(
                MinecraftNativeSurfaceTopAdapter.projectProviderRelief(providerSurfaceY - 63L));
        int expectedTopY = Math.min(
                support.maximumY() + projectedRise,
                authoredTopMaximumY + 64);
        assertEquals(64, projectedRise, "the upper source profile should form a bounded visible island peak");
        assertTrue(expectedTopY > authoredTopMaximumY + 32,
                "the expanded but bounded profile should use more than the former 32-block rise");
        assertEquals(
                ResourceLocation.withDefaultNamespace("grass_block"),
                profile.materialization().blockKeyAt(8, expectedTopY, 8));
        assertTrue(profile.providerWriteClaims().containsKey(
                BlockPos.asLong(8, support.maximumY() + 1, 8)));
        assertEquals(
                volume.id(),
                profile.providerWriteClaims().get(BlockPos.asLong(8, support.maximumY() + 1, 8)));
        assertFalse(profile.providerWriteClaims().containsKey(
                BlockPos.asLong(8, support.maximumY(), 8)));
        assertTrue(profile.materialization().solidBlockCount() > original.solidBlockCount());
    }

    @Test
    void fallingProviderSurfaceKeepsAuthoredSkyforgeCapMaterialAcrossProjectedRelief() {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = catalog.volumes().getFirst();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        MinecraftChunkMaterialization original = adapter.materialize(
                volume.id(),
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        var support = adapter.integerSolidRange(volume.id(), 8, 8).orElseThrow();
        ResourceLocation authoredCapSurface = original.blockKeyAt(8, support.maximumY(), 8);
        ResourceLocation gravel = ResourceLocation.withDefaultNamespace("gravel");

        // The provider can still raise the island profile, but a falling surface material must
        // not paint a large feature-shaped patch over Skyforge's authored cap.
        int providerSurfaceY = 223;
        chunk.setBlockState(
                new BlockPos(8, providerSurfaceY, 8),
                Blocks.GRAVEL.defaultBlockState(),
                false);
        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                original,
                adapter);

        int authoredTopMaximumY = (int) Math.floor(volume.bounds().maximumY());
        int expectedTopY = Math.min(
                support.maximumY() + 64,
                Math.min(chunk.getMaxBuildHeight() - 1, authoredTopMaximumY + 64));
        assertTrue(expectedTopY > support.maximumY(), "the provider height profile should remain active");
        assertEquals(
                authoredCapSurface,
                profile.materialization().blockKeyAt(8, expectedTopY, 8),
                "falling provider material must resolve to the authored Skyforge cap surface");
        assertFalse(gravel.equals(profile.materialization().blockKeyAt(8, expectedTopY, 8)));
        assertTrue(profile.providerWriteClaims().containsKey(
                BlockPos.asLong(8, support.maximumY() + 1, 8)));
    }

    @Test
    void minorityProviderStoneUsesDominantStableSurfaceMaterialAcrossProjectedRelief() {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = catalog.volumes().getFirst();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        MinecraftChunkMaterialization original = adapter.materialize(
                volume.id(),
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        var support = adapter.integerSolidRange(volume.id(), 8, 8).orElseThrow();

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                chunk.setBlockState(
                        new BlockPos(localX, 190, localZ),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        false);
            }
        }
        chunk.setBlockState(
                new BlockPos(8, 223, 8),
                Blocks.STONE.defaultBlockState(),
                false);
        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                original,
                adapter);

        int projectedTopY = Math.min(
                support.maximumY() + 64,
                Math.min(
                        chunk.getMaxBuildHeight() - 1,
                        (int) Math.floor(volume.bounds().maximumY()) + 64));
        assertTrue(projectedTopY > support.maximumY(), "provider relief should still shape the island");
        assertEquals(
                ResourceLocation.withDefaultNamespace("grass_block"),
                profile.materialization().blockKeyAt(8, projectedTopY, 8),
                "minority provider stone should follow the dominant stable provider surface material");
    }

    @Test
    void fallingNativeSurfaceIsSkippedEvenWhenTheSkyforgeTopHasSupport() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        chunk.setBlockState(new BlockPos(0, 64, 0), Blocks.SAND.defaultBlockState(), false);

        MinecraftChunkMaterialization original = materialization(chunk);
        MinecraftChunkMaterialization adapted =
                new MinecraftNativeSurfaceTopAdapter().adapt(chunk, original);

        assertEquals(
                SkyforgeMinecraftBlockPalette.STONE,
                adapted.blockKeyAt(0, 200, 0),
                "falling native material must not become an island cap, even when the column has support");
        assertEquals(
                SkyforgeMinecraftBlockPalette.STONE,
                adapted.blockKeyAt(0, 150, 0),
                "all exposed Skyforge segments must retain stable representation instead of inheriting falling sand");
        assertEquals(original.solidBlockCount(), adapted.solidBlockCount());
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
