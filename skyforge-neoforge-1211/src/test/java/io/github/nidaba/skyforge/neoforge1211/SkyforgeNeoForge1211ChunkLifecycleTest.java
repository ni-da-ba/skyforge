package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211ChunkLifecycleTest {
    private static final long ROOT_SEED = 0x534b59464f524745L;

    @Test
    void realModLoadsAndNewChunkEventWritesOnlySkyforgeSolidOverlay() throws Exception {
        assertTrue(ModList.get().isLoaded(SkyforgeNeoForge1211Mod.MOD_ID));

        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkAdapter adapter = adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        assertTrue(materialization.solidBlockCount() > 0);
        MaterializedPosition firstSolid = firstSolid(materialization);

        BlockPos nativeSentinel = new BlockPos(0, 0, 0);
        assertEquals(
                SkyforgeMinecraftBlockPalette.AIR,
                materialization.blockKeyAt(0, nativeSentinel.getY(), 0));
        chunk.setBlockState(nativeSentinel, Blocks.GOLD_BLOCK.defaultBlockState(), false);
        BlockPos skyforgeSolidPosition = new BlockPos(
                chunkPos.getMinBlockX() + firstSolid.localX(),
                firstSolid.worldY(),
                chunkPos.getMinBlockZ() + firstSolid.localZ());
        assertTrue(chunk.getBlockState(skyforgeSolidPosition).isAir());

        SkyforgeNeoForge1211ChunkWriter writer =
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver());
        try (AutoCloseable activeBinding = SkyforgeNeoForge1211ChunkLifecycle.install(
                level -> true,
                adapter,
                writer)) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding());

            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
            assertTrue(
                    chunk.getBlockState(skyforgeSolidPosition).isAir(),
                    "existing chunk loads must not trigger Skyforge realization");
            assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), chunk.getBlockState(nativeSentinel));

            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, true));
        }

        assertFalse(SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding());
        assertEquals(
                new MinecraftBlockStateResolver().resolve(firstSolid.blockKey()),
                chunk.getBlockState(skyforgeSolidPosition));
        assertEquals(
                Blocks.GOLD_BLOCK.defaultBlockState(),
                chunk.getBlockState(nativeSentinel),
                "Skyforge AIR must preserve native terrain through the real lifecycle event");
    }

    @Test
    void levelSelectorCanExcludeAChunkWithoutChangingIt() throws Exception {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(chunkPos);
        SkyforgeNeoForge1211ChunkAdapter adapter = adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        MaterializedPosition firstSolid = firstSolid(materialization);
        BlockPos position = new BlockPos(firstSolid.localX(), firstSolid.worldY(), firstSolid.localZ());

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211ChunkLifecycle.install(
                level -> false,
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, true));
        }

        assertTrue(chunk.getBlockState(position).isAir());
    }

    private static MaterializedPosition firstSolid(MinecraftChunkMaterialization materialization) {
        for (int worldY = materialization.minimumY();
                worldY < materialization.minimumY() + materialization.height();
                worldY++) {
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

    private record MaterializedPosition(
            int localX,
            int worldY,
            int localZ,
            ResourceLocation blockKey) {}
}
