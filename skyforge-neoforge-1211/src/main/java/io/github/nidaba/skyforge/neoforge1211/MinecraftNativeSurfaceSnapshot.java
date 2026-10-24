package io.github.nidaba.skyforge.neoforge1211;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
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
    private static final int PROFILE_SAMPLE_HALO_RADIUS = PROFILE_FILTER_RADIUS + 1;
    private static final int PROFILE_FILTER_WIDTH = CHUNK_WIDTH + PROFILE_SAMPLE_HALO_RADIUS * 2;
    private static final int PROFILE_FILTER_SAMPLE_COUNT = PROFILE_FILTER_WIDTH * PROFILE_FILTER_WIDTH;
    private static final Map<ProviderDatumKey, Integer> PROVIDER_TERRAIN_DATUMS = new ConcurrentHashMap<>();

    private final ChunkPos chunkPos;
    private final int baselineY;
    private final int[] worldYs;
    private final ResourceLocation[] blockKeys;
    private final ResourceLocation[][] surfaceStrata;
    private final int[] providerHeightHalo;

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
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.baselineY = baselineY;
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface snapshot must contain exactly 256 columns");
        }
        if (surfaceStrata.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface strata must contain exactly 256 columns");
        }
        if (providerHeightHalo.length != PROFILE_FILTER_SAMPLE_COUNT) {
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
            RandomState randomState) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(structureManager, "structureManager");
        Objects.requireNonNull(targetChunk, "targetChunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(randomState, "randomState");

        ProtoChunk providerChunk = detachedProviderChunk(level, targetChunk.getPos());
        Blender blender = Blender.of(level);
        generator.createBiomes(randomState, blender, structureManager, providerChunk).join();
        generator.fillFromNoise(blender, randomState, structureManager, providerChunk).join();
        generator.buildSurface(level, structureManager, randomState, providerChunk);
        MinecraftNativeSurfaceSnapshot captured = capture(
                providerChunk,
                providerTerrainDatumY(generator, level, randomState));
        return captured.withProviderHeightHalo(level, generator, randomState);
    }

    private MinecraftNativeSurfaceSnapshot withProviderHeightHalo(
            WorldGenRegion level,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        int[] heightHalo = sampleProviderHeightHalo(
                chunkPos,
                (worldX, worldZ) -> {
                    NoiseColumn column = generator.getBaseColumn(worldX, worldZ, level, randomState);
                    return firstSolidSurfaceY(
                            column, level.getMinBuildHeight(), level.getMaxBuildHeight());
                });
        return new MinecraftNativeSurfaceSnapshot(
                chunkPos, baselineY, worldYs, blockKeys, surfaceStrata, heightHalo);
    }

    /** Samples the complete filter window through one world-coordinate terrain source. */
    static int[] sampleProviderHeightHalo(
            ChunkPos chunkPos,
            ProviderSurfaceHeightSampler sampler) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(sampler, "sampler");
        int[] halo = new int[PROFILE_FILTER_SAMPLE_COUNT];
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
        int[] halo = new int[PROFILE_FILTER_SAMPLE_COUNT];
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

    private record ProviderDatumKey(NoiseBasedChunkGenerator generator, RandomState randomState) {}

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
