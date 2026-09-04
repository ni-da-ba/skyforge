package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Thread-confined exact-volume mutation fence for one native carver execution.
 *
 * <p>Carvers write directly through ChunkAccess/LevelChunk and therefore bypass the accepted
 * Level/WorldGenRegion population hooks. This stage authorizes only positions owned by the active
 * compiled Skyforge volume and hard-vetoes any solid position owned by a different stacked volume.
 * No attachment halo exists for carving.
 */
public final class SkyforgeCarverExecutionStage {
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private SkyforgeCarverExecutionStage() {}

    static Scope open(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(targetChunk, "targetChunk");
        Objects.requireNonNull(ownerSolid, "ownerSolid");
        Objects.requireNonNull(foreignSolid, "foreignSolid");
        var activeDomain = SkyforgeGenerationDomainStage.activeIslandVolumeId();
        if (activeDomain.isEmpty() || !activeDomain.orElseThrow().equals(volumeId)) {
            throw new IllegalStateException("carver execution requires its exact island generation-domain scope");
        }
        if (ACTIVE.get() != null) {
            throw new IllegalStateException("nested Skyforge carver executions are not supported");
        }
        Execution execution = new Execution(volumeId, targetChunk, ownerSolid, foreignSolid);
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static Scope open(SkyIslandWorldVolumeId volumeId, ChunkPos targetChunk) {
        Objects.requireNonNull(volumeId, "volumeId");
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("carver execution requires an active Skyforge runtime binding");
        }
        Predicate<BlockPos> ownerSolid = position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        volumeId, position.getX(), position.getY(), position.getZ())
                .orElseThrow(() -> new IllegalStateException("Skyforge runtime binding disappeared during carving"));
        Predicate<BlockPos> foreignSolid = position -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                        volumeId, position.getX(), position.getY(), position.getZ())
                .orElseThrow(() -> new IllegalStateException("Skyforge runtime binding disappeared during carving"));
        return open(volumeId, targetChunk, ownerSolid, foreignSolid);
    }

    static Scope openForTest(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        return open(volumeId, targetChunk, ownerSolid, foreignSolid);
    }

    /** Returns whether a native carver mutation scope is active. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /**
     * Called from the LevelChunk mixin before the direct carver write.
     *
     * <p>Outside a carver scope this is inert and returns true.
     */
    public static boolean authorizeWrite(LevelChunk chunk, BlockPos position) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Execution execution = ACTIVE.get();
        if (execution == null) {
            return true;
        }
        if (!chunk.getPos().equals(execution.targetChunk)) {
            throw new IllegalStateException(
                    "native carver attempted to mutate a chunk outside its target: target="
                            + execution.targetChunk + ", actual=" + chunk.getPos());
        }
        return execution.authorize(position);
    }

    /** Called after LevelChunk reports that an authorized direct write actually changed a block. */
    public static void afterChangedWrite(LevelChunk chunk, BlockPos position) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Execution execution = ACTIVE.get();
        if (execution == null) {
            return;
        }
        if (!chunk.getPos().equals(execution.targetChunk)) {
            throw new IllegalStateException("carver changed a chunk outside its target");
        }
        execution.recordChanged(position);
        // LevelChunk#setBlockState already updates heightmaps and lighting. The missing stable-chunk
        // side effect is notifying tracking clients of the changed block.
        chunk.getLevel().getChunkSource().blockChanged(position.immutable());
    }

    static Optional<Snapshot> snapshotIfActive() {
        return Optional.ofNullable(ACTIVE.get()).map(Execution::snapshot);
    }

    record Snapshot(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            int writeAttempts,
            int acceptedWriteAttempts,
            int rejectedWriteAttempts,
            int changedBlocks,
            int uniqueChangedBlocks,
            int minimumChangedY,
            int maximumChangedY) {
        Snapshot {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(targetChunk, "targetChunk");
        }
    }

    private static final class Execution {
        private final SkyIslandWorldVolumeId volumeId;
        private final ChunkPos targetChunk;
        private final Predicate<BlockPos> ownerSolid;
        private final Predicate<BlockPos> foreignSolid;
        private final Set<Long> changedPositions = new HashSet<>();
        private int writeAttempts;
        private int acceptedWriteAttempts;
        private int rejectedWriteAttempts;
        private int changedBlocks;
        private int minimumChangedY = Integer.MAX_VALUE;
        private int maximumChangedY = Integer.MIN_VALUE;

        private Execution(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos targetChunk,
                Predicate<BlockPos> ownerSolid,
                Predicate<BlockPos> foreignSolid) {
            this.volumeId = volumeId;
            this.targetChunk = targetChunk;
            this.ownerSolid = ownerSolid;
            this.foreignSolid = foreignSolid;
        }

        private boolean authorize(BlockPos position) {
            writeAttempts++;
            boolean accepted = ownerSolid.test(position) && !foreignSolid.test(position);
            if (accepted) {
                acceptedWriteAttempts++;
            } else {
                rejectedWriteAttempts++;
            }
            return accepted;
        }

        private void recordChanged(BlockPos position) {
            changedBlocks++;
            changedPositions.add(position.asLong());
            minimumChangedY = Math.min(minimumChangedY, position.getY());
            maximumChangedY = Math.max(maximumChangedY, position.getY());
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    volumeId,
                    targetChunk,
                    writeAttempts,
                    acceptedWriteAttempts,
                    rejectedWriteAttempts,
                    changedBlocks,
                    changedPositions.size(),
                    changedBlocks == 0 ? Integer.MIN_VALUE : minimumChangedY,
                    changedBlocks == 0 ? Integer.MIN_VALUE : maximumChangedY);
        }
    }

    static final class Scope implements AutoCloseable {
        private final Execution execution;
        private boolean closed;

        private Scope(Execution execution) {
            this.execution = execution;
        }

        Snapshot snapshot() {
            requireActive();
            return execution.snapshot();
        }

        void requireActive() {
            if (closed || ACTIVE.get() != execution) {
                throw new IllegalStateException("Skyforge carver execution scope is not active");
            }
        }

        @Override
        public void close() {
            requireActive();
            closed = true;
            ACTIVE.remove();
        }
    }
}
