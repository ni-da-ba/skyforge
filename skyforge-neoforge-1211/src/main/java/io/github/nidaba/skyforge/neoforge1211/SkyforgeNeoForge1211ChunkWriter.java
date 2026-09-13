package io.github.nidaba.skyforge.neoforge1211;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writes an accepted Skyforge materialization into one real Minecraft ChunkAccess. */
public final class SkyforgeNeoForge1211ChunkWriter {
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_AREA = CHUNK_WIDTH * CHUNK_WIDTH;
    private static final long SLOW_DEFERRED_PACKET_NANOS = 8_000_000L;

    private final MinecraftBlockStateResolver blockStateResolver;

    public SkyforgeNeoForge1211ChunkWriter(MinecraftBlockStateResolver blockStateResolver) {
        this.blockStateResolver = Objects.requireNonNull(blockStateResolver, "blockStateResolver");
    }

    /** Resolves one backend-owned material key for non-mutating generator queries. */
    BlockState resolveForQuery(ResourceLocation key) {
        return blockStateResolver.resolve(Objects.requireNonNull(key, "key"));
    }

    /**
     * Resolves every accepted block key and writes the exact owned positions into the target chunk.
     *
     * <p>This exact mode is retained for isolated equivalence proofs and dedicated backends where
     * Skyforge owns the entire target interval. In a normal Minecraft terrain-composition path,
     * use {@link #writeSolidOverlay(ChunkAccess, MinecraftChunkMaterialization)} so Skyforge AIR
     * preserves backend-native terrain.
     */
    public MinecraftChunkWriteResult write(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        validateOwnership(chunk, materialization);
        return writeInternal(chunk, materialization, false, true);
    }

    /**
     * Adds only Skyforge-owned solid states to an existing Minecraft chunk.
     *
     * <p>AIR in a Skyforge materialization means that Skyforge contributes no solid at that
     * position. It does not authorize an additive backend to erase terrain that Minecraft or
     * another backend system already placed there. This mode therefore skips AIR positions
     * entirely while retaining strict registry resolution and read-back verification for every
     * Skyforge solid that is written.
     *
     * <p>Physical admission observation deliberately lives above this writer. That keeps a concrete
     * block writer free of generation-lifecycle side effects and allows deferred exact-volume writes
     * to reuse the same primitive without accidentally resurveying already-mutated chunks. When the
     * deferred stable-chunk lifecycle scope is active, that scope supplies only the runtime lighting
     * and client-broadcast side effects required after each verified mutation.
     */
    public MinecraftChunkWriteResult writeSolidOverlay(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        validateOwnership(chunk, materialization);
        return writeInternal(chunk, materialization, true, true);
    }

    /**
     * Writes one exact deferred materialization after the admission stage has proved that the entire
     * chunk/Y interval has no other catalog candidate.
     *
     * <p>This bypasses only the redundant per-block ownership query. Strict registry resolution,
     * occupancy verification, live ChunkAccess mutation, immediate read-back, and deferred lighting /
     * client side effects remain unchanged.
     */
    MinecraftChunkWriteResult writeAdmittedExactSolidOverlay(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        validateOwnership(chunk, materialization);
        return writeInternal(chunk, materialization, true, false);
    }

