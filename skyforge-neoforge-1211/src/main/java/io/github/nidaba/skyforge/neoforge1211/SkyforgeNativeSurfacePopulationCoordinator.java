package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Coordinates native surface population for exact Skyforge terrain owners.
 *
 * <p>The coordinator owns generation-lifecycle idempotency. One volume/chunk/phase is executed at
 * most once by a coordinator instance. Repeated requests return the cached native result and are
 * marked as replays rather than executing placed features again. Minecraft chunk status already
 * prevents decoration from being rerun on ordinary save/reload; this guard additionally prevents
 * duplicate calls caused by neighboring-chunk orchestration or adapter re-entry during generation.
 */
final class SkyforgeNativeSurfacePopulationCoordinator {
    private static final int CHUNK_WIDTH = 16;
    private static final int CHUNK_MIDDLE_LOCAL = 8;
    private static final List<ColumnProbe> SURFACE_PROBE_ORDER = createSurfaceProbeOrder();

    private final Map<PopulationKey, CachedPhase> completed = new HashMap<>();

    synchronized Result populate(
            WorldGenLevel level,
            ChunkGenerator generator,
            SkyforgeNativeSurfacePopulationPlan plan,
            ChunkPos chunkPos) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(generator, "generator");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(chunkPos, "chunkPos");

        long findSurfaceStart = SkyforgeRuntimePerformanceMetrics.start();
        SurfaceSearchResult search = findSurfaceWithEvidence(level, plan.volumeId(), chunkPos);
        SkyforgeRuntimePerformanceMetrics.recordSince(
                "surfacePopulation.findSurface",
                findSurfaceStart);
        SkyforgeRuntimePerformanceMetrics.recordSample(
                "surfacePopulation.findSurface.heightQueries",
                search.heightQueries());
        Optional<SurfaceSample> surface = search.surface();
        if (surface.isEmpty()) {
            return new Result(plan.volumeId(), chunkPos, false, List.of());
        }
        SurfaceSample sample = surface.orElseThrow();
        List<PhaseResult> phaseResults = new ArrayList<>(plan.phases().size());

        for (GenerationStep.Decoration phase : plan.phases()) {
            PopulationKey key = new PopulationKey(plan.volumeId(), chunkPos.toLong(), phase);
            CachedPhase cached = completed.get(key);
            if (cached != null) {
                requireStableReplay(plan, sample, cached);
                phaseResults.add(new PhaseResult(phase, false, cached.surface(), cached.result()));
                continue;
            }

            var route = phase == GenerationStep.Decoration.VEGETAL_DECORATION
                    ? SkyforgeNativeVegetalFeatureRoute.SURFACE_ECOLOGY
                    : SkyforgeNativeVegetalFeatureRoute.ALL;
            var nativeResult = SkyforgeRuntimePerformanceMetrics.measure(
                    "surfacePopulation.phase." + phase.name(),
                    () -> SkyforgeNativeBiomePopulationRunner.populateStep(
                            level,
                            generator,
                            plan.biomeResolver(),
                            plan.volumeId(),
                            chunkPos,
                            new BlockPos(sample.x(), sample.firstFreeY(), sample.z()),
                            phase,
                            plan.maximumAttachmentDepth(),
                            route));
            CachedPhase created = new CachedPhase(
                    sample,
                    plan.maximumAttachmentDepth(),
                    nativeResult.biomeKey(),
                    nativeResult);
            completed.put(key, created);
            phaseResults.add(new PhaseResult(phase, true, sample, nativeResult));
        }

