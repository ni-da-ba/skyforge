package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Optional Minecraft runtime gate between completed native content and destructive Skyforge writes.
 *
 * <p>When absent, historical Skyforge fixtures retain their accepted behavior. When installed, the
 * stage surveys exact planned volumes after native generation completes and permits solid writes
 * only for whole volumes whose admission ledger has reached ADMITTED. Chunks encountered while a
 * volume remains PLANNED retain only immutable catch-up evidence; no mutable generation-region or
 * chunk reference is retained.
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

    /**
     * Supplies completed BASE_WORLD evidence immediately before Skyforge realization for this chunk.
     *
     * <p>A clear chunk observed before the whole volume can be admitted is retained as an immutable
     * deferred-realization record. The optional native-surface snapshot is the accepted pre-decoration
     * representation evidence; retaining it prevents later vegetation or structures from being
     * mistaken for the native terrain material when the chunk catches up.
     */
    static void observeBeforeRealization(
            ChunkAccess chunk,
            Optional<MinecraftNativeSurfaceSnapshot> nativeSurfaceSnapshot) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(nativeSurfaceSnapshot, "nativeSurfaceSnapshot");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return;
        }
        nativeSurfaceSnapshot.ifPresent(snapshot -> {
            if (!snapshot.chunkPos().equals(chunk.getPos())) {
                throw new IllegalArgumentException("native surface snapshot differs from observed chunk");
            }
        });

        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        synchronized (binding) {
            for (var volume : binding.catalog().query(chunkBounds.worldBounds())) {
                SkyIslandWorldVolumeId volumeId = volume.id();
                SkyforgePhysicalVolumeAdmissionState before = binding.ledger().state(volumeId);
                if (before == SkyforgePhysicalVolumeAdmissionState.REJECTED
                        || before == SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
                    continue;
                }

                var observation = binding.ledger().observe(SkyforgeNativeChunkOccupancySurvey.survey(volumeId, chunk));
                if (observation.state() == SkyforgePhysicalVolumeAdmissionState.PLANNED) {
                    PendingRealization pending = new PendingRealization(
                            volumeId,
                            chunk.getPos().toLong(),
                            nativeSurfaceSnapshot);
                    var byChunk = binding.pendingByVolume()
                            .computeIfAbsent(volumeId, ignored -> new HashMap<>());
                    PendingRealization previous = byChunk.putIfAbsent(pending.chunkKey(), pending);
                    if (previous != null && !previous.equals(pending)) {
                        throw new IllegalStateException(
                                "deferred physical realization evidence changed for an already pending volume/chunk");
                    }
                } else if (observation.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
                    binding.pendingByVolume().remove(volumeId);
                }
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

    /** Exact-volume population is valid only after physical admission has become terminal ADMITTED. */
    static boolean allowsPopulation(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        return binding == null || binding.ledger().admitted(volumeId);
    }

    static SkyforgePhysicalVolumeAdmissionLedger.Observation snapshot(SkyIslandWorldVolumeId volumeId) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("no physical Skyforge volume-admission stage is installed");
        }
        return binding.ledger().snapshot(volumeId);
    }

    /** Eligible deferred writes for one already-available chunk. Returned records remain pending. */
    static List<PendingRealization> eligibleCatchup(ChunkPos chunkPos) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }
        long chunkKey = chunkPos.toLong();
        List<PendingRealization> eligible = new ArrayList<>();
        synchronized (binding) {
            for (var entry : binding.pendingByVolume().entrySet()) {
                if (!binding.ledger().admitted(entry.getKey())) {
                    continue;
                }
                PendingRealization pending = entry.getValue().get(chunkKey);
                if (pending != null) {
                    eligible.add(pending);
                }
            }
        }
        return List.copyOf(eligible);
    }

    /** All chunk keys that currently have at least one ADMITTED deferred realization. */
    static Set<Long> eligibleCatchupChunkKeys() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        Set<Long> keys = new LinkedHashSet<>();
        synchronized (binding) {
            for (var entry : binding.pendingByVolume().entrySet()) {
                if (binding.ledger().admitted(entry.getKey())) {
                    keys.addAll(entry.getValue().keySet());
                }
            }
        }
        return Set.copyOf(keys);
    }

    /** Marks one exact deferred write complete only after its concrete chunk write has succeeded. */
    static void completeCatchup(PendingRealization pending) {
        Objects.requireNonNull(pending, "pending");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("physical admission stage disappeared during deferred realization");
        }
        synchronized (binding) {
            if (!binding.ledger().admitted(pending.volumeId())) {
                throw new IllegalStateException("cannot complete deferred realization for a non-admitted volume");
            }
            Map<Long, PendingRealization> byChunk = binding.pendingByVolume().get(pending.volumeId());
            if (byChunk == null) {
                return;
            }
            PendingRealization existing = byChunk.get(pending.chunkKey());
            if (existing == null) {
                return;
            }
            if (!existing.equals(pending)) {
                throw new IllegalStateException("deferred realization identity changed before completion");
            }
            byChunk.remove(pending.chunkKey());
            if (byChunk.isEmpty()) {
                binding.pendingByVolume().remove(pending.volumeId());
            }
        }
    }

    /** Chunks skipped before later whole-volume admission; exposed for proof diagnostics. */
    static Set<Long> pendingCatchupChunks(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        synchronized (binding) {
            Map<Long, PendingRealization> byChunk = binding.pendingByVolume().get(volumeId);
            return byChunk == null ? Set.of() : Set.copyOf(byChunk.keySet());
        }
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    private static WorldBounds pointBounds(int x, int y, int z) {
        return new WorldBounds(x, x, y, y, z, z);
    }

    record PendingRealization(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey,
            Optional<MinecraftNativeSurfaceSnapshot> nativeSurfaceSnapshot) {
        PendingRealization {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(nativeSurfaceSnapshot, "nativeSurfaceSnapshot");
            nativeSurfaceSnapshot.ifPresent(snapshot -> {
                if (snapshot.chunkPos().toLong() != chunkKey) {
                    throw new IllegalArgumentException("deferred native surface snapshot belongs to another chunk");
                }
            });
        }

        ChunkPos chunkPos() {
            return new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        }
    }

    private record Binding(
            SkyIslandWorldCatalog catalog,
            SkyforgePhysicalVolumeAdmissionLedger ledger,
            Map<SkyIslandWorldVolumeId, Map<Long, PendingRealization>> pendingByVolume) {
        private Binding {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(pendingByVolume, "pendingByVolume");
        }
    }
}
