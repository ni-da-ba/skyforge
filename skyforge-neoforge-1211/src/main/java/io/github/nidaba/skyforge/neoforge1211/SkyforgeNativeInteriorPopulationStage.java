package io.github.nidaba.skyforge.neoforge1211;

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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Production lifecycle for registry-native interior population after the exact volume's final
 * composed cave topology is complete.
 *
 * <p>The stage is orchestration only. It reuses the accepted native population runner, exact-volume
 * vertical transforms/fences, lake admission, generated-fluid provenance, deferred post-processing
 * bridge, and the biome resolver already selected by native surface population.
 */
final class SkyforgeNativeInteriorPopulationStage {
    private static final AtomicReference<Binding> ACTIVE = new AtomicReference<>();

    private SkyforgeNativeInteriorPopulationStage() {}

    static AutoCloseable install(List<SkyforgeNativeInteriorPopulationPlan> plans) {
        Objects.requireNonNull(plans, "plans");
        if (!SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "native interior population requires an installed physical volume-admission stage");
        }
        if (!SkyforgeComposedCaveStage.active()) {
            throw new IllegalStateException(
                    "native interior population requires an installed composed-cave production stage");
        }

        LinkedHashMap<ObligationKey, Obligation> obligations = new LinkedHashMap<>();
        Set<SkyIslandWorldVolumeId> volumeIds = new HashSet<>();
        for (SkyforgeNativeInteriorPopulationPlan plan : List.copyOf(plans)) {
            Objects.requireNonNull(plan, "native interior population plan");
            if (!volumeIds.add(plan.volumeId())) {
                throw new IllegalArgumentException(
                        "duplicate native interior population plan for volume " + plan.volumeId().path());
            }

            List<Long> chunkKeys = new ArrayList<>(
                    SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(plan.volumeId()));
            chunkKeys.sort(Comparator
                    .comparingInt((Long key) -> ChunkPos.getX(key))
                    .thenComparingInt(key -> ChunkPos.getZ(key)));
            for (long chunkKey : chunkKeys) {
                ObligationKey key = new ObligationKey(plan.volumeId(), chunkKey);
                Obligation previous = obligations.put(key, new Obligation(plan));
                if (previous != null) {
                    throw new IllegalStateException(
                            "duplicate native interior population obligation "
                                    + plan.volumeId().path() + "/" + chunkPos(chunkKey));
                }
            }
        }
        if (obligations.isEmpty()) {
            throw new IllegalArgumentException(
                    "native interior population requires at least one exact-volume obligation");
        }

