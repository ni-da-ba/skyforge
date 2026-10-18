package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

final class SkyforgeNoiseBasedChunkGeneratorTest {
    @Test
    void fmlRegistersSkyforgeNoiseOverlayGeneratorCodec() {
        assertTrue(ModList.get().isLoaded(SkyforgeNeoForge1211Mod.MOD_ID));

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                SkyforgeNeoForge1211Mod.MOD_ID,
                SkyforgeNeoForge1211ChunkGenerators.NOISE_OVERLAY_NAME);
        assertSame(
                SkyforgeNoiseBasedChunkGenerator.CODEC,
                BuiltInRegistries.CHUNK_GENERATOR.get(id));
    }

    @Test
    void explicitVoidBaseProducesAirAndHasNoBaseHeightOutsideIslandScope() {
        var settings = BuiltInRegistries.NOISE_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var plains = BuiltInRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS);
        var generator = new SkyforgeNoiseBasedChunkGenerator(
                new FixedBiomeSource(plains),
                settings,
                Optional.of(settings),
                true);
        var chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));

        assertSame(chunk, generator.fillFromNoise(null, null, null, chunk).join());
        assertEquals(
                chunk.getMinBuildHeight(),
                generator.getBaseHeight(
                        0,
                        0,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        chunk,
                        null));
        assertTrue(generator.getBaseColumn(0, 0, chunk, null).getBlock(0).isAir());
        assertTrue(chunk.getBlockState(new net.minecraft.core.BlockPos(0, 64, 0)).isAir());
    }

    @Test
    void vanillaProviderSnapshotSamplesHeightFromIndependentGenerator() {
        var settings = BuiltInRegistries.NOISE_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var plains = BuiltInRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS);
        NoiseBasedChunkGenerator provider = new NoiseBasedChunkGenerator(new FixedBiomeSource(plains), settings);
        RandomState providerRandomState = RandomState.create(
                settings.value(),
                BuiltInRegistries.NOISE,
                817304L);
        var chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));

        var snapshot = MinecraftNativeSurfaceSnapshot.capture(chunk, provider, providerRandomState);

        assertEquals(chunk.getPos(), snapshot.chunkPos());
        assertEquals(provider.getSeaLevel(), snapshot.baselineY());
        assertTrue(snapshot.surface(8, 8).isPresent());
    }
}
