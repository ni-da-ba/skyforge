package io.github.nidaba.skyforge.neoforge1211;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Immutable native terrain surface representation captured after surface construction and before
 * biome decoration.
 *
 * <p>This snapshot is deliberately not a worldgen height authority. It carries the native top block
 * and a short, tagged soil/sand mantle so a later Skyforge realization can inherit Minecraft's
 * already-decided surface material without mistaking post-surface vegetation or structures for
 * terrain or replacing the island's deeper authored body.
 */
final class MinecraftNativeSurfaceSnapshot {
    private static final int CHUNK_WIDTH = 16;
    private static final int SAMPLE_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final Set<String> VANILLA_MANTLE_FALLBACKS = Set.of(
            "dirt", "coarse_dirt", "rooted_dirt", "mud", "sand", "red_sand");
    /** Surface block plus at most seven provider-authored soil/sand blocks beneath it. */
    static final int MAX_SURFACE_LAYERS = 8;

    private final ChunkPos chunkPos;
    private final int baselineY;
    private final int[] worldYs;
    private final ResourceLocation[] blockKeys;
    private final ResourceLocation[] mantleBlockKeys;
    private final ResourceLocation[] sourceBiomeKeys;

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int baselineY,
            int[] worldYs,
            ResourceLocation[] blockKeys,
            ResourceLocation[] mantleBlockKeys,
            ResourceLocation[] sourceBiomeKeys) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        this.baselineY = baselineY;
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface snapshot must contain exactly 256 columns");
        }
        if (mantleBlockKeys.length != SAMPLE_COUNT * MAX_SURFACE_LAYERS) {
            throw new IllegalArgumentException("native surface mantle must contain eight layers for each column");
        }
        this.worldYs = Arrays.copyOf(worldYs, worldYs.length);
        this.blockKeys = Arrays.copyOf(blockKeys, blockKeys.length);
        this.mantleBlockKeys = Arrays.copyOf(mantleBlockKeys, mantleBlockKeys.length);
        if (sourceBiomeKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface source biomes must contain exactly 256 columns");
        }
        this.sourceBiomeKeys = Arrays.copyOf(sourceBiomeKeys, sourceBiomeKeys.length);
    }

    static MinecraftNativeSurfaceSnapshot capture(
            ChunkAccess chunk,
            NoiseBasedChunkGenerator generator,
            RandomState randomState) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(randomState, "randomState");
        int baselineY = generator.getSeaLevel();
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT];
        ResourceLocation[] mantleBlockKeys = new ResourceLocation[SAMPLE_COUNT * MAX_SURFACE_LAYERS];
        ResourceLocation[] sourceBiomeKeys = new ResourceLocation[SAMPLE_COUNT];
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
                    sourceBiomeKeys[index] = generator.getBiomeSource()
                            .getNoiseBiome(
                                    QuartPos.fromBlock(worldX),
                                    QuartPos.fromBlock(worldY),
                                    QuartPos.fromBlock(worldZ),
                                    randomState.sampler())
                            .location();
                    captureMantle(column::getBlock, worldY, localX, localZ, mantleBlockKeys);
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(
                chunk.getPos(), baselineY, worldYs, blockKeys, mantleBlockKeys, sourceBiomeKeys);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk) {
        return capture(chunk, 63);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk, int baselineY) {
        Objects.requireNonNull(chunk, "chunk");
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT];
        ResourceLocation[] mantleBlockKeys = new ResourceLocation[SAMPLE_COUNT * MAX_SURFACE_LAYERS];
        ResourceLocation[] sourceBiomeKeys = new ResourceLocation[SAMPLE_COUNT];
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
                    captureMantle(
                            y -> chunk.getBlockState(cursor.set(worldX, y, worldZ)),
                            worldY,
                            localX,
                            localZ,
                            mantleBlockKeys);
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(
                chunk.getPos(), baselineY, worldYs, blockKeys, mantleBlockKeys, sourceBiomeKeys);
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

    Optional<ResourceLocation> sourceBiomeKey(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        return Optional.ofNullable(sourceBiomeKeys[index(localX, localZ)]);
    }

    Optional<ResourceLocation> mantleBlockAtDepth(int localX, int localZ, int depth) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
        if (depth < 0 || depth >= MAX_SURFACE_LAYERS) {
            throw new IllegalArgumentException("surface mantle depth must be between zero and seven");
        }
        return Optional.ofNullable(mantleBlockKeys[mantleIndex(localX, localZ, depth)]);
    }

    private static void captureMantle(
            java.util.function.IntFunction<BlockState> blockAtY,
            int surfaceY,
            int localX,
            int localZ,
            ResourceLocation[] mantleBlockKeys) {
        for (int depth = 0; depth < MAX_SURFACE_LAYERS; depth++) {
            BlockState state = blockAtY.apply(surfaceY - depth);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                break;
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (key == null || SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                break;
            }
            if (depth > 0 && !isNativeSurfaceMantle(state, key)) {
                break;
            }
            mantleBlockKeys[mantleIndex(localX, localZ, depth)] = key;
        }
    }

    private static boolean isNativeSurfaceMantle(BlockState state, ResourceLocation key) {
        // Default vanilla tags are not always loaded in focused unit tests; keep the vanilla base
        // soil recognizable there while allowing registry/data-pack tags to admit modded soils.
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || ("minecraft".equals(key.getNamespace()) && VANILLA_MANTLE_FALLBACKS.contains(key.getPath()));
    }

    record NativeSurface(int worldY, ResourceLocation blockKey) {
        NativeSurface {
            Objects.requireNonNull(blockKey, "blockKey");
        }
    }

    private static int index(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }

    private static int mantleIndex(int localX, int localZ, int depth) {
        return depth + MAX_SURFACE_LAYERS * index(localX, localZ);
    }
}

