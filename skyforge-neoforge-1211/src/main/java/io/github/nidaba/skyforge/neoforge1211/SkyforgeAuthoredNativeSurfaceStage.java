package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainSemantic;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Applies Minecraft-native surface rules to one exact Skyforge island using that island's authored
 * ecology as the biome authority.
 *
 * <p>The stage never runs SurfaceSystem on the live world chunk. It constructs an isolated scratch
 * ProtoChunk containing only this exact volume's owned terrain. Authored hydrology AIR/fluid cells
 * are preserved in the scratch shape; all remaining solids are normalized to Minecraft's ordinary
 * overworld default block before native surface evaluation. Only material representation is copied
 * back, never occupancy.
 *
 * <p>The biome manager delegates every quart lookup to the same exact-volume resolver later reused
 * by native surface population and persistent biome presentation. Biome transitions therefore arise
 * only from Skyforge's authored ecology/hydrology fields, never from unrelated terrain beneath the
 * suspended island.
 */
final class SkyforgeAuthoredNativeSurfaceStage {
    private static final int NEWLY_EXPOSED_PROFILE_DEPTH = 3;
    private static final WeakHashMap<ServerLevel, Evaluator> EVALUATORS = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, Set<SurfaceKey>> COMPLETED = new WeakHashMap<>();

    private SkyforgeAuthoredNativeSurfaceStage() {}

    static Result apply(
            ServerLevel level,
            LevelChunk chunk,
            ChunkGenerator liveGenerator,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(liveGenerator, "liveGenerator");
        Objects.requireNonNull(volumeId, "volumeId");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("authored native-surface chunk belongs to another level");
        }

        SurfaceKey surfaceKey = new SurfaceKey(volumeId, chunk.getPos().toLong());
        if (completed(level, surfaceKey)) {
            return new Result(volumeId, surfaceKey.chunkKey(), 0);
        }

