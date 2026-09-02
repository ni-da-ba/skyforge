package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

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

        ChunkPos chunkPos = chunk.getPos();
        List<SkyforgeNativeSurfacePopulationPlan> plans = List.copyOf(binding.planResolver().resolve(
                chunkPos,
                chunk.getMinBuildHeight(),
                chunk.getHeight()));
        var volumeIds = new HashSet<SkyIslandWorldVolumeId>();
        List<SkyforgeNativeSurfacePopulationCoordinator.Result> results = new ArrayList<>(plans.size());
        for (SkyforgeNativeSurfacePopulationPlan plan : plans) {
            Objects.requireNonNull(plan, "surface population plan resolver returned null plan");
            if (!volumeIds.add(plan.volumeId())) {
                throw new IllegalStateException("surface population plan resolver returned duplicate volume plan for chunk "
                        + chunkPos + ": " + plan.volumeId().path());
            }
            results.add(binding.coordinator().populate(level, generator, plan, chunkPos));
        }
        return List.copyOf(results);
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
