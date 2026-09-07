package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
                new HashMap<>(),
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

                var survey = SkyforgeRuntimePerformanceMetrics.measure(
                        "admission.nativeOccupancySurvey",
                        () -> SkyforgeNativeChunkOccupancySurvey.survey(volumeId, chunk));
                var observation = binding.ledger().observe(survey);
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
                    binding.pendingBiomePresentationByVolume().remove(volumeId);
                } else if (observation.state() == SkyforgePhysicalVolumeAdmissionState.ADMITTED
                        && observation.transitionedNow()) {
                    Set<Long> previous = binding.pendingBiomePresentationByVolume().put(
                            volumeId,
                            new LinkedHashSet<>(binding.ledger().requiredChunkKeys(volumeId)));
                    if (previous != null) {
                        throw new IllegalStateException("biome-presentation obligations already existed before admission");
                    }
                }
            }
        }
    }

    /**
     * Fail-closed position gate used by the solid-overlay writer.
     *
     * <p>A PLANNED exact owner blocks the coordinate because its eventual physical presence is not
     * yet known. An ADMITTED owner authorizes it. A REJECTED owner is physically absent and therefore
     * cannot veto another admitted owner. If all exact owners are rejected, no Skyforge write occurs.
     */
    static boolean allowsWriteAt(int worldX, int worldY, int worldZ) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return true;
        }
        var candidates = binding.catalog().query(pointBounds(worldX, worldY, worldZ));
        boolean admittedOwner = false;
        for (var candidate : candidates) {
            boolean owned = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                            candidate.id(), worldX, worldY, worldZ)
                    .orElseThrow(() -> new IllegalStateException(
                            "Skyforge terrain binding disappeared during physical write admission"));
            if (!owned) {
                continue;
            }
            SkyforgePhysicalVolumeAdmissionState state = binding.ledger().state(candidate.id());
            if (state == SkyforgePhysicalVolumeAdmissionState.PLANNED) {
                return false;
            }
            if (state == SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
                admittedOwner = true;
            }
        }
        return admittedOwner;
    }

    /**
     * Returns whether direct composite terrain realization can currently produce an authorized write.
     *
     * <p>When admission is absent, historical direct-realization behavior is preserved. With
     * admission active, at least one exact candidate volume intersecting the chunk must already be
     * ADMITTED. PLANNED candidates have already retained immutable deferred catch-up evidence during
     * observation, so projecting them immediately would only perform work the writer must reject.
     * Mixed states remain safe: one ADMITTED candidate keeps the historical composite writer path,
     * where PLANNED coordinates are still fenced by {@link #allowsWriteAt(int, int, int)}.
     */
    static boolean allowsDirectRealization(ChunkAccess chunk) {
        Objects.requireNonNull(chunk, "chunk");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return true;
        }
        MinecraftChunkBounds chunkBounds = new MinecraftChunkBounds(
                chunk.getPos(),
                chunk.getMinBuildHeight(),
                chunk.getHeight());
        synchronized (binding) {
            for (var volume : binding.catalog().query(chunkBounds.worldBounds())) {
                if (binding.ledger().admitted(volume.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Exact-volume population is valid only after physical admission has become terminal ADMITTED. */
    static boolean allowsPopulation(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        return binding == null || binding.ledger().admitted(volumeId);
    }

    /** Finite semantic bounds used to avoid scanning unrelated Minecraft biome sections. */
    static WorldBounds volumeBounds(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("no physical Skyforge volume-admission stage is installed");
        }
        return binding.catalog().volumes().stream()
                .filter(volume -> volume.id().equals(volumeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown physical-admission volume: " + volumeId.path()))
                .bounds();
    }

    static SkyforgePhysicalVolumeAdmissionLedger.Observation snapshot(SkyIslandWorldVolumeId volumeId) {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("no physical Skyforge volume-admission stage is installed");
        }
        return binding.ledger().snapshot(volumeId);
    }

    /** Exact finite chunk footprint already owned by the physical-admission ledger. */
    static Set<Long> requiredChunkKeys(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("no physical Skyforge volume-admission stage is installed");
        }
        return binding.ledger().requiredChunkKeys(volumeId);
    }

    /** Whether one exact admitted volume/chunk still owes deferred terrain realization. */
    static boolean hasPendingCatchup(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(chunkPos, "chunkPos");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return false;
        }
        synchronized (binding) {
            Map<Long, PendingRealization> byChunk = binding.pendingByVolume().get(volumeId);
            return byChunk != null && byChunk.containsKey(chunkPos.toLong());
        }
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
        return orderedChunkKeys(keys);
    }

    /** All loaded-on-demand chunk keys that still owe persistent exact-volume biome presentation. */
    static Set<Long> eligibleBiomePresentationChunkKeys() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        Set<Long> keys = new LinkedHashSet<>();
        synchronized (binding) {
            for (var entry : binding.pendingBiomePresentationByVolume().entrySet()) {
                if (binding.ledger().admitted(entry.getKey())) {
                    keys.addAll(entry.getValue());
                }
            }
        }
        return orderedChunkKeys(keys);
    }

    /**
     * Canonical chunk scheduling order for deferred production work.
     *
     * <p>Admission evidence can arrive through HashMap-backed ledgers and Minecraft chunk callbacks
     * in different orders across otherwise identical JVM runs. Production mutation order must not
     * inherit that incidental ordering because native population/carvers can observe already-written
     * neighboring state. Sort by chunk X/Z before exposing any bounded catch-up iteration.
     */
    static Set<Long> orderedChunkKeys(Iterable<Long> chunkKeys) {
        Objects.requireNonNull(chunkKeys, "chunkKeys");
        List<Long> ordered = new ArrayList<>();
        for (Long key : chunkKeys) {
            ordered.add(Objects.requireNonNull(key, "chunk key"));
        }
        ordered.sort(Comparator
                .comparingInt((Long key) -> ChunkPos.getX(key))
                .thenComparingInt(key -> ChunkPos.getZ(key)));
        return Collections.unmodifiableSet(new LinkedHashSet<>(ordered));
    }

    /** Exact admitted volumes that still owe biome presentation in one already-available chunk. */
    static List<SkyIslandWorldVolumeId> eligibleBiomePresentation(ChunkPos chunkPos) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }
        long chunkKey = chunkPos.toLong();
        List<SkyIslandWorldVolumeId> eligible = new ArrayList<>();
        synchronized (binding) {
            for (var entry : binding.pendingBiomePresentationByVolume().entrySet()) {
                if (binding.ledger().admitted(entry.getKey()) && entry.getValue().contains(chunkKey)) {
                    eligible.add(entry.getKey());
                }
            }
        }
        return List.copyOf(eligible);
    }

    /** Completes one durable biome-presentation obligation after the chunk storage mutation succeeds. */
    static void completeBiomePresentation(SkyIslandWorldVolumeId volumeId, ChunkPos chunkPos) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(chunkPos, "chunkPos");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            throw new IllegalStateException("physical admission stage disappeared during biome presentation");
        }
        synchronized (binding) {
            if (!binding.ledger().admitted(volumeId)) {
                throw new IllegalStateException("cannot complete biome presentation for a non-admitted volume");
            }
            Set<Long> pending = binding.pendingBiomePresentationByVolume().get(volumeId);
            if (pending == null) {
                return;
            }
            pending.remove(chunkPos.toLong());
            if (pending.isEmpty()) {
                binding.pendingBiomePresentationByVolume().remove(volumeId);
            }
        }
    }

    static Set<Long> pendingBiomePresentationChunks(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        synchronized (binding) {
            Set<Long> pending = binding.pendingBiomePresentationByVolume().get(volumeId);
            return pending == null ? Set.of() : Set.copyOf(pending);
        }
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
            Map<SkyIslandWorldVolumeId, Map<Long, PendingRealization>> pendingByVolume,
            Map<SkyIslandWorldVolumeId, Set<Long>> pendingBiomePresentationByVolume) {
        private Binding {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(pendingByVolume, "pendingByVolume");
            Objects.requireNonNull(pendingBiomePresentationByVolume, "pendingBiomePresentationByVolume");
        }
    }
}
