package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

/** Deterministic precommit headroom admission for native tree placed features. */
final class SkyforgeSurfaceVegetationHeadroomPolicy {
    private static final int CHUNK_WIDTH = 16;

    private SkyforgeSurfaceVegetationHeadroomPolicy() {}

    static boolean admits(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int maximumAttachmentDepth) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(chunkPos, "chunkPos");
        if (maximumAttachmentDepth <= 0) {
            return true;
        }

        int maximumOwnerSurfaceY = Integer.MIN_VALUE;
        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                var range = SkyforgeNeoForge1211SurfaceStage.integerSolidRange(
                        volumeId, worldX, worldZ);
                if (range.isPresent()) {
                    maximumOwnerSurfaceY = Math.max(
                            maximumOwnerSurfaceY, range.orElseThrow().maximumY());
                }
            }
        }
        if (maximumOwnerSurfaceY == Integer.MIN_VALUE) {
            return true;
        }

        int maximumBuildHeightExclusive = Math.addExact(level.getMinBuildHeight(), level.getHeight());
        return admits(
                Math.addExact(maximumOwnerSurfaceY, 1),
                maximumBuildHeightExclusive,
                maximumAttachmentDepth);
    }

    static boolean admits(
            int firstFreeY,
            int maximumBuildHeightExclusive,
            int requiredHeadroom) {
        if (requiredHeadroom < 0) {
            throw new IllegalArgumentException("requiredHeadroom must be non-negative");
        }
        return (long) firstFreeY + requiredHeadroom <= maximumBuildHeightExclusive;
    }
}
