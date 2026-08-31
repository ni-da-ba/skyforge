package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/** Immutable 16xH x16 Minecraft block-key projection for one chunk interval. */
public record MinecraftChunkMaterialization(
        ChunkPos chunkPos,
        int minimumY,
        int height,
        ResourceLocation[] blockKeys,
        int candidateVolumeReferences) {
    private static final int CHUNK_WIDTH = 16;

    public MinecraftChunkMaterialization {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(blockKeys, "blockKeys");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        if (candidateVolumeReferences < 0) {
            throw new IllegalArgumentException("candidateVolumeReferences must be non-negative");
        }
        int expectedLength = Math.multiplyExact(Math.multiplyExact(CHUNK_WIDTH, CHUNK_WIDTH), height);
        if (blockKeys.length != expectedLength) {
            throw new IllegalArgumentException("block-key length differs from 16xheightx16 chunk interval");
        }
        for (ResourceLocation key : blockKeys) {
            Objects.requireNonNull(key, "block key");
        }
        blockKeys = blockKeys.clone();
    }

    @Override
    public ResourceLocation[] blockKeys() {
        return blockKeys.clone();
    }

    /** Concrete block key at local X/Z and absolute world Y. */
    public ResourceLocation blockKeyAt(int localX, int worldY, int localZ) {
        requireLocal("x", localX);
        requireLocal("z", localZ);
        int localY = worldY - minimumY;
        if (localY < 0 || localY >= height) {
            throw new IndexOutOfBoundsException("world y outside materialized chunk interval");
        }
        return blockKeys[linearIndex(localX, localY, localZ)];
    }

    /** Number of projected non-air block positions. */
    public int solidBlockCount() {
        int count = 0;
        for (ResourceLocation key : blockKeys) {
            if (!SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                count++;
            }
        }
        return count;
    }

    private int linearIndex(int localX, int localY, int localZ) {
        return localX + CHUNK_WIDTH * (localZ + CHUNK_WIDTH * localY);
    }

    private static void requireLocal(String axis, int coordinate) {
        if (coordinate < 0 || coordinate >= CHUNK_WIDTH) {
            throw new IndexOutOfBoundsException(axis + " coordinate outside [0, 16)");
        }
    }
}
