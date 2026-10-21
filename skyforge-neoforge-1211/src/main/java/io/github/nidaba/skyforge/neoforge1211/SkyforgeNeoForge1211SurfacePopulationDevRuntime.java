package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Development-only acceptance fixture for the reusable SF-IMP-0055 surface population stage. */
final class SkyforgeNeoForge1211SurfacePopulationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.surfacePopulation";
    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_PROOF_CHUNKS =
            (PROOF_RADIUS_CHUNKS * 2 + 1) * (PROOF_RADIUS_CHUNKS * 2 + 1);
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final int VEGETATION_SCAN_HEIGHT = 40;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211SurfacePopulationDevRuntime.class.getName());

    private static final Set<Long> observedChunks = new HashSet<>();
    private static final Aggregate lowerAggregate = new Aggregate();
    private static final Aggregate upperAggregate = new Aggregate();
    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentPopulationBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211SurfacePopulationDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentPopulationBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0055 surface population proof over another terrain binding");
        }
        if (SkyforgeNativeSurfacePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0055 surface population proof over another population binding");
        }

        var catalog = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog();
        var volumes = catalog.volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0055 fixture requires the accepted two-volume biome catalog");
        }
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        SkyforgeExactVolumeBiomeResolver biomeResolver = resolver(lowerId, upperId);

        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentPopulationBinding = SkyforgeNativeSurfacePopulationStage.install((chunkPos, minimumY, height) -> {
            if (!isProofChunk(chunkPos)) {
                return List.of();
            }
            return List.of(
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            lowerId,
                            biomeResolver,
                            MAXIMUM_ATTACHMENT_DEPTH),
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            upperId,
                            biomeResolver,
                            MAXIMUM_ATTACHMENT_DEPTH));
        });

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0055 reusable surface-population specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and inspect the stacked forest/taiga tablelands near the origin. "
                        + "Only VEGETAL_DECORATION is admitted. The fixture re-invokes the coordinator after each "
                        + "chunk and fails unless the replay executes zero native phases.");
    }

    static synchronized void observe(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        if (!enabled() || proofComplete || !isProofChunk(chunk.getPos())) {
            return;
        }
        if (!observedChunks.add(chunk.getPos().toLong())) {
            return;
        }
        if (!SkyforgeNativeSurfacePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException("SF-IMP-0055 fixture ran without the reusable surface population stage");
        }

        // The generator's accepted post-realization callback invoked the stage immediately before
        // this observer. Invoke it again now: every populated phase must be a cached no-op replay.
        List<SkyforgeNativeSurfacePopulationCoordinator.Result> replay =
                SkyforgeNativeSurfacePopulationStage.populate(level, chunk, generator);
        var volumes = SkyforgeNeoForge1211BiomePopulationDevRuntime.catalog().volumes();
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();

        for (var result : replay) {
            if (!result.terrainPresent()) {
                continue;
            }
            if (result.phases().size() != 1) {
                throw new IllegalStateException("surface population proof expected exactly one admitted phase per volume");
            }
            var phase = result.phases().getFirst();
            if (phase.phase() != GenerationStep.Decoration.VEGETAL_DECORATION) {
                throw new IllegalStateException("non-surface generation phase leaked into SF-IMP-0055: " + phase.phase());
            }
            if (phase.executedNow()) {
                throw new IllegalStateException("idempotent surface population replay executed native features twice for "
                        + result.volumeId().path() + " in " + chunk.getPos());
            }

            VegetationEvidence vegetation = scanVegetation(
                    level,
                    chunk.getPos(),
                    phase.surface().firstFreeY());
            if (result.volumeId().equals(lowerId)) {
                lowerAggregate.add(phase.nativeResult(), vegetation);
            } else if (result.volumeId().equals(upperId)) {
                upperAggregate.add(phase.nativeResult(), vegetation);
            } else {
                throw new IllegalStateException("unexpected volume returned by SF-IMP-0055 plan resolver: "
                        + result.volumeId().path());
            }
        }

        if (observedChunks.size() == EXPECTED_PROOF_CHUNKS) {
            completeProof(lowerId, upperId);
        }
    }

    private static void completeProof(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate, Biomes.TAIGA);
        int completedPhases = SkyforgeNativeSurfacePopulationStage.completedPhaseCount();
        int expectedCompletedPhases = lowerAggregate.chunks + upperAggregate.chunks;
        if (completedPhases != expectedCompletedPhases) {
            throw new IllegalStateException("surface population idempotency ledger size mismatch: completed="
                    + completedPhases + ", expected=" + expectedCompletedPhases);
        }
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0055 SURFACE POPULATION COORDINATED PASS: observedChunks=" + observedChunks.size()
                        + ", completedPhases=" + completedPhases
                        + ", replayExecutedPhases=0"
                        + ", admittedPhases=" + SkyforgeSurfacePopulationPhasePolicy.admittedPhases()
                        + ", lower={volume=" + lowerId.path()
                        + ", biome=" + lowerAggregate.biomeKey.location()
                        + ", chunks=" + lowerAggregate.chunks
                        + ", attempted=" + lowerAggregate.attemptedFeatures
                        + ", successful=" + lowerAggregate.successfulFeatures
                        + ", attachments=" + lowerAggregate.attachmentWrites
                        + ", logs=" + lowerAggregate.logBlocks
                        + ", leaves=" + lowerAggregate.leafBlocks
                        + "}, upper={volume=" + upperId.path()
                        + ", biome=" + upperAggregate.biomeKey.location()
                        + ", chunks=" + upperAggregate.chunks
                        + ", attempted=" + upperAggregate.attemptedFeatures
                        + ", successful=" + upperAggregate.successfulFeatures
                        + ", attachments=" + upperAggregate.attachmentWrites
                        + ", logs=" + upperAggregate.logBlocks
                        + ", leaves=" + upperAggregate.leafBlocks
                        + "}. Reusable coordinator produced persistent native surface ecology once per exact "
                        + "volume/chunk/phase and rejected duplicate execution by replaying cached results only.");
    }

    private static SkyforgeExactVolumeBiomeResolver resolver(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        return (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return Biomes.TAIGA;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0055 proof volume: " + volumeId.path());
        };
    }

    private static boolean isProofChunk(ChunkPos chunkPos) {
        return Math.abs(chunkPos.x) <= PROOF_RADIUS_CHUNKS && Math.abs(chunkPos.z) <= PROOF_RADIUS_CHUNKS;
    }

    private static VegetationEvidence scanVegetation(
            WorldGenLevel level,
            ChunkPos chunkPos,
            int surfaceY) {
        int logs = 0;
        int leaves = 0;
        int minimumY = Math.max(level.getMinBuildHeight(), surfaceY);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                surfaceY + VEGETATION_SCAN_HEIGHT);
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(BlockTags.LOGS)) {
                        logs++;
                    }
                    if (state.is(BlockTags.LEAVES)) {
                        leaves++;
                    }
                }
            }
        }
        return new VegetationEvidence(logs, leaves);
    }

    private static void requireAggregate(
            String label,
            Aggregate aggregate,
            ResourceKey<Biome> expectedBiome) {
        if (aggregate.chunks == 0) {
            throw new IllegalStateException(label + " surface population proof found no exact-volume terrain chunks");
        }
        if (!expectedBiome.equals(aggregate.biomeKey)) {
            throw new IllegalStateException(label + " surface population proof resolved unexpected biome: "
                    + aggregate.biomeKey);
        }
        if (aggregate.successfulFeatures == 0 || aggregate.logBlocks == 0 || aggregate.leafBlocks == 0) {
            throw new IllegalStateException(label + " surface population proof produced insufficient persistent ecology: "
                    + "successful=" + aggregate.successfulFeatures
                    + ", logs=" + aggregate.logBlocks
                    + ", leaves=" + aggregate.leafBlocks);
        }
    }

    private record VegetationEvidence(int logs, int leaves) {}

    private static final class Aggregate {
        private ResourceKey<Biome> biomeKey;
        private int chunks;
        private int attemptedFeatures;
        private int successfulFeatures;
        private int attachmentWrites;
        private int logBlocks;
        private int leafBlocks;

        private void add(
                SkyforgeNativeBiomePopulationRunner.Result result,
                VegetationEvidence vegetation) {
            if (biomeKey == null) {
                biomeKey = result.biomeKey();
            } else if (!biomeKey.equals(result.biomeKey())) {
                throw new IllegalStateException("one exact volume changed biome identity across surface population chunks");
            }
            chunks++;
            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
            logBlocks = Math.addExact(logBlocks, vegetation.logs());
            leafBlocks = Math.addExact(leafBlocks, vegetation.leaves());
        }
    }
}
