package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Immutable native terrain-top representation and bounded near-surface strata captured after
 * surface construction and before biome decoration.
 *
 * <p>This snapshot is deliberately not a worldgen height authority. It exists only so a later
 * Skyforge realization can inherit Minecraft's already-decided surface material profile without
 * mistaking post-surface vegetation or structures for terrain.
 */
final class MinecraftNativeSurfaceSnapshot {
    private static final int CHUNK_WIDTH = 16;
    private static final int SAMPLE_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;
    static final int SURFACE_STRATA_DEPTH = 4;
    private static final int PROFILE_FILTER_RADIUS = 5;
    private static final int PROFILE_SAMPLE_HALO_RADIUS = PROFILE_FILTER_RADIUS;
    private static final int PROFILE_FILTER_WIDTH = CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS * 2;
    static final int PROVIDER_HEIGHT_HALO_SAMPLE_COUNT = PROFILE_FILTER_WIDTH * PROFILE_FILTER_WIDTH;
    private static final Map<ProviderDatumKey, Integer> PROVIDER_TERRAIN_DATUMS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ProviderIslandDatumKey, Integer> PROVIDER_ISLAND_TERRAIN_DATUMS =
            new ConcurrentHashMap<>();
    // The 11x11 smoothing kernel needs at most a one-chunk border. Cache immutable provider
    // surface tiles so neighboring halos reuse chunk-level generation instead of thousands of
    // expensive per-column NoiseBasedChunkGenerator.getBaseHeight calls.
    private static final int MAX_CACHED_PROVIDER_SURFACE_CHUNKS = 512;
    private static final ConcurrentHashMap<ProviderSurfaceChunkKey, MinecraftNativeSurfaceSnapshot>
            PROVIDER_SURFACE_CHUNKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean TRIMMING_PROVIDER_SURFACE_CHUNKS = new AtomicBoolean();
    private static final int MAX_CACHED_PROVIDER_HEIGHT_CHUNKS = 1_024;
    private static final ConcurrentHashMap<ProviderSurfaceChunkKey, int[]>
            PROVIDER_HEIGHT_CHUNKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean TRIMMING_PROVIDER_HEIGHT_CHUNKS = new AtomicBoolean();

