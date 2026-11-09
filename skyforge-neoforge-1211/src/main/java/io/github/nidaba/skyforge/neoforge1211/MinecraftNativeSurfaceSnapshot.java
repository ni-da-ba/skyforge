package io.github.nidaba.skyforge.neoforge1211;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

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
    static final int MAX_SURFACE_LAYERS = 8;

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
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT * MAX_SURFACE_LAYERS) {
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
        int baselineY = generator.getSeaLevel();
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT * MAX_SURFACE_LAYERS];
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
                    for (int depth = 0; depth < MAX_SURFACE_LAYERS; depth++) {
                        int layerY = worldY - depth;
                        if (layerY < chunk.getMinBuildHeight()) {
                            break;
                        }
                        BlockState layer = column.getBlock(layerY);
                        if (layer.isAir() || !layer.getFluidState().isEmpty()) {
                            break;
                        }
                        ResourceLocation layerKey = BuiltInRegistries.BLOCK.getKey(layer.getBlock());
                        if (layerKey == null || SkyforgeMinecraftBlockPalette.AIR.equals(layerKey)) {
                            break;
                        }
                        blockKeys[layerIndex(index, depth)] = layerKey;
                    }
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk) {
        return capture(chunk, 63);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk, int baselineY) {
        Objects.requireNonNull(chunk, "chunk");
        int[] worldYs = new int[SAMPLE_COUNT];
        Arrays.fill(worldYs, Integer.MIN_VALUE);
        ResourceLocation[] blockKeys = new ResourceLocation[SAMPLE_COUNT * MAX_SURFACE_LAYERS];
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
                    for (int depth = 0; depth < MAX_SURFACE_LAYERS; depth++) {
                        int layerY = worldY - depth;
                        if (layerY < chunk.getMinBuildHeight()) {
                            break;
                        }
                        BlockState layer = chunk.getBlockState(cursor.set(worldX, layerY, worldZ));
                        if (layer.isAir() || !layer.getFluidState().isEmpty()) {
                            break;
                        }
                        ResourceLocation layerKey = BuiltInRegistries.BLOCK.getKey(layer.getBlock());
                        if (layerKey == null || SkyforgeMinecraftBlockPalette.AIR.equals(layerKey)) {
                            break;
                        }
                        blockKeys[layerIndex(index, depth)] = layerKey;
                    }
                    break;
                }
            }
        }
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), baselineY, worldYs, blockKeys);
    }

    ChunkPos chunkPos() {
        return chunkPos;
    }

    int baselineY() {
        return baselineY;
    }

    Optional<NativeSurface> surface(int localX, int localZ) {
        checkColumn(localX, localZ);
        int index = index(localX, localZ);
        ResourceLocation blockKey = blockKeys[layerIndex(index, 0)];
        return blockKey == null
                ? Optional.empty()
                : Optional.of(new NativeSurface(worldYs[index], blockKey));
    }

    int surfaceMaterialLayerCount(int localX, int localZ) {
        checkColumn(localX, localZ);
        int columnIndex = index(localX, localZ);
        int count = 0;
        while (count < MAX_SURFACE_LAYERS && blockKeys[layerIndex(columnIndex, count)] != null) {
            count++;
        }
        return count;
    }

    Optional<ResourceLocation> surfaceMaterialAtDepth(int localX, int localZ, int depth) {
        checkColumn(localX, localZ);
        if (depth < 0 || depth >= MAX_SURFACE_LAYERS) {
            throw new IllegalArgumentException("surface material depth outside captured profile");
        }
        return Optional.ofNullable(blockKeys[layerIndex(index(localX, localZ), depth)]);
    }

    private static void checkColumn(int localX, int localZ) {
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            throw new IllegalArgumentException("local surface coordinate must be inside one chunk");
        }
    }

    record NativeSurface(int worldY, ResourceLocation blockKey) {
        NativeSurface {
            Objects.requireNonNull(blockKey, "blockKey");
        }
    }

    private static int index(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }

    private static int layerIndex(int columnIndex, int depth) {
        return columnIndex * MAX_SURFACE_LAYERS + depth;
    }
}