        Binding binding = new Binding(obligations);
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a native interior population stage is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException(
                        "native interior population stage changed before close");
            }
        };
    }

    /**
     * Services eligible obligations for one already-loaded stable chunk.
     *
     * <p>The caller is responsible for obtaining the chunk through a no-ticket path such as
     * {@code ServerChunkCache#getChunkNow}. The stage never loads neighboring chunks.
     */
    static ServiceResult service(
            ServerLevel level,
            LevelChunk chunk,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("native interior population chunk belongs to another level");
        }

        Binding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return ServiceResult.idle();
        }

        long chunkKey = chunk.getPos().toLong();
        List<Map.Entry<ObligationKey, Obligation>> candidates = new ArrayList<>();
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (entry.getKey().chunkKey() == chunkKey && !entry.getValue().completed()) {
                    candidates.add(entry);
                }
            }
        }

        List<Completion> completions = new ArrayList<>();
        for (var candidate : candidates) {
            ObligationKey key = candidate.getKey();
            Obligation obligation = candidate.getValue();
            SkyIslandWorldVolumeId volumeId = key.volumeId();

            if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)
                    || SkyforgePhysicalVolumeAdmissionStage.hasPendingCatchup(volumeId, chunk.getPos())) {
                continue;
            }

            var caveSnapshot = SkyforgeComposedCaveStage.snapshot(volumeId);
            if (caveSnapshot.totalObligations() <= 0
                    || caveSnapshot.pendingObligations() > 0
                    || caveSnapshot.completedObligations() != caveSnapshot.totalObligations()) {
                continue;
            }

            var surfacePlan = SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId)
                    .orElseThrow(() -> new IllegalStateException(
                            "native interior population requires the existing exact-volume biome plan for "
                                    + volumeId.path() + "/" + chunk.getPos()));

            Optional<SkyforgeNativeSurfacePopulationCoordinator.SurfaceSample> surface =
                    SkyforgeNativeSurfacePopulationCoordinator.findSurface(level, volumeId, chunk.getPos());
            Completion completion;
            if (surface.isEmpty()) {
                completion = new Completion(volumeId, chunk.getPos(), List.of());
            } else {
                var sample = surface.orElseThrow();
                BlockPos biomeSample = new BlockPos(sample.x(), sample.firstFreeY(), sample.z());
                List<SkyforgeNativeBiomePopulationRunner.Result> phaseResults = new ArrayList<>();
                var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
                try {
                    for (var phase : obligation.plan().phases()) {
                        var route = phase == GenerationStep.Decoration.VEGETAL_DECORATION
                                ? SkyforgeNativeVegetalFeatureRoute.POST_CAVE
                                : SkyforgeNativeVegetalFeatureRoute.ALL;
                        phaseResults.add(SkyforgeRuntimePerformanceMetrics.measure(
                                "interior." + phase.name(),
                                () -> SkyforgeNativeBiomePopulationRunner.populateStep(
                                        level,
                                        generator,
                                        surfacePlan.biomeResolver(),
                                        volumeId,
                                        chunk.getPos(),
                                        biomeSample,
                                        phase,
                                        obligation.plan().maximumAttachmentDepth(),
                                        route)));
                    }
                } finally {
                    postProcessing.close();
                }
                completion = new Completion(volumeId, chunk.getPos(), List.copyOf(phaseResults));
            }

            synchronized (binding) {
                Obligation current = binding.obligations().get(key);
                if (current != obligation || current.completed()) {
                    throw new IllegalStateException(
                            "native interior population obligation changed during service");
                }
                current.complete(completion);
            }
            completions.add(completion);
        }

        return completions.isEmpty()
                ? ServiceResult.idle()
                : new ServiceResult(true, List.copyOf(completions));
    }

    static Set<Long> pendingChunkKeys() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (!entry.getValue().completed()) {
                    keys.add(entry.getKey().chunkKey());
                }
            }
        }
        return Collections.unmodifiableSet(keys);
    }

    static Snapshot snapshot() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return new Snapshot(0, 0, 0, 0);
        }
        int completed = 0;
        int empty = 0;
        synchronized (binding) {
            for (Obligation obligation : binding.obligations().values()) {
                if (obligation.completed()) {
                    completed++;
                    if (obligation.completion().phaseResults().isEmpty()) {
                        empty++;
                    }
                }
            }
        }
        return new Snapshot(
                binding.obligations().size(),
                binding.obligations().size() - completed,
                completed,
                empty);
    }

    static Snapshot snapshot(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return new Snapshot(0, 0, 0, 0);
        }
        int total = 0;
        int completed = 0;
        int empty = 0;
        synchronized (binding) {
            for (var entry : binding.obligations().entrySet()) {
                if (!entry.getKey().volumeId().equals(volumeId)) {
                    continue;
                }
                total++;
                if (entry.getValue().completed()) {
                    completed++;
                    if (entry.getValue().completion().phaseResults().isEmpty()) {
                        empty++;
                    }
                }
            }
        }
        return new Snapshot(total, total - completed, completed, empty);
    }

    static List<Completion> completed() {
        Binding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }
        List<Completion> result = new ArrayList<>();
        synchronized (binding) {
            for (Obligation obligation : binding.obligations().values()) {
                if (obligation.completed()) {
                    result.add(obligation.completion());
                }
            }
        }
        return List.copyOf(result);
    }

    static boolean active() {
        return ACTIVE.get() != null;
    }

    private static ChunkPos chunkPos(long chunkKey) {
        return new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
    }

    private record ObligationKey(SkyIslandWorldVolumeId volumeId, long chunkKey) {
        private ObligationKey {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    private static final class Obligation {
        private final SkyforgeNativeInteriorPopulationPlan plan;
        private Completion completion;

        private Obligation(SkyforgeNativeInteriorPopulationPlan plan) {
            this.plan = Objects.requireNonNull(plan, "plan");
        }

        private SkyforgeNativeInteriorPopulationPlan plan() {
            return plan;
        }

        private boolean completed() {
            return completion != null;
        }

        private Completion completion() {
            if (completion == null) {
                throw new IllegalStateException("pending native interior obligation has no completion");
            }
            return completion;
        }

        private void complete(Completion value) {
            if (completion != null) {
                throw new IllegalStateException("native interior population obligation completed twice");
            }
            completion = Objects.requireNonNull(value, "completion");
        }
    }

    private record Binding(LinkedHashMap<ObligationKey, Obligation> obligations) {
        private Binding {
            Objects.requireNonNull(obligations, "obligations");
        }
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
                throw new IllegalArgumentException("invalid native interior population ledger counts");
            }
        }
    }

    record Completion(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            List<SkyforgeNativeBiomePopulationRunner.Result> phaseResults) {
        Completion {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(chunkPos, "chunkPos");
            Objects.requireNonNull(phaseResults, "phaseResults");
            phaseResults = List.copyOf(phaseResults);
        }
    }

    record ServiceResult(boolean worked, List<Completion> completions) {
        ServiceResult {
            Objects.requireNonNull(completions, "completions");
            completions = List.copyOf(completions);
            if (!worked && !completions.isEmpty()) {
                throw new IllegalArgumentException(
                        "idle native interior population service cannot contain completions");
            }
            if (worked && completions.isEmpty()) {
                throw new IllegalArgumentException(
                        "worked native interior population service requires a completion");
            }
        }

        static ServiceResult idle() {
            return new ServiceResult(false, List.of());
        }
    }
}
