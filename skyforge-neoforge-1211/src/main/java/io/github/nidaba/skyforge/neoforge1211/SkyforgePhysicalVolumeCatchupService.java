package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
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
    /**
     * Composed-cave cursors deliberately expose very small resumable quanta. SF-IMP-0070 measured
     * those quanta as cheap but numerous, so servicing exactly one per 20-TPS tick introduced
     * minutes of scheduler latency. Pump multiple deterministic quanta while retaining both a hard
     * work cap and an elapsed-time guard. A single non-preemptible quantum may overrun the elapsed
     * guard, but no tick can start more than the hard work cap.
     */
    static final int MAX_COMPOSED_CAVE_QUANTA_PER_LEVEL_TICK = 128;
    static final long COMPOSED_CAVE_TIME_BUDGET_NANOS = 8_000_000L;
    private static final int MAX_NATIVE_INTERIOR_POPULATION_CHUNKS_PER_LEVEL_TICK = 1;

    private SkyforgePhysicalVolumeCatchupService() {}

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
        Objects.requireNonNull(serviceOneQuantum, "serviceOneQuantum");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (maximumQuanta <= 0) {
            throw new IllegalArgumentException("maximumQuanta must be positive");
        }
        if (timeBudgetNanos <= 0L) {
            throw new IllegalArgumentException("timeBudgetNanos must be positive");
        }

        long start = nanoTime.getAsLong();
        int workedQuanta = 0;
        while (workedQuanta < maximumQuanta) {
            if (workedQuanta > 0
                    && Math.max(0L, nanoTime.getAsLong() - start) >= timeBudgetNanos) {
                break;
            }
            if (!serviceOneQuantum.getAsBoolean()) {
                break;
            }
            workedQuanta++;
        }
        long elapsedNanos = Math.max(0L, nanoTime.getAsLong() - start);
        return new PumpResult(workedQuanta, elapsedNanos);
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
            for (long chunkKey : SkyforgePhysicalVolumeAdmissionStage.eligibleCatchupChunkKeys()) {
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                int completed;
                var mutationLifecycle = SkyforgeDeferredChunkMutationLifecycle.open(level, chunk);
                try {
                    completed = SkyforgeNeoForge1211SurfaceStage.serviceCatchup(chunk);
                } finally {
                    mutationLifecycle.close();
                }
                if (completed > 0) {
                    SkyforgeNativeSurfacePopulationStage.populateDeferred(level, chunk, generator);
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
                    COMPOSED_CAVE_TIME_BUDGET_NANOS);
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
            for (long chunkKey : SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentationChunkKeys()) {
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (var volumeId : SkyforgePhysicalVolumeAdmissionStage.eligibleBiomePresentation(chunk.getPos())) {
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
