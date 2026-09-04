package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.ArrayList;
import java.util.HashMap;
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
 * Production-facing lifecycle stage for SF-IMP-0067 native-first/authored-last cave composition.
 *
 * <p>The stage owns only invocation/idempotency. It does not copy native carver definitions,
 * reinterpret AUTH-0030 geometry, or maintain a second biome mapping.
 *
 * <p>Plans remain pending until the exact volume is admitted and any deferred terrain realization
 * for that volume/chunk has completed. The stage services only LevelChunks already loaded
 * independently by Minecraft. The catch-up service uses {@code getChunkNow}; this stage never
 * creates a generation ticket.
 */
final class SkyforgeComposedCavePopulationStage {
    private static final AtomicReference<RuntimeBinding> ACTIVE = new AtomicReference<>();

    private SkyforgeComposedCavePopulationStage() {}

    static AutoCloseable install(List<Plan> plans) {
        Objects.requireNonNull(plans, "plans");
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("composed cave production stage requires at least one plan");
        }

        Map<SkyIslandWorldVolumeId, RuntimePlan> byVolume = new LinkedHashMap<>();
        for (Plan plan : List.copyOf(plans)) {
            Objects.requireNonNull(plan, "composed cave plan");
            RuntimePlan runtimePlan = new RuntimePlan(
                    plan,
                    new LinkedHashSet<>(coveredChunkKeys(plan.volume().bounds())),
                    new HashMap<>());
            RuntimePlan previous = byVolume.putIfAbsent(plan.volume().id(), runtimePlan);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate composed cave plan for volume " + plan.volume().id().path());
            }
        }

        RuntimeBinding binding = new RuntimeBinding(
                Map.copyOf(byVolume),
                new HashSet<>());
        if (!ACTIVE.compareAndSet(null, binding)) {
            throw new IllegalStateException("a composed cave production binding is already installed");
        }
        return () -> {
            if (!ACTIVE.compareAndSet(binding, null)) {
                throw new IllegalStateException("composed cave production binding changed before close");
            }
        };
    }

    /**
     * Returns currently serviceable chunk keys without loading them.
     *
     * <p>A key is eligible only after exact-volume admission. Deferred terrain catch-up is checked
     * again per volume inside {@link #populateDeferred}; another stacked volume may still block one
     * plan while a different plan in the same chunk is ready.
     */
    static Set<Long> eligibleChunkKeys() {
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return Set.of();
        }

        Set<Long> keys = new LinkedHashSet<>();
        synchronized (binding) {
            for (RuntimePlan runtimePlan : binding.plansByVolume().values()) {
                if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(
                        runtimePlan.plan().volume().id())) {
                    continue;
                }
                keys.addAll(runtimePlan.pendingChunkKeys());
            }
        }
        return Set.copyOf(keys);
    }

    /**
     * Services all ready exact-volume cave obligations for one already-loaded stable chunk.
     *
     * <p>The exact biome resolver is reused from the accepted native surface-population plan. If
     * that identity is not yet available, the obligation stays pending.
     */
    static List<ServiceResult> populateDeferred(
            ServerLevel level,
            LevelChunk chunk,
            ChunkGenerator generator) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(generator, "generator");
        if (chunk.getLevel() != level) {
            throw new IllegalArgumentException("composed cave chunk belongs to another level");
        }

        RuntimeBinding binding = ACTIVE.get();
        if (binding == null || !SkyforgePhysicalVolumeAdmissionStage.active()) {
            return List.of();
        }
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(
                    "composed cave production stage requires a NoiseBasedChunkGenerator");
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "composed cave production stage requires an active Skyforge terrain binding");
        }

        long chunkKey = chunk.getPos().toLong();
        List<ServiceResult> results = new ArrayList<>();
        for (RuntimePlan runtimePlan : binding.plansByVolume().values()) {
            Plan plan = runtimePlan.plan();
            SkyIslandWorldVolumeId volumeId = plan.volume().id();

            synchronized (binding) {
                if (!runtimePlan.pendingChunkKeys().contains(chunkKey)
                        || binding.inProgress().contains(new ObligationKey(volumeId, chunkKey))) {
                    continue;
                }
            }
            if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId)) {
                continue;
            }
            if (SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId)
                    .contains(chunkKey)) {
                continue;
            }

            Optional<SkyforgeNativeSurfacePopulationPlan> nativePlan =
                    SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId);
            if (nativePlan.isEmpty()) {
                continue;
            }

            ObligationKey key = new ObligationKey(volumeId, chunkKey);
            synchronized (binding) {
                if (!runtimePlan.pendingChunkKeys().contains(chunkKey)
                        || !binding.inProgress().add(key)) {
                    continue;
                }
            }

            try {
                ServiceResult result = realize(
                        level,
                        chunk,
                        noiseGenerator,
                        plan,
                        nativePlan.orElseThrow().biomeResolver());
                synchronized (binding) {
                    if (!runtimePlan.pendingChunkKeys().remove(chunkKey)) {
                        throw new IllegalStateException(
                                "composed cave obligation disappeared before completion");
                    }
                    ServiceResult previous = runtimePlan.completedByChunk().put(chunkKey, result);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "composed cave obligation completed more than once");
                    }
                }
                results.add(result);
            } finally {
                synchronized (binding) {
                    binding.inProgress().remove(key);
                }
            }
        }
        return List.copyOf(results);
    }

    private static ServiceResult realize(
            ServerLevel level,
            LevelChunk chunk,
            NoiseBasedChunkGenerator generator,
            Plan plan,
            SkyforgeExactVolumeBiomeResolver biomeResolver) {
        SkyIslandWorldVolume volume = plan.volume();
        OwnerSpan span = widestOwnerSpan(volume, chunk);
        if (span == null) {
            int authoredPositive = countAuthoredPositive(volume, plan.authoredField(), chunk);
            if (authoredPositive != 0) {
                throw new IllegalStateException(
                        "AUTH-0030 positive material has no exact owner-solid realization in chunk "
                                + chunk.getPos() + ": positiveSamples=" + authoredPositive);
            }
            return ServiceResult.empty(volume.id(), chunk.getPos());
        }

        int targetMinimumY = span.minimumY();
        int targetMaximumY = span.maximumY();
        if (targetMaximumY <= targetMinimumY) {
            var authored = SkyforgeExteriorConnectedCaveRealizer.realize(
                    level,
                    volume,
                    plan.authoredField(),
                    chunk);
            if (!authored.accepted()) {
                throw new IllegalStateException(
                        "narrow composed cave chunk failed authored owner preflight: "
                                + chunk.getPos());
            }
            return ServiceResult.authoredOnly(
                    volume.id(),
                    chunk.getPos(),
                    authored);
        }

        var result = SkyforgeComposedCaveRealizer.realize(
                level,
                generator,
                biomeResolver,
                volume,
                plan.authoredField(),
                chunk,
                new BlockPos(
                        span.x(),
                        (targetMinimumY + targetMaximumY) / 2,
                        span.z()),
                targetMinimumY,
                targetMaximumY);

        return ServiceResult.composed(
                volume.id(),
                chunk.getPos(),
                result);
    }

    private static OwnerSpan widestOwnerSpan(
            SkyIslandWorldVolume volume,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));

        OwnerSpan best = null;
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int first = Integer.MAX_VALUE;
                int last = Integer.MIN_VALUE;
                for (int y = minimumY; y <= maximumY; y++) {
                    if (ownerSolid(volume.id(), x, y, z)) {
                        if (first == Integer.MAX_VALUE) {
                            first = y;
                        }
                        last = y;
                    }
                }
                if (first == Integer.MAX_VALUE) {
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

    private static int countAuthoredPositive(
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();
        int positive = 0;

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    if (realized.sample(new SkyIslandRealizedSubsurfacePosition(local, y)).inside()) {
                        positive++;
                    }
                }
            }
        }
        return positive;
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolumeId volumeId,
            int x,
            int y,
            int z) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, x, y, z)
                .orElseThrow(() -> new IllegalStateException(
                        "Skyforge terrain binding disappeared during composed cave production"));
    }

    private static Set<Long> coveredChunkKeys(WorldBounds bounds) {
        int minimumChunkX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);

        Set<Long> keys = new LinkedHashSet<>();
        for (int x = minimumChunkX; x <= maximumChunkX; x++) {
            for (int z = minimumChunkZ; z <= maximumChunkZ; z++) {
                keys.add(ChunkPos.asLong(x, z));
            }
        }
        return Set.copyOf(keys);
    }

    static int pendingCount() {
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return 0;
        }
        synchronized (binding) {
            return binding.plansByVolume().values().stream()
                    .mapToInt(plan -> plan.pendingChunkKeys().size())
                    .sum();
        }
    }

    static int completedCount() {
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return 0;
        }
        synchronized (binding) {
            return binding.plansByVolume().values().stream()
                    .mapToInt(plan -> plan.completedByChunk().size())
                    .sum();
        }
    }

    static Set<Long> pendingChunkKeys(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return Set.of();
        }
        synchronized (binding) {
            RuntimePlan plan = binding.plansByVolume().get(volumeId);
            return plan == null ? Set.of() : Set.copyOf(plan.pendingChunkKeys());
        }
    }

    static List<ServiceResult> completedResults(SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        RuntimeBinding binding = ACTIVE.get();
        if (binding == null) {
            return List.of();
        }
        synchronized (binding) {
            RuntimePlan plan = binding.plansByVolume().get(volumeId);
            return plan == null ? List.of() : List.copyOf(plan.completedByChunk().values());
        }
    }

    static boolean hasActiveBinding() {
        return ACTIVE.get() != null;
    }

    record Plan(
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField authoredField) {
        Plan {
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(authoredField, "authoredField");
        }
    }

    record ServiceResult(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            boolean empty,
            boolean nativeAttempted,
            int nativeChangedBlocks,
            int nativeSuccessfulCalls,
            long nativeTransformDigest,
            long nativeCarveDigest,
            int authoredPositiveSamples,
            int authoredChangedBlocks,
            int authoredUnsafeSamples,
            long authoredChangedDigest,
            long authoredProvenanceDigest) {
        ServiceResult {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(chunkPos, "chunkPos");
            if (nativeChangedBlocks < 0
                    || nativeSuccessfulCalls < 0
                    || authoredPositiveSamples < 0
                    || authoredChangedBlocks < 0
                    || authoredUnsafeSamples < 0) {
                throw new IllegalArgumentException("composed cave service counts must be non-negative");
            }
            if (empty && (nativeAttempted
                    || nativeChangedBlocks != 0
                    || nativeTransformDigest != 0L
                    || nativeCarveDigest != 0L
                    || authoredPositiveSamples != 0
                    || authoredChangedBlocks != 0
                    || authoredUnsafeSamples != 0
                    || authoredChangedDigest != 0L
                    || authoredProvenanceDigest != 0L)) {
                throw new IllegalArgumentException("empty composed cave obligation contains mutations");
            }
        }

        static ServiceResult empty(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos) {
            return new ServiceResult(
                    volumeId, chunkPos, true, false,
                    0, 0, 0L, 0L,
                    0, 0, 0, 0L, 0L);
        }

        static ServiceResult authoredOnly(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos,
                SkyforgeExteriorConnectedCaveRealizer.Result authored) {
            return new ServiceResult(
                    volumeId,
                    chunkPos,
                    false,
                    false,
                    0,
                    0,
                    0L,
                    0L,
                    authored.positiveSamples(),
                    authored.changedBlocks(),
                    authored.unsafePositiveSamples(),
                    authored.changedPositionDigest(),
                    authored.provenanceDigest());
        }

        static ServiceResult composed(
                SkyIslandWorldVolumeId volumeId,
                ChunkPos chunkPos,
                SkyforgeComposedCaveRealizer.Result result) {
            return new ServiceResult(
                    volumeId,
                    chunkPos,
                    false,
                    true,
                    result.nativeResult().changedBlocks(),
                    result.nativeResult().successfulCalls(),
                    result.nativeResult().transformDigest(),
                    result.nativeResult().changedPositionDigest(),
                    result.authoredResult().positiveSamples(),
                    result.authoredResult().changedBlocks(),
                    result.authoredResult().unsafePositiveSamples(),
                    result.authoredResult().changedPositionDigest(),
                    result.authoredResult().provenanceDigest());
        }
    }

    private record ObligationKey(
            SkyIslandWorldVolumeId volumeId,
            long chunkKey) {
        ObligationKey {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }

    private record OwnerSpan(
            int x,
            int z,
            int minimumY,
            int maximumY) {}

    private record RuntimePlan(
            Plan plan,
            Set<Long> pendingChunkKeys,
            Map<Long, ServiceResult> completedByChunk) {
        RuntimePlan {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(pendingChunkKeys, "pendingChunkKeys");
            Objects.requireNonNull(completedByChunk, "completedByChunk");
        }
    }

    private record RuntimeBinding(
            Map<SkyIslandWorldVolumeId, RuntimePlan> plansByVolume,
            Set<ObligationKey> inProgress) {
        RuntimeBinding {
            Objects.requireNonNull(plansByVolume, "plansByVolume");
            Objects.requireNonNull(inProgress, "inProgress");
        }
    }
}
