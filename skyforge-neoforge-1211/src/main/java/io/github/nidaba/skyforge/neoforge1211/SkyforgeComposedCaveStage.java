package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Production-facing lifecycle stage for admitted exact-volume composed cave realization.
 *
 * <p>Each installed plan expands to the same finite broad X/Z chunk footprint already owned by the
 * physical-admission ledger. Obligations begin PENDING and transition exactly once to COMPLETED.
 * Service is permitted only after whole-volume admission, after any deferred terrain obligation for
 * the same exact volume/chunk is gone, and on a {@link LevelChunk} that Minecraft loaded for an
 * independent reason.
 *
 * <p>The stage owns no Minecraft biome mapping. It reuses the exact-volume native surface
 * population plan selected by {@link SkyforgeNativeSurfacePopulationStage} and passes that plan's
 * resolver into {@link SkyforgeComposedCaveRealizer}.
 */
final class SkyforgeComposedCaveStage {
    private static final int MAX_PREPARE_COLUMNS_PER_SERVICE = 8;
    private static final int MAX_PREPARE_VOXELS_PER_SERVICE = 2048;
    private static final int MAX_PREPARE_CANONICAL_SAMPLES_PER_SERVICE = 32;
    private static final AtomicReference<Binding> ACTIVE = new AtomicReference<>();

    private SkyforgeComposedCaveStage() {}

    static AutoCloseable install(List<SkyforgeComposedCavePlan> plans) {
        Objects.requireNonNull(plans, "plans");
        if (!SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "composed cave stage requires an installed physical volume-admission stage");
        }

        LinkedHashMap<ObligationKey, Obligation> obligations = new LinkedHashMap<>();
        Set<SkyIslandWorldVolumeId> volumeIds = new HashSet<>();
        for (SkyforgeComposedCavePlan plan : List.copyOf(plans)) {
            Objects.requireNonNull(plan, "composed cave plan");
            SkyIslandWorldVolumeId volumeId = plan.volume().id();
            if (!volumeIds.add(volumeId)) {
                throw new IllegalArgumentException(
                        "duplicate composed cave plan for volume " + volumeId.path());
            }

            var columns = new SkyIslandCompiledVolumeColumnField(plan.volume().compiledVolume());
            var realizedAuthoredField = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                    plan.authoredField(),
                    columns);
            var spatialIndex = SkyforgeExteriorConnectedCaveSpatialIndex.create(plan.authoredField());

