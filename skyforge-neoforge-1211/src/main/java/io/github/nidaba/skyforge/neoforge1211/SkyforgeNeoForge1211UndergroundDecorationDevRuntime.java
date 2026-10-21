package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Development-only acceptance proof for SF-IMP-0062 registry-native cave-surface decoration.
 *
 * <p>The fixture deliberately composes two already accepted backend capabilities: SF-IMP-0061
 * carves persistent AIR into an admitted high Skyforge tableland, then Minecraft's final-registry
 * DRIPSTONE_CAVES {@link GenerationStep.Decoration#UNDERGROUND_DECORATION} list executes against
 * those real post-carver blocks. Production authorization remains phase-based; no placed-feature
 * identity is used to admit or relocate content.
 */
final class SkyforgeNeoForge1211UndergroundDecorationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.undergroundDecoration";

    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_REQUIRED_CHUNKS = 25;
    private static final int INTERIOR_MARGIN = 8;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundDecorationDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211UndergroundDecorationDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0062 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0062 proof over another physical-admission binding");
        }

        SkyIslandWorldCatalog catalog = catalog();
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0062 underground-decoration specimen enabled. The accepted high tableland is "
                        + "physically admitted first, registry-native AIR carvers create real owner-local cave "
                        + "surfaces, then the final-registry minecraft:dripstone_caves UNDERGROUND_DECORATION "
                        + "feature list executes through the exact-volume population domain. No cave-decoration "
                        + "feature ID is admitted specially.");
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled()
                || proofStarted
                || proofComplete
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (level.players().isEmpty() && !SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            return;
        }

        SkyIslandWorldVolume volume = catalog().volumes().getFirst();
        SkyIslandWorldVolumeId volumeId = volume.id();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()) {
            return;
        }
        if (admission.requiredChunks() != EXPECTED_REQUIRED_CHUNKS
                || admission.observedChunks() != EXPECTED_REQUIRED_CHUNKS) {
            throw new IllegalStateException(
                    "SF-IMP-0062 development volume admitted with unexpected footprint evidence: observed="
                            + admission.observedChunks() + ", required=" + admission.requiredChunks());
        }

        List<ProofChunk> proofChunks = loadedOwnerChunks(level, volumeId);
        if (proofChunks.isEmpty()) {
            return;
        }

        var generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(
                    "SF-IMP-0062 requires the active Minecraft noise generator, found " + generator.getClass());
        }

        int minimumEnvelopeY = Math.max(
                level.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumEnvelopeY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        int carverMinimumY = Math.addExact(minimumEnvelopeY, INTERIOR_MARGIN);
        int carverMaximumY = Math.subtractExact(maximumEnvelopeY, INTERIOR_MARGIN);
        if (carverMaximumY <= carverMinimumY) {
            throw new IllegalStateException("SF-IMP-0062 proof volume has no safe carver interior frame");
        }

        List<BaseColumnSnapshot> baseColumnsBefore = captureBaseColumns(level, proofChunks);
        proofStarted = true;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0062 proof resolved unexpected volume " + candidateId.path());
            }
            return Biomes.DRIPSTONE_CAVES;
        };

        int carveCalls = 0;
        int carvedBlocks = 0;
        int mappedCarverSamplesOutsideTarget = 0;
        long carveTransformDigest = FNV_OFFSET_BASIS;
        long carveDigest = FNV_OFFSET_BASIS;
        for (ProofChunk proofChunk : proofChunks) {
            var result = SkyforgeNativeCarverRunner.carveAir(
                    level,
                    noiseGenerator,
                    biomeResolver,
                    volumeId,
                    proofChunk.chunk(),
                    proofChunk.biomeSample(),
                    carverMinimumY,
                    carverMaximumY);
            carveCalls = Math.addExact(carveCalls, result.carveCalls());
            carvedBlocks = Math.addExact(carvedBlocks, result.changedBlocks());
            mappedCarverSamplesOutsideTarget = Math.addExact(
                    mappedCarverSamplesOutsideTarget,
                    result.mappedOutsideTarget());
            carveTransformDigest = mix(carveTransformDigest, proofChunk.chunk().getPos().toLong());
            carveTransformDigest = mix(carveTransformDigest, result.transformDigest());
            carveDigest = mix(carveDigest, proofChunk.chunk().getPos().toLong());
            carveDigest = mix(carveDigest, result.changedPositionDigest());
        }
        if (carveCalls <= 0 || carvedBlocks <= 0 || mappedCarverSamplesOutsideTarget != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 prerequisite carver pass did not produce bounded cave topology: carveCalls="
                            + carveCalls + ", carvedBlocks=" + carvedBlocks
                            + ", mappedOutsideTarget=" + mappedCarverSamplesOutsideTarget);
        }

        List<CaveNeighborhoodState> caveBefore = captureCarvedCaveNeighborhood(level, volumeId, proofChunks);
        int carvedAirPositions = 0;
        for (CaveNeighborhoodState state : caveBefore) {
            if (state.state().isAir()) {
                carvedAirPositions++;
            }
        }
        if (carvedAirPositions <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 carvers reported changes but no compiled owner position contains persistent cave AIR");
        }

        int attemptedFeatures = 0;
        int successfulFeatures = 0;
        int heightRangeSamples = 0;
        int mappedDecorationSamplesOutsideVolume = 0;
        int acceptedWritePreflights = 0;
        int acceptedWriteAttempts = 0;
        long decorationTransformDigest = FNV_OFFSET_BASIS;
        List<ResourceLocation> featureKeys = new ArrayList<>();
        List<ResourceLocation> successfulFeatureKeys = new ArrayList<>();

        for (ProofChunk proofChunk : proofChunks) {
            SkyforgeNativeBiomePopulationRunner.Result result;
            SkyforgeUndergroundPlacementProbe.Snapshot snapshot;
            var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
            try {
                try (var probe = SkyforgeUndergroundPlacementProbe.open(
                        volumeId,
                        minimumEnvelopeY,
                        maximumEnvelopeY)) {
                    result = SkyforgeNativeBiomePopulationRunner.populateStep(
                            level,
                            noiseGenerator,
                            biomeResolver,
                            volumeId,
                            proofChunk.chunk().getPos(),
                            proofChunk.biomeSample(),
                            GenerationStep.Decoration.UNDERGROUND_DECORATION,
                            MAXIMUM_ATTACHMENT_DEPTH);
                    snapshot = probe.snapshot();
                }
            } finally {
                postProcessing.close();
            }

            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            heightRangeSamples = Math.addExact(heightRangeSamples, snapshot.heightRangeSamples());
            mappedDecorationSamplesOutsideVolume = Math.addExact(
                    mappedDecorationSamplesOutsideVolume,
                    Math.addExact(snapshot.mappedSamplesBelowEnvelope(), snapshot.mappedSamplesAboveEnvelope()));
            acceptedWritePreflights = Math.addExact(
                    acceptedWritePreflights, snapshot.acceptedWritePreflights());
            acceptedWriteAttempts = Math.addExact(
                    acceptedWriteAttempts, snapshot.acceptedWriteAttempts());
            decorationTransformDigest = mix(
                    decorationTransformDigest,
                    proofChunk.chunk().getPos().toLong());
            decorationTransformDigest = mix(
                    decorationTransformDigest,
                    snapshot.heightTransformDigest());

            for (var feature : result.featureResults()) {
                if (!featureKeys.contains(feature.featureKey())) {
                    featureKeys.add(feature.featureKey());
                }
                if (feature.placed() && !successfulFeatureKeys.contains(feature.featureKey())) {
                    successfulFeatureKeys.add(feature.featureKey());
                }
            }
        }

        if (attemptedFeatures <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 final-registry dripstone-caves biome exposes no UNDERGROUND_DECORATION features");
        }
        if (successfulFeatures <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 attempted native cave decoration but no feature succeeded: keys=" + featureKeys);
        }
        if (mappedDecorationSamplesOutsideVolume != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 mapped an UNDERGROUND_DECORATION height sample outside the exact volume");
        }

        DecorationEvidence decoration = compareCaveNeighborhood(level, caveBefore);
        if (decoration.changedBlocks() <= 0 || decoration.changedCarvedAirBlocks() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0062 native features reported success without persistent mutation of actual carved cave "
                            + "AIR: changedNeighborhood=" + decoration.changedBlocks()
                            + ", changedCarvedAir=" + decoration.changedCarvedAirBlocks()
                            + ", successfulKeys=" + successfulFeatureKeys);
        }
        requireBaseColumnsPreserved(level, baseColumnsBefore);

        proofComplete = true;
        String carveTransform = Long.toUnsignedString(carveTransformDigest, 16);
        String carved = Long.toUnsignedString(carveDigest, 16);
        String decorationTransform = Long.toUnsignedString(decorationTransformDigest, 16);
        String decorationDigest = Long.toUnsignedString(decoration.digest(), 16);

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0062 UNDERGROUND DECORATION PASS: volume=" + volumeId.path()
                        + ", biome=" + Biomes.DRIPSTONE_CAVES.location()
                        + ", proofChunks=" + proofChunks.size()
                        + ", carverFrameY=[" + carverMinimumY + "," + carverMaximumY + "]"
                        + ", carveCalls=" + carveCalls
                        + ", carvedBlocks=" + carvedBlocks
                        + ", carvedAirPositions=" + carvedAirPositions
                        + ", carveTransformDigest=" + carveTransform
                        + ", carveDigest=" + carved
                        + ", attemptedFeatures=" + attemptedFeatures
                        + ", successfulFeatures=" + successfulFeatures
                        + ", featureKeys=" + featureKeys
                        + ", successfulFeatureKeys=" + successfulFeatureKeys
                        + ", heightRangeSamples=" + heightRangeSamples
                        + ", mappedDecorationSamplesOutsideVolume=" + mappedDecorationSamplesOutsideVolume
                        + ", acceptedWritePreflights=" + acceptedWritePreflights
                        + ", acceptedWriteAttempts=" + acceptedWriteAttempts
                        + ", decorationTransformDigest=" + decorationTransform
                        + ", changedCaveNeighborhood=" + decoration.changedBlocks()
                        + ", changedCarvedAir=" + decoration.changedCarvedAirBlocks()
                        + ", decorationDigest=" + decorationDigest
                        + ", sampleDecoration=" + decoration.samplePosition()
                        + ", sampleState=" + decoration.sampleState()
                        + ", baseColumnsPreserved=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("carveTransformDigest", carveTransform),
                        java.util.Map.entry("carveDigest", carved),
                        java.util.Map.entry("decorationTransformDigest", decorationTransform),
                        java.util.Map.entry("decorationDigest", decorationDigest),
                        java.util.Map.entry("attemptedFeatures", attemptedFeatures),
                        java.util.Map.entry("successfulFeatures", successfulFeatures),
                        java.util.Map.entry("changedCaveNeighborhood", decoration.changedBlocks()),
                        java.util.Map.entry("changedCarvedAir", decoration.changedCarvedAirBlocks()),
                        java.util.Map.entry("mappedOutsideVolume", mappedDecorationSamplesOutsideVolume),
                        java.util.Map.entry("baseColumnsPreserved", true),
                        java.util.Map.entry(
                                "sampleDecorationPos",
                                decoration.samplePosition() == null
                                        ? "none"
                                        : Long.toString(decoration.samplePosition().asLong())),
                        java.util.Map.entry(
                                "sampleDecorationState",
                                decoration.sampleState() == null ? "none" : decoration.sampleState().toString())));
    }

    static SkyIslandWorldCatalog catalog() {
        return SkyforgeNeoForge1211LocalModificationsDevRuntime.catalog();
    }

    private static List<ProofChunk> loadedOwnerChunks(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        List<ProofChunk> result = new ArrayList<>();
        for (int chunkX = -PROOF_RADIUS_CHUNKS; chunkX <= PROOF_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = -PROOF_RADIUS_CHUNKS; chunkZ <= PROOF_RADIUS_CHUNKS; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    return List.of();
                }
                BlockPos sample = biomeSample(level, volumeId, chunk.getPos());
                if (sample != null) {
                    result.add(new ProofChunk(chunk, sample));
                }
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos biomeSample(
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
        return best;
    }

    private static List<CaveNeighborhoodState> captureCarvedCaveNeighborhood(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            List<ProofChunk> proofChunks) {
        var bounds = SkyforgeNeoForge1211SurfaceStage.volumeBounds(volumeId).orElseThrow();
        int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(bounds.minimumY()));
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(bounds.maximumY()));
        List<CaveNeighborhoodState> result = new ArrayList<>();
        for (ProofChunk proofChunk : proofChunks) {
            ChunkPos chunkPos = proofChunk.chunk().getPos();
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    for (int y = minimumY; y <= maximumY; y++) {
                        if (!compiledOwner(volumeId, x, y, z)) {
                            continue;
                        }
                        BlockPos position = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(position);
                        if (state.isAir() || adjacentCompiledCarvedAir(level, volumeId, position)) {
                            result.add(new CaveNeighborhoodState(position.asLong(), state));
                        }
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean adjacentCompiledCarvedAir(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            BlockPos position) {
        int[][] offsets = {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 1, 0},
            {0, -1, 0},
            {0, 0, 1},
            {0, 0, -1}
        };
        for (int[] offset : offsets) {
            BlockPos neighbor = position.offset(offset[0], offset[1], offset[2]);
            if (compiledOwner(volumeId, neighbor.getX(), neighbor.getY(), neighbor.getZ())
                    && level.getBlockState(neighbor).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean compiledOwner(
            SkyIslandWorldVolumeId volumeId,
            int x,
            int y,
            int z) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volumeId, x, y, z)
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0062 runtime binding disappeared during cave-neighborhood scan"));
    }

    private static DecorationEvidence compareCaveNeighborhood(
            ServerLevel level,
            List<CaveNeighborhoodState> before) {
        int changed = 0;
        int changedCarvedAir = 0;
        long digest = FNV_OFFSET_BASIS;
        BlockPos samplePosition = null;
        BlockState sampleState = null;
        for (CaveNeighborhoodState snapshot : before) {
            BlockPos position = BlockPos.of(snapshot.position());
            BlockState actual = level.getBlockState(position);
            if (actual.equals(snapshot.state())) {
                continue;
            }
            changed++;
            if (snapshot.state().isAir()) {
                changedCarvedAir++;
            }
            digest = mix(digest, snapshot.position());
            digest = mix(digest, actual.toString().hashCode());
            if (samplePosition == null && snapshot.state().isAir()) {
                samplePosition = position.immutable();
                sampleState = actual;
            }
        }
        return new DecorationEvidence(changed, changedCarvedAir, digest, samplePosition, sampleState);
    }

    private static List<BaseColumnSnapshot> captureBaseColumns(
            ServerLevel level,
            List<ProofChunk> proofChunks) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                BASE_COLUMN_MAXIMUM_Y);
        List<BaseColumnSnapshot> result = new ArrayList<>(proofChunks.size());
        for (ProofChunk proofChunk : proofChunks) {
            int x = proofChunk.biomeSample().getX();
            int z = proofChunk.biomeSample().getZ();
            List<BlockState> states = new ArrayList<>(maximumY - minimumY + 1);
            for (int y = minimumY; y <= maximumY; y++) {
                states.add(level.getBlockState(new BlockPos(x, y, z)));
            }
            result.add(new BaseColumnSnapshot(x, z, minimumY, List.copyOf(states)));
        }
        return List.copyOf(result);
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
                    throw new IllegalStateException(
                            "SF-IMP-0062 mutated vertically unrelated BASE_WORLD terrain at BlockPos{x="
                                    + column.x() + ", y=" + y + ", z=" + column.z()
                                    + "}: before=" + expected + ", after=" + actual);
                }
            }
        }
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private record ProofChunk(LevelChunk chunk, BlockPos biomeSample) {}

    private record CaveNeighborhoodState(long position, BlockState state) {}

    private record BaseColumnSnapshot(int x, int z, int minimumY, List<BlockState> states) {}

    private record DecorationEvidence(
            int changedBlocks,
            int changedCarvedAirBlocks,
            long digest,
            BlockPos samplePosition,
            BlockState sampleState) {}
}
