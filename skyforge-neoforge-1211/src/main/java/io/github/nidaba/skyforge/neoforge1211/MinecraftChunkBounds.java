package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import net.minecraft.world.level.ChunkPos;

/** Closed world-space bounds for one Minecraft 1.21.1 chunk vertical interval. */
public record MinecraftChunkBounds(ChunkPos chunkPos, int minimumY, int height) {
    public MinecraftChunkBounds {
        Objects.requireNonNull(chunkPos, "chunkPos");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }
        Math.addExact(minimumY, height - 1);
    }

    /** Inclusive maximum block Y owned by this interval. */
    public int maximumY() {
        return Math.addExact(minimumY, height - 1);
    }

    /** Conservative closed query bounds matching the block coordinates owned by this chunk. */
    public WorldBounds worldBounds() {
        return new WorldBounds(
                chunkPos.getMinBlockX(),
                chunkPos.getMaxBlockX(),
                minimumY,
                maximumY(),
                chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockZ());
    }
}