    private final ChunkPos chunkPos;
    private final int baselineY;
    private final int[] worldYs;
    private final ResourceLocation[] blockKeys;
    private final ResourceLocation[][] surfaceStrata;
    private final int[] providerHeightHalo;
    private final Map<SkyIslandWorldVolumeId, Integer> providerReliefDatums;

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int baselineY,
            int[] worldYs,
            ResourceLocation[] blockKeys,
            ResourceLocation[][] surfaceStrata) {
        this(chunkPos, baselineY, worldYs, blockKeys, surfaceStrata, edgePaddedProviderHeightHalo(worldYs));
    }

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int baselineY,
            int[] worldYs,
            ResourceLocation[] blockKeys,
            ResourceLocation[][] surfaceStrata,
            int[] providerHeightHalo) {
        this(chunkPos, baselineY, worldYs, blockKeys, surfaceStrata, providerHeightHalo, Map.of());
    }

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int baselineY,
            int[] worldYs,
            ResourceLocation[] blockKeys,
            ResourceLocation[][] surfaceStrata,
            int[] providerHeightHalo,
            Map<SkyIslandWorldVolumeId, Integer> providerReliefDatums) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.baselineY = baselineY;
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface snapshot must contain exactly 256 columns");
        }
        if (surfaceStrata.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface strata must contain exactly 256 columns");
        }
        if (providerHeightHalo.length != PROVIDER_HEIGHT_HALO_SAMPLE_COUNT) {
            throw new IllegalArgumentException("provider height halo must include the chunk and the profile-filter border");
        }
        this.worldYs = Arrays.copyOf(worldYs, worldYs.length);
        this.blockKeys = Arrays.copyOf(blockKeys, blockKeys.length);
        this.surfaceStrata = new ResourceLocation[SAMPLE_COUNT][SURFACE_STRATA_DEPTH];
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            if (surfaceStrata[index].length != SURFACE_STRATA_DEPTH) {
                throw new IllegalArgumentException("native surface strata column has an unexpected depth");
            }
            this.surfaceStrata[index] = Arrays.copyOf(surfaceStrata[index], SURFACE_STRATA_DEPTH);
        }
        this.providerHeightHalo = Arrays.copyOf(providerHeightHalo, providerHeightHalo.length);
        this.providerReliefDatums = Map.copyOf(
                Objects.requireNonNull(providerReliefDatums, "providerReliefDatums"));
    }

    static MinecraftNativeSurfaceSnapshot capture(
            ChunkAccess chunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(randomState, "randomState");
        int baselineY = providerTerrainDatumY(generator, chunk, randomState);
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT];
        ResourceLocation[][] surfaceStrata = emptySurfaceStrata();
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                NoiseColumn column = generator.getBaseColumn(worldX, worldZ, chunk, randomState);
                int index = index(localX, localZ);
                int layer = 0;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight() && layer < SURFACE_STRATA_DEPTH;
                        worldY--) {
                    BlockState state = column.getBlock(worldY);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        if (layer > 0) {
                            break;
                        }
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        if (layer > 0) {
                            break;
                        }
                        continue;
                    }
                    if (layer == 0) {
                        worldYs[index] = worldY;
                        blockKeys[index] = key;
                    }
                    surfaceStrata[index][layer++] = key;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys, surfaceStrata);
    }

    /**
     * Captures the selected provider's actual surface-rule result without exposing temporary
     * provider terrain to the live chunk region.
     *
     * <p>The provider's chunk-level density fill and {@code buildSurface} pipeline run against a
     * detached in-memory chunk using the live worldgen biome, structure, seed, and noise context.
     * Only the immutable top-column profile escapes.
     */
    static MinecraftNativeSurfaceSnapshot captureProviderSurface(
            WorldGenRegion level,
            StructureManager structureManager,
            ChunkAccess targetChunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            List<SkyIslandWorldVolume> candidateVolumes) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(structureManager, "structureManager");
        Objects.requireNonNull(targetChunk, "targetChunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(randomState, "randomState");
        Objects.requireNonNull(candidateVolumes, "candidateVolumes");

        long stageStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftNativeSurfaceSnapshot captured = cachedProviderSurfaceChunk(
                level, structureManager, targetChunk.getPos(), generator, randomState);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.surfaceChunkLookup", stageStart);
        stageStart = SkyforgeRuntimePerformanceMetrics.start();
        Map<SkyIslandWorldVolumeId, Integer> reliefDatums = new HashMap<>();
        for (SkyIslandWorldVolume volume : candidateVolumes) {
            reliefDatums.put(
                    volume.id(),
                    providerIslandTerrainDatumY(level, generator, randomState, volume));
        }
        MinecraftNativeSurfaceSnapshot withHalo =
                captured.withProviderHeightHalo(level, structureManager, generator, randomState, reliefDatums);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.heightHalo", stageStart);
        return withHalo;
    }

    private MinecraftNativeSurfaceSnapshot withProviderHeightHalo(
            WorldGenRegion level,
            StructureManager structureManager,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            Map<SkyIslandWorldVolumeId, Integer> providerReliefDatums) {
        int[] heightHalo = sampleProviderHeightHalo(
                chunkPos,
                (worldX, worldZ) -> cachedProviderSurfaceHeight(
                        level, structureManager, generator, randomState, worldX, worldZ));
        return new MinecraftNativeSurfaceSnapshot(
                chunkPos,
                baselineY,
                worldYs,
                blockKeys,
                surfaceStrata,
                heightHalo,
                providerReliefDatums);
    }

    /** Samples the complete filter window through one world-coordinate terrain source. */
    static int[] sampleProviderHeightHalo(
            ChunkPos chunkPos,
            ProviderSurfaceHeightSampler sampler) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(sampler, "sampler");
        int[] halo = new int[PROVIDER_HEIGHT_HALO_SAMPLE_COUNT];
        Arrays.fill(halo, Integer.MIN_VALUE);
        for (int localZ = -PROFILE_SAMPLE_HALO_RADIUS; localZ < CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS; localZ++) {
            for (int localX = -PROFILE_SAMPLE_HALO_RADIUS; localX < CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                halo[profileHeightIndex(localX, localZ)] = sampler.surfaceY(worldX, worldZ);
            }
        }
        return halo;
    }

    @FunctionalInterface
    interface ProviderSurfaceHeightSampler {
        int surfaceY(int worldX, int worldZ);
    }

    private static int[] edgePaddedProviderHeightHalo(int[] worldYs) {
        if (worldYs.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("provider profile must contain exactly 256 chunk columns");
        }
        int[] halo = new int[PROVIDER_HEIGHT_HALO_SAMPLE_COUNT];
        Arrays.fill(halo, Integer.MIN_VALUE);
        for (int localZ = -PROFILE_SAMPLE_HALO_RADIUS; localZ < CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS; localZ++) {
            for (int localX = -PROFILE_SAMPLE_HALO_RADIUS; localX < CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS; localX++) {
                int clampedX = Math.max(0, Math.min(CHUNK_WIDTH - 1, localX));
                int clampedZ = Math.max(0, Math.min(CHUNK_WIDTH - 1, localZ));
                halo[profileHeightIndex(localX, localZ)] = worldYs[index(clampedX, clampedZ)];
            }
        }
        return halo;
    }

    private static int firstSolidSurfaceY(NoiseColumn column, int minimumY, int maximumY) {
        for (int worldY = maximumY - 1; worldY >= minimumY; worldY--) {
            BlockState state = column.getBlock(worldY);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                return worldY;
            }
        }
        return Integer.MIN_VALUE;
    }

    static int profileHeightIndex(int localX, int localZ) {
        return localX + PROFILE_SAMPLE_HALO_RADIUS
                + PROFILE_FILTER_WIDTH * (localZ + PROFILE_SAMPLE_HALO_RADIUS);
    }

    /** Uses a chunk-seam-safe weighted neighborhood to soften sharp provider relief transitions. */
    int smoothedProviderSurfaceY(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        return smoothedProviderSurfaceY(providerHeightHalo, localX, localZ);
    }

    static int smoothedProviderSurfaceY(int[] providerHeightHalo, int localX, int localZ) {
        int weightedHeight = 0;
        int totalWeight = 0;
        int sampleCount = 0;
        for (int deltaZ = -PROFILE_FILTER_RADIUS; deltaZ <= PROFILE_FILTER_RADIUS; deltaZ++) {
            for (int deltaX = -PROFILE_FILTER_RADIUS; deltaX <= PROFILE_FILTER_RADIUS; deltaX++) {
                int height = providerHeightHalo[profileHeightIndex(localX + deltaX, localZ + deltaZ)];
                if (height == Integer.MIN_VALUE) {
                    continue;
                }
                // A triangular 11x11 kernel softens block-scale ledges while preserving broad
                // mountain forms. Every sample comes from the same world-coordinate terrain source.
                int axisWeightX = PROFILE_FILTER_RADIUS + 1 - Math.abs(deltaX);
                int axisWeightZ = PROFILE_FILTER_RADIUS + 1 - Math.abs(deltaZ);
                int weight = axisWeightX * axisWeightZ;
                weightedHeight += height * weight;
                totalWeight += weight;
                sampleCount++;
            }
        }
        int centerHeight = providerHeightHalo[profileHeightIndex(localX, localZ)];
        if (sampleCount < 5) {
            // Sparse/no-surface neighborhoods are common in fixtures and must not invent a
            // smoothed value from a handful of unrelated columns.
            return centerHeight;
        }
        return (int) Math.round(weightedHeight / (double) totalWeight);
    }

    /** Uses a deterministic base-column neighborhood at each island's anchor as its local datum. */
    private static int providerIslandTerrainDatumY(
            WorldGenRegion level,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            SkyIslandWorldVolume volume) {
        SkyIslandWorldVolumeId volumeId = volume.id();
        return PROVIDER_ISLAND_TERRAIN_DATUMS.computeIfAbsent(
                new ProviderIslandDatumKey(generator, randomState, volumeId),
                ignored -> {
                    var bounds = volume.bounds();
                    int centerX = (int) Math.floor((bounds.minimumX() + bounds.maximumX()) * 0.5d);
                    int centerZ = (int) Math.floor((bounds.minimumZ() + bounds.maximumZ()) * 0.5d);
                    int[] offsets = {-4, 0, 4};
                    int[] heights = new int[offsets.length * offsets.length];
                    int sample = 0;
                    for (int deltaZ : offsets) {
                        for (int deltaX : offsets) {
                            NoiseColumn column = generator.getBaseColumn(
                                    centerX + deltaX, centerZ + deltaZ, level, randomState);
                            heights[sample++] = firstSolidSurfaceY(
                                    column, level.getMinBuildHeight(), level.getMaxBuildHeight());
                        }
                    }
                    return medianProviderTerrainDatumY(heights, generator.getSeaLevel());
                });
    }

    static int medianProviderTerrainDatumY(int[] providerHeights, int fallbackY) {
        Objects.requireNonNull(providerHeights, "providerHeights");
        int[] samples = Arrays.stream(providerHeights)
                .filter(height -> height != Integer.MIN_VALUE)
                .toArray();
        if (samples.length == 0) {
            return fallbackY;
        }
        Arrays.sort(samples);
        int upper = samples.length / 2;
        if ((samples.length & 1) == 1) {
            return samples[upper];
        }
        return (int) Math.round((samples[upper - 1] + (double) samples[upper]) * 0.5d);
    }

    /** Uses a cached, world-fixed minimum origin profile as the provider-relative datum. */
    private static int providerTerrainDatumY(
            NoiseBasedChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            RandomState randomState) {
        return PROVIDER_TERRAIN_DATUMS.computeIfAbsent(
                new ProviderDatumKey(generator, randomState),
                ignored -> calculateProviderTerrainDatumY(generator, level, randomState));
    }

    private static int calculateProviderTerrainDatumY(
            NoiseBasedChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            RandomState randomState) {
        int[] originChunkSurfaceYs = new int[SAMPLE_COUNT];
        int originChunkSamples = 0;
        for (int worldZ = 0; worldZ < CHUNK_WIDTH; worldZ++) {
            for (int worldX = 0; worldX < CHUNK_WIDTH; worldX++) {
                NoiseColumn column = generator.getBaseColumn(worldX, worldZ, level, randomState);
                for (int worldY = level.getMaxBuildHeight() - 1;
                        worldY >= level.getMinBuildHeight();
                        worldY--) {
                    BlockState state = column.getBlock(worldY);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        originChunkSurfaceYs[originChunkSamples++] = worldY;
                        break;
                    }
                }
            }
        }
        if (originChunkSamples == 0) {
            return generator.getSeaLevel();
        }
        Arrays.sort(originChunkSurfaceYs, 0, originChunkSamples);
        // Keep the fixed origin profile's minimum as a zero datum. This lets the provider's
        // sampled relief lift the authored cap while bounding low-source outliers with the
        // adapter's 64-block rise limit.
        return originChunkSurfaceYs[0];
    }

    private static MinecraftNativeSurfaceSnapshot cachedProviderSurfaceChunk(
            WorldGenRegion level,
            StructureManager structureManager,
            ChunkPos chunkPos,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        ProviderSurfaceChunkKey key = providerSurfaceChunkKey(level, chunkPos, generator, randomState);
        java.util.concurrent.atomic.AtomicBoolean created = new java.util.concurrent.atomic.AtomicBoolean();
        MinecraftNativeSurfaceSnapshot captured = PROVIDER_SURFACE_CHUNKS.computeIfAbsent(
                key,
                ignored -> {
                    created.set(true);
                    return generateProviderSurfaceChunk(
                            level, structureManager, chunkPos, generator, randomState);
                });
        SkyforgeRuntimePerformanceMetrics.recordSample(
                created.get()
                        ? "terrain.providerSnapshot.surfaceChunkCacheMisses"
                        : "terrain.providerSnapshot.surfaceChunkCacheHits",
                1);
        PROVIDER_HEIGHT_CHUNKS.putIfAbsent(key, captured.copySurfaceHeights());
        trimProviderHeightChunks();
        if (created.get()) {
            trimProviderSurfaceChunks();
        }
        return captured;
    }

    private static MinecraftNativeSurfaceSnapshot generateProviderSurfaceChunk(
            WorldGenRegion level,
            StructureManager structureManager,
            ChunkPos chunkPos,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        ProtoChunk providerChunk = detachedProviderChunk(level, chunkPos);
        Blender blender = Blender.of(level);
        long stageStart = SkyforgeRuntimePerformanceMetrics.start();
        generator.createBiomes(randomState, blender, structureManager, providerChunk).join();
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.createBiomes", stageStart);
        stageStart = SkyforgeRuntimePerformanceMetrics.start();
        generator.fillFromNoise(blender, randomState, structureManager, providerChunk).join();
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.fillFromNoise", stageStart);
        stageStart = SkyforgeRuntimePerformanceMetrics.start();
        generator.buildSurface(level, structureManager, randomState, providerChunk);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.buildSurface", stageStart);
        stageStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftNativeSurfaceSnapshot captured = capture(
                providerChunk,
                providerTerrainDatumY(generator, level, randomState));
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.captureSurface", stageStart);
        return captured;
    }

    private static int cachedProviderSurfaceHeight(
            WorldGenRegion level,
            StructureManager structureManager,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            int worldX,
            int worldZ) {
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "terrain.providerSnapshot.providerSurfaceHeightSamples",
                1);
        ChunkPos sampleChunk = new ChunkPos(worldX >> 4, worldZ >> 4);
        ProviderSurfaceChunkKey key = providerSurfaceChunkKey(
                level, sampleChunk, generator, randomState);
        int[] heights = PROVIDER_HEIGHT_CHUNKS.get(key);
        boolean created = false;
        if (heights == null) {
            AtomicBoolean generated = new AtomicBoolean();
            heights = PROVIDER_HEIGHT_CHUNKS.computeIfAbsent(
                    key,
                    ignored -> {
                        generated.set(true);
                        return generateProviderHeightChunk(
                                level, structureManager, sampleChunk, generator, randomState);
                    });
            created = generated.get();
            if (created) {
                trimProviderHeightChunks();
            }
        }
        SkyforgeRuntimePerformanceMetrics.recordSample(
                created
                        ? "terrain.providerSnapshot.heightTileCacheMisses"
                        : "terrain.providerSnapshot.heightTileCacheHits",
                1);
        return heights[(worldX & 15) + CHUNK_WIDTH * (worldZ & 15)];
    }

    private static int[] generateProviderHeightChunk(
            WorldGenRegion level,
            StructureManager structureManager,
            ChunkPos chunkPos,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        ProtoChunk providerChunk = detachedProviderChunk(level, chunkPos);
        long stageStart = SkyforgeRuntimePerformanceMetrics.start();
        generator.fillFromNoise(
                        Blender.of(level), randomState, structureManager, providerChunk)
                .join();
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.fillFromNoise", stageStart);
        stageStart = SkyforgeRuntimePerformanceMetrics.start();
        int[] heights = captureProviderSurfaceHeights(providerChunk);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.providerSnapshot.captureHeightTile", stageStart);
        return heights;
    }

    private static int[] captureProviderSurfaceHeights(ChunkAccess chunk) {
        int[] heights = new int[SAMPLE_COUNT];
        Arrays.fill(heights, Integer.MIN_VALUE);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maximumY = chunk.getMinBuildHeight() + chunk.getHeight() - 1;
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                for (int worldY = maximumY; worldY >= chunk.getMinBuildHeight(); worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        heights[index(localX, localZ)] = worldY;
                        break;
                    }
                }
            }
        }
        return heights;
    }

    private static ProviderSurfaceChunkKey providerSurfaceChunkKey(
            WorldGenRegion level,
            ChunkPos chunkPos,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        return new ProviderSurfaceChunkKey(
                generator,
                randomState,
                level.getMinBuildHeight(),
                level.getMaxBuildHeight(),
                chunkPos.x,
                chunkPos.z);
    }

    private static void trimProviderSurfaceChunks() {
        if (PROVIDER_SURFACE_CHUNKS.size() <= MAX_CACHED_PROVIDER_SURFACE_CHUNKS
                || !TRIMMING_PROVIDER_SURFACE_CHUNKS.compareAndSet(false, true)) {
            return;
        }
        try {
            int targetSize = MAX_CACHED_PROVIDER_SURFACE_CHUNKS / 2;
            var keys = PROVIDER_SURFACE_CHUNKS.keySet().iterator();
            while (PROVIDER_SURFACE_CHUNKS.size() > targetSize && keys.hasNext()) {
                PROVIDER_SURFACE_CHUNKS.remove(keys.next());
            }
        } finally {
            TRIMMING_PROVIDER_SURFACE_CHUNKS.set(false);
        }
    }

    private static void trimProviderHeightChunks() {
        if (PROVIDER_HEIGHT_CHUNKS.size() <= MAX_CACHED_PROVIDER_HEIGHT_CHUNKS
                || !TRIMMING_PROVIDER_HEIGHT_CHUNKS.compareAndSet(false, true)) {
            return;
        }
        try {
            int targetSize = MAX_CACHED_PROVIDER_HEIGHT_CHUNKS / 2;
            var keys = PROVIDER_HEIGHT_CHUNKS.keySet().iterator();
            while (PROVIDER_HEIGHT_CHUNKS.size() > targetSize && keys.hasNext()) {
                PROVIDER_HEIGHT_CHUNKS.remove(keys.next());
            }
        } finally {
            TRIMMING_PROVIDER_HEIGHT_CHUNKS.set(false);
        }
    }

    private record ProviderSurfaceChunkKey(
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            int minimumBuildHeight,
            int maximumBuildHeight,
            int chunkX,
            int chunkZ) {}

    private record ProviderDatumKey(NoiseBasedChunkGenerator generator, RandomState randomState) {}

    private record ProviderIslandDatumKey(
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            SkyIslandWorldVolumeId volumeId) {}

    /**
     * A detached ProtoChunk retains worldgen postprocessing marks without exposing provider
     * writes to the live region. Its biome registry and height range come from the same region.
     */
    private static ProtoChunk detachedProviderChunk(WorldGenRegion level, ChunkPos chunkPos) {
        return new ProtoChunk(
                chunkPos,
                UpgradeData.EMPTY,
                level,
                level.registryAccess().registryOrThrow(Registries.BIOME),
                null);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk) {
        return capture(chunk, 63);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk, int baselineY) {
        Objects.requireNonNull(chunk, "chunk");
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT];
        ResourceLocation[][] surfaceStrata = emptySurfaceStrata();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maximumY = chunk.getMinBuildHeight() + chunk.getHeight() - 1;

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int index = index(localX, localZ);
                int layer = 0;
                for (int worldY = maximumY;
                        worldY >= chunk.getMinBuildHeight() && layer < SURFACE_STRATA_DEPTH;
                        worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        if (layer > 0) {
                            break;
                        }
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        if (layer > 0) {
                            break;
                        }
                        continue;
                    }
                    if (layer == 0) {
                        worldYs[index] = worldY;
                        blockKeys[index] = key;
                    }
                    surfaceStrata[index][layer++] = key;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys, surfaceStrata);
    }

    private static ResourceLocation[][] emptySurfaceStrata() {
        ResourceLocation[][] strata = new ResourceLocation[SAMPLE_COUNT][SURFACE_STRATA_DEPTH];
        for (ResourceLocation[] column : strata) {
            Arrays.fill(column, null);
        }
        return strata;
    }

    /** Returns the most common non-fluid surface material produced by the selected provider. */
    ResourceLocation mostCommonSurfaceMaterial() {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (ResourceLocation blockKey : blockKeys) {
            if (blockKey != null) {
                counts.merge(blockKey, 1, Integer::sum);
            }
        }
        ResourceLocation mostCommon = null;
        int mostCommonCount = 0;
        for (var entry : counts.entrySet()) {
            int count = entry.getValue();
            if (count > mostCommonCount
                    || (count == mostCommonCount
                            && (mostCommon == null
                                    || entry.getKey().toString().compareTo(mostCommon.toString()) < 0))) {
                mostCommon = entry.getKey();
                mostCommonCount = count;
            }
        }
        return mostCommon;
    }

    boolean hasSurfaceMaterial(int localX, int localZ, ResourceLocation expected) {
        Objects.requireNonNull(expected, "expected");
        return surface(localX, localZ).map(NativeSurface::blockKey).filter(expected::equals).isPresent();
    }

    ChunkPos chunkPos() {
        return chunkPos;
    }

    int baselineY() {
        return baselineY;
    }

    int providerReliefDatumY(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        return providerReliefDatums.getOrDefault(volumeId, baselineY);
    }

    Optional<NativeSurface> surface(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        int index = index(localX, localZ);
        ResourceLocation blockKey = blockKeys[index];
        return blockKey == null
                ? Optional.empty()
                : Optional.of(new NativeSurface(worldYs[index], blockKey));
    }

    private int surfaceY(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        return worldYs[index(localX, localZ)];
    }

    private int[] copySurfaceHeights() {
        return Arrays.copyOf(worldYs, worldYs.length);
    }

    Optional<ResourceLocation> surfaceStratum(int localX, int localZ, int depth) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        if (depth < 0 || depth >= SURFACE_STRATA_DEPTH) {
            throw new IllegalArgumentException("surface stratum depth is outside the captured profile");
        }
        return Optional.ofNullable(surfaceStrata[index(localX, localZ)][depth]);
    }

    record NativeSurface(int worldY, ResourceLocation blockKey) {
        NativeSurface {
            Objects.requireNonNull(blockKey, "blockKey");
        }
    }

    private static int index(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }
}
