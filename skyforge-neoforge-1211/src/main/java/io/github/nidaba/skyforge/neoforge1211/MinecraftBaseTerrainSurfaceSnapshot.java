package io.github.nidaba.skyforge.neoforge1211;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Immutable first-free surface heights for one chunk before Skyforge realizes any island overlay.
 *
 * <p>This snapshot is the terrain authority for {@link MinecraftTerrainDomain.BaseWorld}. It exists
 * only across the SURFACE-to-FEATURES generation interval and is never serialized into the world.
 */
final class MinecraftBaseTerrainSurfaceSnapshot {
    private static final int CHUNK_WIDTH = 16;
    private static final int SAMPLE_COUNT = CHUNK_WIDTH * CHUNK_WIDTH;

    private final ChunkPos chunkPos;
    private final int[] firstFreeHeights;

    MinecraftBaseTerrainSurfaceSnapshot(ChunkPos chunkPos, int[] firstFreeHeights) {
        this.chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(firstFreeHeights, "firstFreeHeights");
        if (firstFreeHeights.length != SAMPLE_COUNT) {
            throw new IllegalArgumentException("base terrain snapshot must contain exactly 256 columns");
        }
        this.firstFreeHeights = Arrays.copyOf(firstFreeHeights, firstFreeHeights.length);
    }

    static MinecraftBaseTerrainSurfaceSnapshot capture(ChunkAccess chunk, Heightmap.Types type) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(type, "type");
        ChunkPos chunkPos = chunk.getPos();
        int[] heights = new int[SAMPLE_COUNT];
        for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                // ChunkAccess#getHeight is the highest occupied coordinate. Minecraft's gravity
                // processor consumes the adjacent first-free height, so normalize once here.
                heights[index(localX, localZ)] = Math.addExact(chunk.getHeight(type, worldX, worldZ), 1);
            }
        }
        return new MinecraftBaseTerrainSurfaceSnapshot(chunkPos, heights);
    }

    ChunkPos chunkPos() {
        return chunkPos;
    }

    OptionalInt firstFreeHeight(int worldX, int worldZ) {
        int localX = worldX - chunkPos.getMinBlockX();
        int localZ = worldZ - chunkPos.getMinBlockZ();
        if (localX < 0 || localX >= CHUNK_WIDTH || localZ < 0 || localZ >= CHUNK_WIDTH) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(firstFreeHeights[index(localX, localZ)]);
    }

    int[] copyFirstFreeHeights() {
        return Arrays.copyOf(firstFreeHeights, firstFreeHeights.length);
    }

    private static int index(int localX, int localZ) {
        return localX + CHUNK_WIDTH * localZ;
    }
}
