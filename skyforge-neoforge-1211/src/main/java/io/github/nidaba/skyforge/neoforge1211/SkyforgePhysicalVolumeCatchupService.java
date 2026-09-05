package io.github.nidaba.skyforge.neoforge1211;

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
     * Native composed-cave realization is materially more expensive than ordinary ledger scans.
     * Bound stable-chunk catch-up so a burst of independently loaded chunks cannot monopolize one
     * server tick for an entire exact volume.
     */
    private static final int MAX_COMPOSED_CAVE_CHUNKS_PER_LEVEL_TICK = 1;
    private static final int MAX_NATIVE_INTERIOR_POPULATION_CHUNKS_PER_LEVEL_TICK = 1;

    private SkyforgePhysicalVolumeCatchupService() {}

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
            // volume/chunk. getChunkNow preserves the no-ticket lifecycle contract. Unlike the
            // inexpensive ledger scan, actual composed realization is explicitly bounded per tick:
            // a mass chunk-load event must not drain a whole island's cave work in one server tick.
            int servicedComposedCaveChunks = 0;
            for (long chunkKey : SkyforgeComposedCaveStage.pendingChunkKeys()) {
                if (servicedComposedCaveChunks >= MAX_COMPOSED_CAVE_CHUNKS_PER_LEVEL_TICK) {
                    break;
                }
                int chunkX = ChunkPos.getX(chunkKey);
                int chunkZ = ChunkPos.getZ(chunkKey);
                LevelChunk chunk = chunkSource.getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                if (SkyforgeComposedCaveStage.service(level, chunk, generator).worked()) {
                    servicedComposedCaveChunks++;
                }
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
