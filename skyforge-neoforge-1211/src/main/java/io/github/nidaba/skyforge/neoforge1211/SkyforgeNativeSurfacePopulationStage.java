package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;

/** Runtime binding for production-facing exact-volume native surface population. */
final class SkyforgeNativeSurfacePopulationStage {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();

    private SkyforgeNativeSurfacePopulationStage() {}

    static List<SkyforgeNativeSurfacePopulationCoordinator.Result> populate(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }

        List<SkyforgeNativeSurfacePopulationCoordinator.Result> results = new ArrayList<>();
        for (SkyforgeNativeSurfacePopulationPlan plan : resolvePlans(binding, chunk)) {
            if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(plan.volumeId())) {
                continue;
            }
            results.add(SkyforgeRuntimePerformanceMetrics.measure(
                    "surfacePopulation.coordinator",
                    () -> binding.coordinator().populate(level, generator, plan, chunk.getPos())));
        }
        return List.copyOf(results);
    }

    /**
     * Runs the ordinary exact-volume coordinator on an already-loaded chunk while preserving the
     * generation-time post-processing contract expected by native placed features.
     *
     * <p>Direct WorldGenRegion population never enters this scope and remains byte-for-byte on the
     * accepted SF-IMP-0055 lifecycle. The deferred bridge is therefore a catch-up adapter concern,
     * not a second population implementation.
     */
    static List<SkyforgeNativeSurfacePopulationCoordinator.Result> populateDeferred(
            ServerLevel level,
            LevelChunk chunk,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("deferred population chunk belongs to another level");
        }
        var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
        try {
            return populate(level, chunk, generator);
        } finally {
            postProcessing.close();
        }
    }

    /**
     * Replays the normal coordinator for one exact volume after deferred terrain realization.
     *
     * <p>The coordinator remains the idempotence authority, so calling this after an already-run
     * normal population pass cannot duplicate a completed phase.
     */
    static List<SkyforgeNativeSurfacePopulationCoordinator.Result> populateVolume(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
            return List.of();
        }

        List<SkyforgeNativeSurfacePopulationCoordinator.Result> results = new ArrayList<>(1);
        for (SkyforgeNativeSurfacePopulationPlan plan : resolvePlans(binding, chunk)) {
            if (plan.volumeId().equals(volumeId)) {
                results.add(SkyforgeRuntimePerformanceMetrics.measure(
                        "surfacePopulation.coordinator",
                        () -> binding.coordinator().populate(level, generator, plan, chunk.getPos())));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Returns the exact-volume biome resolver already selected for native population in this chunk.
     *
     * <p>Persistent client presentation deliberately reuses this plan instead of maintaining a
     * second Minecraft-biome mapping that could drift from the generation-time identity.
     */
    static Optional<SkyforgeNativeSurfacePopulationPlan> planForVolume(
            ChunkAccess chunk,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Optional.empty();
        }
        return resolvePlans(binding, chunk).stream()
                .filter(plan -> plan.volumeId().equals(volumeId))
                .findFirst();
    }

    private static List<SkyforgeNativeSurfacePopulationPlan> resolvePlans(
            RuntimeBinding binding,
            ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<SkyforgeNativeSurfacePopulationPlan> plans = List.copyOf(binding.planResolver().resolve(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight()));
        var volumeIds = new HashSet<SkyIslandWorldVolumeId>();
        for (SkyforgeNativeSurfacePopulationPlan plan : plans) {
            Objects.requireNonNull(plan, "surface population plan resolver returned null plan");
            if (!volumeIds.add(plan.volumeId())) {
                throw new IllegalStateException("surface population plan resolver returned duplicate volume plan for chunk "
                        + chunkPos + ": " + plan.volumeId().path());
            }
        }
        return plans;
    }

    static AutoCloseable install(PlanResolver planResolver) {
        Objects.requireNonNull(planResolver, "planResolver");
        RuntimeBinding binding = new RuntimeBinding(
                planResolver,
                new SkyforgeNativeSurfacePopulationCoordinator());
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a Skyforge native surface population binding is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("Skyforge native surface population binding changed before close");
            }
        };
    }

    static boolean hasActiveBinding() {
        return ACTIVE.get() != null;
    }

    static int completedPhaseCount() {
        RuntimeBinding binding = ACTIVE.get();
        return binding == null ? 0 : binding.coordinator().completedPhaseCount();
    }

    static List<SkyforgeNativeBiomePopulationRunner.Result> completedNativeResults(
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        return binding == null
                ? List.of()
                : binding.coordinator().completedNativeResults(volumeId);
    }

    @FunctionalInterface
    interface PlanResolver {
        List<SkyforgeNativeSurfacePopulationPlan> resolve(
                ChunkPos chunkPos,
                int minimumY,
                int height);
    }

    private record RuntimeBinding(
            PlanResolver planResolver,
            SkyforgeNativeSurfacePopulationCoordinator coordinator) {
        private RuntimeBinding {
            Objects.requireNonNull(planResolver, "planResolver");
            Objects.requireNonNull(coordinator, "coordinator");
        }
    }
}
