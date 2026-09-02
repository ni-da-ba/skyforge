package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Optional Minecraft runtime gate between completed native content and destructive Skyforge writes.
 *
 * <p>When absent, historical Skyforge fixtures retain their accepted behavior. When installed, the
 * stage surveys exact planned volumes before the writer mutates a chunk and permits solid writes
 * only for whole volumes whose admission ledger has reached ADMITTED.
 */
final class SkyforgePhysicalVolumeAdmissionStage {
    private static final AtomicReference<Binding> ACTIVE = new AtomicReference<>();

    private SkyforgePhysicalVolumeAdmissionStage() {}

    static AutoCloseable install(SkyIslandWorldCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        Binding binding = new Binding(
                catalog,
                new SkyforgePhysicalVolumeAdmissionLedger(catalog.volumes()),
                new HashMap<>());
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a physical Skyforge volume-admission stage is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("physical Skyforge volume-admission stage changed before close");
            }
        };
    }

    /** Called once immediately before a writer begins mutating the supplied completed native chunk. */
    static void observeBeforeWrite(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return;
        }

        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        for (var volume : binding.catalog().query(chunkBounds.worldBounds())) {
            SkyIslandWorldVolumeId volumeId = volume.id();
            SkyforgePhysicalVolumeAdmissionState before = binding.ledger().state(volumeId);
            if (before == SkyforgePhysicalVolumeAdmissionState.REJECTED
                    || before == SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
                continue;
            }

            var observation = binding.ledger().observe(SkyforgeNativeChunkOccupancySurvey.survey(volumeId, chunk));
            if (observation.state() == SkyforgePhysicalVolumeAdmissionState.PLANNED) {
                binding.pendingChunks()
                        .computeIfAbsent(volumeId, ignored -> new HashSet<>())
                        .add(chunk.getPos().toLong());
            } else if (observation.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
                binding.pendingChunks().remove(volumeId);
            }
        }
    }

    /**
     * Fail-closed position gate used by the solid-overlay writer.
     *
     * <p>If the active catalog says one or more exact volumes own this coordinate, every owner must
     * be ADMITTED. This also prevents an as-yet-unresolved Skyforge/Skyforge overlap from becoming a
     * loophole in the native-content collision gate.
     */
    static boolean allowsWriteAt(int worldX, int worldY, int worldZ) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return true;
        }
        var candidates = binding.catalog().query(pointBounds(worldX, worldY, worldZ));
        boolean foundOwner = false;
        for (var candidate : candidates) {
            boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            candidate.id(), worldX, worldY, worldZ)
                    .orElseThrow(() -> new IllegalStateException(
                            "Skyforge terrain binding disappeared during physical write admission"));
            if (!owned) {
                continue;
            }
            foundOwner = true;
            if (!binding.ledger().admitted(candidate.id())) {
                return false;
            }
        }
        return foundOwner;
    }

    static SkyforgePhysicalVolumeAdmissionLedger.Observation snapshot(SkyIslandWorldVolumeId volumeId) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("no physical Skyforge volume-admission stage is installed");
        }
        return binding.ledger().snapshot(volumeId);
    }

    /** Chunks skipped before a later whole-volume admission; these require safe catch-up realization. */
    static Set<Long> pendingCatchupChunks(SkyIslandWorldVolumeId volumeId) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        return Set.copyOf(binding.pendingChunks().getOrDefault(volumeId, Set.of()));
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    private static WorldBounds pointBounds(int x, int y, int z) {
        return new WorldBounds(x, x, y, y, z, z);
    }

    private record Binding(
            SkyIslandWorldCatalog catalog,
            SkyforgePhysicalVolumeAdmissionLedger ledger,
            Map<SkyIslandWorldVolumeId, Set<Long>> pendingChunks) {
        private Binding {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(pendingChunks, "pendingChunks");
        }
    }
}
