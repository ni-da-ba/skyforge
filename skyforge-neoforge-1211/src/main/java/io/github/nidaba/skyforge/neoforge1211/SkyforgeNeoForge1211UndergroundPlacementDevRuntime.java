package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only acceptance proof for SF-IMP-0059 volume-local underground placement. */
final class SkyforgeNeoForge1211UndergroundPlacementDevRuntime {
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int EXPECTED_SURFACE_POPULATION_PHASES = 21;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundPlacementDevRuntime.class.getName());

    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211UndergroundPlacementDevRuntime() {}

    static synchronized void observeLoaded(ServerLevel level) {
        if (!SkyforgeUndergroundPlacementProbe.enabled() || proofStarted || proofComplete) {
            return;
        }

        var volumes = SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0059 proof requires the accepted two-volume 0056 catalog");
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

        var originChunk = level.getChunkSource().getChunkNow(0, 0);
        if (originChunk == null) {
            return;
        }
        int upperSurfaceY = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        PROOF_X,
                        PROOF_Z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .orElseThrow(() -> new IllegalStateException("SF-IMP-0059 upper proof volume has no origin surface"))
                .height();

        int minimumEnvelopeY = (int) Math.ceil(upperVolume.bounds().minimumY());
        int maximumEnvelopeY = (int) Math.floor(upperVolume.bounds().maximumY());
        int nativeMinimumY = level.getMinBuildHeight();
        int nativeMaximumY = Math.addExact(nativeMinimumY, Math.subtractExact(level.getHeight(), 1));
        List<BlockState> baseColumnBefore = captureBaseColumn(level);
        proofStarted = true;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(upperId)) {
                throw new IllegalArgumentException("SF-IMP-0059 proof resolved an unexpected volume: "
                        + volumeId.path());
            }
            return Biomes.TAIGA;
        };

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
                        originChunk.getPos(),
                        new BlockPos(PROOF_X, upperSurfaceY - 1, PROOF_Z),
                        GenerationStep.Decoration.UNDERGROUND_ORES,
                        0);
                snapshot = probe.snapshot();
            }
        } finally {
            postProcessing.close();
        }

        if (snapshot.heightRangeSamples() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0059 executed UNDERGROUND_ORES without observing any HeightRangePlacement samples");
        }
        int nativeOutsideEnvelope = Math.addExact(
                snapshot.nativeSamplesBelowEnvelope(),
                snapshot.nativeSamplesAboveEnvelope());
        int mappedOutsideEnvelope = Math.addExact(
                snapshot.mappedSamplesBelowEnvelope(),
                snapshot.mappedSamplesAboveEnvelope());
        if (nativeOutsideEnvelope <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0059 fixture no longer discriminates native absolute-height placement from local placement");
        }
        if (snapshot.transformedHeightSamples() <= 0) {
            throw new IllegalStateException("SF-IMP-0059 vertical frame transformed no native height samples");
        }
        if (mappedOutsideEnvelope != 0
                || snapshot.mappedSamplesInsideEnvelope() != snapshot.heightRangeSamples()) {
            throw new IllegalStateException(
                    "SF-IMP-0059 vertical frame left a HeightRangePlacement sample outside the exact volume envelope");
        }
        if (snapshot.acceptedWritePreflights() <= 0
                || snapshot.uniqueAcceptedPreflightPositions() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0059 transformed underground phase produced no exact-owner write candidates");
        }
        if (result.successfulFeatures() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0059 transformed underground phase produced no successful registry-native features");
        }

        List<BlockState> baseColumnAfter = captureBaseColumn(level);
        if (!baseColumnAfter.equals(baseColumnBefore)) {
            int minimumCapturedY = capturedMinimumY(level);
            for (int index = 0; index < baseColumnBefore.size(); index++) {
                BlockState before = baseColumnBefore.get(index);
                BlockState after = baseColumnAfter.get(index);
                if (!before.equals(after)) {
                    int changedY = minimumCapturedY + index;
                    throw new IllegalStateException(
                            "SF-IMP-0059 local underground placement mutated vertically unrelated BASE_WORLD terrain at "
                                    + "BlockPos{x=" + PROOF_X + ", y=" + changedY + ", z=" + PROOF_Z + "}: before="
                                    + before + ", after=" + after);
                }
            }
            throw new IllegalStateException(
                    "SF-IMP-0059 local underground placement changed BASE_WORLD capture size unexpectedly");
        }

        List<String> successfulFeatures = result.featureResults().stream()
                .filter(SkyforgeNativeBiomePopulationRunner.FeatureResult::placed)
                .map(feature -> feature.featureKey().toString())
                .toList();
        String transformDigest = Long.toUnsignedString(snapshot.heightTransformDigest(), 16);

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0059 UNDERGROUND PLACEMENT PASS: volume=" + upperId.path()
                        + ", nativeFrameY=[" + nativeMinimumY + "," + nativeMaximumY + "]"
                        + ", volumeFrameY=[" + minimumEnvelopeY + "," + maximumEnvelopeY + "]"
                        + ", surfaceY=" + upperSurfaceY
                        + ", phase=" + GenerationStep.Decoration.UNDERGROUND_ORES
                        + ", attemptedFeatures=" + result.attemptedFeatures()
                        + ", successfulFeatures=" + result.successfulFeatures()
                        + ", successfulFeatureKeys=" + successfulFeatures
                        + ", heightRangeSamples=" + snapshot.heightRangeSamples()
                        + ", nativeSampleYRange=[" + snapshot.minimumNativeSampleY() + ","
                        + snapshot.maximumNativeSampleY() + "]"
                        + ", nativeOutsideVolume=" + nativeOutsideEnvelope
                        + ", mappedSampleYRange=[" + snapshot.minimumMappedSampleY() + ","
                        + snapshot.maximumMappedSampleY() + "]"
                        + ", mappedOutsideVolume=" + mappedOutsideEnvelope
                        + ", transformedHeightSamples=" + snapshot.transformedHeightSamples()
                        + ", transformDigest=" + transformDigest
                        + ", writePreflightChecks=" + snapshot.writePreflightChecks()
                        + ", acceptedWritePreflights=" + snapshot.acceptedWritePreflights()
                        + ", uniqueAcceptedPreflightPositions=" + snapshot.uniqueAcceptedPreflightPositions()
                        + ", acceptedPreflightYRange=[" + snapshot.minimumAcceptedPreflightY() + ","
                        + snapshot.maximumAcceptedPreflightY() + "]"
                        + ", rejectedWritePreflights=" + snapshot.rejectedWritePreflights()
                        + ", baseColumnPreserved=true. Native HeightRangePlacement randomness was sampled first, then "
                        + "mapped monotonically into the admitted exact-volume frame; optimized ore writes remained "
                        + "subject to exact owner preflight and vertically unrelated BASE_WORLD terrain stayed intact.");
    }

    private static List<BlockState> captureBaseColumn(ServerLevel level) {
        int minimumY = capturedMinimumY(level);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                BASE_COLUMN_MAXIMUM_Y);
        List<BlockState> states = new ArrayList<>(Math.max(0, maximumY - minimumY + 1));
        for (int y = minimumY; y <= maximumY; y++) {
            states.add(level.getBlockState(new BlockPos(PROOF_X, y, PROOF_Z)));
        }
        return List.copyOf(states);
    }

    private static int capturedMinimumY(ServerLevel level) {
        return Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
    }
}
