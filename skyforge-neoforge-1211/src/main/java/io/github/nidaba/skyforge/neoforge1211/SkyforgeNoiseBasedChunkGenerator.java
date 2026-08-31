package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Vanilla noise generator with supported Skyforge post-surface and supplemental feature seams.
 *
 * <p>All vanilla noise, surface and biome-decoration behavior is retained. Skyforge's post-surface
 * binding remains inert unless an already-compiled runtime is installed explicitly, and the
 * additional-surface feature scope exists only while vanilla decorates one chunk.
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

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        try (SkyforgeNeoForge1211FeatureStage.Scope scope = SkyforgeNeoForge1211FeatureStage.open(chunk)) {
            scope.requireActive();
            super.applyBiomeDecoration(level, chunk, structureManager);
        }
    }
}
