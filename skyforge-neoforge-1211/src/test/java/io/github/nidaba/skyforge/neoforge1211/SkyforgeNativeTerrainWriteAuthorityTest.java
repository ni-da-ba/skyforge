package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeTerrainWriteAuthorityTest {
    @Test
    void providerWriteRequiresAdmittedOwnerAndStaysInsideItsExistingColumnEnvelope() throws Exception {
        var fixture = fixture();
        var writer = new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");
        ResourceLocation[] keys = airMaterialization(fixture.chunk());
        put(keys, fixture.chunk(), fixture.providerPosition(), stone);
        var materialization = new MinecraftChunkMaterialization(
                fixture.chunk().getPos(),
                fixture.chunk().getMinBuildHeight(),
                fixture.chunk().getHeight(),
                keys,
                1);
        Map<Long, SkyIslandWorldVolumeId> claims = Map.of(
                fixture.providerPosition().asLong(),
                fixture.volumeId());

        try (AutoCloseable terrain = SkyforgeNeoForge1211SurfaceStage.install(
                        fixture.adapter(),
                        writer);
                AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(
                        fixture.catalog(),
                        Map.of(fixture.volumeId(), Set.of(fixture.chunk().getPos().toLong())))) {
            assertNotNull(terrain);
            assertNotNull(admission);
            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    fixture.chunk(),
                    Optional.empty());
            assertEquals(
                    SkyforgePhysicalVolumeAdmissionState.ADMITTED,
                    SkyforgePhysicalVolumeAdmissionStage.snapshot(fixture.volumeId()).state());

            try (AutoCloseable authority = SkyforgeNativeTerrainWriteAuthority.open(claims)) {
                assertNotNull(authority);
                assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(
                        fixture.providerPosition().getX(),
                        fixture.providerPosition().getY(),
                        fixture.providerPosition().getZ()));
                assertFalse(SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(
                        fixture.providerPosition().getX(),
                        fixture.providerPosition().getY() - 1,
                        fixture.providerPosition().getZ()),
                        "provider writes must not enter the Skyforge-owned island body");
                assertFalse(SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(
                        fixture.providerPosition().getX(),
                        (int) Math.floor(fixture.bounds().maximumY()) + 1,
                        fixture.providerPosition().getZ()));
            }

            var result = writer.writeProviderTerrainOverlay(fixture.chunk(), materialization, claims);

            assertEquals(1, result.solidBlockCount());
            assertEquals(Blocks.STONE.defaultBlockState(), fixture.chunk().getBlockState(fixture.providerPosition()));
        }
    }

    @Test
    void deferredProviderPacketPreservesNativeContentAndAdvancesCursor() throws Exception {
        var fixture = fixture();
        var writer = new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        fixture.chunk().setBlockState(
                fixture.providerPosition(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                false);
        ResourceLocation[] keys = airMaterialization(fixture.chunk());
        put(
                keys,
                fixture.chunk(),
                fixture.providerPosition(),
                ResourceLocation.withDefaultNamespace("stone"));
        var materialization = new MinecraftChunkMaterialization(
                fixture.chunk().getPos(),
                fixture.chunk().getMinBuildHeight(),
                fixture.chunk().getHeight(),
                keys,
                1);
        Map<Long, SkyIslandWorldVolumeId> claims = Map.of(
                fixture.providerPosition().asLong(),
                fixture.volumeId());

        try (AutoCloseable terrain = SkyforgeNeoForge1211SurfaceStage.install(
                        fixture.adapter(),
                        writer);
                AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(
                        fixture.catalog(),
                        Map.of(fixture.volumeId(), Set.of(fixture.chunk().getPos().toLong())))) {
            assertNotNull(terrain);
            assertNotNull(admission);
            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    fixture.chunk(),
                    Optional.empty());
            assertEquals(
                    SkyforgePhysicalVolumeAdmissionState.ADMITTED,
                    SkyforgePhysicalVolumeAdmissionStage.snapshot(fixture.volumeId()).state());

            var advance = writer.writeDeferredSolidOverlayPacket(
                    fixture.chunk(),
                    materialization,
                    SkyforgeNeoForge1211ChunkWriter.DeferredSolidWriteCursor.start(),
                    1,
                    true,
                    claims);

            assertTrue(advance.complete());
            assertEquals(1, advance.assignedSolidWrites());
            assertEquals(0, advance.solidWrites());
            assertEquals(1, advance.cursor().cumulativeAssignedSolidWrites());
            assertEquals(
                    Blocks.GOLD_BLOCK.defaultBlockState(),
                    fixture.chunk().getBlockState(fixture.providerPosition()));
        }
    }

    @Test
    void providerWritePreservesLiveNativeBlockAtClaimedPosition() throws Exception {
        var fixture = fixture();
        var writer = new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        fixture.chunk().setBlockState(
                fixture.providerPosition(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                false);
        ResourceLocation[] keys = airMaterialization(fixture.chunk());
        put(
                keys,
                fixture.chunk(),
                fixture.providerPosition(),
                ResourceLocation.withDefaultNamespace("stone"));
        var materialization = new MinecraftChunkMaterialization(
                fixture.chunk().getPos(),
                fixture.chunk().getMinBuildHeight(),
                fixture.chunk().getHeight(),
                keys,
                1);
        Map<Long, SkyIslandWorldVolumeId> claims = Map.of(
                fixture.providerPosition().asLong(),
                fixture.volumeId());

        try (AutoCloseable terrain = SkyforgeNeoForge1211SurfaceStage.install(
                        fixture.adapter(),
                        writer);
                AutoCloseable admission = SkyforgePhysicalVolumeAdmissionStage.install(
                        fixture.catalog(),
                        Map.of(fixture.volumeId(), Set.of(fixture.chunk().getPos().toLong())))) {
            assertNotNull(terrain);
            assertNotNull(admission);
            SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(
                    fixture.chunk(),
                    Optional.empty());
            assertEquals(
                    SkyforgePhysicalVolumeAdmissionState.ADMITTED,
                    SkyforgePhysicalVolumeAdmissionStage.snapshot(fixture.volumeId()).state());

            var result = writer.writeProviderTerrainOverlay(fixture.chunk(), materialization, claims);

            assertEquals(0, result.solidBlockCount());
            assertEquals(
                    Blocks.GOLD_BLOCK.defaultBlockState(),
                    fixture.chunk().getBlockState(fixture.providerPosition()));
        }
    }

    private static Fixture fixture() {
        var sourceCatalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volume = sourceCatalog.volumes().getFirst();
        SkyIslandWorldCatalog catalog = new SkyIslandWorldCatalog(
                sourceCatalog.rootSeed(),
                java.util.List.of(volume));
        var bounds = volume.bounds();
        int centerX = (int) Math.floor((bounds.minimumX() + bounds.maximumX()) * 0.5);
        int centerZ = (int) Math.floor((bounds.minimumZ() + bounds.maximumZ()) * 0.5);
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(centerX, 16), Math.floorDiv(centerZ, 16));
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        var adapter = new SkyforgeNeoForge1211ChunkAdapter(
                catalog,
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
        int volumeMaximumY = (int) Math.floor(bounds.maximumY());
        BlockPos providerPosition = null;

        for (int localZ = 0; localZ < 16 && providerPosition == null; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int x = chunkPos.getMinBlockX() + localX;
                int z = chunkPos.getMinBlockZ() + localZ;
                var solidRange = adapter.integerSolidRange(volume.id(), x, z);
                if (solidRange.isEmpty()) {
                    continue;
                }
                int topY = solidRange.orElseThrow().maximumY();
                int candidateY = topY + 1;
                if (candidateY <= volumeMaximumY
                        && candidateY < chunk.getMaxBuildHeight()
                        && bounds.contains(x, candidateY, z)) {
                    providerPosition = new BlockPos(x, candidateY, z);
                    break;
                }
            }
        }
        if (providerPosition == null) {
            throw new AssertionError("fixture must contain an island column with top-envelope provider space");
        }
        return new Fixture(catalog, volume.id(), bounds, chunk, adapter, providerPosition);
    }

    private static ResourceLocation[] airMaterialization(ProtoChunk chunk) {
        ResourceLocation[] keys = new ResourceLocation[16 * 16 * chunk.getHeight()];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        return keys;
    }

    private static void put(
            ResourceLocation[] keys,
            ProtoChunk chunk,
            BlockPos position,
            ResourceLocation key) {
        int localX = position.getX() - chunk.getPos().getMinBlockX();
        int localY = position.getY() - chunk.getMinBuildHeight();
        int localZ = position.getZ() - chunk.getPos().getMinBlockZ();
        keys[localX + 16 * (localZ + 16 * localY)] = key;
    }

    private record Fixture(
            SkyIslandWorldCatalog catalog,
            SkyIslandWorldVolumeId volumeId,
            io.github.nidaba.skyforge.world.WorldBounds bounds,
            ProtoChunk chunk,
            SkyforgeNeoForge1211ChunkAdapter adapter,
            BlockPos providerPosition) {}
}