        var plan = SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId)
                .orElseThrow(() -> new IllegalStateException(
                        "authored native surface requires the exact-volume surface ecology plan"));
        SkyforgeExactVolumeBiomeResolver resolver = plan.biomeResolver();

        Evaluator evaluator = evaluator(level, liveGenerator);
        Registry<Biome> biomeRegistry = evaluator.biomeRegistry();
        ProtoChunk scratch = new ProtoChunk(
                chunk.getPos(),
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(chunk.getMinBuildHeight(), chunk.getHeight()),
                biomeRegistry,
                null);

        BlockState defaultBlock = evaluator.defaultBlock();
        seedExactVolumeScratch(chunk, scratch, volumeId, defaultBlock);

        BiomeManager islandBiomeManager = level.getBiomeManager().withDifferentSource(
                (quartX, quartY, quartZ) -> resolveBiome(
                        biomeRegistry,
                        resolver,
                        volumeId,
                        quartBlockCenter(quartX),
                        quartBlockCenter(quartY),
                        quartBlockCenter(quartZ)));

        evaluator.generator().buildSurface(
                scratch,
                new WorldGenerationContext(evaluator.generator(), level),
                evaluator.randomState(),
                level.structureManager(),
                islandBiomeManager,
                biomeRegistry,
                Blender.empty());

        int changed = copySurfaceRepresentation(chunk, scratch, volumeId, resolver);
        if (changed > 0) {
            chunk.setUnsaved(true);
        }
        return new Result(volumeId, chunk.getPos().toLong(), changed);
    }

    private static void seedExactVolumeScratch(
            LevelChunk live,
            ProtoChunk scratch,
            SkyIslandWorldVolumeId volumeId,
            BlockState defaultBlock) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minimumX = live.getPos().getMinBlockX();
        int minimumZ = live.getPos().getMinBlockZ();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = minimumZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = minimumX + localX;
                var optionalRange =
                        SkyforgeNeoForge1211SurfaceStage.integerSolidRange(volumeId, worldX, worldZ);
                if (optionalRange.isEmpty()) {
                    continue;
                }
                var range = optionalRange.orElseThrow();
                for (int worldY = range.minimumY(); worldY <= range.maximumY(); worldY++) {
                    boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volumeId, worldX, worldY, worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during native surfacing"));
                    if (!owned) {
                        continue;
                    }
                    cursor.set(worldX, worldY, worldZ);
                    BlockState liveState = live.getBlockState(cursor);
                    if (liveState.isAir()) {
                        continue;
                    }
                    if (!liveState.getFluidState().isEmpty()) {
                        scratch.setBlockState(cursor, liveState, false);
                        continue;
                    }
                    scratch.setBlockState(cursor, defaultBlock, false);
                }
            }
        }
    }

    private static int copySurfaceRepresentation(
            LevelChunk live,
            ProtoChunk scratch,
            SkyIslandWorldVolumeId volumeId,
            SkyforgeExactVolumeBiomeResolver resolver) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        int minimumX = live.getPos().getMinBlockX();
        int minimumZ = live.getPos().getMinBlockZ();

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = minimumZ + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = minimumX + localX;
                var optionalRange =
                        SkyforgeNeoForge1211SurfaceStage.integerSolidRange(volumeId, worldX, worldZ);
                if (optionalRange.isEmpty()) {
                    continue;
                }

                var range = optionalRange.orElseThrow();
                int solidDepthFromExposure = Integer.MAX_VALUE;
                for (int worldY = range.maximumY(); worldY >= range.minimumY(); worldY--) {
                    cursor.set(worldX, worldY, worldZ);
                    BlockState nativeState = scratch.getBlockState(cursor);
                    if (nativeState.isAir() || !nativeState.getFluidState().isEmpty()) {
                        solidDepthFromExposure = -1;
                        continue;
                    }

                    if (solidDepthFromExposure == Integer.MAX_VALUE || solidDepthFromExposure < 0) {
                        solidDepthFromExposure = 0;
                    } else {
                        solidDepthFromExposure++;
                    }

                    SkyIslandTerrainSemantic semantic = SkyforgeNeoForge1211SurfaceStage.terrainSemantic(
                                    volumeId, worldX, worldY, worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during native surface copy"));

                    boolean surfaceRepresentation = semantic == SkyIslandTerrainSemantic.SURFACE_MANTLE
                            || solidDepthFromExposure < NEWLY_EXPOSED_PROFILE_DEPTH;
                    if (!surfaceRepresentation
                            || !resolver.supportsSurface(volumeId, worldX, worldY, worldZ)) {
                        continue;
                    }

                    boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volumeId, worldX, worldY, worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during native surface copy"));
                    if (!owned) {
                        continue;
                    }

                    BlockState liveState = live.getBlockState(cursor);
                    if (liveState.isAir() || !liveState.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (!liveState.equals(nativeState)) {
                        live.setBlockState(cursor, nativeState, false);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static synchronized Evaluator evaluator(
            ServerLevel level,
            ChunkGenerator liveGenerator) {
        Evaluator existing = EVALUATORS.get(level);
        if (existing != null) {
            return existing;
        }

        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder<NoiseGeneratorSettings> overworldSettings = level.registryAccess()
                .registryOrThrow(Registries.NOISE_SETTINGS)
                .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        RandomState randomState = RandomState.create(
                overworldSettings.value(),
                noises,
                level.getSeed());
        Evaluator created = new Evaluator(
                new NoiseBasedChunkGenerator(liveGenerator.getBiomeSource(), overworldSettings),
                randomState,
                biomeRegistry,
                overworldSettings.value().defaultBlock());
        EVALUATORS.put(level, created);
        return created;
    }

    private static Holder<Biome> resolveBiome(
            Registry<Biome> registry,
            SkyforgeExactVolumeBiomeResolver resolver,
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        var key = Objects.requireNonNull(
                resolver.resolve(volumeId, worldX, worldY, worldZ),
                "authored native-surface biome resolver returned null");
        return registry.getHolder(key)
                .orElseThrow(() -> new IllegalStateException(
                        "authored native-surface biome is absent from final registry: "
                                + key.location()));
    }

    private static int quartBlockCenter(int quart) {
        return Math.addExact(Math.multiplyExact(quart, 4), 2);
    }

    private record Evaluator(
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            Registry<Biome> biomeRegistry,
            BlockState defaultBlock) {
        private Evaluator {
            Objects.requireNonNull(generator, "generator");
            Objects.requireNonNull(randomState, "randomState");
            Objects.requireNonNull(biomeRegistry, "biomeRegistry");
            Objects.requireNonNull(defaultBlock, "defaultBlock");
        }
    }

    record Result(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            int changedBlocks) {
        Result {
            Objects.requireNonNull(volumeId, "volumeId");
            if (changedBlocks < 0) {
                throw new IllegalArgumentException("native surface changedBlocks must be nonnegative");
            }
        }
    }
}