    /**
     * Advances one bounded deferred solid-overlay packet in the writer's historical Y -> Z -> X
     * assignment order.
     *
     * <p>The packet budget counts assigned solid writes, not visited AIR cells. AIR is skipped while
     * advancing the traversal cursor. When physical admission temporarily blocks a solid assignment,
     * the cursor deliberately stops on that exact coordinate instead of advancing past it; a later
     * quantum therefore retries the same authoritative solid after the competing admission decision
     * becomes terminal.
     */
    DeferredSolidWriteAdvance writeDeferredSolidOverlayPacket(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization,
            DeferredSolidWriteCursor cursor,
            int maximumAssignedSolidWrites,
            boolean enforcePhysicalAdmission) {
        validateOwnership(chunk, materialization);
        Objects.requireNonNull(cursor, "cursor");
        if (maximumAssignedSolidWrites <= 0) {
            throw new IllegalArgumentException("deferred solid-write packet budget must be positive");
        }

        boolean attributeTiming = SkyforgeRuntimePerformanceMetrics.enabled();
        long packetWallStart = attributeTiming ? System.nanoTime() : 0L;
        long packetCpuStart = attributeTiming ? currentThreadCpuTimeNanos() : -1L;

        int totalCells = Math.multiplyExact(materialization.height(), CHUNK_AREA);
        if (cursor.nextLinearIndex() < 0 || cursor.nextLinearIndex() > totalCells) {
            throw new IllegalArgumentException("deferred solid-write cursor exceeds materialization bounds");
        }
        if (cursor.cumulativeAssignedSolidWrites() < 0 || cursor.cumulativeSolidWrites() < 0
                || cursor.cumulativeSolidWrites() > cursor.cumulativeAssignedSolidWrites()) {
            throw new IllegalArgumentException("invalid deferred solid-write cumulative accounting");
        }
        if (cursor.nextLinearIndex() == totalCells) {
            DeferredSolidWriteAdvance result = new DeferredSolidWriteAdvance(cursor, 0, 0, true, false);
            recordDeferredPacketTiming(attributeTiming, packetWallStart, packetCpuStart);
            return result;
        }

        int minimumX = materialization.chunkPos().getMinBlockX();
        int minimumZ = materialization.chunkPos().getMinBlockZ();
        int nextLinearIndex = cursor.nextLinearIndex();
        int localY = nextLinearIndex / CHUNK_AREA;
        int withinLayer = nextLinearIndex % CHUNK_AREA;
        int localZ = withinLayer / CHUNK_WIDTH;
        int localX = withinLayer % CHUNK_WIDTH;
        int assignedThisPacket = 0;
        int solidThisPacket = 0;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        while (nextLinearIndex < totalCells && assignedThisPacket < maximumAssignedSolidWrites) {
            int worldY = Math.addExact(materialization.minimumY(), localY);
            ResourceLocation key = materialization.blockKeyAtLinearIndex(nextLinearIndex);
            if (!SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                int worldX = Math.addExact(minimumX, localX);
                int worldZ = Math.addExact(minimumZ, localZ);
                if (enforcePhysicalAdmission
                        && !SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(worldX, worldY, worldZ)) {
                    DeferredSolidWriteCursor blockedCursor = new DeferredSolidWriteCursor(
                            nextLinearIndex,
                            Math.addExact(cursor.cumulativeAssignedSolidWrites(), assignedThisPacket),
                            Math.addExact(cursor.cumulativeSolidWrites(), solidThisPacket));
                    DeferredSolidWriteAdvance result = new DeferredSolidWriteAdvance(
                            blockedCursor,
                            assignedThisPacket,
                            solidThisPacket,
                            false,
                            true);
                    recordDeferredPacketTiming(attributeTiming, packetWallStart, packetCpuStart);
                    return result;
                }

                BlockState state = blockStateResolver.resolve(key);
                if (state.isAir()) {
                    throw new IllegalStateException(
                            "resolved BlockState changed authoritative Skyforge occupancy for " + key);
                }

                blockPos.set(worldX, worldY, worldZ);
                BlockState previousState = chunk.getBlockState(blockPos);
                chunk.setBlockState(blockPos, state, false);
                BlockState stored = chunk.getBlockState(blockPos);
                if (!stored.equals(state)) {
                    throw new IllegalStateException("ChunkAccess did not retain the resolved BlockState");
                }
                SkyforgeDeferredChunkMutationLifecycle.afterWrite(chunk, blockPos, previousState, stored);
                assignedThisPacket++;
                solidThisPacket++;
            }

            nextLinearIndex++;
            localX++;
            if (localX == CHUNK_WIDTH) {
                localX = 0;
                localZ++;
                if (localZ == CHUNK_WIDTH) {
                    localZ = 0;
                    localY++;
                }
            }
        }

        DeferredSolidWriteCursor nextCursor = new DeferredSolidWriteCursor(
                nextLinearIndex,
                Math.addExact(cursor.cumulativeAssignedSolidWrites(), assignedThisPacket),
                Math.addExact(cursor.cumulativeSolidWrites(), solidThisPacket));
        DeferredSolidWriteAdvance result = new DeferredSolidWriteAdvance(
                nextCursor,
                assignedThisPacket,
                solidThisPacket,
                nextLinearIndex == totalCells,
                false);
        recordDeferredPacketTiming(attributeTiming, packetWallStart, packetCpuStart);
        return result;
    }

