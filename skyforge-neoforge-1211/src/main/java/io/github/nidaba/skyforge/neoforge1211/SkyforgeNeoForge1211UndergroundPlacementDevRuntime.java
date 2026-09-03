package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/** Development-only baseline for SF-IMP-0059 native underground vertical placement. */
final class SkyforgeNeoForge1211UndergroundPlacementDevRuntime {
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int EXPECTED_SURFACE_POPULATION_PHASES = 21;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundPlacementDevRuntime.class.getName());

    private static boolean baselineStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211UndergroundPlacementDevRuntime() {}

    static synchronized void observeLoaded(ServerLevel level) {
        if (!SkyforgeUndergroundPlacementProbe.enabled() || baselineStarted || proofComplete) {
            return;
        }

        var volumes = SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0059 baseline requires the accepted two-volume 0056 catalog");
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

        int minimumEnvelopeY = (int) Math.floor(upperVolume.bounds().minimumY());
        int maximumEnvelopeY = (int) Math.ceil(upperVolume.bounds().maximumY());
        List<BlockState> baseColumnBefore = captureBaseColumn(level);
        baselineStarted = true;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(upperId)) {
                throw new IllegalArgumentException("SF-IMP-0059 baseline resolved an unexpected volume: "
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
                    "SF-IMP-0059 baseline executed UNDERGROUND_ORES without observing any HeightRangePlacement samples");
        }
        if (!captureBaseColumn(level).equals(baseColumnBefore)) {
            throw new IllegalStateException(
                    "SF-IMP-0059 native underground baseline mutated vertically unrelated BASE_WORLD terrain");
        }

        int outsideEnvelopeSamples = Math.addExact(
                snapshot.samplesBelowEnvelope(),
                snapshot.samplesAboveEnvelope());
        String outcome = outsideEnvelopeSamples == 0
                ? "NATIVE_HEIGHTS_ALREADY_LOCAL"
                : "ABSOLUTE_HEIGHT_FRAME_MISMATCH_CONFIRMED";
        List<String> successfulFeatures = result.featureResults().stream()
                .filter(SkyforgeNativeBiomePopulationRunner.FeatureResult::placed)
                .map(feature -> feature.featureKey().toString())
                .toList();

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0059 NATIVE UNDERGROUND BASELINE: outcome=" + outcome
                        + ", volume=" + upperId.path()
                        + ", envelopeY=[" + minimumEnvelopeY + "," + maximumEnvelopeY + "]"
                        + ", surfaceY=" + upperSurfaceY
                        + ", phase=" + GenerationStep.Decoration.UNDERGROUND_ORES
                        + ", attemptedFeatures=" + result.attemptedFeatures()
                        + ", successfulFeatures=" + result.successfulFeatures()
                        + ", successfulFeatureKeys=" + successfulFeatures
                        + ", heightRangeSamples=" + snapshot.heightRangeSamples()
                        + ", samplesBelowEnvelope=" + snapshot.samplesBelowEnvelope()
                        + ", samplesInsideEnvelope=" + snapshot.samplesInsideEnvelope()
                        + ", samplesAboveEnvelope=" + snapshot.samplesAboveEnvelope()
                        + ", sampleYRange=[" + snapshot.minimumSampleY() + "," + snapshot.maximumSampleY() + "]"
                        + ", acceptedWriteAttempts=" + snapshot.acceptedWriteAttempts()
                        + ", rejectedWriteAttempts=" + snapshot.rejectedWriteAttempts()
                        + ", uniqueAcceptedWritePositions=" + snapshot.uniqueAcceptedWritePositions()
                        + ", acceptedWriteYRange=[" + snapshot.minimumAcceptedWriteY() + ","
                        + snapshot.maximumAcceptedWriteY() + "]"
                        + ", baseColumnPreserved=true. Registry-native underground placement executed without any "
                        + "vertical transform; exact-volume visibility/write isolation remained authoritative.");
    }

    private static List<BlockState> captureBaseColumn(ServerLevel level) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                BASE_COLUMN_MAXIMUM_Y);
        List<BlockState> states = new ArrayList<>(Math.max(0, maximumY - minimumY + 1));
        for (int y = minimumY; y <= maximumY; y++) {
            states.add(level.getBlockState(new BlockPos(PROOF_X, y, PROOF_Z)));
        }
        return List.copyOf(states);
    }
}