        return new Result(plan.volumeId(), chunkPos, true, List.copyOf(phaseResults));
    }

    synchronized int completedPhaseCount() {
        return completed.size();
    }

    synchronized List<SkyforgeNativeBiomePopulationRunner.Result> completedNativeResults(
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(volumeId, "volumeId");
        return completed.entrySet().stream()
                .filter(entry -> entry.getKey().volumeId().equals(volumeId))
                .map(entry -> entry.getValue().result())
                .toList();
    }

    static Optional<SurfaceSample> findSurface(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos) {
        return findSurfaceWithEvidence(level, volumeId, chunkPos).surface();
    }

    private static SurfaceSearchResult findSurfaceWithEvidence(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos) {
        int minimumX = chunkPos.getMinBlockX();
        int minimumZ = chunkPos.getMinBlockZ();
        int heightQueries = 0;

        for (ColumnProbe probe : SURFACE_PROBE_ORDER) {
            int x = minimumX + probe.localX();
            int z = minimumZ + probe.localZ();
            heightQueries++;
            var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                    volumeId,
                    x,
                    z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    level.getMinBuildHeight(),
                    level.getHeight());
            if (claim.isEmpty()) {
                continue;
            }
            return new SurfaceSearchResult(
                    Optional.of(new SurfaceSample(x, z, claim.orElseThrow().height())),
                    heightQueries);
        }

        return new SurfaceSearchResult(Optional.empty(), heightQueries);
    }

    static List<ColumnProbe> surfaceProbeOrder() {
        return SURFACE_PROBE_ORDER;
    }

    private static List<ColumnProbe> createSurfaceProbeOrder() {
        List<ColumnProbe> probes = new ArrayList<>(CHUNK_WIDTH * CHUNK_WIDTH);
        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int distance = Math.abs(localX - CHUNK_MIDDLE_LOCAL)
                        + Math.abs(localZ - CHUNK_MIDDLE_LOCAL);
                probes.add(new ColumnProbe(localX, localZ, distance));
            }
        }
        probes.sort(Comparator.comparingInt(ColumnProbe::distance)
                .thenComparingInt(ColumnProbe::localX)
                .thenComparingInt(ColumnProbe::localZ));
        return List.copyOf(probes);
    }

    private static void requireStableReplay(
            SkyforgeNativeSurfacePopulationPlan plan,
            SurfaceSample currentSurface,
            CachedPhase cached) {
        if (!cached.surface().equals(currentSurface)) {
            throw new IllegalStateException("exact-volume surface changed after native population completed");
        }
        if (cached.maximumAttachmentDepth() != plan.maximumAttachmentDepth()) {
            throw new IllegalStateException("surface population attachment policy changed for an already populated phase");
        }
        ResourceKey<Biome> currentBiome = Objects.requireNonNull(
                plan.biomeResolver().resolve(
                        plan.volumeId(),
                        currentSurface.x(),
                        currentSurface.firstFreeY(),
                        currentSurface.z()),
                "biome resolver returned null during idempotent replay");
        if (!currentBiome.equals(cached.biomeKey())) {
            throw new IllegalStateException("exact-volume biome assignment changed after native population completed");
        }
    }

    private record PopulationKey(
            SkyIslandWorldVolumeId volumeId,
            long chunkPos,
            GenerationStep.Decoration phase) {
        private PopulationKey {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(phase, "phase");
        }
    }

    record ColumnProbe(int localX, int localZ, int distance) {}

    record SurfaceSample(int x, int z, int firstFreeY) {}

    private record SurfaceSearchResult(Optional<SurfaceSample> surface, int heightQueries) {
        private SurfaceSearchResult {
            Objects.requireNonNull(surface, "surface");
            if (heightQueries < 0 || heightQueries > CHUNK_WIDTH * CHUNK_WIDTH) {
                throw new IllegalArgumentException("surface search height-query count is out of range");
            }
        }
    }

    private record CachedPhase(
            SurfaceSample surface,
            int maximumAttachmentDepth,
            ResourceKey<Biome> biomeKey,
            SkyforgeNativeBiomePopulationRunner.Result result) {
        private CachedPhase {
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(biomeKey, "biomeKey");
            Objects.requireNonNull(result, "result");
        }
    }

    record PhaseResult(
            GenerationStep.Decoration phase,
            boolean executedNow,
            SurfaceSample surface,
            SkyforgeNativeBiomePopulationRunner.Result nativeResult) {
        PhaseResult {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(nativeResult, "nativeResult");
        }
    }

    record Result(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            boolean terrainPresent,
            List<PhaseResult> phases) {
        Result {
            Objects.requireNonNull(volumeId, "volumeId");
            Objects.requireNonNull(chunkPos, "chunkPos");
            Objects.requireNonNull(phases, "phases");
            phases = List.copyOf(phases);
            if (!terrainPresent && !phases.isEmpty()) {
                throw new IllegalArgumentException("no-terrain population result cannot contain executed phases");
            }
        }

        boolean executedAnyNow() {
            return phases.stream().anyMatch(PhaseResult::executedNow);
        }
    }
}
