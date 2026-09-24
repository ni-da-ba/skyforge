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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
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

        int changed = copySurfaceRepresentation(level, chunk, scratch, volumeId, resolver);
        if (changed > 0) {
            chunk.setUnsaved(true);
        }
        markCompleted(level, surfaceKey);
        return new Result(volumeId, surfaceKey.chunkKey(), changed);
    }

    static boolean completed(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            long chunkKey) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        return completed(level, new SurfaceKey(volumeId, chunkKey));
    }

    private static synchronized boolean completed(ServerLevel level, SurfaceKey key) {
        Set<SurfaceKey> completed = COMPLETED.get(level);
        return completed != null && completed.contains(key);
    }

    private static synchronized void markCompleted(ServerLevel level, SurfaceKey key) {
        COMPLETED.computeIfAbsent(level, ignored -> new HashSet<>()).add(key);
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
            ServerLevel level,
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
                boolean gapAbove = false;
                boolean authoredHydrologyGapAbove = false;
                int hydrologyExposureDepth = Integer.MAX_VALUE;
                for (int worldY = range.maximumY(); worldY >= range.minimumY(); worldY--) {
                    cursor.set(worldX, worldY, worldZ);
                    BlockState nativeState = scratch.getBlockState(cursor);
                    if (nativeState.isAir() || !nativeState.getFluidState().isEmpty()) {
                        gapAbove = true;
                        authoredHydrologyGapAbove |= SkyforgeNeoForge1211SurfaceStage
                                .authoredHydrologyPopulationState(volumeId, cursor)
                                .isPresent();
                        hydrologyExposureDepth = Integer.MAX_VALUE;
                        continue;
                    }

                    if (gapAbove) {
                        hydrologyExposureDepth = authoredHydrologyGapAbove
                                ? 0
                                : Integer.MAX_VALUE;
                        gapAbove = false;
                        authoredHydrologyGapAbove = false;
                    } else if (hydrologyExposureDepth != Integer.MAX_VALUE) {
                        hydrologyExposureDepth++;
                    }

                    SkyIslandTerrainSemantic semantic = SkyforgeNeoForge1211SurfaceStage.terrainSemantic(
                                    volumeId, worldX, worldY, worldZ)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during native surface copy"));

                    boolean surfaceRepresentation = semantic == SkyIslandTerrainSemantic.SURFACE_MANTLE
                            || hydrologyExposureDepth < NEWLY_EXPOSED_PROFILE_DEPTH;
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

                    BlockPos currentPosition = cursor.immutable();
                    BlockState liveState = live.getBlockState(currentPosition);
                    if (liveState.isAir() || !liveState.getFluidState().isEmpty()) {
                        continue;
                    }

                    // Hydrology owns occupancy and geometry, not the final material palette.
                    // High suspended islands are far above vanilla sea level, so otherwise-native
                    // surface rules can choose grass/mycelium topsoil even for an authored submerged
                    // bed. Preserve Minecraft material authority but use the native subsurface member
                    // of that same generated profile whenever the block immediately above is authored
                    // water. Registered DiskFeature dressing can then add biome-native sand/clay/
                    // gravel variation instead of starting from a grassy lake floor.
                    boolean submergedBed = SkyforgeNeoForge1211SurfaceStage
                            .authoredHydrologyPopulationState(volumeId, currentPosition.above())
                            .map(SkyforgeAuthoredVisibleHydrologyAdapter::isWaterBearing)
                            .orElse(false);
                    BlockState representationState = submergedBed
                            ? nativeSubmergedState(scratch, currentPosition, nativeState)
                            : nativeState;

                    if (!stableWhenCopied(live, currentPosition, representationState)) {
                        continue;
                    }
                    if (!liveState.equals(representationState)) {
                        BlockPos changedPosition = currentPosition;
                        live.setBlockState(changedPosition, representationState, false);
                        level.getChunkSource().getLightEngine().checkBlock(changedPosition);
                        level.getChunkSource().blockChanged(changedPosition);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static BlockState nativeSubmergedState(
            ProtoChunk scratch,
            BlockPos position,
            BlockState nativeState) {
        if (!nativeState.is(Blocks.GRASS_BLOCK)
                && !nativeState.is(Blocks.MYCELIUM)
                && !nativeState.is(Blocks.PODZOL)) {
            return nativeState;
        }
        for (int depth = 1; depth <= NEWLY_EXPOSED_PROFILE_DEPTH; depth++) {
            BlockState candidate = scratch.getBlockState(position.below(depth));
            if (candidate.isAir() || !candidate.getFluidState().isEmpty()) {
                continue;
            }
            if (!candidate.is(Blocks.GRASS_BLOCK)
                    && !candidate.is(Blocks.MYCELIUM)
                    && !candidate.is(Blocks.PODZOL)) {
                return candidate;
            }
        }
        return nativeState;
    }

    /**
     * Native surface authority may choose gravity-affected sand/gravel, but it may not invalidate
     * authoritative Skyforge occupancy. Preserve the same support invariant as the pre-decoration
     * native-surface adapter: a falling representation is copied only onto a real dry support block.
     */
    private static boolean stableWhenCopied(
            LevelChunk live,
            BlockPos position,
            BlockState nativeState) {
        if (!(nativeState.getBlock() instanceof FallingBlock)) {
            return true;
        }
        if (position.getY() <= live.getMinBuildHeight()) {
            return false;
        }
        BlockState support = live.getBlockState(position.below());
        return !support.isAir() && support.getFluidState().isEmpty();
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

    private record SurfaceKey(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey) {
        private SurfaceKey {
            Objects.requireNonNull(volumeId, "volumeId");
        }
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
