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
    void smoothedProviderSurfaceReducesSingleColumnReliefWithoutChangingFlatNeighbors() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                chunk.setBlockState(
                        new BlockPos(localX, 190, localZ),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        false);
            }
        }
        chunk.setBlockState(new BlockPos(8, 210, 8), Blocks.GRASS_BLOCK.defaultBlockState(), false);

        MinecraftNativeSurfaceSnapshot snapshot = MinecraftNativeSurfaceSnapshot.capture(chunk, 63);

        assertEquals(
                191,
                snapshot.smoothedProviderSurfaceY(8, 8),
                "the center spike should blend deterministically with its 7x7 provider neighborhood");
        assertEquals(
                191,
                snapshot.smoothedProviderSurfaceY(7, 7),
                "the nearby profile should transition gradually away from the spike");
        assertEquals(
                190,
                snapshot.smoothedProviderSurfaceY(4, 4),
                "the flat area beyond the smoothing radius should retain its source height");
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
    void fallingProviderSurfaceUsesDominantStableMaterialInsteadOfGrayAuthoredCap() {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = catalog.volumes().getFirst();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        MinecraftChunkMaterialization materialized = adapter.materialize(
                volume.id(),
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        var support = adapter.integerSolidRange(volume.id(), 8, 8).orElseThrow();

        // The provider profile is mostly stable grass but has a small, conspicuous gravel patch.
        // The authored island cap at that patch is stone, which exposed the old fallback bug.
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                chunk.setBlockState(
                        new BlockPos(localX, 190, localZ),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        false);
            }
        }
        for (int localZ = 4; localZ <= 12; localZ++) {
            for (int localX = 4; localX <= 12; localX++) {
                chunk.setBlockState(
                        new BlockPos(localX, 223, localZ),
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        false);
            }
        }
        chunk.setBlockState(new BlockPos(8, 223, 8), Blocks.GRAVEL.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 222, 8), Blocks.GRAVEL.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 221, 8), Blocks.GRAVEL.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 220, 8), Blocks.GRAVEL.defaultBlockState(), false);

        ResourceLocation[] authoredKeys = materialized.blockKeys();
        int authoredTopLocalY = support.maximumY() - materialized.minimumY();
        authoredKeys[8 + CHUNK_WIDTH * (8 + CHUNK_WIDTH * authoredTopLocalY)] =
                SkyforgeMinecraftBlockPalette.STONE;
        MinecraftChunkMaterialization original = new MinecraftChunkMaterialization(
                chunkPos,
                materialized.minimumY(),
                materialized.height(),
                authoredKeys,
                materialized.candidateVolumeReferences());
        assertEquals(
                SkyforgeMinecraftBlockPalette.STONE,
                original.blockKeyAt(8, support.maximumY(), 8),
                "fixture must expose the authored gray cap that the old falling-material fallback preserved");

        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                original,
                adapter);

        int authoredTopMaximumY = (int) Math.floor(volume.bounds().maximumY());
        int expectedTopY = Math.min(
                support.maximumY() + 64,
                Math.min(chunk.getMaxBuildHeight() - 1, authoredTopMaximumY + 64));
        assertTrue(expectedTopY > support.maximumY(), "the provider height profile should remain active");
        ResourceLocation grass = ResourceLocation.withDefaultNamespace("grass_block");
        for (int depth = 0; depth < 4; depth++) {
            assertEquals(
                    grass,
                    profile.materialization().blockKeyAt(8, expectedTopY - depth, 8),
                    "rejected falling surface and strata must use the stable regional surface instead of gray filler");
        }
        assertFalse(
                ResourceLocation.withDefaultNamespace("gravel")
                        .equals(profile.materialization().blockKeyAt(8, expectedTopY, 8)));
        assertTrue(profile.providerWriteClaims().containsKey(
                BlockPos.asLong(8, support.maximumY() + 1, 8)));
    }

    @Test
    void providerNearSurfaceStrataFollowLoweredIslandTopAndReplaceGrayCap() {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = catalog.volumes().getFirst();
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        MinecraftChunkMaterialization materialized = adapter.materialize(
                volume.id(),
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());

        // Pick a genuinely thick cap so a six-block cut still leaves room for the full
        // captured near-surface profile beneath the newly exposed top.
        int surfaceX = -1;
        int surfaceZ = -1;
        int supportMaximumY = Integer.MIN_VALUE;
        int capDepth = 0;
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                var range = adapter.integerSolidRange(volume.id(), localX, localZ);
                if (range.isEmpty()) {
                    continue;
                }
                int candidateTopY = range.orElseThrow().maximumY();
                int candidateBottomY = candidateTopY;
                while (candidateBottomY > materialized.minimumY()
                        && !SkyforgeMinecraftBlockPalette.AIR.equals(
                                materialized.blockKeyAt(localX, candidateBottomY - 1, localZ))) {
                    candidateBottomY--;
                }
                int candidateDepth = candidateTopY - candidateBottomY + 1;
                if (candidateDepth > capDepth) {
                    surfaceX = localX;
                    surfaceZ = localZ;
                    supportMaximumY = candidateTopY;
                    capDepth = candidateDepth;
                }
            }
        }
        assertTrue(capDepth >= 10, "fixture needs a thick continuous cap for the bounded cut and three strata");
        chunk.setBlockState(new BlockPos(surfaceX, 223, surfaceZ), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(surfaceX, 222, surfaceZ), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(surfaceX, 221, surfaceZ), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(surfaceX, 220, surfaceZ), Blocks.STONE.defaultBlockState(), false);

        ResourceLocation[] authoredKeys = materialized.blockKeys();
        authoredKeys[surfaceX + CHUNK_WIDTH * (surfaceZ + CHUNK_WIDTH * (supportMaximumY - materialized.minimumY()))] =
                SkyforgeMinecraftBlockPalette.STONE;
        MinecraftChunkMaterialization grayCap = new MinecraftChunkMaterialization(
                chunkPos,
                materialized.minimumY(),
                materialized.height(),
                authoredKeys,
                materialized.candidateVolumeReferences());

        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 238),
                grayCap,
                adapter);

        int loweredTopY = materialized.minimumY() + materialized.height() - 1;
        while (loweredTopY >= materialized.minimumY()
                && SkyforgeMinecraftBlockPalette.AIR.equals(
                        profile.materialization().blockKeyAt(surfaceX, loweredTopY, surfaceZ))) {
            loweredTopY--;
        }
        int expectedTopY = supportMaximumY
                + Math.toIntExact(MinecraftNativeSurfaceTopAdapter.projectProviderRelief(-15));
        assertEquals(expectedTopY, loweredTopY, "negative provider relief should cut into, not through, the cap");
        assertEquals(
                ResourceLocation.withDefaultNamespace("grass_block"),
                profile.materialization().blockKeyAt(surfaceX, loweredTopY, surfaceZ));
        assertEquals(
                ResourceLocation.withDefaultNamespace("dirt"),
                profile.materialization().blockKeyAt(surfaceX, loweredTopY - 1, surfaceZ));
        assertEquals(
                ResourceLocation.withDefaultNamespace("dirt"),
                profile.materialization().blockKeyAt(surfaceX, loweredTopY - 2, surfaceZ));
        assertEquals(
                ResourceLocation.withDefaultNamespace("stone"),
                profile.materialization().blockKeyAt(surfaceX, loweredTopY - 3, surfaceZ));
        assertEquals(
                SkyforgeMinecraftBlockPalette.AIR,
                profile.materialization().blockKeyAt(surfaceX, supportMaximumY, surfaceZ),
                "surface re-skinning must not restore cleared occupancy above the lowered top");
    }

    @Test
    void providerNearSurfaceStrataFollowNewlyProjectedIslandRise() {
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
        chunk.setBlockState(new BlockPos(8, 223, 8), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 222, 8), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 221, 8), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 220, 8), Blocks.STONE.defaultBlockState(), false);

        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                original,
                adapter);

        int projectedTopY = Math.min(
                support.maximumY() + 64,
                Math.min(
                        chunk.getMaxBuildHeight() - 1,
                        (int) Math.floor(volume.bounds().maximumY()) + 64));
        assertTrue(projectedTopY - 3 > support.maximumY());
        assertEquals(
                ResourceLocation.withDefaultNamespace("grass_block"),
                profile.materialization().blockKeyAt(8, projectedTopY, 8));
        assertEquals(
                ResourceLocation.withDefaultNamespace("dirt"),
                profile.materialization().blockKeyAt(8, projectedTopY - 1, 8));
        assertEquals(
                ResourceLocation.withDefaultNamespace("dirt"),
                profile.materialization().blockKeyAt(8, projectedTopY - 2, 8));
        assertEquals(
                ResourceLocation.withDefaultNamespace("stone"),
                profile.materialization().blockKeyAt(8, projectedTopY - 3, 8));
        assertEquals(
                ResourceLocation.withDefaultNamespace("stone"),
                profile.materialization().blockKeyAt(8, projectedTopY - 5, 8),
                "provider-rise faces below the captured native strata should retain structural material");
    }

    @Test
    void providerCutReskinsRaisedNeighborWallBelowItsOriginalCapTop() {
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

        int riseX = -1;
        int riseZ = -1;
        int cutX = -1;
        int cutZ = -1;
        int riseTopY = Integer.MIN_VALUE;
        int cutTopY = Integer.MIN_VALUE;
        for (int localZ = 1; localZ < CHUNK_WIDTH - 1 && riseX < 0; localZ++) {
            for (int localX = 1; localX < CHUNK_WIDTH - 1 && riseX < 0; localX++) {
                var riseRange = adapter.integerSolidRange(volume.id(), localX, localZ);
                if (riseRange.isEmpty()) {
                    continue;
                }
                int candidateRiseTopY = riseRange.orElseThrow().maximumY();
                int riseCapBottomY = candidateRiseTopY;
                while (riseCapBottomY > original.minimumY()
                        && !SkyforgeMinecraftBlockPalette.AIR.equals(
                                original.blockKeyAt(localX, riseCapBottomY - 1, localZ))) {
                    riseCapBottomY--;
                }
                if (candidateRiseTopY - riseCapBottomY < 2) {
                    continue;
                }
                for (int[] direction : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                    int neighborX = localX + direction[0];
                    int neighborZ = localZ + direction[1];
                    var cutRange = adapter.integerSolidRange(volume.id(), neighborX, neighborZ);
                    if (cutRange.isEmpty()) {
                        continue;
                    }
                    int candidateCutTopY = cutRange.orElseThrow().maximumY();
                    int cutCapBottomY = candidateCutTopY;
                    while (cutCapBottomY > original.minimumY()
                            && !SkyforgeMinecraftBlockPalette.AIR.equals(
                                    original.blockKeyAt(neighborX, cutCapBottomY - 1, neighborZ))) {
                        cutCapBottomY--;
                    }
                    if (candidateCutTopY >= candidateRiseTopY
                            && candidateCutTopY - candidateRiseTopY <= 8
                            && candidateCutTopY - cutCapBottomY >= 16) {
                        riseX = localX;
                        riseZ = localZ;
                        cutX = neighborX;
                        cutZ = neighborZ;
                        riseTopY = candidateRiseTopY;
                        cutTopY = candidateCutTopY;
                        break;
                    }
                }
            }
        }
        assertTrue(riseX >= 0, "fixture needs adjacent thick-cap columns with a slight top gradient");

        chunk.setBlockState(new BlockPos(riseX, 223, riseZ), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(riseX, 222, riseZ), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(riseX, 221, riseZ), Blocks.DIRT.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(riseX, 220, riseZ), Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(cutX, 30, cutZ), Blocks.GRASS_BLOCK.defaultBlockState(), false);

        ResourceLocation[] authoredKeys = original.blockKeys();
        authoredKeys[riseX + CHUNK_WIDTH * (riseZ + CHUNK_WIDTH * (riseTopY - original.minimumY()))] =
                SkyforgeMinecraftBlockPalette.STONE;
        MinecraftChunkMaterialization grayCappedIsland = new MinecraftChunkMaterialization(
                chunkPos,
                original.minimumY(),
                original.height(),
                authoredKeys,
                original.candidateVolumeReferences());

        var profile = new MinecraftNativeSurfaceTopAdapter().adaptTerrainProfile(
                MinecraftNativeSurfaceSnapshot.capture(chunk, 63),
                grayCappedIsland,
                adapter);

        int projectedRiseTopY = riseTopY
                + Math.toIntExact(MinecraftNativeSurfaceTopAdapter.projectProviderRelief(160));
        int projectedCutTopY = cutTopY
                + Math.toIntExact(MinecraftNativeSurfaceTopAdapter.projectProviderRelief(-33));
        assertEquals(riseTopY + 64, projectedRiseTopY, "the provider ridge should rise by the bounded maximum");
        assertTrue(
                projectedCutTopY < riseTopY - 1,
                "the neighboring provider cut must expose the raised column's original cap wall");
        assertEquals(
                ResourceLocation.withDefaultNamespace("dirt"),
                profile.materialization().blockKeyAt(riseX, riseTopY - 1, riseZ),
                "the exposed join should use the matching shallow provider stratum");
        assertEquals(
                grayCappedIsland.blockKeyAt(riseX, riseTopY - 5, riseZ),
                profile.materialization().blockKeyAt(riseX, riseTopY - 5, riseZ),
                "deep island material below the bounded join skin must not be repainted into a repeating wall");
    }

    @Test
    void surfaceStrataStopAtCaveGapsInsteadOfSamplingBelowThem() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        chunk.setBlockState(new BlockPos(8, 223, 8), Blocks.GRASS_BLOCK.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 222, 8), Blocks.DIRT.defaultBlockState(), false);
        // Y=221 is a cave/air gap; stone below it is not part of the surface profile.
        chunk.setBlockState(new BlockPos(8, 220, 8), Blocks.STONE.defaultBlockState(), false);

        MinecraftNativeSurfaceSnapshot snapshot = MinecraftNativeSurfaceSnapshot.capture(chunk);

        assertEquals(ResourceLocation.withDefaultNamespace("grass_block"),
                snapshot.surfaceStratum(8, 8, 0).orElseThrow());
        assertEquals(ResourceLocation.withDefaultNamespace("dirt"),
                snapshot.surfaceStratum(8, 8, 1).orElseThrow());
        assertTrue(snapshot.surfaceStratum(8, 8, 2).isEmpty(),
                "surface strata must not bridge an intervening cave gap");
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
        for (int localZ = 4; localZ <= 12; localZ++) {
            for (int localX = 4; localX <= 12; localX++) {
                chunk.setBlockState(
                        new BlockPos(localX, 223, localZ),
                        Blocks.STONE.defaultBlockState(),
                        false);
            }
        }
        chunk.setBlockState(
                new BlockPos(8, 223, 8),
                Blocks.STONE.defaultBlockState(),
                false);
        chunk.setBlockState(new BlockPos(8, 222, 8), Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 221, 8), Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(new BlockPos(8, 220, 8), Blocks.STONE.defaultBlockState(), false);
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
        ResourceLocation grass = ResourceLocation.withDefaultNamespace("grass_block");
        assertEquals(
                grass,
                profile.materialization().blockKeyAt(8, projectedTopY, 8),
                "minority provider stone should follow the dominant stable provider surface material");
        assertEquals(grass, profile.materialization().blockKeyAt(8, projectedTopY - 1, 8));
        assertEquals(grass, profile.materialization().blockKeyAt(8, projectedTopY - 2, 8));
        assertEquals(
                grass,
                profile.materialization().blockKeyAt(8, projectedTopY - 3, 8),
                "minority stone in the captured near-surface profile must not recreate a gray exposed wall");
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
