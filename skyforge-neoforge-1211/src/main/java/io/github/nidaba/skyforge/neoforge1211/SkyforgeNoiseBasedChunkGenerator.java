package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashMap;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Vanilla noise generator with supported Skyforge early-query, structure-admission, post-surface
 * and supplemental feature seams.
 *
 * <p>All vanilla structure selection, noise, surface and biome-decoration behavior is retained.
 * Skyforge intervenes only when its early height answer actually elevates a native structure
 * candidate above vanilla terrain.
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
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types type,
            LevelHeightAccessor level,
            RandomState random) {
        int vanillaHeight = super.getBaseHeight(x, z, type, level, random);
        var skyforgeClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                x,
                z,
                type,
                level.getMinBuildHeight(),
                level.getHeight());
        if (skyforgeClaim.isEmpty()) {
            return vanillaHeight;
        }

        MinecraftSkyforgeHeightClaim claim = skyforgeClaim.orElseThrow();
        if (claim.height() > vanillaHeight) {
            SkyforgeStructureCandidateStage.record(claim);
            return claim.height();
        }
        return vanillaHeight;
    }

    /**
     * Wraps one vanilla structure candidate after NeoForge widens the otherwise-private method.
     *
     * <p>Vanilla still chooses structure sets, weights and alternatives. Skyforge only rejects a
     * generated start when the candidate depended on an elevated Skyforge height and the real
     * native bounding-box footprint fails the accepted backend-neutral support evaluator.
     */
    @Override
    protected boolean tryGenerateStructure(
            StructureSet.StructureSelectionEntry structureSelectionEntry,
            StructureManager structureManager,
            RegistryAccess registryAccess,
            RandomState random,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkAccess chunk,
            ChunkPos chunkPos,
            SectionPos sectionPos) {
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            return super.tryGenerateStructure(
                    structureSelectionEntry,
                    structureManager,
                    registryAccess,
                    random,
                    structureTemplateManager,
                    seed,
                    chunk,
                    chunkPos,
                    sectionPos);
        }

        Structure structure = structureSelectionEntry.structure().value();
        var previousStarts = new HashMap<>(chunk.getAllStarts());
        boolean generated;
        Set<SkyIslandWorldVolumeId> claimedVolumeIds;
        try (SkyforgeStructureCandidateStage.Scope scope = SkyforgeStructureCandidateStage.open()) {
            generated = super.tryGenerateStructure(
                    structureSelectionEntry,
                    structureManager,
                    registryAccess,
                    random,
                    structureTemplateManager,
                    seed,
                    chunk,
                    chunkPos,
                    sectionPos);
            claimedVolumeIds = scope.claimedVolumeIds();
        }

        if (!generated || claimedVolumeIds.isEmpty()) {
            return generated;
        }
        if (claimedVolumeIds.size() != 1) {
            chunk.setAllStarts(previousStarts);
            return false;
        }

        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            chunk.setAllStarts(previousStarts);
            return false;
        }

        SkyIslandWorldVolumeId claimedVolumeId = claimedVolumeIds.iterator().next();
        var requirements = MinecraftStructureSupportPolicy.requirements(start.getBoundingBox());
        boolean accepted = SkyforgeNeoForge1211SurfaceStage.assessSurfaceSupport(requirements)
                .orElseThrow(() -> new IllegalStateException("active Skyforge binding disappeared during structure generation"))
                .stream()
                .filter(assessment -> assessment.supportingVolumeId().equals(claimedVolumeId))
                .findFirst()
                .map(assessment -> assessment.accepted())
                .orElse(false);
        if (accepted) {
            return true;
        }

        chunk.setAllStarts(previousStarts);
        return false;
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
