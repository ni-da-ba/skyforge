package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;

/** Minimal real-Minecraft chunk fixture used only by NeoForge adapter integration tests. */
final class MinecraftTestChunkFactory {
    private static final int MINIMUM_BUILD_Y = -64;
    private static final int BUILD_HEIGHT = 384;
    private static final Registry<Biome> BIOMES = createBiomeRegistry();

    private MinecraftTestChunkFactory() {}

    static ProtoChunk protoChunk(ChunkPos chunkPos) {
        return new ProtoChunk(
                chunkPos,
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(MINIMUM_BUILD_Y, BUILD_HEIGHT),
                BIOMES,
                null);
    }

    private static Registry<Biome> createBiomeRegistry() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        // LevelChunkSection initializes its biome palette from Biomes.PLAINS. The chunk fixture is
        // testing real section/block storage rather than biome generation, so bind the synthetic
        // test biome to that required vanilla key instead of inventing a key the section cannot use.
        registry.register(Biomes.PLAINS, createBiome(), RegistrationInfo.BUILT_IN);
        return registry.freeze();
    }

    private static Biome createBiome() {
        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .fogColor(0)
                .waterColor(0)
                .waterFogColor(0)
                .skyColor(0)
                .build();
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(effects)
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }
}
