package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Thread-confined exact-volume mutation fence for one native carver execution.
 *
 * <p>Carvers write directly through ChunkAccess/LevelChunk and therefore bypass the accepted
 * Level/WorldGenRegion population hooks. This stage authorizes only positions owned by the active
 * compiled Skyforge volume and hard-vetoes any solid position owned by a different stacked volume.
 * No attachment halo exists for carving.
 */
public final class SkyforgeCarverExecutionStage {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final ThreadLocal<Execution> ACTIVE = new ThreadLocal<>();

    private SkyforgeCarverExecutionStage() {}

    static Scope open(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        return open(volumeId, targetChunk, ownerSolid, foreignSolid, false);
    }

    private static Scope open(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid,
            boolean virtualizeReads) {
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
        Execution execution = new Execution(volumeId, targetChunk, ownerSolid, foreignSolid, virtualizeReads);
        ACTIVE.set(execution);
        return new Scope(execution);
    }

    static Scope open(SkyIslandWorldVolumeId volumeId, ChunkPos targetChunk) {
        return openRuntime(volumeId, targetChunk, false);
    }

    /** Opens the same exact-volume write fence with deterministic topology reads for vanilla carvers. */
    static Scope openNativeCarver(SkyIslandWorldVolumeId volumeId, ChunkPos targetChunk) {
        return openRuntime(volumeId, targetChunk, true);
    }

    private static Scope openRuntime(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            boolean virtualizeReads) {
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
        return open(volumeId, targetChunk, ownerSolid, foreignSolid, virtualizeReads);
    }

    static Scope openForTest(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        return open(volumeId, targetChunk, ownerSolid, foreignSolid);
    }

    static Scope openForTestWithVirtualReads(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos targetChunk,
            Predicate<BlockPos> ownerSolid,
            Predicate<BlockPos> foreignSolid) {
        return open(volumeId, targetChunk, ownerSolid, foreignSolid, true);
    }

    /** Returns whether a native carver mutation scope is active. */
    public static boolean active() {
        return ACTIVE.get() != null;
    }

