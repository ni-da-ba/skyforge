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
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/** Runtime binding between compiled Skyforge terrain and the Minecraft 1.21.1 adapter. */
public final class SkyforgeNeoForge1211SurfaceStage {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();

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

    private static boolean realizeDeferred(
            RuntimeBinding binding,
            ChunkAccess chunk,
            SkyforgePhysicalVolumeAdmissionStage.PendingRealization pending) {
        long performanceStart = SkyforgeRuntimePerformanceMetrics.start();
        MinecraftChunkMaterialization materialization = binding.adapter().materialize(
                pending.volumeId(),
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        if (binding.nativeSurfaceTopAdapter().isPresent()) {
            MinecraftNativeSurfaceSnapshot snapshot = pending.nativeSurfaceSnapshot()
                    .orElseThrow(() -> new IllegalStateException(
                            "native-surface-adapted deferred realization lost its pre-decoration snapshot"));
            materialization = binding.nativeSurfaceTopAdapter().orElseThrow().adapt(snapshot, materialization);
        }

        int expectedSolidBlocks = materialization.solidBlockCount();
        MinecraftChunkWriteResult result = binding.writer().writeSolidOverlay(chunk, materialization);
        if (result.solidBlockCount() != expectedSolidBlocks) {
            // Another exact volume still owns at least one blocked coordinate. Keep the record
            // pending until all owners have terminal admission decisions.
            return false;
        }
        SkyforgePhysicalVolumeAdmissionStage.completeCatchup(pending);
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

    /** Backward-compatible scalar view of the richer composite early-height query. */
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

    /**
     * Legacy composite early-height query retained for diagnostics and compatibility tests.
     *
     * <p>Ordinary base-world generation must not call this under SF-IMP-0052. Island-owned
     * generation uses the exact-volume overload below.
     */
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

    /**
     * Evaluates one exact independently compiled island as an early height query.
     *
     * <p>Vanilla terrain and other stacked islands are intentionally invisible. An empty island
     * column remains empty rather than falling through to a different terrain owner.
     */
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

    /** Returns the backend-neutral bounds of one exact runtime-bound island volume. */
    static Optional<WorldBounds> volumeBounds(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null ? Optional.empty() : binding.adapter().volumeBounds(volumeId);
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

    /** Cheap catalog prefilter used before snapshot capture or full terrain projection. */
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