            List<Long> chunkKeys = new ArrayList<>(
                    SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId));
            chunkKeys.sort(Comparator
                    .comparingInt((Long key) -> ChunkPos.getX(key))
                    .thenComparingInt(key -> ChunkPos.getZ(key)));
            for (long chunkKey : chunkKeys) {
                ObligationKey key = new ObligationKey(volumeId, chunkKey);
                Obligation previous = obligations.put(
                        key,
                        new Obligation(
                                plan,
                                columns,
                                realizedAuthoredField,
                                spatialIndex,
                                State.PENDING,
                                null));
                if (previous != null) {
                    throw new IllegalStateException(
                            "duplicate composed cave obligation " + volumeId.path() + "/" + chunkPos(chunkKey));
                }
            }
        }
        if (obligations.isEmpty()) {
            throw new IllegalArgumentException("composed cave stage requires at least one exact-volume obligation");
        }

        Binding binding = new Binding(
                obligations,
                new LinkedHashMap<>(),
                new LinkedHashMap<>());
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a composed cave production stage is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("composed cave production stage changed before close");
            }
        };
    }

    /**
     * Services every eligible exact-volume obligation for one already-loaded stable chunk.
     *
     * <p>This method never asks Minecraft for a chunk. Callers must obtain the supplied chunk through
     * a no-ticket path such as {@code ServerChunkCache#getChunkNow}.
     */
    static ServiceResult service(
            ServerLevel level,
            LevelChunk chunk,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("composed cave service chunk belongs to another level");
        }

        Binding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return ServiceResult.idle();
        }

        long chunkKey = chunk.getPos().toLong();
        List<ObligationKey> candidates = new ArrayList<>();
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (entry.getKey().chunkKey() == chunkKey
                        && entry.getValue().state() == State.PENDING) {
                    candidates.add(entry.getKey());
                }
            }
        }

        for (ObligationKey key : candidates) {
            Obligation obligation = requirePending(binding, key);
            SkyIslandWorldVolume volume = obligation.plan().volume();
            SkyIslandWorldVolumeId volumeId = volume.id();

            if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
                continue;
            }
            if (SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(volumeId, chunk.getPos())) {
                continue;
            }

            SkyforgeNativeSurfacePopulationPlan populationPlan =
                    SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "admitted composed cave obligation has no exact-volume native surface plan: "
                                            + volumeId.path() + "/" + chunk.getPos()));

            Optional<OwnerSpan> cachedOwnerSpan;
            synchronized (binding) {
                cachedOwnerSpan = binding.ownerSpans().get(key);
                if (cachedOwnerSpan == null) {
                    cachedOwnerSpan = Optional.ofNullable(
                            widestOwnerSpan(volume, obligation.columns(), chunk));
                    binding.ownerSpans().put(key, cachedOwnerSpan);
                }
            }
            OwnerSpan ownerSpan = cachedOwnerSpan.orElse(null);

            if (ownerSpan == null) {
                if (containsAuthoredPositive(
                        volume,
                        obligation.columns(),
                        obligation.realizedAuthoredField(),
                        obligation.spatialIndex(),
                        chunk)) {
                    throw new IllegalStateException(
                            "AUTH-0030-positive composed cave chunk has no exact owner-solid terrain: "
                                    + volumeId.path() + "/" + chunk.getPos());
                }
                Completion completion = Completion.empty(volumeId, chunk.getPos());
                complete(binding, key, obligation, completion);
                return ServiceResult.completed(completion);
            }

            if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
                throw new IllegalStateException(
                        "composed cave realization requires Minecraft's active noise generator");
            }

            SkyforgeExteriorConnectedCavePreparationCursor preparation;
            synchronized (binding) {
                preparation = binding.preparations().get(key);
                if (preparation == null) {
                    preparation = new SkyforgeExteriorConnectedCavePreparationCursor(
                            volume,
                            obligation.realizedAuthoredField(),
                            obligation.spatialIndex(),
                            chunk.getPos(),
                            chunk.getMinBuildHeight(),
                            chunk.getMaxBuildHeight(),
                            position -> ownerSolid(
                                    volumeId,
                                    position.getX(),
                                    position.getY(),
                                    position.getZ()),
                            position -> foreignSolid(
                                    volumeId,
                                    position.getX(),
                                    position.getY(),
                                    position.getZ()));
                    binding.preparations().put(key, preparation);
                }
            }

            var advance = preparation.advance(
                    MAX_PREPARE_COLUMNS_PER_SERVICE,
                    MAX_PREPARE_VOXELS_PER_SERVICE,
                    MAX_PREPARE_CANONICAL_SAMPLES_PER_SERVICE);
            if (!advance.complete()) {
                return advance.worked() ? ServiceResult.worked() : ServiceResult.idle();
            }

            var prepared = preparation.prepared();
            if (prepared.unsafePositiveSamples() > 0) {
                throw new IllegalStateException(
                        "AUTH-0030 authored preflight rejected before native carving: unsafePositiveSamples="
                                + prepared.unsafePositiveSamples()
                                + ", firstUnsafe=" + prepared.firstUnsafePosition());
            }

            BlockPos biomeSample = new BlockPos(
                    ownerSpan.x(),
                    ownerSpan.minimumY() + (ownerSpan.maximumY() - ownerSpan.minimumY()) / 2,
                    ownerSpan.z());
            var result = SkyforgeComposedCaveRealizer.realizePrepared(
                    level,
                    noiseGenerator,
                    populationPlan.biomeResolver(),
                    volume,
                    prepared,
                    chunk,
                    biomeSample,
                    ownerSpan.minimumY(),
                    ownerSpan.maximumY());
            Completion completion = new Completion(
                    volumeId,
                    chunk.getPos(),
                    biomeSample,
                    ownerSpan.minimumY(),
                    ownerSpan.maximumY(),
                    Optional.of(result));
            synchronized (binding) {
                binding.preparations().remove(key);
            }
            complete(binding, key, obligation, completion);
            return ServiceResult.completed(completion);
        }
        return ServiceResult.idle();
    }

    /** Chunk keys with at least one still-pending exact-volume obligation. */
    static Set<Long> pendingChunkKeys() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (entry.getValue().state() == State.PENDING) {
                    keys.add(entry.getKey().chunkKey());
                }
            }
        }
        // Preserve installation order so the stable-chunk catch-up service can apply a bounded
        // per-tick budget without introducing hash-order-dependent scheduling.
        return Collections.unmodifiableSet(keys);
    }

    static Snapshot snapshot() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return new Snapshot(0, 0, 0, 0);
        }
        int pending = 0;
        int completed = 0;
        int empty = 0;
        synchronized (binding) {
            for (Obligation obligation : binding.obligations().values()) {
                if (obligation.state() == State.PENDING) {
                    pending++;
                } else {
                    completed++;
                    if (obligation.completion() != null
                            && obligation.completion().result().isEmpty()) {
                        empty++;
                    }
                }
            }
        }
        return new Snapshot(binding.obligations().size(), pending, completed, empty);
    }

    static Snapshot snapshot(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return new Snapshot(0, 0, 0, 0);
        }
        int total = 0;
        int pending = 0;
        int completed = 0;
        int empty = 0;
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (!entry.getKey().volumeId().equals(volumeId)) {
                    continue;
                }
                total++;
                Obligation obligation = entry.getValue();
                if (obligation.state() == State.PENDING) {
                    pending++;
                } else {
                    completed++;
                    if (obligation.completion() != null
                            && obligation.completion().result().isEmpty()) {
                        empty++;
                    }
                }
            }
        }
        return new Snapshot(total, pending, completed, empty);
    }

    static List<Completion> completed() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }
        List<Completion> result = new ArrayList<>();
        synchronized (binding) {
            for (Obligation obligation : binding.obligations().values()) {
                if (obligation.state() == State.COMPLETED) {
                    result.add(Objects.requireNonNull(obligation.completion(), "completed obligation missing evidence"));
                }
            }
        }
        return List.copyOf(result);
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    private static Obligation requirePending(
            Binding binding,
            ObligationKey key) {
        synchronized (binding) {
            Obligation obligation = binding.obligations().get(key);
            if (obligation == null) {
                throw new IllegalStateException("composed cave obligation disappeared");
            }
            return obligation;
        }
    }

    private static void complete(
            Binding binding,
            ObligationKey key,
            Obligation expected,
            Completion completion) {
        Objects.requireNonNull(completion, "completion");
        synchronized (binding) {
            Obligation current = binding.obligations().get(key);
            if (current != expected) {
                throw new IllegalStateException("composed cave obligation changed during service");
            }
            if (current.state() != State.PENDING || current.completion() != null) {
                throw new IllegalStateException("composed cave obligation replayed after completion");
            }
            binding.obligations().put(
                    key,
                    new Obligation(
                            current.plan(),
                            current.columns(),
                            current.realizedAuthoredField(),
                            current.spatialIndex(),
                            State.COMPLETED,
                            completion));
        }
    }

    private static OwnerSpan widestOwnerSpan(
            SkyIslandWorldVolume volume,
            SkyIslandCompiledVolumeColumnField columns,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                ceilToInt(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                floorToInt(volume.bounds().maximumY()));
        if (maximumY < minimumY) {
            return null;
        }

        var descriptor = volume.compiledVolume().descriptor();
        OwnerSpan best = null;
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                var column = columns.columnAt(local);
                if (column.isEmpty()) {
                    continue;
                }

                var present = column.orElseThrow();
                int first = Math.max(
                        minimumY,
                        Math.addExact(floorToInt(present.undersideY()), 1));
                int last = Math.min(
                        maximumY,
                        Math.subtractExact(ceilToInt(present.upperY()), 1));
                if (last < first) {
                    continue;
                }

                // The compiled column gives the authoritative broad vertical envelope. Verify the
                // discrete endpoints through the exact ownership seam so any future density
                // refinement remains fail-closed without restoring a full per-Y scan.
                while (first <= last && !ownerSolid(volume.id(), x, first, z)) {
                    first++;
                }
                while (last >= first && !ownerSolid(volume.id(), x, last, z)) {
                    last--;
                }
                if (last < first) {
                    continue;
                }

                OwnerSpan candidate = new OwnerSpan(x, z, first, last);
                if (best == null
                        || candidate.maximumY() - candidate.minimumY()
                                > best.maximumY() - best.minimumY()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static boolean containsAuthoredPositive(
            SkyIslandWorldVolume volume,
            SkyIslandCompiledVolumeColumnField columns,
            SkyIslandRealizedExteriorConnectedCaveVolumeField realized,
            SkyforgeExteriorConnectedCaveSpatialIndex spatialIndex,
            LevelChunk chunk) {
        var descriptor = volume.compiledVolume().descriptor();

        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                floorToInt(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                ceilToInt(volume.bounds().maximumY()));
        if (maximumY < minimumY) {
            return false;
        }

        double localMinimumX = chunk.getPos().getMinBlockX() - descriptor.centerX();
        double localMaximumX = chunk.getPos().getMaxBlockX() - descriptor.centerX();
        double localMinimumZ = chunk.getPos().getMinBlockZ() - descriptor.centerZ();
        double localMaximumZ = chunk.getPos().getMaxBlockZ() - descriptor.centerZ();
        var spatialSlice = spatialIndex.slice(
                localMinimumX,
                localMaximumX,
                localMinimumZ,
                localMaximumZ);
        if (spatialSlice.candidatePrimitiveBounds() == 0) {
            return false;
        }

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                var column = columns.columnAt(local);
                if (column.isEmpty()) {
                    continue;
                }

                var present = column.orElseThrow();
                int first = Math.max(minimumY, ceilToInt(present.undersideY()));
                int last = Math.min(maximumY, floorToInt(present.upperY()));
                for (int y = first; y <= last; y++) {
                    var depth = present.depthFractionAt(y);
                    if (depth.isEmpty()) {
                        continue;
                    }
                    var semantic = new io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition(
                            local,
                            depth.orElseThrow());
                    if (!spatialSlice.mayContainPositive(semantic)) {
                        continue;
                    }
                    if (realized.semanticField().sample(semantic).inside()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        volumeId,
                        worldX,
                        worldY,
                        worldZ)
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge terrain binding disappeared during composed cave planning"));
    }

    private static boolean foreignSolid(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedByOtherVolume(
                        volumeId,
                        worldX,
                        worldY,
                        worldZ)
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge terrain binding disappeared during composed cave planning"));
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world bound exceeds Minecraft integer coordinates: " + value);
        }
        return (int) floored;
    }

    private static int ceilToInt(double value) {
        double ceiled = Math.ceil(value);
        if (ceiled < Integer.MIN_VALUE || ceiled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("world bound exceeds Minecraft integer coordinates: " + value);
        }
        return (int) ceiled;
    }

    private static ChunkPos chunkPos(long chunkKey) {
        return new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
    }

    record Snapshot(
            int totalObligations,
            int pendingObligations,
            int completedObligations,
            int emptyObligations) {
        Snapshot {
            if (totalObligations < 0
                    || pendingObligations < 0
                    || completedObligations < 0
                    || emptyObligations < 0
                    || pendingObligations + completedObligations != totalObligations
                    || emptyObligations > completedObligations) {
                throw new IllegalArgumentException("invalid composed cave stage ledger counts");
            }
        }
    }

    record Completion(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            BlockPos biomeSample,
            int nativeTargetMinimumY,
            int nativeTargetMaximumY,
            Optional<SkyforgeComposedCaveRealizer.Result> result) {
        Completion {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(chunkPos, "chunkPos");
            Objects.requireNonNull(result, "result");
            if (result.isPresent()) {
                Objects.requireNonNull(biomeSample, "biomeSample");
                if (nativeTargetMaximumY < nativeTargetMinimumY) {
                    throw new IllegalArgumentException("native target frame must be ordered");
                }
            } else if (biomeSample != null
                    || nativeTargetMinimumY != Integer.MIN_VALUE
                    || nativeTargetMaximumY != Integer.MIN_VALUE) {
                throw new IllegalArgumentException("empty composed cave completion cannot carry a native target");
            }
        }

        private static Completion empty(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos) {
            return new Completion(
                    volumeId,
                    chunkPos,
                    null,
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE,
                    Optional.empty());
        }
    }

    private record ObligationKey(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey) {
        private ObligationKey {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    private record Obligation(
            SkyforgeComposedCavePlan plan,
            SkyIslandCompiledVolumeColumnField columns,
            SkyIslandRealizedExteriorConnectedCaveVolumeField realizedAuthoredField,
            SkyforgeExteriorConnectedCaveSpatialIndex spatialIndex,
            State state,
            Completion completion) {
        private Obligation {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(columns, "columns");
            Objects.requireNonNull(realizedAuthoredField, "realizedAuthoredField");
            Objects.requireNonNull(spatialIndex, "spatialIndex");
            Objects.requireNonNull(state, "state");
            if ((state == State.PENDING) != (completion == null)) {
                throw new IllegalArgumentException("composed cave obligation state/evidence mismatch");
            }
        }
    }

    private record Binding(
            LinkedHashMap<ObligationKey, Obligation> obligations,
            LinkedHashMap<ObligationKey, Optional<OwnerSpan>> ownerSpans,
            LinkedHashMap<ObligationKey, SkyforgeExteriorConnectedCavePreparationCursor> preparations) {
        private Binding {
            Objects.requireNonNull(obligations, "obligations");
            Objects.requireNonNull(ownerSpans, "ownerSpans");
            Objects.requireNonNull(preparations, "preparations");
        }
    }

    record ServiceResult(
            boolean worked,
            List<Completion> completions) {
        ServiceResult {
            completions = List.copyOf(completions);
            if (!worked && !completions.isEmpty()) {
                throw new IllegalArgumentException("idle composed cave service cannot complete obligations");
            }
        }

        static ServiceResult idle() {
            return new ServiceResult(false, List.of());
        }

        static ServiceResult worked() {
            return new ServiceResult(true, List.of());
        }

        static ServiceResult completed(Completion completion) {
            return new ServiceResult(true, List.of(Objects.requireNonNull(completion, "completion")));
        }
    }

    private record OwnerSpan(
            int x,
            int z,
            int minimumY,
            int maximumY) {}

    private enum State {
        PENDING,
        COMPLETED
    }
}