    /**
     * Returns the deterministic block state visible to native carver reads while the exact-volume
     * carver scope is active.
     *
     * <p>Deferred carvers execute against stable LevelChunks after other lifecycle work may already
     * have populated them. Reading those live blocks makes vanilla carver control flow/RNG depend on
     * chunk scheduling. Present the immutable compiled ownership topology instead: this volume's
     * solids are ordinary carveable stone, foreign-volume solids are an uncarvable barrier, and
     * everything else is exterior air. Actual mutations still go through the normal write fence.
     */
    public static Optional<BlockState> virtualRead(LevelChunk chunk, BlockPos position) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Execution execution = ACTIVE.get();
        if (execution == null || !execution.virtualizeReads) {
            return Optional.empty();
        }
        if (!chunk.getPos().equals(execution.targetChunk)) {
            throw new IllegalStateException(
                    "native carver attempted to read a chunk outside its target: target="
                            + execution.targetChunk + ", actual=" + chunk.getPos());
        }
        return Optional.of(execution.virtualBlockState(position));
    }

    /**
     * Returns the deterministic compiled-terrain first-free height for a registry-native carver.
     *
     * <p>Like block reads, stable chunk heightmaps can contain vegetation or other lifecycle writes
     * that differ with deferred scheduling. Native carvers must see the exact volume's immutable
     * terrain column instead. Authored commit scopes do not opt into virtual reads and therefore
     * receive an empty result here.
     */
    public static OptionalInt virtualFirstFreeHeight(
            ChunkPos chunk,
            Heightmap.Types heightmapType,
            int worldX,
            int worldZ,
            int minimumY,
            int height) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(heightmapType, "heightmapType");
        Execution execution = ACTIVE.get();
        if (execution == null || !execution.virtualizeReads) {
            return OptionalInt.empty();
        }
        if (!chunk.equals(execution.targetChunk)) {
            throw new IllegalStateException(
                    "native carver attempted to read height outside its target chunk: target="
                            + execution.targetChunk + ", actual=" + chunk);
        }
        var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                execution.volumeId,
                worldX,
                worldZ,
                heightmapType,
                minimumY,
                height);
        return claim.isPresent()
                ? OptionalInt.of(claim.orElseThrow().height())
                : OptionalInt.of(minimumY);
    }

    /**
     * Called from the LevelChunk mixin before the direct carver write.
     *
     * <p>Outside a carver scope this is inert and returns true.
     */
    public static boolean authorizeWrite(LevelChunk chunk, BlockPos position, BlockState state) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Execution execution = ACTIVE.get();
        if (execution == null) {
            return true;
        }
        if (!chunk.getPos().equals(execution.targetChunk)) {
            throw new IllegalStateException(
                    "native carver attempted to mutate a chunk outside its target: target="
                            + execution.targetChunk + ", actual=" + chunk.getPos());
        }
        return execution.authorize(position, !state.getFluidState().isEmpty());
    }

    /** Called after LevelChunk reports that an authorized direct write actually changed a block. */
    public static void afterChangedWrite(LevelChunk chunk, BlockPos position, BlockState state) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Execution execution = ACTIVE.get();
        if (execution == null) {
            return;
        }
        if (!chunk.getPos().equals(execution.targetChunk)) {
            throw new IllegalStateException("carver changed a chunk outside its target");
        }
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("exact-volume native carver requires a server LevelChunk");
        }
        execution.recordChanged(position);
        if (!state.getFluidState().isEmpty()) {
            SkyforgeGeneratedFluidPropagationStage.observeCarverFluidWrite(
                    serverLevel,
                    execution.volumeId,
                    position,
                    state);
        }
        // LevelChunk#setBlockState already updates heightmaps and lighting. The missing stable-chunk
        // side effect is notifying tracking clients of the changed block.
        serverLevel.getChunkSource().blockChanged(position.immutable());
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
            int rejectedFluidWriteAttempts,
            int changedBlocks,
            int uniqueChangedBlocks,
            long changedPositionDigest,
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
        private final boolean virtualizeReads;
        private final Set<Long> changedPositions = new HashSet<>();
        private int writeAttempts;
        private int acceptedWriteAttempts;
        private int rejectedWriteAttempts;
        private int rejectedFluidWriteAttempts;
        private int changedBlocks;
        private long changedPositionDigest = FNV_OFFSET_BASIS;
        private int minimumChangedY = Integer.MAX_VALUE;
        private int maximumChangedY = Integer.MIN_VALUE;

        private Execution(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos targetChunk,
                Predicate<BlockPos> ownerSolid,
                Predicate<BlockPos> foreignSolid,
                boolean virtualizeReads) {
            this.volumeId = volumeId;
            this.targetChunk = targetChunk;
            this.ownerSolid = ownerSolid;
            this.foreignSolid = foreignSolid;
            this.virtualizeReads = virtualizeReads;
        }

        private BlockState virtualBlockState(BlockPos position) {
            Objects.requireNonNull(position, "position");
            if (foreignSolid.test(position)) {
                return Blocks.BEDROCK.defaultBlockState();
            }
            if (ownerSolid.test(position)) {
                return Blocks.STONE.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        private boolean authorize(BlockPos position) {
            return authorize(position, false);
        }

        private boolean authorize(BlockPos position, boolean fluidWrite) {
            writeAttempts++;
            boolean ownerAccepted = ownerSolid.test(position) && !foreignSolid.test(position);
            boolean fluidShellRejected = fluidWrite
                    && ownerAccepted
                    && !SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(position, ownerSolid);
            boolean accepted = ownerAccepted && !fluidShellRejected;
            if (accepted) {
                acceptedWriteAttempts++;
            } else {
                rejectedWriteAttempts++;
                if (fluidShellRejected) {
                    rejectedFluidWriteAttempts++;
                }
            }
            return accepted;
        }

        private void recordChanged(BlockPos position) {
            changedBlocks++;
            long packed = position.asLong();
            changedPositions.add(packed);
            changedPositionDigest ^= packed;
            changedPositionDigest *= FNV_PRIME;
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
                    rejectedFluidWriteAttempts,
                    changedBlocks,
                    changedPositions.size(),
                    changedPositionDigest,
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

        BlockState virtualBlockStateForTest(BlockPos position) {
            requireActive();
            return execution.virtualBlockState(Objects.requireNonNull(position, "position"));
        }

        boolean virtualizesReadsForTest() {
            requireActive();
            return execution.virtualizeReads;
        }

        boolean authorizeForTest(BlockPos position) {
            requireActive();
            return execution.authorize(Objects.requireNonNull(position, "position"));
        }

        boolean authorizeFluidForTest(BlockPos position) {
            requireActive();
            return execution.authorize(Objects.requireNonNull(position, "position"), true);
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
