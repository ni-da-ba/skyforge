package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Services admitted deferred Skyforge terrain only after Minecraft has promoted target chunks to
 * stable loaded LevelChunks.
 *
 * <p>The service uses {@code ServerChunkCache#getChunkNow}, which never creates a generation ticket.
 * Missing chunks simply remain pending until Minecraft loads them for an independent reason. After
 * exact terrain catch-up, the normal native surface-population coordinator is replayed for that
 * chunk through the deferred-population lifecycle adapter; its existing idempotency ledger remains
 * authoritative. Persistent biome presentation is a separate admitted-volume obligation and is
 * likewise serviced only for already-loaded chunks.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgePhysicalVolumeCatchupService {
    private static final Comparator<Long> CHUNK_KEY_ORDER = Comparator
            .comparingInt((Long key) -> ChunkPos.getX(key))
            .thenComparingInt(key -> ChunkPos.getZ(key));

    /**
     * Composed-cave cursors deliberately expose very small resumable quanta. SF-IMP-0070 measured
     * those quanta as cheap but numerous, so servicing exactly one per 20-TPS tick introduced
     * minutes of scheduler latency. Pump multiple deterministic quanta while retaining both a hard
     * work cap and an elapsed-time guard. A single non-preemptible quantum may overrun the elapsed
     * guard, but no tick can start more than the hard work cap.
     */
    static final int MAX_TERRAIN_CATCHUP_CHUNKS_PER_LEVEL_TICK = 64;
    /** Issue #524 safety ceiling: no scheduler quantum may assign more than 1024 solid writes. */
    static final int MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM = 1024;
    /**
     * Evidence-tuned operating target. At 256 writes, two independent ordinary 4-CPU runs retained
     * low packet percentiles (p99 near 3.2 ms) but failed the issue #524 <16 ms max gate with
     * 17.07 ms and 24.99 ms outliers. Use 128 writes to halve deterministic mutation work per packet
     * while retaining the issue #524 1024-write hard safety ceiling.
     */
    static final int TARGET_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM = 128;
    static final long TERRAIN_CATCHUP_TIME_BUDGET_NANOS = 8_000_000L;
    static final int MAX_COMPOSED_CAVE_QUANTA_PER_LEVEL_TICK = 128;
    static final long COMPOSED_CAVE_TIME_BUDGET_NANOS = 8_000_000L;
    private static final int MAX_NATIVE_INTERIOR_POPULATION_CHUNKS_PER_LEVEL_TICK = 1;

    private SkyforgePhysicalVolumeCatchupService() {}

    private static long activeTerrainCatchupTimeBudgetNanos() {
        return SkyforgeDr70HumanReviewAtlasRuntime.foregroundPreparationActive()
                ? SkyforgeDr70HumanReviewAtlasRuntime.FOREGROUND_PREPARATION_TIME_BUDGET_NANOS
                : TERRAIN_CATCHUP_TIME_BUDGET_NANOS;
    }

    private static long activeComposedCaveTimeBudgetNanos() {
        return SkyforgeDr70HumanReviewAtlasRuntime.foregroundPreparationActive()
                ? SkyforgeDr70HumanReviewAtlasRuntime.FOREGROUND_PREPARATION_TIME_BUDGET_NANOS
                : COMPOSED_CAVE_TIME_BUDGET_NANOS;
    }

    /**
     * Services at most one canonical already-loaded deferred exact (volume, chunk) obligation.
     *
     * <p>Only one exact volume is completed per call even when several vertically stacked volumes
     * share the chunk. This makes the bounded pump genuinely preemptible between independent
     * volume writes. Native surface population is replayed only after the chunk has no remaining
     * currently eligible terrain catch-up, preserving the historical terrain-before-population
     * ordering across stacked volumes. A failed realization at the first loaded pending key remains
     * a deterministic barrier for this tick, preserving the canonical X/Z scheduling contract.
     */
    private static boolean serviceOneTerrainCatchupChunk(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        for (long chunkKey : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }

            SkyforgeNeoForge1211SurfaceStage.DeferredCatchupPacketResult packet;
            var mutationLifecycle = SkyforgeDeferredChunkMutationLifecycle.open(level, chunk);
            try {
                packet = SkyforgeNeoForge1211SurfaceStage.serviceOneCatchupPacket(
                        level,
                        chunk,
                        TARGET_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM);
            } finally {
                mutationLifecycle.close();
            }
            if (!packet.worked()) {
                return false;
            }
            if (packet.assignedSolidWrites() > MAX_ASSIGNED_SOLID_WRITES_PER_TERRAIN_QUANTUM) {
                throw new IllegalStateException("deferred terrain packet exceeded scheduler write budget");
            }
            // DR-70's neutral review carrier intentionally excludes DR-30 native structure
            // authoring. Production remains fail-closed through the runtime operation.
            if (packet.completed()
                    && !SkyforgeDr70HumanReviewAtlasRuntime.suppressNativeStructureRuntime()) {
                SkyforgeNativeStructureRuntimeOperation.execute(
                        level,
                        chunk,
                        packet.servicedVolumeId().orElseThrow());
            }
            return true;
        }
        return false;
    }

    /**
     * Bounded deferred-terrain pump. The first chunk is always allowed so a single expensive
     * materialization cannot starve progress; later chunks require remaining time budget.
     */
    static PumpResult pumpTerrainCatchupChunks(
            BooleanSupplier serviceOneChunk,
            LongSupplier nanoTime,
            int maximumChunks,
            long timeBudgetNanos) {
        return pumpBoundedWork(serviceOneChunk, nanoTime, maximumChunks, timeBudgetNanos);
    }

    /**
     * Services the first canonical pending cave chunk that can make progress without loading it.
     *
     * <p>Returning after one worked service call is intentional. The pump then refreshes
     * {@link SkyforgeComposedCaveStage#pendingChunkKeys()} and restarts from the beginning, exactly
     * matching the historical ordering across server ticks even when one obligation completes.
     */
    private static boolean serviceOneComposedCaveQuantum(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        var generator = chunkSource.getGenerator();
        for (long chunkKey : SkyforgeComposedCaveStage.pendingChunkKeys()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                continue;
            }
            if (SkyforgeComposedCaveStage.service(level, chunk, generator).worked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pure bounded-pump primitive retained package-visible for deterministic unit tests.
     *
     * <p>The first quantum is always allowed so a single slow non-preemptible operation cannot
     * permanently starve progress. Subsequent quanta require both remaining work capacity and
     * remaining elapsed-time budget.
     */
    static PumpResult pumpComposedCaveQuanta(
            BooleanSupplier serviceOneQuantum,
            LongSupplier nanoTime,
            int maximumQuanta,
            long timeBudgetNanos) {
        return pumpBoundedWork(serviceOneQuantum, nanoTime, maximumQuanta, timeBudgetNanos);
    }

    static List<Long> canonicalPopulationChunkKeys(Set<Long> chunkKeys) {
        Objects.requireNonNull(chunkKeys, "chunkKeys");
        List<Long> ordered = new ArrayList<>(chunkKeys);
        ordered.sort(CHUNK_KEY_ORDER);
        return List.copyOf(ordered);
    }

    static List<Long> earlierPopulationKeys(
            long candidateKey,
            List<Long> canonicalKeys) {
        Objects.requireNonNull(canonicalKeys, "canonicalKeys");
        List<Long> dependencies = new ArrayList<>();
        for (long key : canonicalKeys) {
            if (key == candidateKey) {
                break;
            }
            dependencies.add(key);
        }
        return List.copyOf(dependencies);
    }

    static boolean authoredSurfaceReadyForVolume(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        for (long chunkKey : SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId)) {
            ChunkPos chunkPos = new ChunkPos(chunkKey);
            boolean requiresAuthoredSurface = SkyforgeNativeSurfacePopulationStage.planForVolume(
                            chunkPos,
                            level.getMinBuildHeight(),
                            level.getHeight(),
                            volumeId)
                    .isPresent();
            if (requiresAuthoredSurface
                    && !SkyforgeAuthoredNativeSurfaceStage.completed(level, volumeId, chunkKey)) {
                return false;
            }
        }
        return true;
    }

    private static PumpResult pumpBoundedWork(
            BooleanSupplier serviceOneWorkItem,
            LongSupplier nanoTime,
            int maximumWorkItems,
            long timeBudgetNanos) {
        Objects.requireNonNull(serviceOneWorkItem, "serviceOneWorkItem");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (maximumWorkItems <= 0) {
            throw new IllegalArgumentException("maximumWorkItems must be positive");
        }
        if (timeBudgetNanos <= 0L) {
            throw new IllegalArgumentException("timeBudgetNanos must be positive");
        }

        long start = nanoTime.getAsLong();
        int workedItems = 0;
        while (workedItems < maximumWorkItems) {
            if (workedItems > 0
                    && Math.max(0L, nanoTime.getAsLong() - start) >= timeBudgetNanos) {
                break;
            }
            if (!serviceOneWorkItem.getAsBoolean()) {
                break;
            }
            workedItems++;
        }
        long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - start);
        return new PumpResult(workedItems, elapsedNanos);
    }

    record PumpResult(int workedQuanta, long elapsedNanos) {
        PumpResult {
            if (workedQuanta < 0 || elapsedNanos < 0L) {
                throw new IllegalArgumentException("pump result values must be nonnegative");
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!SkyforgePhysicalVolumeAdmissionStage.active()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var chunkSource = level.getChunkSource();
            var generator = chunkSource.getGenerator();

            // Deferred terrain materialization can be substantially more expensive than a cave
            // cursor quantum. Historically every loaded pending chunk was realized in one tick,
            // which allowed realistic large-volume acceptance to monopolize the server thread for
            // minutes. Preserve canonical order but yield between bounded work batches.
            long terrainPumpStart = SkyforgeRuntimePerformanceMetrics.start();
            PumpResult terrainPump = pumpTerrainCatchupChunks(
                    () -> serviceOneTerrainCatchupChunk(level),
                    System::nanoTime,
                    MAX_TERRAIN_CATCHUP_CHUNKS_PER_LEVEL_TICK,
                    activeTerrainCatchupTimeBudgetNanos());
            if (terrainPump.workedQuanta() > 0) {
                SkyforgeRuntimePerformanceMetrics.recordSince(
                        "catchup.terrainPump",
                        terrainPumpStart);
            }

            // Surface representation is geometry, not decoration. Realize every currently available
            // exact-volume authored surface before allowing any native population for that volume.
            // This keeps cross-chunk vegetation from becoming an input to a later surface pass when
            // Minecraft promotes otherwise identical chunks in different scheduler orders.
            var presentationChunkKeys = canonicalPopulationChunkKeys(
                    SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentationChunkKeys());
            for (long chunkKey : presentationChunkKeys) {
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null
                        || !SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos()).isEmpty()) {
                    continue;
                }
                for (var volumeId : SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentation(chunk.getPos())) {
                    // Older physical-admission proofs deliberately have no ecology plan. They retain
                    // their accepted no-op surface/population behavior rather than fabricating a biome
                    // authority merely to satisfy the authored-surface adapter.
                    if (SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId).isPresent()) {
                        SkyforgeAuthoredNativeSurfaceStage.apply(level, chunk, generator, volumeId);
                    }
                }
            }

            // Population is a whole-volume downstream phase of authored surfacing. The readiness
            // barrier is evaluated once per volume per tick and includes plan-bearing chunks that are
            // not currently loaded, using the surface completion ledger rather than adding tickets.
            var surfaceReadyByVolume = new java.util.HashMap<SkyIslandWorldVolumeId, Boolean>();
            for (long chunkKey : presentationChunkKeys) {
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null
                        || !SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos()).isEmpty()) {
                    continue;
                }
                for (var volumeId : SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentation(chunk.getPos())) {
                    boolean surfaceReady = surfaceReadyByVolume.computeIfAbsent(
                            volumeId,
                            candidate -> authoredSurfaceReadyForVolume(level, candidate));
                    if (!surfaceReady) {
                        continue;
                    }

                    // Native placed features both read and write mutable world state beyond their
                    // origin column. Even when direct attachment envelopes do not overlap, allowing
                    // independently scheduled chunks to populate in different orders changes native
                    // feature predicates and therefore the exact population outcome. The authored
                    // surface barrier above already requires the complete plan-bearing footprint to
                    // have been realized without adding tickets. After that barrier, serialize the
                    // volume's native population in one canonical X/Z order. Missing earlier chunks
                    // simply keep later chunks pending until Minecraft loads them independently.
                    List<Long> volumePopulationKeys = canonicalPopulationChunkKeys(
                            SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId));
                    boolean blockedByEarlierPopulation = false;
                    for (long earlierKey : earlierPopulationKeys(chunkKey, volumePopulationKeys)) {
                        if (!SkyforgeNativeSurfacePopulationStage.populationCompleted(
                                new ChunkPos(earlierKey),
                                level.getMinBuildHeight(),
                                level.getHeight(),
                                volumeId)) {
                            blockedByEarlierPopulation = true;
                            break;
                        }
                    }
                    if (blockedByEarlierPopulation) {
                        continue;
                    }
                    SkyforgeNativeSurfacePopulationStage.populateVolumeDeferred(
                            level, chunk, generator, volumeId);
                }
            }

            // Composed caves are a post-terrain exact-volume obligation. The stage itself gates on
            // whole-volume admission and on the absence of deferred terrain for the same
            // volume/chunk. getChunkNow preserves the no-ticket lifecycle contract. After each
            // successful micro-step, restart from the canonical first still-pending chunk. That
            // preserves the exact mutation order produced by the historical one-quantum-per-tick
            // policy while allowing multiple cheap cursor advances to share one server tick.
            long composedPumpStart = SkyforgeRuntimePerformanceMetrics.start();
            PumpResult composedPump = pumpComposedCaveQuanta(
                    () -> serviceOneComposedCaveQuantum(level),
                    System::nanoTime,
                    MAX_COMPOSED_CAVE_QUANTA_PER_LEVEL_TICK,
                    activeComposedCaveTimeBudgetNanos());
            if (composedPump.workedQuanta() > 0) {
                SkyforgeRuntimePerformanceMetrics.recordSince(
                        "catchup.composedCavePump",
                        composedPumpStart);
            }

            // Native interior population is downstream of the final composed cave topology.
            // The stage itself requires whole-volume cave completion and reuses the existing
            // surface-population biome resolver. As with cave catch-up, only already-loaded chunks
            // are visible here and work is explicitly bounded per tick.
            int servicedInteriorPopulationChunks = 0;
            for (long chunkKey : SkyforgeNativeInteriorPopulationStage.pendingChunkKeys()) {
                if (servicedInteriorPopulationChunks >= MAX_NATIVE_INTERIOR_POPULATION_CHUNKS_PER_LEVEL_TICK) {
                    break;
                }
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (SkyforgeNativeInteriorPopulationStage.service(level, chunk, generator).worked()) {
                    servicedInteriorPopulationChunks++;
                }
            }

            // Biome identity is committed only after admission and only on stable chunks Minecraft
            // already loaded independently. The obligation includes the admission-triggering chunk,
            // which may never have needed terrain catch-up, as well as all earlier deferred chunks.
            for (long chunkKey : canonicalPopulationChunkKeys(
                    SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentationChunkKeys())) {
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null
                        || !SkyforgePhysicalVolumeAdmissionStage.eligibleCatchup(chunk.getPos()).isEmpty()) {
                    continue;
                }
                for (var volumeId : SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentation(chunk.getPos())) {
                    boolean surfaceReady = surfaceReadyByVolume.computeIfAbsent(
                            volumeId,
                            candidate -> authoredSurfaceReadyForVolume(level, candidate));
                    if (!surfaceReady) {
                        continue;
                    }
                    SkyforgePersistentBiomePresentationStage.present(level, chunk, volumeId);
                    SkyforgePhysicalVolumeAdmissionStage.completeBiomePresentation(volumeId, chunk.getPos());
                }
            }
            SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.observeLoaded(level);
            SkyforgeNeoForge1211BiomePresentationDevRuntime.observeLoaded(level);
            SkyforgeNeoForge1211UndergroundPlacementDevRuntime.observeLoaded(level);
            if (level.dimension().equals(Level.OVERWORLD)) {
                SkyforgeNeoForge1211LocalModificationsDevRuntime.observeLoaded(level);
                SkyforgeNeoForge1211CarverDevRuntime.observeLoaded(level);
                SkyforgeNeoForge1211UndergroundDecorationDevRuntime.observeLoaded(level);
                SkyforgeNeoForge1211FluidSpringsDevRuntime.observeLoaded(level);
                SkyforgeNeoForge1211NativeLakesDevRuntime.observeLoaded(level);
            }
        }
    }
}
