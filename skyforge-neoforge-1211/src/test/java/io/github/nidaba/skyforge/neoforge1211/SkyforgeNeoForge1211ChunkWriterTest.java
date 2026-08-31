package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211ChunkWriterTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;
    private static final int MINIMUM_Y = 176;
    private static final int HEIGHT = 144;

    @Test
    void acceptedMaterializationsWriteExactLiveBlockStatesIntoRealProtoChunks() {
        SkyforgeNeoForge1211ChunkAdapter adapter = adapter();
        SkyforgeNeoForge1211ChunkWriter writer =
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        ChunkPos westPos = new ChunkPos(-1, 0);
        ChunkPos eastPos = new ChunkPos(0, 0);
        MinecraftChunkMaterialization westMaterialization = adapter.materialize(westPos, MINIMUM_Y, HEIGHT);
        MinecraftChunkMaterialization eastMaterialization = adapter.materialize(eastPos, MINIMUM_Y, HEIGHT);
        ProtoChunk westChunk = MinecraftTestChunkFactory.protoChunk(westPos);
        ProtoChunk eastChunk = MinecraftTestChunkFactory.protoChunk(eastPos);

        MinecraftChunkWriteResult westResult = writer.write(westChunk, westMaterialization);
        MinecraftChunkWriteResult eastResult = writer.write(eastChunk, eastMaterialization);

        int expectedAssignments = 16 * 16 * HEIGHT;
        assertEquals(expectedAssignments, westResult.assignedBlockCount());
        assertEquals(expectedAssignments, eastResult.assignedBlockCount());
        assertEquals(westMaterialization.solidBlockCount(), westResult.solidBlockCount());
        assertEquals(eastMaterialization.solidBlockCount(), eastResult.solidBlockCount());
        assertEquals(1, westResult.candidateVolumeReferences());
        assertEquals(1, eastResult.candidateVolumeReferences());
        assertTrue(westResult.solidBlockCount() > 0);
        assertTrue(eastResult.solidBlockCount() > 0);

        assertExactStorage(westChunk, westMaterialization);
        assertExactStorage(eastChunk, eastMaterialization);
        assertContinuousAcrossSeam(westChunk, eastChunk);

        assertTrue(westChunk.getBlockState(new BlockPos(-1, MINIMUM_Y - 1, 0)).isAir());
        assertTrue(eastChunk.getBlockState(new BlockPos(0, MINIMUM_Y - 1, 0)).isAir());
    }

    @Test
    void solidOverlayWritesOnlySkyforgeSolidsAndPreservesExistingBackendTerrain() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        int minimumY = 0;
        ResourceLocation[] keys = new ResourceLocation[16 * 16];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        keys[0] = SkyforgeMinecraftBlockPalette.STONE;
        MinecraftChunkMaterialization materialization =
                new MinecraftChunkMaterialization(chunkPos, minimumY, 1, keys, 1);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        BlockPos skyforgeSolid = new BlockPos(0, minimumY, 0);
        BlockPos backendOwnedAirSample = new BlockPos(1, minimumY, 0);
        chunk.setBlockState(backendOwnedAirSample, Blocks.GOLD_BLOCK.defaultBlockState(), false);

        MinecraftChunkWriteResult result = new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver())
                .writeSolidOverlay(chunk, materialization);

        assertEquals(1, result.assignedBlockCount());
        assertEquals(1, result.solidBlockCount());
        assertEquals(Blocks.STONE.defaultBlockState(), chunk.getBlockState(skyforgeSolid));
        assertEquals(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                chunk.getBlockState(backendOwnedAirSample),
                "Skyforge AIR must preserve backend-native terrain in additive mode");
    }

    @Test
    void writerRejectsWrongChunkAndOutOfRangeVerticalIntervals() {
        SkyforgeNeoForge1211ChunkWriter writer =
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        MinecraftChunkMaterialization materialization =
                adapter().materialize(new ChunkPos(0, 0), MINIMUM_Y, 16);

        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(MinecraftTestChunkFactory.protoChunk(new ChunkPos(1, 0)), materialization));

        ResourceLocation[] keys = new ResourceLocation[16 * 16 * 16];
        Arrays.fill(keys, SkyforgeMinecraftBlockPalette.AIR);
        MinecraftChunkMaterialization belowWorld =
                new MinecraftChunkMaterialization(new ChunkPos(0, 0), -80, 16, keys, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0)), belowWorld));
    }

    private static void assertExactStorage(
            ProtoChunk chunk,
            MinecraftChunkMaterialization materialization) {
        int minimumX = materialization.chunkPos().getMinBlockX();
        int minimumZ = materialization.chunkPos().getMinBlockZ();
        for (int worldY = materialization.minimumY();
                worldY < materialization.minimumY() + materialization.height();
                worldY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    var expectedState = BuiltInRegistries.BLOCK.get(key).defaultBlockState();
                    BlockPos position = new BlockPos(minimumX + localX, worldY, minimumZ + localZ);
                    assertEquals(expectedState, chunk.getBlockState(position));
                }
            }
        }
    }

    private static void assertContinuousAcrossSeam(ProtoChunk westChunk, ProtoChunk eastChunk) {
        boolean continuous = false;
        for (int worldY = MINIMUM_Y; worldY < MINIMUM_Y + HEIGHT && !continuous; worldY++) {
            for (int worldZ = 0; worldZ < 16; worldZ++) {
                if (!westChunk.getBlockState(new BlockPos(-1, worldY, worldZ)).isAir()
                        && !eastChunk.getBlockState(new BlockPos(0, worldY, worldZ)).isAir()) {
                    continuous = true;
                    break;
                }
            }
        }
        assertTrue(continuous, "real ProtoChunk storage should preserve the x=-1/0 island seam");
    }

    private static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    private static SkyIslandWorldCatalog catalog() {
        var compiled = compiledMassif();
        var id = new SkyIslandWorldVolumeId(ROOT_SEED, "anchor", 0, 0, ROOT_SEED);
        var worldVolume = new SkyIslandWorldVolume(
                id,
                new WorldBounds(-256.0, 256.0, 100.0, 500.0, -256.0, 256.0),
                compiled);
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(worldVolume));
    }

    private static io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume compiledMassif() {
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                ROOT_SEED,
                0.0,
                0.0,
                320.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
    }
}
