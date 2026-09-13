package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.SurfaceFoundationAssessment;
import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportAssessment;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import io.github.nidaba.skyforge.world.TerrainBoxObservation;
import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/** Runtime binding between compiled Skyforge terrain and the Minecraft 1.21.1 adapter. */
public final class SkyforgeNeoForge1211SurfaceStage {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();
    private static final int MAX_DEFERRED_MATERIALIZATION_SLICE_HEIGHT = 32;

    private SkyforgeNeoForge1211SurfaceStage() {}

    /** Direct realization retained for isolated tests and callers without staged surface adaptation. */
    static Optional<MinecraftChunkWriteResult> realize(ChunkAccess chunk) {
        return realize(chunk, Optional.empty());
    }

    /**
     * Realizes Skyforge after native decoration while adapting exposed island tops from the native
     * terrain snapshot captured before that decoration began.
     */
    static Optional<MinecraftChunkWriteResult> realize(
            ChunkAccess chunk,
            MinecraftNativeSurfaceSnapshot nativeSurfaceSnapshot) {
        return realize(chunk, Optional.of(Objects.requireNonNull(nativeSurfaceSnapshot, "nativeSurfaceSnapshot")));
    }

    private static Optional<MinecraftChunkWriteResult> realize(
            ChunkAccess chunk,
            Optional<MinecraftNativeSurfaceSnapshot> nativeSurfaceSnapshot) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nativeSurfaceSnapshot, "nativeSurfaceSnapshot");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }

        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        if (!binding.adapter().hasCandidateVolume(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight())) {
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.realizeNoCandidate", performanceStart);
            return Optional.of(new MinecraftChunkWriteResult(0, 0, 0));
        }

        // Physical admission is deliberately observed here, above the concrete writer and after
        // BASE_WORLD has completed. A deferred exact-volume write can therefore reuse the writer
        // without accidentally resurveying already-mutated terrain.
        SkyforgePhysicalVolumeAdmissionStage.observeBeforeRealization(chunk, nativeSurfaceSnapshot);
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsDirectRealization(chunk)) {
            SkyforgeRuntimePerformanceMetrics.recordSince(
                    "terrain.plannedDirectProjectionSkipped",
                    performanceStart);
            return Optional.of(new MinecraftChunkWriteResult(0, 0, 0));
        }

        SkyforgeNeoForge1211IsolationDevRuntime.Proof isolationProof =
                SkyforgeNeoForge1211IsolationDevRuntime.captureBeforeSkyforge(chunk);
        MinecraftChunkMaterialization materialization = materialize(binding, chunk);
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            var adapter = binding.nativeSurfaceTopAdapter().orElseThrow();
            materialization = nativeSurfaceSnapshot.isPresent()
                    ? adapter.adapt(nativeSurfaceSnapshot.orElseThrow(), materialization)
                    : adapter.adapt(chunk, materialization);
        }
        MinecraftChunkWriteResult result = binding.writer().writeSolidOverlay(chunk, materialization);
        SkyforgeNeoForge1211IsolationDevRuntime.verifyAfterSkyforge(chunk, isolationProof);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.realize", performanceStart);
        return Optional.of(result);
    }

    /**
     * Services ADMITTED deferred terrain only in chunks already available to the current generation
     * region. {@link WorldGenRegion#hasChunk(int, int)} is checked before every lookup, so this path
     * does not create generation tickets or force future chunks to exist.
     *
     * <p>Each successful exact terrain catch-up is immediately followed by that volume's normal
     * native population coordinator. The coordinator remains the replay/idempotence authority.
     */
    static int serviceAvailableCatchup(
            WorldGenLevel level,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return 0;
        }
        if (!(level instanceof WorldGenRegion region)) {
            return 0;
        }

        int completed = 0;
        for (long chunkKey : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            if (!region.hasChunk(chunkX, chunkZ)) {
                continue;
            }
            ChunkAccess target = region.getChunk(chunkX, chunkZ);
            for (var pending : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(target.getPos())) {
                if (!realizeDeferred(binding, target, pending)) {
                    continue;
                }
                SkyforgeNativeSurfacePopulationStage.populateVolume(
                        level,
                        target,
                        generator,
                        pending.volumeId());
                completed++;
            }
        }
        return completed;
    }

    /** Services eligible exact-volume terrain records for one already-available chunk. */
    static int serviceCatchup(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return 0;
        }
        int completed = 0;
        for (var pending : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos())) {
            if (realizeDeferred(binding, chunk, pending)) {
                completed++;
            }
        }
        return completed;
    }

    /** Advances one bounded persisted deferred-terrain packet for the canonical obligation. */
    static DeferredCatchupPacketResult serviceOneCatchupPacket(
            ServerLevel level,
            ChunkAccess chunk,
            int maximumAssignedSolidWrites) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        if (maximumAssignedSolidWrites <= 0) {
            throw new IllegalArgumentException("maximumAssignedSolidWrites must be positive");
        }
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return new DeferredCatchupPacketResult(false, false, 0);
        }
        var progressData = SkyforgeDeferredTerrainWriteProgressData.get(level);
        for (var pending : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos())) {
            return realizeDeferredPacket(binding, chunk, pending, progressData, maximumAssignedSolidWrites);
        }
        return new DeferredCatchupPacketResult(false, false, 0);
    }

    private static DeferredCatchupPacketResult realizeDeferredPacket(
            RuntimeBinding binding,
            ChunkAccess chunk,
            SkyforgePhysicalVolumeAdmissionStage.PendingRealization pending,
            SkyforgeDeferredTerrainWriteProgressData progressData,
            int maximumAssignedSolidWrites) {
        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        WorldBounds volumeBounds = binding.adapter().volumeBounds(pending.volumeId())
                .orElseThrow(() -> new IllegalStateException(
                        "deferred realization requires exact bound volume " + pending.volumeId().path()));
        VerticalRange range = boundedVerticalRange(chunk, volumeBounds);
        if (range.height() <= 0) {
            throw new IllegalStateException(
                    "deferred exact-volume realization has no vertical overlap with target chunk");
        }

        var existing = progressData.find(pending.volumeId(), pending.chunkKey());
        MinecraftChunkMaterialization materialization = progressData
                .cachedMaterialization(pending.volumeId(), pending.chunkKey())
                .orElse(null);
        boolean rematerialized = materialization == null;
        if (rematerialized) {
            var preparation = progressData.getOrCreatePreparation(
                    pending.volumeId(),
                    chunk.getPos(),
                    range.minimumY(),
                    range.height());
            if (preparation.preparedHeight() == 0) {
                SkyforgeRuntimePerformanceMetrics.recordSample(
                        "terrain.deferredVerticalSamples",
                        range.height());
            }

            long materializeSliceStart = SkyforgeRuntimePerformanceMetrics.start();
            var preparationAdvance = preparation.advance(
                    binding.adapter()::materialize,
                    MAX_DEFERRED_MATERIALIZATION_SLICE_HEIGHT);
            long materializeSliceNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(materializeSliceStart);
            preparation.recordWorkNanos(materializeSliceNanos);
            SkyforgeRuntimePerformanceMetrics.recordElapsed(
                    "terrain.deferred.materializeSlice",
                    materializeSliceNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.materializeSliceWallNanos",
                    materializeSliceNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.materializeSliceHeight",
                    preparationAdvance.preparedHeight());

            if (!preparationAdvance.complete()) {
                long quantumElapsedNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(performanceStart);
                SkyforgeRuntimePerformanceMetrics.recordElapsed(
                        "terrain.realizeDeferredPacket",
                        quantumElapsedNanos);
                SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                        "terrain.deferred.quantumWallNanos",
                        quantumElapsedNanos);
                return new DeferredCatchupPacketResult(true, false, 0);
            }

            materialization = preparationAdvance.completedMaterialization().orElseThrow();
            SkyforgeRuntimePerformanceMetrics.recordElapsed(
                    "terrain.deferred.materialize",
                    preparation.cumulativeWorkNanos());
            progressData.discardCachedPreparation(pending.volumeId(), pending.chunkKey());

            if (binding.nativeSurfaceTopAdapter().isPresent()) {
                MinecraftNativeSurfaceSnapshot snapshot = pending.nativeSurfaceSnapshot()
                        .orElseThrow(() -> new IllegalStateException(
                                "native-surface-adapted deferred realization lost its pre-decoration snapshot"));
                long adaptStart = SkyforgeRuntimePerformanceMetrics.start();
                materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(snapshot, materialization);
                SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.adaptSurface", adaptStart);
            }
            progressData.cacheMaterialization(pending.volumeId(), pending.chunkKey(), materialization);
        }

        int expectedSolidBlocks;
        if (existing.isPresent()) {
            expectedSolidBlocks = existing.orElseThrow().expectedSolidBlocks();
            if (rematerialized) {
                long solidCountStart = SkyforgeRuntimePerformanceMetrics.start();
                int reproducedSolidBlocks = materialization.solidBlockCount();
                SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.solidCount", solidCountStart);
                if (reproducedSolidBlocks != expectedSolidBlocks) {
                    throw new IllegalStateException(
                            "deferred terrain materialization changed across persisted resume");
                }
            }
        } else {
            long solidCountStart = SkyforgeRuntimePerformanceMetrics.start();
            expectedSolidBlocks = materialization.solidBlockCount();
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.solidCount", solidCountStart);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.expectedSolidBlocks", expectedSolidBlocks);
        }
        var progress = progressData.getOrCreate(pending.volumeId(), pending.chunkKey(), expectedSolidBlocks);
        if (progress.terminal()) {
            progressData.discardCachedPreparation(pending.volumeId(), pending.chunkKey());
            progressData.discardCachedMaterialization(pending.volumeId(), pending.chunkKey());
            SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
            long quantumElapsedNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(performanceStart);
            SkyforgeRuntimePerformanceMetrics.recordElapsed("terrain.realizeDeferredPacket", quantumElapsedNanos);
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "terrain.deferred.quantumWallNanos", quantumElapsedNanos);
            return new DeferredCatchupPacketResult(true, true, 0);
        }

        boolean exactAdmissionFastPath = SkyforgePhysicalVolumeAdmissionStage.canUseExactDeferredWriteFastPath(
                pending, chunk, range.minimumY(), range.height());
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "terrain.deferredExactAdmissionFastPath", exactAdmissionFastPath ? 1L : 0L);
        long writeStart = SkyforgeRuntimePerformanceMetrics.start();
        var advance = binding.writer().writeDeferredSolidOverlayPacket(
                chunk,
                materialization,
                progress.cursor(),
                maximumAssignedSolidWrites,
                !exactAdmissionFastPath);
        long writeElapsedNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(writeStart);
        SkyforgeRuntimePerformanceMetrics.recordElapsed("terrain.deferred.writePacket", writeElapsedNanos);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "terrain.deferred.packetWallNanos", writeElapsedNanos);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "terrain.deferred.packetAssignedSolidWrites", advance.assignedSolidWrites());

        if (advance.complete()
                && advance.cursor().cumulativeAssignedSolidWrites() != expectedSolidBlocks) {
            throw new IllegalStateException(
                    "completed deferred terrain packet count differs from authoritative solid count");
        }
        boolean terminal = advance.complete();
        progressData.store(progress.advance(advance, terminal));
        if (terminal) {
            long completeStart = SkyforgeRuntimePerformanceMetrics.start();
            SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
            progressData.discardCachedPreparation(pending.volumeId(), pending.chunkKey());
            progressData.discardCachedMaterialization(pending.volumeId(), pending.chunkKey());
            SkyforgeRuntimePerformanceMetrics.recordSince("terrain.deferred.completeCatchup", completeStart);
        }
        long quantumElapsedNanos = SkyforgeRuntimePerformanceMetrics.elapsedSince(performanceStart);
        SkyforgeRuntimePerformanceMetrics.recordElapsed("terrain.realizeDeferredPacket", quantumElapsedNanos);
        SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                "terrain.deferred.quantumWallNanos", quantumElapsedNanos);
        boolean worked = terminal || advance.assignedSolidWrites() > 0;
        return new DeferredCatchupPacketResult(worked, terminal, advance.assignedSolidWrites());
    }

    record DeferredCatchupPacketResult(boolean worked, boolean completed, int assignedSolidWrites) {
        DeferredCatchupPacketResult {
            if (assignedSolidWrites < 0) {
                throw new IllegalArgumentException("assignedSolidWrites must be nonnegative");
            }
            if (completed && !worked) {
                throw new IllegalArgumentException("completed packet must count as worked");
            }
        }
    }

    static int serviceOneCatchup(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return 0;
        }
        for (var pending : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos())) {
            if (realizeDeferred(binding, chunk, pending)) {
                return 1;
            }
        }
        return 0;
    }

    private static boolean realizeDeferred(
            RuntimeBinding binding,
            ChunkAccess chunk,
            SkyforgePhysicalVolumeAdmissionStage.PendingRealization pending) {
        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        WorldBounds volumeBounds = binding.adapter().volumeBounds(pending.volumeId())
                .orElseThrow(() -> new IllegalStateException(
                        "deferred realization requires exact bound volume " + pending.volumeId().path()));
        VerticalRange range = boundedVerticalRange(chunk, volumeBounds);
        if (range.height() <= 0) {
            throw new IllegalStateException(
                    "deferred exact-volume realization has no vertical overlap with target chunk");
        }
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "terrain.deferredVerticalSamples",
                range.height());
        long materializeStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                pending.volumeId(),
                chunk.getPos(),
                range.minimumY(),
                range.height());
        SkyforgeRuntimePerformanceMetrics.recordSince(
                "terrain.deferred.materialize",
                materializeStart);
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            MinecraftNativeSurfaceSnapshot snapshot = pending.nativeSurfaceSnapshot()
                    .orElseThrow(() -> new IllegalStateException(
                            "native-surface-adapted deferred realization lost its pre-decoration snapshot"));
            long adaptStart = SkyforgeRuntimePerformanceMetrics.start();
            materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(snapshot, materialization);
            SkyforgeRuntimePerformanceMetrics.recordSince(
                    "terrain.deferred.adaptSurface",
                    adaptStart);
        }

        boolean exactAdmissionFastPath =
                SkyforgePhysicalVolumeAdmissionStage.canUseExactDeferredWriteFastPath(
                        pending,
                        chunk,
                        range.minimumY(),
                        range.height());
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "terrain.deferredExactAdmissionFastPath",
                exactAdmissionFastPath ? 1L : 0L);

        int expectedSolidBlocks = -1;
        if (!exactAdmissionFastPath) {
            long solidCountStart = SkyforgeRuntimePerformanceMetrics.start();
            expectedSolidBlocks = materialization.solidBlockCount();
            SkyforgeRuntimePerformanceMetrics.recordSince(
                    "terrain.deferred.solidCount",
                    solidCountStart);
        }

        long writeStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftChunkWriteResult result = exactAdmissionFastPath
                ? binding.writer().writeAdmittedExactSolidOverlay(chunk, materialization)
                : binding.writer().writeSolidOverlay(chunk, materialization);
        SkyforgeRuntimePerformanceMetrics.recordSince(
                "terrain.deferred.write",
                writeStart);
        if (!exactAdmissionFastPath && result.solidBlockCount() != expectedSolidBlocks) {
            return false;
        }
        long completeStart = SkyforgeRuntimePerformanceMetrics.start();
        SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
        SkyforgeRuntimePerformanceMetrics.recordSince(
                "terrain.deferred.completeCatchup",
                completeStart);
        SkyforgeRuntimePerformanceMetrics.recordSince("terrain.realizeDeferred", performanceStart);
        return true;
    }

    static Optional<MinecraftChunkMaterialization> materializeOccupancy(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        return Optional.of(materialize(binding, chunk));
    }

    static OptionalInt queryBaseHeight(
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Optional<MinecraftSkyforgeHeightClaim> claim = queryBaseHeightClaim(
                worldX, worldZ, type, minimumY, height);
        return claim.isPresent() ? OptionalInt.of(claim.orElseThrow().height()) : OptionalInt.empty();
    }

    static Optional<MinecraftSkyforgeHeightClaim> queryBaseHeightClaim(
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Objects.requireNonNull(type, "type");
        if (height <= 0) {
            throw new IllegalArgumentException("height must be positive");
        }

        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }

        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                chunkPos,
                minimumY,
                height);
        int localX = worldX - chunkPos.getMinBlockX();
        int localZ = worldZ - chunkPos.getMinBlockZ();
        int maximumYExclusive = Math.addExact(minimumY, height);

        for (int worldY = maximumYExclusive - 1; worldY >= minimumY; worldY--) {
            var key = materialization.blockKeyAt(localX, worldY, localZ);
            var state = binding.writer().resolveForQuery(key);
            if (type.isOpaque().test(state)) {
                var volumeIds = binding.adapter().claimingVolumeIds(worldX, worldY, worldZ);
                if (volumeIds.isEmpty()) {
                    throw new IllegalStateException("materialized Skyforge height has no owning world volume");
                }
                return Optional.of(new MinecraftSkyforgeHeightClaim(worldY + 1, volumeIds));
            }
        }
        return Optional.empty();
    }

    static Optional<MinecraftSkyforgeHeightClaim> queryBaseHeightClaim(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ,
            Heightmap.Types type,
            int minimumY,
            int height) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(type, "type");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        OptionalInt firstFree = binding.adapter().firstFreeHeight(volumeId, worldX, worldZ, minimumY, height);
        return firstFree.isPresent()
                ? Optional.of(new MinecraftSkyforgeHeightClaim(firstFree.getAsInt(), List.of(volumeId)))
                : Optional.empty();
    }

    static Optional<WorldBounds> volumeBounds(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null ? Optional.empty() : binding.adapter().volumeBounds(volumeId);
    }

    static Optional<SkyforgeExactVoxelSupportBounds.ColumnRange> integerSolidRange(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException(
                    "exact column support requires an active Skyforge terrain binding");
        }
        return binding.adapter().integerSolidRange(volumeId, worldX, worldZ);
    }

    static Optional<List<SurfaceSupportAssessment>> assessSurfaceSupport(SurfaceSupportRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().assessSurfaceSupport(requirements));
    }

    static Optional<List<SurfaceFoundationAssessment>> assessSurfaceFoundation(
            SurfaceFoundationRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().assessSurfaceFoundation(requirements));
    }

    static Optional<TerrainBoxObservation> observeTerrainBox(
            SkyIslandWorldVolumeId volumeId,
            TerrainBoxObservationRequirements requirements) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(requirements, "requirements");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().observeTerrainBox(volumeId, requirements));
    }

    static Optional<Boolean> isSolidOwnedBy(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().isSolidOwnedBy(volumeId, worldX, worldY, worldZ));
    }

    static Optional<Boolean> isSolidOwnedByOtherVolume(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? Optional.empty()
                : Optional.of(binding.adapter().isSolidOwnedByOtherVolume(
                        volumeId,
                        worldX,
                        worldY,
                        worldZ));
    }

    static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        return install(adapter, writer, Optional.empty());
    }

    static AutoCloseable installNativeSurfaceAdapted(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer) {
        return install(adapter, writer, Optional.of(new MinecraftNativeSurfaceTopAdapter()));
    }

    private static AutoCloseable install(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer,
            Optional<MinecraftNativeSurfaceTopAdapter> nativeSurfaceTopAdapter) {
        RuntimeBinding binding = new RuntimeBinding(adapter, writer, nativeSurfaceTopAdapter);
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a Skyforge post-surface runtime binding is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("Skyforge post-surface runtime binding changed before close");
            }
        };
    }

    static boolean hasActiveBinding() {
        return ACTIVE.get() != null;
    }

    static boolean hasNativeSurfaceAdaptation() {
        RuntimeBinding binding = ACTIVE.get();
        return binding != null && binding.nativeSurfaceTopAdapter().isPresent();
    }

    static boolean hasCandidateVolume(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return false;
        }
        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        boolean candidate = binding.adapter().hasCandidateVolume(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        if (!candidate) {
            SkyforgeRuntimePerformanceMetrics.recordSince(
                    "terrain.noCandidatePrefilter",
                    performanceStart);
        }
        return candidate;
    }

    static VerticalRange boundedVerticalRange(ChunkAccess chunk, WorldBounds volumeBounds) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(volumeBounds, "volumeBounds");

        int chunkMinimumY = chunk.getMinBuildHeight();
        int chunkMaximumYExclusive = chunk.getMaxBuildHeight();
        int boundedMinimumY = Math.min(
                chunkMaximumYExclusive,
                Math.max(chunkMinimumY, floorToInt(volumeBounds.minimumY())));
        long volumeMaximumYExclusive = Math.addExact((long) floorToInt(volumeBounds.maximumY()), 1L);
        int boundedMaximumYExclusive = Math.max(
                chunkMinimumY,
                (int) Math.min((long) chunkMaximumYExclusive, volumeMaximumYExclusive));
        if (boundedMaximumYExclusive < boundedMinimumY) {
            boundedMaximumYExclusive = boundedMinimumY;
        }
        return new VerticalRange(boundedMinimumY, boundedMaximumYExclusive);
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world bound exceeds Minecraft integer coordinates: " + value);
        }
        return (int) floored;
    }

    record VerticalRange(int minimumY, int maximumYExclusive) {
        VerticalRange {
            if (maximumYExclusive < minimumY) {
                throw new IllegalArgumentException("vertical range maximum must not precede minimum");
            }
        }

        int height() {
            return maximumYExclusive - minimumY;
        }
    }

    private static MinecraftChunkMaterialization materialize(RuntimeBinding binding, ChunkAccess chunk) {
        return binding.adapter().materialize(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
    }

    private record RuntimeBinding(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            SkyforgeNeoForge1211ChunkWriter writer,
            Optional<MinecraftNativeSurfaceTopAdapter> nativeSurfaceTopAdapter) {
        private RuntimeBinding {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(writer, "writer");
            Objects.requireNonNull(nativeSurfaceTopAdapter, "nativeSurfaceTopAdapter");
        }
    }
}
