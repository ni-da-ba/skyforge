package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Development-only SF-IMP-0064 proof for whole-footprint native LAKES and subsequent generated-fluid
 * containment.
 */
final class SkyforgeNeoForge1211NativeLakesDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.nativeLakes";

    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_REQUIRED_CHUNKS = 25;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final int SETTLE_TICKS = 80;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211NativeLakesDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;
    private static long settleStartTick;
    private static SkyIslandWorldVolumeId proofVolumeId;
    private static PlacementEvidence placementEvidence;
    private static List<BaseColumnSnapshot> baseColumnsBefore = List.of();

    private SkyforgeNeoForge1211NativeLakesDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static SkyIslandWorldCatalog catalog() {
        return SkyforgeNeoForge1211LocalModificationsDevRuntime.catalog();
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0064 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0064 proof over another physical-admission binding");
        }

        SkyIslandWorldCatalog catalog = catalog();
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0064 native LAKES specimen enabled. Final-registry river LAKES are "
                        + "mapped into the admitted high tableland. Native LakeFeature is allowed to begin only "
                        + "after its conservative whole-footprint compiled-owner preflight succeeds.");
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofComplete || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        if (level.players().isEmpty() && !SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            return;
        }

        if (!proofStarted) {
            startProof(level);
            return;
        }
        if (level.getGameTime() - settleStartTick < SETTLE_TICKS) {
            return;
        }
        finishProof(level);
    }

    private static void startProof(ServerLevel level) {
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
                    "SF-IMP-0064 development volume admitted with unexpected footprint evidence: observed="
                            + admission.observedChunks() + ", required=" + admission.requiredChunks());
        }

        List<ProofChunk> proofChunks = loadedOwnerChunks(level, volumeId);
        if (proofChunks.isEmpty()) {
            return;
        }
        proofStarted = true;
        proofVolumeId = volumeId;

        int minimumEnvelopeY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(volume.bounds().minimumY()));
        int maximumEnvelopeY = Math.min(
                level.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        StateSnapshot before = StateSnapshot.capture(
                level,
                (int) Math.floor(volume.bounds().minimumX()) + SkyforgeNativeLakeAdmissionStage.MIN_X_OFFSET,
                (int) Math.ceil(volume.bounds().maximumX()) + SkyforgeNativeLakeAdmissionStage.MAX_X_OFFSET,
                minimumEnvelopeY + SkyforgeNativeLakeAdmissionStage.MIN_Y_OFFSET,
                maximumEnvelopeY + SkyforgeNativeLakeAdmissionStage.MAX_Y_OFFSET,
                (int) Math.floor(volume.bounds().minimumZ()) + SkyforgeNativeLakeAdmissionStage.MIN_Z_OFFSET,
                (int) Math.ceil(volume.bounds().maximumZ()) + SkyforgeNativeLakeAdmissionStage.MAX_Z_OFFSET);
        baseColumnsBefore = captureBaseColumns(level, proofChunks);

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0064 proof resolved unexpected volume " + candidateId.path());
            }
            return Biomes.RIVER;
        };

        int attemptedFeatures = 0;
        int successfulFeatures = 0;
        int configuredLakeAttempts = 0;
        int admittedConfiguredLakes = 0;
        int rejectedConfiguredLakes = 0;
        int unsupportedLakeFeatures = 0;
        int inspectedPositions = 0;
        int heightRangeSamples = 0;
        int mappedOutsideVolume = 0;
        long admissionDigest = FNV_OFFSET_BASIS;
        long transformDigest = FNV_OFFSET_BASIS;
        List<ResourceLocation> featureKeys = new ArrayList<>();
        List<ResourceLocation> successfulFeatureKeys = new ArrayList<>();
        List<BlockPos> admittedOrigins = new ArrayList<>();
        List<BlockPos> rejectedOrigins = new ArrayList<>();

        for (ProofChunk proofChunk : proofChunks) {
            SkyforgeNativeBiomePopulationRunner.Result result;
            SkyforgeUndergroundPlacementProbe.Snapshot placement;
            var postProcessing = SkyforgeDeferredPopulationPostProcessingBridge.open(level);
            try {
                try (var probe = SkyforgeUndergroundPlacementProbe.open(
                        volumeId,
                        minimumEnvelopeY,
                        maximumEnvelopeY)) {
                    result = SkyforgeNativeBiomePopulationRunner.populateStep(
                            level,
                            level.getChunkSource().getGenerator(),
                            biomeResolver,
                            volumeId,
                            proofChunk.chunk().getPos(),
                            proofChunk.surfaceBlock(),
                            GenerationStep.Decoration.LAKES,
                            MAXIMUM_ATTACHMENT_DEPTH);
                    placement = probe.snapshot();
                }
            } finally {
                postProcessing.close();
            }

            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            var lake = result.lakeEvidence();
            configuredLakeAttempts = Math.addExact(configuredLakeAttempts, lake.attemptedConfiguredLakes());
            admittedConfiguredLakes = Math.addExact(admittedConfiguredLakes, lake.admittedConfiguredLakes());
            rejectedConfiguredLakes = Math.addExact(rejectedConfiguredLakes, lake.rejectedConfiguredLakes());
            unsupportedLakeFeatures = Math.addExact(unsupportedLakeFeatures, lake.unsupportedPlacedFeatures());
            inspectedPositions = Math.addExact(inspectedPositions, lake.inspectedPositions());
            admittedOrigins.addAll(lake.admittedOrigins());
            rejectedOrigins.addAll(lake.rejectedOrigins());
            admissionDigest = mix(admissionDigest, proofChunk.chunk().getPos().toLong());
            admissionDigest = mix(admissionDigest, lake.decisionDigest());

            heightRangeSamples = Math.addExact(heightRangeSamples, placement.heightRangeSamples());
            mappedOutsideVolume = Math.addExact(
                    mappedOutsideVolume,
                    Math.addExact(placement.mappedSamplesBelowEnvelope(), placement.mappedSamplesAboveEnvelope()));
            transformDigest = mix(transformDigest, proofChunk.chunk().getPos().toLong());
            transformDigest = mix(transformDigest, placement.heightTransformDigest());

            for (var feature : result.featureResults()) {
                if (!featureKeys.contains(feature.featureKey())) {
                    featureKeys.add(feature.featureKey());
                }
                if (feature.placed() && !successfulFeatureKeys.contains(feature.featureKey())) {
                    successfulFeatureKeys.add(feature.featureKey());
                }
            }
        }

        if (featureKeys.isEmpty()) {
            throw new IllegalStateException("SF-IMP-0064 final-registry river LAKES exposed no feature keys");
        }
        BlockPos rejectionProbeOrigin = new BlockPos(
                (int) Math.floor(volume.bounds().maximumX()),
                volume.center().y(),
                (int) Math.round((volume.bounds().minimumZ() + volume.bounds().maximumZ()) * 0.5));
        StateSnapshot rejectionProbeBefore = StateSnapshot.capture(
                level,
                rejectionProbeOrigin.getX() + SkyforgeNativeLakeAdmissionStage.MIN_X_OFFSET,
                rejectionProbeOrigin.getX() + SkyforgeNativeLakeAdmissionStage.MAX_X_OFFSET,
                rejectionProbeOrigin.getY() + SkyforgeNativeLakeAdmissionStage.MIN_Y_OFFSET,
                rejectionProbeOrigin.getY() + SkyforgeNativeLakeAdmissionStage.MAX_Y_OFFSET,
                rejectionProbeOrigin.getZ() + SkyforgeNativeLakeAdmissionStage.MIN_Z_OFFSET,
                rejectionProbeOrigin.getZ() + SkyforgeNativeLakeAdmissionStage.MAX_Z_OFFSET);
        var rejectionOperation = SkyforgePopulationOperation.create(
                volumeId,
                new ChunkPos(rejectionProbeOrigin),
                featureKeys.getFirst(),
                GenerationStep.Decoration.LAKES.ordinal(),
                attemptedFeatures);
        var rejectionProbe = SkyforgeNativeLakeAdmissionStage.probe(
                rejectionOperation,
                rejectionProbeOrigin);
        int rejectionProbeChangedBlocks = rejectionProbeBefore.changedPositions(level).size();
        boolean rejectionProbeRejected = rejectionProbe.rejected() == 1
                && rejectionProbe.admitted() == 0
                && rejectionProbeChangedBlocks == 0;
        if (!rejectionProbeRejected) {
            throw new IllegalStateException(
                    "SF-IMP-0064 deterministic edge rejection probe did not fail closed: origin="
                            + rejectionProbeOrigin
                            + ", admitted=" + rejectionProbe.admitted()
                            + ", rejected=" + rejectionProbe.rejected()
                            + ", changedBlocks=" + rejectionProbeChangedBlocks);
        }
        admissionDigest = mix(admissionDigest, rejectionProbeOrigin.asLong());
        admissionDigest = mix(admissionDigest, rejectionProbe.decisionDigest());

        List<Long> placementChanges = before.changedPositions(level);
        int changedInsideAdmitted = 0;
        int changedRejectedOnly = 0;
        for (long packed : placementChanges) {
            BlockPos position = BlockPos.of(packed);
            boolean admitted = insideAnyAdmissionEnvelope(position, admittedOrigins);
            boolean rejected = insideAnyAdmissionEnvelope(position, rejectedOrigins);
            if (!admitted) {
                throw new IllegalStateException(
                        "SF-IMP-0064 native LAKES changed a block outside every admitted whole-feature envelope: "
                                + position + ", admittedOrigins=" + admittedOrigins);
            }
            changedInsideAdmitted++;
            if (rejected && !admitted) {
                changedRejectedOnly++;
            }
        }

        // Re-scan rejected envelopes directly: a rejected-only cell may not appear in the general
        // changed list only if it is exactly unchanged. Overlap with an admitted envelope is allowed
        // because an independently accepted lake owns that mutation.
        for (BlockPos rejectedOrigin : rejectedOrigins) {
            for (long packed : before.changedPositionsWithin(level, rejectedOrigin)) {
                BlockPos position = BlockPos.of(packed);
                if (!insideAnyAdmissionEnvelope(position, admittedOrigins)) {
                    changedRejectedOnly++;
                }
            }
        }

        var provenance = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        List<SkyforgeGeneratedFluidPropagationStage.TrackedFluid> tracked =
                SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volumeId);

        if (attemptedFeatures != proofChunks.size() * 2) {
            throw new IllegalStateException(
                    "SF-IMP-0064 final-registry river LAKES did not expose two placed features per proof chunk: "
                            + "attempted=" + attemptedFeatures + ", chunks=" + proofChunks.size()
                            + ", featureKeys=" + featureKeys);
        }
        if (unsupportedLakeFeatures != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0064 vanilla river LAKES unexpectedly contains unsupported configured feature classes: "
                            + unsupportedLakeFeatures);
        }
        if (configuredLakeAttempts <= 0 || admittedConfiguredLakes <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0064 deterministic registry stream produced no admitted native lake: configured="
                            + configuredLakeAttempts + ", admitted=" + admittedConfiguredLakes
                            + ", rejected=" + rejectedConfiguredLakes);
        }
        if (successfulFeatures <= 0 || placementChanges.isEmpty() || tracked.isEmpty()) {
            throw new IllegalStateException(
                    "SF-IMP-0064 admitted native lake produced no persistent lake mutation/fluid: successfulFeatures="
                            + successfulFeatures + ", changed=" + placementChanges.size()
                            + ", trackedFluids=" + tracked.size());
        }
        if (changedRejectedOnly != 0 || changedInsideAdmitted != placementChanges.size()) {
            throw new IllegalStateException(
                    "SF-IMP-0064 rejected lake left persistent mutation: changedRejectedOnly=" + changedRejectedOnly);
        }
        if (heightRangeSamples <= 0 || mappedOutsideVolume != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0064 native lake vertical mapping evidence invalid: samples=" + heightRangeSamples
                            + ", mappedOutsideVolume=" + mappedOutsideVolume);
        }

        SkyforgeGeneratedFluidPropagationStage.TrackedFluid sample = tracked.getFirst();
        BlockPos samplePosition = BlockPos.of(sample.position());
        var sampleFluid = level.getFluidState(samplePosition);
        if (sampleFluid.isEmpty()
                || !sample.fluidKey().equals(BuiltInRegistries.FLUID.getKey(sampleFluid.getType()))) {
            throw new IllegalStateException(
                    "SF-IMP-0064 tracked lake-fluid sample no longer matches live state at " + samplePosition);
        }
        // LakeFeature does not need to schedule a tick to prove placement. Trigger one ordinary
        // vanilla fluid tick after population has closed to prove that a later environmental update
        // recovers the SF-IMP-0063 persisted provenance rather than escaping the exact owner.
        level.scheduleTick(samplePosition, sampleFluid.getType(), 1);

        placementEvidence = new PlacementEvidence(
                attemptedFeatures,
                successfulFeatures,
                List.copyOf(featureKeys),
                List.copyOf(successfulFeatureKeys),
                configuredLakeAttempts,
                admittedConfiguredLakes,
                rejectedConfiguredLakes,
                rejectionProbeRejected,
                rejectionProbeChangedBlocks,
                unsupportedLakeFeatures,
                inspectedPositions,
                Long.toUnsignedString(admissionDigest, 16),
                Long.toUnsignedString(transformDigest, 16),
                heightRangeSamples,
                mappedOutsideVolume,
                placementChanges.size(),
                changedRejectedOnly,
                provenance.trackedPositions(),
                samplePosition.asLong(),
                level.getBlockState(samplePosition).toString());
        settleStartTick = level.getGameTime();

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0064 NATIVE LAKES INITIAL PASS: volume=" + volumeId.path()
                        + ", biome=" + Biomes.RIVER.location()
                        + ", attemptedFeatures=" + attemptedFeatures
                        + ", successfulFeatures=" + successfulFeatures
                        + ", featureKeys=" + featureKeys
                        + ", successfulFeatureKeys=" + successfulFeatureKeys
                        + ", configuredLakeAttempts=" + configuredLakeAttempts
                        + ", admittedConfiguredLakes=" + admittedConfiguredLakes
                        + ", rejectedConfiguredLakes=" + rejectedConfiguredLakes
                        + ", rejectionProbeRejected=" + rejectionProbeRejected
                        + ", rejectionProbeChangedBlocks=" + rejectionProbeChangedBlocks
                        + ", unsupportedLakeFeatures=" + unsupportedLakeFeatures
                        + ", inspectedPositions=" + inspectedPositions
                        + ", admissionDigest=" + placementEvidence.admissionDigest()
                        + ", transformDigest=" + placementEvidence.transformDigest()
                        + ", heightRangeSamples=" + heightRangeSamples
                        + ", mappedOutsideVolume=" + mappedOutsideVolume
                        + ", placementChangedBlocks=" + placementChanges.size()
                        + ", changedRejectedOnly=0"
                        + ", initialTrackedLakeFluids=" + provenance.trackedPositions()
                        + ". Population scope is closed; one persisted lake fluid has been scheduled through "
                        + "ordinary vanilla ticking to prove asynchronous containment.");
    }

    private static void finishProof(ServerLevel level) {
        if (proofVolumeId == null || placementEvidence == null) {
            throw new IllegalStateException("SF-IMP-0064 finish reached incomplete proof state");
        }

        var provenance = SkyforgeGeneratedFluidPropagationStage.snapshot(level, proofVolumeId);
        List<SkyforgeGeneratedFluidPropagationStage.TrackedFluid> tracked =
                SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, proofVolumeId);
        int matchingPersistentFluids = 0;
        BlockPos sample = null;
        String sampleState = null;
        for (var entry : tracked) {
            BlockPos position = BlockPos.of(entry.position());
            if (!compiledOwner(proofVolumeId, position)) {
                throw new IllegalStateException(
                        "SF-IMP-0064 generated lake fluid escaped compiled owner provenance: " + position);
            }
            var state = level.getFluidState(position);
            ResourceLocation actual = state.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(state.getType());
            if (entry.fluidKey().equals(actual)) {
                matchingPersistentFluids++;
                if (sample == null) {
                    sample = position.immutable();
                    sampleState = level.getBlockState(position).toString();
                }
            }
        }

        requireBaseColumnsPreserved(level, baseColumnsBefore);
        if (provenance.propagationTicks() <= 0
                || matchingPersistentFluids <= 0
                || sample == null
                || sampleState == null) {
            throw new IllegalStateException(
                    "SF-IMP-0064 lake fluid failed asynchronous containment proof: propagationTicks="
                            + provenance.propagationTicks() + ", matchingPersistentFluids="
                            + matchingPersistentFluids);
        }

        proofComplete = true;
        String provenanceDigest = Long.toUnsignedString(provenance.digest(), 16);
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0064 NATIVE LAKES PASS: admissionDigest=" + placementEvidence.admissionDigest()
                        + ", transformDigest=" + placementEvidence.transformDigest()
                        + ", configuredLakeAttempts=" + placementEvidence.configuredLakeAttempts()
                        + ", admittedConfiguredLakes=" + placementEvidence.admittedConfiguredLakes()
                        + ", rejectedConfiguredLakes=" + placementEvidence.rejectedConfiguredLakes()
                        + ", rejectionProbeRejected=" + placementEvidence.rejectionProbeRejected()
                        + ", rejectionProbeChangedBlocks=" + placementEvidence.rejectionProbeChangedBlocks()
                        + ", successfulFeatures=" + placementEvidence.successfulFeatures()
                        + ", placementChangedBlocks=" + placementEvidence.placementChangedBlocks()
                        + ", changedRejectedOnly=0"
                        + ", finalTrackedLakeFluids=" + provenance.trackedPositions()
                        + ", matchingPersistentFluids=" + matchingPersistentFluids
                        + ", propagationTicks=" + provenance.propagationTicks()
                        + ", hiddenBoundaryReads=" + provenance.hiddenBoundaryReads()
                        + ", rejectedBoundaryWrites=" + provenance.rejectedBoundaryWrites()
                        + ", scheduledOutsideOwner=" + provenance.scheduledOutsideOwner()
                        + ", provenanceDigest=" + provenanceDigest
                        + ", baseColumnsPreserved=true"
                        + ", sampleFluid=" + sample
                        + ", sampleState=" + sampleState + ".");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("admissionDigest", placementEvidence.admissionDigest()),
                        java.util.Map.entry("transformDigest", placementEvidence.transformDigest()),
                        java.util.Map.entry("attemptedFeatures", placementEvidence.attemptedFeatures()),
                        java.util.Map.entry("successfulFeatures", placementEvidence.successfulFeatures()),
                        java.util.Map.entry("configuredLakeAttempts", placementEvidence.configuredLakeAttempts()),
                        java.util.Map.entry("admittedConfiguredLakes", placementEvidence.admittedConfiguredLakes()),
                        java.util.Map.entry("rejectedConfiguredLakes", placementEvidence.rejectedConfiguredLakes()),
                        java.util.Map.entry("rejectionProbeRejected", placementEvidence.rejectionProbeRejected()),
                        java.util.Map.entry("rejectionProbeChangedBlocks", placementEvidence.rejectionProbeChangedBlocks()),
                        java.util.Map.entry("unsupportedLakeFeatures", placementEvidence.unsupportedLakeFeatures()),
                        java.util.Map.entry("mappedOutsideVolume", placementEvidence.mappedOutsideVolume()),
                        java.util.Map.entry("placementChangedBlocks", placementEvidence.placementChangedBlocks()),
                        java.util.Map.entry("changedRejectedOnly", 0),
                        java.util.Map.entry("finalTrackedLakeFluids", provenance.trackedPositions()),
                        java.util.Map.entry("matchingPersistentFluids", matchingPersistentFluids),
                        java.util.Map.entry("propagationTicks", provenance.propagationTicks()),
                        java.util.Map.entry("hiddenBoundaryReads", provenance.hiddenBoundaryReads()),
                        java.util.Map.entry("rejectedBoundaryWrites", provenance.rejectedBoundaryWrites()),
                        java.util.Map.entry("scheduledOutsideOwner", provenance.scheduledOutsideOwner()),
                        java.util.Map.entry("provenanceDigest", provenanceDigest),
                        java.util.Map.entry("baseColumnsPreserved", true),
                        java.util.Map.entry("sampleFluidPos", Long.toString(sample.asLong())),
                        java.util.Map.entry("sampleFluidState", sampleState)));
    }

    private static boolean compiledOwner(
            SkyIslandWorldVolumeId volumeId,
            BlockPos position) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                        volumeId,
                        position.getX(),
                        position.getY(),
                        position.getZ())
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0064 runtime binding disappeared during ownership scan"));
    }

    private static boolean insideAnyAdmissionEnvelope(
            BlockPos position,
            List<BlockPos> origins) {
        for (BlockPos origin : origins) {
            int dx = position.getX() - origin.getX();
            int dy = position.getY() - origin.getY();
            int dz = position.getZ() - origin.getZ();
            if (dx >= SkyforgeNativeLakeAdmissionStage.MIN_X_OFFSET
                    && dx <= SkyforgeNativeLakeAdmissionStage.MAX_X_OFFSET
                    && dy >= SkyforgeNativeLakeAdmissionStage.MIN_Y_OFFSET
                    && dy <= SkyforgeNativeLakeAdmissionStage.MAX_Y_OFFSET
                    && dz >= SkyforgeNativeLakeAdmissionStage.MIN_Z_OFFSET
                    && dz <= SkyforgeNativeLakeAdmissionStage.MAX_Z_OFFSET) {
                return true;
            }
        }
        return false;
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
                BlockPos sample = surfaceSample(level, volumeId, chunk.getPos());
                if (sample != null) {
                    result.add(new ProofChunk(chunk, sample));
                }
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos surfaceSample(
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

    private static List<BaseColumnSnapshot> captureBaseColumns(
            ServerLevel level,
            List<ProofChunk> proofChunks) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, BASE_COLUMN_MAXIMUM_Y);
        List<BaseColumnSnapshot> result = new ArrayList<>(proofChunks.size());
        for (ProofChunk proofChunk : proofChunks) {
            int x = proofChunk.surfaceBlock().getX();
            int z = proofChunk.surfaceBlock().getZ();
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
                            "SF-IMP-0064 mutated vertically unrelated BASE_WORLD terrain at BlockPos{x="
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

    private record ProofChunk(LevelChunk chunk, BlockPos surfaceBlock) {}

    private record BaseColumnSnapshot(int x, int z, int minimumY, List<BlockState> states) {}

    private record PlacementEvidence(
            int attemptedFeatures,
            int successfulFeatures,
            List<ResourceLocation> featureKeys,
            List<ResourceLocation> successfulFeatureKeys,
            int configuredLakeAttempts,
            int admittedConfiguredLakes,
            int rejectedConfiguredLakes,
            boolean rejectionProbeRejected,
            int rejectionProbeChangedBlocks,
            int unsupportedLakeFeatures,
            int inspectedPositions,
            String admissionDigest,
            String transformDigest,
            int heightRangeSamples,
            int mappedOutsideVolume,
            int placementChangedBlocks,
            int changedRejectedOnly,
            int initialTrackedFluids,
            long initialSamplePosition,
            String initialSampleState) {}

    private static final class StateSnapshot {
        private final int minimumX;
        private final int maximumX;
        private final int minimumY;
        private final int maximumY;
        private final int minimumZ;
        private final int maximumZ;
        private final BlockState[] states;

        private StateSnapshot(
                int minimumX,
                int maximumX,
                int minimumY,
                int maximumY,
                int minimumZ,
                int maximumZ,
                BlockState[] states) {
            this.minimumX = minimumX;
            this.maximumX = maximumX;
            this.minimumY = minimumY;
            this.maximumY = maximumY;
            this.minimumZ = minimumZ;
            this.maximumZ = maximumZ;
            this.states = states;
        }

        static StateSnapshot capture(
                ServerLevel level,
                int minimumX,
                int maximumX,
                int minimumY,
                int maximumY,
                int minimumZ,
                int maximumZ) {
            int sizeX = Math.addExact(Math.subtractExact(maximumX, minimumX), 1);
            int sizeY = Math.addExact(Math.subtractExact(maximumY, minimumY), 1);
            int sizeZ = Math.addExact(Math.subtractExact(maximumZ, minimumZ), 1);
            int size = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
            BlockState[] states = new BlockState[size];
            int index = 0;
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    for (int y = minimumY; y <= maximumY; y++) {
                        states[index++] = level.getBlockState(new BlockPos(x, y, z));
                    }
                }
            }
            return new StateSnapshot(
                    minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ, states);
        }

        List<Long> changedPositions(ServerLevel level) {
            List<Long> changed = new ArrayList<>();
            int index = 0;
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    for (int y = minimumY; y <= maximumY; y++) {
                        BlockPos position = new BlockPos(x, y, z);
                        if (!states[index++].equals(level.getBlockState(position))) {
                            changed.add(position.asLong());
                        }
                    }
                }
            }
            return List.copyOf(changed);
        }

        List<Long> changedPositionsWithin(
                ServerLevel level,
                BlockPos origin) {
            Set<Long> changed = new HashSet<>();
            for (int dx = SkyforgeNativeLakeAdmissionStage.MIN_X_OFFSET;
                    dx <= SkyforgeNativeLakeAdmissionStage.MAX_X_OFFSET;
                    dx++) {
                for (int dz = SkyforgeNativeLakeAdmissionStage.MIN_Z_OFFSET;
                        dz <= SkyforgeNativeLakeAdmissionStage.MAX_Z_OFFSET;
                        dz++) {
                    for (int dy = SkyforgeNativeLakeAdmissionStage.MIN_Y_OFFSET;
                            dy <= SkyforgeNativeLakeAdmissionStage.MAX_Y_OFFSET;
                            dy++) {
                        BlockPos position = origin.offset(dx, dy, dz);
                        if (!contains(position)) {
                            continue;
                        }
                        int index = index(position);
                        if (!states[index].equals(level.getBlockState(position))) {
                            changed.add(position.asLong());
                        }
                    }
                }
            }
            return List.copyOf(changed);
        }

        private boolean contains(BlockPos position) {
            return position.getX() >= minimumX
                    && position.getX() <= maximumX
                    && position.getY() >= minimumY
                    && position.getY() <= maximumY
                    && position.getZ() >= minimumZ
                    && position.getZ() <= maximumZ;
        }

        private int index(BlockPos position) {
            int sizeY = maximumY - minimumY + 1;
            int sizeZ = maximumZ - minimumZ + 1;
            int xIndex = position.getX() - minimumX;
            int zIndex = position.getZ() - minimumZ;
            int yIndex = position.getY() - minimumY;
            return (xIndex * sizeZ + zIndex) * sizeY + yIndex;
        }
    }
}
