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
 * Immutable native terrain-top representation captured after surface construction and before biome
 * decoration.
 *
 * <p>This snapshot is deliberately not a worldgen height authority. It exists only so a later
 * Skyforge realization can inherit Minecraft's already-decided surface material without mistaking
 * post-surface vegetation or structures for terrain.
 */
final class MinecraftNativeSurfaceSnapshot {
    private static final int CHUNK_WIDTH = 16;
    private static final int SAMPLE_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final int TERRAIN_DATUM_GRID_WIDTH = 16;
    private static final int TERRAIN_DATUM_SAMPLE_SPACING = 16;
    private static final Map<ProviderDatumKey, Integer> PROVIDER_TERRAIN_DATUMS = new ConcurrentHashMap<>();

    private final ChunkPos chunkPos;
    private final int baselineY;
    private final int[] worldYs;
    private final ResourceLocation[] blockKeys;

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int baselineY,
            int[] worldYs,
            ResourceLocation[] blockKeys) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.baselineY = baselineY;
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface snapshot must contain exactly 256 columns");
        }
        this.worldYs = Arrays.copyOf(worldYs, worldYs.length);
        this.blockKeys = Arrays.copyOf(blockKeys, blockKeys.length);
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
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                NoiseColumn column = generator.getBaseColumn(worldX, worldZ, chunk, randomState);
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = column.getBlock(worldY);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        continue;
                    }
                    int index = index(localX, localZ);
                    worldYs[index] = worldY;
                    blockKeys[index] = key;
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys);
    }

    /**
     * Captures the selected provider's actual surface-rule result without exposing temporary
     * provider terrain to the live chunk region.
     *
     * <p>The provider's chunk-level density fill and {@code buildSurface} pipeline run against a
     * detached in-memory chunk using the live worldgen biome, structure, seed, and noise context.
     * Only the immutable top-column snapshot escapes.
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
        return capture(
                providerChunk,
                providerTerrainDatumY(generator, level, randomState));
    }

    /**
     * Uses a cached, world-fixed lower-quartile elevation sampled over a 256-by-256-block window
     * around the origin as the terrain datum. Sea level is not a useful zero for raised providers
     * such as Tectonic. Sampling only the origin chunk can place the datum on a small high plateau
     * and leave the island relief below its authored cap; a fixed broad window makes the datum
     * representative while preserving cross-chunk continuity.
     */
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
        int[] surfaceYs = new int[TERRAIN_DATUM_GRID_WIDTH * TERRAIN_DATUM_GRID_WIDTH];
        int samples = 0;
        for (int sampleZ = 0; sampleZ < TERRAIN_DATUM_GRID_WIDTH; sampleZ++) {
            int worldZ = (sampleZ - TERRAIN_DATUM_GRID_WIDTH / 2)
                    * TERRAIN_DATUM_SAMPLE_SPACING + TERRAIN_DATUM_SAMPLE_SPACING / 2;
            for (int sampleX = 0; sampleX < TERRAIN_DATUM_GRID_WIDTH; sampleX++) {
                int worldX = (sampleX - TERRAIN_DATUM_GRID_WIDTH / 2)
                        * TERRAIN_DATUM_SAMPLE_SPACING + TERRAIN_DATUM_SAMPLE_SPACING / 2;
                NoiseColumn column = generator.getBaseColumn(worldX, worldZ, level, randomState);
                for (int worldY = level.getMaxBuildHeight() - 1;
                        worldY >= level.getMinBuildHeight();
                        worldY--) {
                    BlockState state = column.getBlock(worldY);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        surfaceYs[samples++] = worldY;
                        break;
                    }
                }
            }
        }
        if (samples == 0) {
            return generator.getSeaLevel();
        }
        Arrays.sort(surfaceYs, 0, samples);
        int lowerQuartile = (samples - 1) / 4;
        return surfaceYs[lowerQuartile];
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
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maximumY = chunk.getMinBuildHeight() + chunk.getHeight() - 1;

        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunk.getPos().getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int index = index(localX, localZ);
                for (int worldY = maximumY; worldY >= chunk.getMinBuildHeight(); worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        continue;
                    }
                    worldYs[index] = worldY;
                    blockKeys[index] = key;
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys);
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

    record NativeSurface(int worldY, ResourceLocation blockKey) {
        NativeSurface {
            Objects.requireNonNull(blockKey, "blockKey");
        }
    }

    private static int index(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }
}