    /**
     * Development-only packet attribution. Comparing current-thread CPU time with wall time lets the
     * performance fixture distinguish deterministic writer work from rare scheduler/safepoint pauses
     * without timing every individual block operation and perturbing the hot loop itself.
     */
    private static void recordDeferredPacketTiming(
            boolean attributeTiming,
            long packetWallStart,
            long packetCpuStart) {
        if (!attributeTiming) {
            return;
        }
        long wallNanos = Math.max(0L, System.nanoTime() - packetWallStart);
        long packetCpuEnd = currentThreadCpuTimeNanos();
        if (packetCpuStart < 0L || packetCpuEnd < packetCpuStart) {
            SkyforgeRuntimePerformanceMetrics.recordSample(
                    "terrain.deferred.packetCpuTimingUnavailable", 1L);
            return;
        }

        long cpuNanos = packetCpuEnd - packetCpuStart;
        long nonCpuWallNanos = Math.max(0L, wallNanos - cpuNanos);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "terrain.deferred.packetCpuNanos", cpuNanos);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "terrain.deferred.packetNonCpuWallNanos", nonCpuWallNanos);
        if (wallNanos >= SLOW_DEFERRED_PACKET_NANOS) {
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.slowPacketWallNanos", wallNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.slowPacketCpuNanos", cpuNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.slowPacketNonCpuWallNanos", nonCpuWallNanos);
        }
    }

    private static long currentThreadCpuTimeNanos() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (!threadBean.isCurrentThreadCpuTimeSupported() || !threadBean.isThreadCpuTimeEnabled()) {
            return -1L;
        }
        return threadBean.getCurrentThreadCpuTime();
    }

    private MinecraftChunkWriteResult writeInternal(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization,
            boolean solidOverlayOnly,
            boolean enforcePhysicalAdmission) {
        long maximumYExclusive = (long) materialization.minimumY() + materialization.height();
        int minimumX = materialization.chunkPos().getMinBlockX();
        int minimumZ = materialization.chunkPos().getMinBlockZ();
        int assigned = 0;
        int solid = 0;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int worldY = materialization.minimumY();
                worldY < maximumYExclusive;
                worldY++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int worldZ = Math.addExact(minimumZ, localZ);
                for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    boolean expectedAir = SkyforgeMinecraftBlockPalette.AIR.equals(key);
                    if (solidOverlayOnly && expectedAir) {
                        continue;
                    }

                    int worldX = Math.addExact(minimumX, localX);
                    if (!expectedAir
                            && enforcePhysicalAdmission
                            && !SkyforgePhysicalVolumeAdmissionStage.allowsWriteAt(worldX, worldY, worldZ)) {
                        continue;
                    }

                    BlockState state = blockStateResolver.resolve(key);
                    if (state.isAir() != expectedAir) {
                        throw new IllegalStateException(
                                "resolved BlockState changed authoritative Skyforge occupancy for " + key);
                    }

                    blockPos.set(worldX, worldY, worldZ);
                    BlockState previousState = chunk.getBlockState(blockPos);
                    chunk.setBlockState(blockPos, state, false);
                    BlockState stored = chunk.getBlockState(blockPos);
                    if (!stored.equals(state)) {
                        throw new IllegalStateException("ChunkAccess did not retain the resolved BlockState");
                    }
                    SkyforgeDeferredChunkMutationLifecycle.afterWrite(chunk, blockPos, previousState, stored);
                    assigned++;
                    if (!state.isAir()) {
                        solid++;
                    }
                }
            }
        }

        return new MinecraftChunkWriteResult(
                assigned,
                solid,
                materialization.candidateVolumeReferences());
    }

    private static void validateOwnership(
            ChunkAccess chunk,
            MinecraftChunkMaterialization materialization) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(materialization, "materialization");
        if (!chunk.getPos().equals(materialization.chunkPos())) {
            throw new IllegalArgumentException("materialization ChunkPos differs from target ChunkAccess");
        }

        long maximumYExclusive = (long) materialization.minimumY() + materialization.height();
        if (materialization.minimumY() < chunk.getMinBuildHeight()
                || maximumYExclusive > chunk.getMaxBuildHeight()) {
            throw new IllegalArgumentException("materialization vertical interval exceeds target ChunkAccess");
        }
    }

    record DeferredSolidWriteCursor(
            int nextLinearIndex,
            int cumulativeAssignedSolidWrites,
            int cumulativeSolidWrites) {
        static DeferredSolidWriteCursor start() {
            return new DeferredSolidWriteCursor(0, 0, 0);
        }
    }

    record DeferredSolidWriteAdvance(
            DeferredSolidWriteCursor cursor,
            int assignedSolidWrites,
            int solidWrites,
            boolean complete,
            boolean blocked) {
        DeferredSolidWriteAdvance {
            Objects.requireNonNull(cursor, "cursor");
            if (assignedSolidWrites < 0 || solidWrites < 0 || solidWrites > assignedSolidWrites) {
                throw new IllegalArgumentException("invalid deferred solid-write packet accounting");
            }
            if (complete && blocked) {
                throw new IllegalArgumentException("completed deferred solid-write packet cannot be blocked");
            }
        }
    }
}
