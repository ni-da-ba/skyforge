package io.github.nidaba.skyforge.neoforge1211;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

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

    private final ChunkPos chunkPos;
    private final int[] worldYs;
    private final ResourceLocation[] blockKeys;

    private MinecraftNativeSurfaceSnapshot(
            ChunkPos chunkPos,
            int[] worldYs,
            ResourceLocation[] blockKeys) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        if (worldYs.length != SAMPLE_COUNT || blockKeys.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("native surface snapshot must contain exactly 256 columns");
        }
        this.worldYs = Arrays.copyOf(worldYs, worldYs.length);
        this.blockKeys = Arrays.copyOf(blockKeys, blockKeys.length);
    }

    static MinecraftNativeSurfaceSnapshot capture(ChunkAccess chunk) {
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
        return new MinecraftNativeSurfaceSnapshot(chunk.getPos(), worldYs, blockKeys);
    }

    ChunkPos chunkPos() {
        return chunkPos;
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
