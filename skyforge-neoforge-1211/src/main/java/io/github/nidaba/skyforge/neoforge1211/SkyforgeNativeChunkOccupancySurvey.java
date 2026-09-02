package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Non-destructively compares one exact Skyforge volume with completed native content in a chunk. */
final class SkyforgeNativeChunkOccupancySurvey {
    private SkyforgeNativeChunkOccupancySurvey() {}

    static Result survey(
            SkyIslandWorldVolumeId volumeId,
            ChunkAccess chunk) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(chunk, "chunk");
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("native occupancy survey requires an active Skyforge terrain binding");
        }

        int minimumX = chunk.getPos().getMinBlockX();
        int minimumZ = chunk.getPos().getMinBlockZ();
        int minimumY = chunk.getMinBuildHeight();
        int maximumY = chunk.getMaxBuildHeight();
        int ownedSolids = 0;
        int occupiedNativePositions = 0;
        Conflict firstConflict = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = minimumY; y < maximumY; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = minimumZ + localZ;
                for (int localX = 0; localX < 16; localX++) {
                    int x = minimumX + localX;
                    boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, x, y, z)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Skyforge terrain binding disappeared during native occupancy survey"));
                    if (!owned) {
                        continue;
                    }
                    ownedSolids++;
                    cursor.set(x, y, z);
                    BlockState nativeState = chunk.getBlockState(cursor);
                    if (nativeState.isAir()) {
                        continue;
                    }
                    occupiedNativePositions++;
                    if (firstConflict == null) {
                        BlockPos immutablePos = cursor.immutable();
                        firstConflict = new Conflict(
                                immutablePos,
                                nativeState,
                                chunk.getBlockEntity(immutablePos) != null);
                    }
                }
            }
        }

        return new Result(
                volumeId,
                chunk.getPos().toLong(),
                ownedSolids,
                occupiedNativePositions,
                Optional.ofNullable(firstConflict));
    }

    record Conflict(
            BlockPos position,
            BlockState nativeState,
            boolean blockEntityPresent) {
        Conflict {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(nativeState, "nativeState");
            if (nativeState.isAir()) {
                throw new IllegalArgumentException("physical occupancy conflict cannot be air");
            }
        }
    }

    record Result(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            int skyforgeSolidPositions,
            int occupiedNativePositions,
            Optional<Conflict> firstConflict) {
        Result {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(firstConflict, "firstConflict");
            if (skyforgeSolidPositions < 0 || occupiedNativePositions < 0) {
                throw new IllegalArgumentException("occupancy counts must be non-negative");
            }
            if (occupiedNativePositions > skyforgeSolidPositions) {
                throw new IllegalArgumentException("native conflicts cannot exceed Skyforge-owned solid positions");
            }
            if ((occupiedNativePositions == 0) != firstConflict.isEmpty()) {
                throw new IllegalArgumentException("first-conflict evidence is inconsistent with conflict count");
            }
        }

        boolean conflicts() {
            return occupiedNativePositions > 0;
        }
    }
}
