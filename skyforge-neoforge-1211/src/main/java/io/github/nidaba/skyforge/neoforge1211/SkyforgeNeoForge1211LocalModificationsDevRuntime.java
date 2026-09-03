package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only acceptance probe for SF-IMP-0060 registry-native local modifications. */
final class SkyforgeNeoForge1211LocalModificationsDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.localModifications";
    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_SURFACE_POPULATION_PHASES = 21;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final ResourceLocation AMETHYST_GEODE =
            ResourceLocation.withDefaultNamespace("amethyst_geode");
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211LocalModificationsDevRuntime.class.getName());

    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211LocalModificationsDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofStarted || proofComplete) {
            return;
        }

        var volumes = SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0060 proof requires the accepted two-volume 0056 catalog");
        }
        var lowerVolume = volumes.get(0);
        var upperVolume = volumes.get(1);
        SkyIslandWorldVolumeId lowerId = lowerVolume.id();
        SkyIslandWorldVolumeId upperId = upperVolume.id();
        var lower = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);
        var upper = SkyforgePhysicalVolumeAdmissionStage.snapshot(upperId);

        if (lower.state() != SkyforgePhysicalVolumeAdmissionState.REJECTED
                || upper.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upperId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(upperId).isEmpty()
                || SkyforgeNativeSurfacePopulationStage.completedPhaseCount() != EXPECTED_SURFACE_POPULATION_PHASES) {
            return;
        }

        List<ProofChunk> proofChunks = loadedSurfaceChunks(level, upperId);
        if (proofChunks.size() != EXPECTED_SURFACE_POPULATION_PHASES) {
            return;
        }

        int minimumEnvelopeY = (int) Math.ceil(upperVolume.bounds().minimumY());
        int maximumEnvelopeY = (int) Math.floor(upperVolume.bounds().maximumY());
        List<BaseColumnSnapshot> baseColumnsBefore = captureBaseColumns(level, proofChunks);
        proofStarted = true;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(upperId)) {
                throw new IllegalArgumentException("SF-IMP-0060 proof resolved an unexpected volume: "
                        + volumeId.path());
            }
            return Biomes.TAIGA;
        };

        int attemptedFeatures = 0;
        int successfulFeatures = 0;
        int geodeAttempts = 0;
        int geodeSuccesses = 0;
        int heightRangeSamples = 0;
        int nativeOutsideVolume = 0;
        int mappedOutsideVolume = 0;
        int transformedHeightSamples = 0;
        int acceptedWritePreflights = 0;
        int rejectedWritePreflights = 0;
        int acceptedWriteAttempts = 0;
        int rejectedWriteAttempts = 0;
        long transformDigest = FNV_OFFSET_BASIS;
        List<ResourceLocation> observedFeatureKeys = new ArrayList<>();

        for (ProofChunk proofChunk : proofChunks) {
            SkyforgeNativeBiomePopulationRunner.Result result;
            SkyforgeUndergroundPlacementProbe.Snapshot snapshot;
            var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
            try {
                try (var probe = SkyforgeUndergroundPlacementProbe.open(
                        upperId,
                        minimumEnvelopeY,
                        maximumEnvelopeY)) {
                    result = SkyforgeNativeBiomePopulationRunner.populateStep(
                            level,
                            level.getChunkSource().getGenerator(),
                            biomeResolver,
                            upperId,
                            proofChunk.chunk().getPos(),
                            proofChunk.surfaceBlock(),
                            GenerationStep.Decoration.LOCAL_MODIFICATIONS,
                            MAXIMUM_ATTACHMENT_DEPTH);
                    snapshot = probe.snapshot();
                }
            } finally {
                postProcessing.close();
            }

            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            heightRangeSamples = Math.addExact(heightRangeSamples, snapshot.heightRangeSamples());
            nativeOutsideVolume = Math.addExact(
                    nativeOutsideVolume,
                    Math.addExact(snapshot.nativeSamplesBelowEnvelope(), snapshot.nativeSamplesAboveEnvelope()));
            mappedOutsideVolume = Math.addExact(
                    mappedOutsideVolume,
                    Math.addExact(snapshot.mappedSamplesBelowEnvelope(), snapshot.mappedSamplesAboveEnvelope()));
            transformedHeightSamples = Math.addExact(
                    transformedHeightSamples,
                    snapshot.transformedHeightSamples());
            acceptedWritePreflights = Math.addExact(
                    acceptedWritePreflights,
                    snapshot.acceptedWritePreflights());
            rejectedWritePreflights = Math.addExact(
                    rejectedWritePreflights,
                    snapshot.rejectedWritePreflights());
            acceptedWriteAttempts = Math.addExact(
                    acceptedWriteAttempts,
                    snapshot.acceptedWriteAttempts());
            rejectedWriteAttempts = Math.addExact(
                    rejectedWriteAttempts,
                    snapshot.rejectedWriteAttempts());
            transformDigest = mix(transformDigest, proofChunk.chunk().getPos().toLong());
            transformDigest = mix(transformDigest, snapshot.heightTransformDigest());

            for (var feature : result.featureResults()) {
                if (!observedFeatureKeys.contains(feature.featureKey())) {
                    observedFeatureKeys.add(feature.featureKey());
                }
                if (!feature.featureKey().equals(AMETHYST_GEODE)) {
                    continue;
                }
                geodeAttempts++;
                if (feature.placed()) {
                    geodeSuccesses++;
                }
            }
        }

        if (geodeAttempts != proofChunks.size()) {
            throw new IllegalStateException("SF-IMP-0060 final-registry taiga LOCAL_MODIFICATIONS did not expose exactly "
                    + "one minecraft:amethyst_geode per proof chunk: chunks=" + proofChunks.size()
                    + ", geodeAttempts=" + geodeAttempts + ", featureKeys=" + observedFeatureKeys);
        }
        if (heightRangeSamples <= 0 || nativeOutsideVolume <= 0 || transformedHeightSamples <= 0) {
            throw new IllegalStateException("SF-IMP-0060 did not exercise discriminating native absolute-height local "
                    + "modification placement: heightRangeSamples=" + heightRangeSamples
                    + ", nativeOutsideVolume=" + nativeOutsideVolume
                    + ", transformedHeightSamples=" + transformedHeightSamples);
        }
        if (mappedOutsideVolume != 0) {
            throw new IllegalStateException("SF-IMP-0060 mapped LOCAL_MODIFICATIONS outside the exact volume: "
                    + mappedOutsideVolume);
        }
        if (geodeSuccesses <= 0) {
            throw new IllegalStateException("SF-IMP-0060 deterministic admitted footprint exercised " + geodeAttempts
                    + " native minecraft:amethyst_geode attempts but none passed vanilla placement/realization; "
                    + "adjust the development specimen rather than bypassing native rarity");
        }
        if (acceptedWriteAttempts <= 0 && acceptedWritePreflights <= 0) {
            throw new IllegalStateException("SF-IMP-0060 successful local modifications produced no exact-owner write "
                    + "decisions or optimized write preflights");
        }

        GeodeEvidence geodeEvidence = scanGeodeEvidence(level, minimumEnvelopeY, maximumEnvelopeY);
        if (geodeEvidence.totalBlocks() <= 0) {
            throw new IllegalStateException("SF-IMP-0060 reported native geode success without persistent geode material "
                    + "inside the admitted high-volume envelope");
        }
        requireBaseColumnsPreserved(level, baseColumnsBefore);

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0060 LOCAL MODIFICATIONS PASS: volume=" + upperId.path()
                        + ", phase=" + GenerationStep.Decoration.LOCAL_MODIFICATIONS
                        + ", proofChunks=" + proofChunks.size()
                        + ", attemptedFeatures=" + attemptedFeatures
                        + ", successfulFeatures=" + successfulFeatures
                        + ", geodeAttempts=" + geodeAttempts
                        + ", geodeSuccesses=" + geodeSuccesses
                        + ", heightRangeSamples=" + heightRangeSamples
                        + ", nativeOutsideVolume=" + nativeOutsideVolume
                        + ", mappedOutsideVolume=" + mappedOutsideVolume
                        + ", transformedHeightSamples=" + transformedHeightSamples
                        + ", transformDigest=" + Long.toUnsignedString(transformDigest, 16)
                        + ", acceptedWritePreflights=" + acceptedWritePreflights
                        + ", rejectedWritePreflights=" + rejectedWritePreflights
                        + ", acceptedWriteAttempts=" + acceptedWriteAttempts
                        + ", rejectedWriteAttempts=" + rejectedWriteAttempts
                        + ", geodeMaterial={amethyst=" + geodeEvidence.amethystBlocks()
                        + ", budding=" + geodeEvidence.buddingAmethystBlocks()
                        + ", calcite=" + geodeEvidence.calciteBlocks()
                        + ", smoothBasalt=" + geodeEvidence.smoothBasaltBlocks()
                        + "}, baseColumnsPreserved=true. Final-registry LOCAL_MODIFICATIONS consumed native placement "
                        + "randomness first, mapped absolute HeightRangePlacement output into the admitted exact-volume "
                        + "frame, and left vertically unrelated BASE_WORLD proof columns unchanged.");
    }

    private static List<ProofChunk> loadedSurfaceChunks(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        List<ProofChunk> chunks = new ArrayList<>();
        for (int chunkX = -PROOF_RADIUS_CHUNKS; chunkX <= PROOF_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -PROOF_RADIUS_CHUNKS; chunkZ <= PROOF_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return List.of();
                }
                Optional<BlockPos> surface = surfaceSample(level, volumeId, chunk.getPos());
                surface.ifPresent(blockPos -> chunks.add(new ProofChunk(chunk, blockPos)));
            }
        }
        return List.copyOf(chunks);
    }

    private static Optional<BlockPos> surfaceSample(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos) {
        int middleX = chunkPos.getMiddleBlockX();
        int middleZ = chunkPos.getMiddleBlockZ();
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
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
                int distance = Math.abs(x - middleX) + Math.abs(z - middleZ);
                if (distance < bestDistance) {
                    best = new BlockPos(x, claim.orElseThrow().height() - 1, z);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<BaseColumnSnapshot> captureBaseColumns(
            ServerLevel level,
            List<ProofChunk> proofChunks) {
        List<BaseColumnSnapshot> snapshots = new ArrayList<>(proofChunks.size());
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                BASE_COLUMN_MAXIMUM_Y);
        for (ProofChunk proofChunk : proofChunks) {
            BlockPos surface = proofChunk.surfaceBlock();
            List<BlockState> states = new ArrayList<>(maximumY - minimumY + 1);
            for (int y = minimumY; y <= maximumY; y++) {
                states.add(level.getBlockState(new BlockPos(surface.getX(), y, surface.getZ())));
            }
            snapshots.add(new BaseColumnSnapshot(surface.getX(), surface.getZ(), minimumY, List.copyOf(states)));
        }
        return List.copyOf(snapshots);
    }

    private static void requireBaseColumnsPreserved(
            ServerLevel level,
            List<BaseColumnSnapshot> before) {
        for (BaseColumnSnapshot column : before) {
            for (int index = 0; index < column.states().size(); index++) {
                int y = column.minimumY() + index;
                BlockState expected = column.states().get(index);
                BlockState actual = level.getBlockState(new BlockPos(column.x(), y, column.z()));
                if (!actual.equals(expected)) {
                    throw new IllegalStateException("SF-IMP-0060 local modification mutated vertically unrelated "
                            + "BASE_WORLD terrain at BlockPos{x=" + column.x() + ", y=" + y + ", z=" + column.z()
                            + "}: before=" + expected + ", after=" + actual);
                }
            }
        }
    }

    private static GeodeEvidence scanGeodeEvidence(
            ServerLevel level,
            int minimumEnvelopeY,
            int maximumEnvelopeY) {
        int amethyst = 0;
        int budding = 0;
        int calcite = 0;
        int smoothBasalt = 0;
        for (int chunkX = -PROOF_RADIUS_CHUNKS; chunkX <= PROOF_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -PROOF_RADIUS_CHUNKS; chunkZ <= PROOF_RADIUS_CHUNKS; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                    for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                        for (int y = minimumEnvelopeY; y <= maximumEnvelopeY; y++) {
                            BlockState state = level.getBlockState(new BlockPos(x, y, z));
                            if (state.is(Blocks.AMETHYST_BLOCK)) {
                                amethyst++;
                            } else if (state.is(Blocks.BUDDING_AMETHYST)) {
                                budding++;
                            } else if (state.is(Blocks.CALCITE)) {
                                calcite++;
                            } else if (state.is(Blocks.SMOOTH_BASALT)) {
                                smoothBasalt++;
                            }
                        }
                    }
                }
            }
        }
        return new GeodeEvidence(amethyst, budding, calcite, smoothBasalt);
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private record ProofChunk(LevelChunk chunk, BlockPos surfaceBlock) {}

    private record BaseColumnSnapshot(int x, int z, int minimumY, List<BlockState> states) {}

    private record GeodeEvidence(
            int amethystBlocks,
            int buddingAmethystBlocks,
            int calciteBlocks,
            int smoothBasaltBlocks) {
        int totalBlocks() {
            return Math.addExact(
                    Math.addExact(amethystBlocks, buddingAmethystBlocks),
                    Math.addExact(calciteBlocks, smoothBasaltBlocks));
        }
    }
}
