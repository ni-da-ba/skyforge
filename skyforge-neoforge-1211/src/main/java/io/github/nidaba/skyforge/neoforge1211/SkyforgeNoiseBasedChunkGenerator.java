package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Vanilla noise generator with one supported Skyforge insertion immediately after surface build.
 *
 * <p>All vanilla noise and surface behavior is retained. The post-surface binding is deliberately
 * inert unless an already-compiled Skyforge runtime is installed explicitly.
 */
public final class SkyforgeNoiseBasedChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<SkyforgeNoiseBasedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings))
                    .apply(instance, instance.stable(SkyforgeNoiseBasedChunkGenerator::new)));

    private final Holder<NoiseGeneratorSettings> settings;

    public SkyforgeNoiseBasedChunkGenerator(
            BiomeSource biomeSource,
            Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            RandomState random,
            ChunkAccess chunk) {
        super.buildSurface(level, structureManager, random, chunk);
        SkyforgeNeoForge1211SurfaceStage.realize(chunk);
    }
}
