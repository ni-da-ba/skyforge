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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.material.Fluids;

/**
 * Development-only SF-IMP-0063 proof for final-registry native fluid springs and asynchronous
 * exact-volume containment.
 */
final class SkyforgeNeoForge1211FluidSpringsDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.fluidSprings";

    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_REQUIRED_CHUNKS = 25;
    private static final int INTERIOR_MARGIN = 8;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final int SETTLE_TICKS = 100;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211FluidSpringsDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;
    private static long settleStartTick;
    private static SkyIslandWorldVolumeId proofVolumeId;
    private static InitialEvidence initialEvidence;
    private static List<BaseColumnSnapshot> baseColumnsBefore = List.of();
    private static OrdinaryFluidProbe ordinaryFluidProbe;

    private SkyforgeNeoForge1211FluidSpringsDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0063 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0063 proof over another physical-admission binding");
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
                "Skyforge SF-IMP-0063 FLUID_SPRINGS specimen enabled. The accepted high tableland will be "
                        + "physically admitted and carved first; final-registry minecraft:dripstone_caves springs "
                        + "then run through exact-volume population. Their later vanilla fluid ticks must recover "
                        + "persisted Skyforge provenance and remain inside compiled owner terrain.");
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

    static SkyIslandWorldCatalog catalog() {
        return SkyforgeNeoForge1211LocalModificationsDevRuntime.catalog();
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
                    "SF-IMP-0063 development volume admitted with unexpected footprint evidence: observed="
                            + admission.observedChunks() + ", required=" + admission.requiredChunks());
        }

        List<ProofChunk> proofChunks = loadedOwnerChunks(level, volumeId);
        if (proofChunks.isEmpty()) {
            return;
        }
        var generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(
                    "SF-IMP-0063 requires the active Minecraft noise generator, found " + generator.getClass());
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
            throw new IllegalStateException("SF-IMP-0063 proof volume has no safe carver interior frame");
        }

        baseColumnsBefore = captureBaseColumns(level, proofChunks);
        proofStarted = true;
        proofVolumeId = volumeId;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0063 proof resolved unexpected volume " + candidateId.path());
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
                    "SF-IMP-0063 prerequisite carver pass did not produce bounded cave topology: carveCalls="
                            + carveCalls + ", carvedBlocks=" + carvedBlocks
                            + ", mappedOutsideTarget=" + mappedCarverSamplesOutsideTarget);
        }

        Set<Long> carvedAir = captureOwnerAir(level, volumeId, proofChunks);
        if (carvedAir.isEmpty()) {
            throw new IllegalStateException("SF-IMP-0063 carver pass produced no persistent owner-local cave AIR");
        }

        int attemptedFeatures = 0;
        int successfulFeatures = 0;
        int heightRangeSamples = 0;
        int mappedSamplesOutsideVolume = 0;
        int acceptedWritePreflights = 0;
        int acceptedWriteAttempts = 0;
        long springTransformDigest = FNV_OFFSET_BASIS;
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
                            GenerationStep.Decoration.FLUID_SPRINGS,
                            MAXIMUM_ATTACHMENT_DEPTH);
                    snapshot = probe.snapshot();
                }
            } finally {
                postProcessing.close();
            }

            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            heightRangeSamples = Math.addExact(heightRangeSamples, snapshot.heightRangeSamples());
            mappedSamplesOutsideVolume = Math.addExact(
                    mappedSamplesOutsideVolume,
                    Math.addExact(snapshot.mappedSamplesBelowEnvelope(), snapshot.mappedSamplesAboveEnvelope()));
            acceptedWritePreflights = Math.addExact(
                    acceptedWritePreflights, snapshot.acceptedWritePreflights());
            acceptedWriteAttempts = Math.addExact(
                    acceptedWriteAttempts, snapshot.acceptedWriteAttempts());
            springTransformDigest = mix(springTransformDigest, proofChunk.chunk().getPos().toLong());
            springTransformDigest = mix(springTransformDigest, snapshot.heightTransformDigest());

            for (var feature : result.featureResults()) {
                if (!featureKeys.contains(feature.featureKey())) {
                    featureKeys.add(feature.featureKey());
                }
                if (feature.placed() && !successfulFeatureKeys.contains(feature.featureKey())) {
                    successfulFeatureKeys.add(feature.featureKey());
                }
            }
        }

        var initialProvenance = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        List<SkyforgeGeneratedFluidPropagationStage.TrackedFluid> initialTracked =
                SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volumeId);
        int adjacentToCarvedAir = 0;
        for (var tracked : initialTracked) {
            BlockPos position = BlockPos.of(tracked.position());
            if (adjacentToAny(position, carvedAir)) {
                adjacentToCarvedAir++;
            }
        }

        if (attemptedFeatures <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0063 final-registry dripstone-caves biome exposes no FLUID_SPRINGS features");
        }
        if (successfulFeatures <= 0 || initialTracked.isEmpty()) {
            throw new IllegalStateException(
                    "SF-IMP-0063 native FLUID_SPRINGS did not create tracked owner-local fluid: attempted="
                            + attemptedFeatures + ", successful=" + successfulFeatures
                            + ", keys=" + featureKeys + ", tracked=" + initialTracked.size());
        }
        if (adjacentToCarvedAir <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0063 created springs but none touch the actual carved cave neighborhood");
        }
        if (mappedSamplesOutsideVolume != 0) {
            throw new IllegalStateException("SF-IMP-0063 mapped a native spring height outside the exact volume");
        }

        ordinaryFluidProbe = createOrdinaryFluidProbe(
                level,
                volumeId,
                proofChunks,
                maximumEnvelopeY);

        initialEvidence = new InitialEvidence(
                carveCalls,
                carvedBlocks,
                Long.toUnsignedString(carveTransformDigest, 16),
                Long.toUnsignedString(carveDigest, 16),
                attemptedFeatures,
                successfulFeatures,
                List.copyOf(featureKeys),
                List.copyOf(successfulFeatureKeys),
                heightRangeSamples,
                mappedSamplesOutsideVolume,
                acceptedWritePreflights,
                acceptedWriteAttempts,
                Long.toUnsignedString(springTransformDigest, 16),
                initialProvenance,
                initialTracked.size(),
                adjacentToCarvedAir);
        settleStartTick = level.getGameTime();

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0063 FLUID SPRINGS INITIAL PASS: volume=" + volumeId.path()
                        + ", biome=" + Biomes.DRIPSTONE_CAVES.location()
                        + ", attemptedFeatures=" + attemptedFeatures
                        + ", successfulFeatures=" + successfulFeatures
                        + ", featureKeys=" + featureKeys
                        + ", successfulFeatureKeys=" + successfulFeatureKeys
                        + ", heightRangeSamples=" + heightRangeSamples
                        + ", mappedOutsideVolume=" + mappedSamplesOutsideVolume
                        + ", initialTrackedFluids=" + initialTracked.size()
                        + ", springsAdjacentToCarvedAir=" + adjacentToCarvedAir
                        + ", springTransformDigest=" + initialEvidence.springTransformDigest()
                        + ". Population scope is now closed; waiting " + SETTLE_TICKS
                        + " vanilla server ticks to prove asynchronous provenance containment.");
    }

    private static void finishProof(ServerLevel level) {
        if (proofVolumeId == null || initialEvidence == null || ordinaryFluidProbe == null) {
            throw new IllegalStateException("SF-IMP-0063 finish reached incomplete proof state");
        }

        var finalProvenance = SkyforgeGeneratedFluidPropagationStage.snapshot(level, proofVolumeId);
        List<SkyforgeGeneratedFluidPropagationStage.TrackedFluid> tracked =
                SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, proofVolumeId);

        int matchingPersistentFluids = 0;
        BlockPos samplePosition = null;
        String sampleState = null;
        for (var generated : tracked) {
            BlockPos position = BlockPos.of(generated.position());
            if (!compiledOwner(proofVolumeId, position)) {
                throw new IllegalStateException(
                        "SF-IMP-0063 persisted generated-fluid provenance outside compiled owner: " + position);
            }
            var state = level.getFluidState(position);
            ResourceLocation actualKey = state.isEmpty()
                    ? null
                    : BuiltInRegistries.FLUID.getKey(state.getType());
            if (!generated.fluidKey().equals(actualKey)) {
                continue;
            }
            matchingPersistentFluids++;
            if (samplePosition == null) {
                samplePosition = position.immutable();
                sampleState = level.getBlockState(position).toString();
            }
        }

        boolean ordinaryFlowed = !level.getFluidState(ordinaryFluidProbe.flowTarget()).isEmpty();
        boolean ordinaryTracked = tracked.stream()
                .anyMatch(entry -> entry.position() == ordinaryFluidProbe.source().asLong()
                        || entry.position() == ordinaryFluidProbe.flowTarget().asLong());

        requireBaseColumnsPreserved(level, baseColumnsBefore);

        if (finalProvenance.propagationTicks() <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0063 native springs scheduled no observed asynchronous FlowingFluid ticks");
        }
        if (finalProvenance.capturedSchedules() <= initialEvidence.initialProvenance().capturedSchedules()) {
            throw new IllegalStateException(
                    "SF-IMP-0063 asynchronous propagation produced no descendant scheduled fluid ticks");
        }
        if (matchingPersistentFluids <= 0 || samplePosition == null || sampleState == null) {
            throw new IllegalStateException(
                    "SF-IMP-0063 generated spring provenance has no persistent matching fluid after settle window");
        }
        if (!ordinaryFlowed || ordinaryTracked) {
            throw new IllegalStateException(
                    "SF-IMP-0063 interfered with unrelated vanilla fluid control: flowed="
                            + ordinaryFlowed + ", acquiredProvenance=" + ordinaryTracked);
        }

        cleanupOrdinaryProbe(level, ordinaryFluidProbe);
        proofComplete = true;

        String provenanceDigest = Long.toUnsignedString(finalProvenance.digest(), 16);
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0063 FLUID SPRINGS PASS: volume=" + proofVolumeId.path()
                        + ", biome=" + Biomes.DRIPSTONE_CAVES.location()
                        + ", attemptedFeatures=" + initialEvidence.attemptedFeatures()
                        + ", successfulFeatures=" + initialEvidence.successfulFeatures()
                        + ", successfulFeatureKeys=" + initialEvidence.successfulFeatureKeys()
                        + ", springTransformDigest=" + initialEvidence.springTransformDigest()
                        + ", initialTrackedFluids=" + initialEvidence.initialTrackedFluids()
                        + ", finalTrackedFluids=" + finalProvenance.trackedPositions()
                        + ", matchingPersistentFluids=" + matchingPersistentFluids
                        + ", capturedSchedules=" + finalProvenance.capturedSchedules()
                        + ", propagationTicks=" + finalProvenance.propagationTicks()
                        + ", hiddenBoundaryReads=" + finalProvenance.hiddenBoundaryReads()
                        + ", rejectedBoundaryWrites=" + finalProvenance.rejectedBoundaryWrites()
                        + ", scheduledOutsideOwner=" + finalProvenance.scheduledOutsideOwner()
                        + ", provenanceDigest=" + provenanceDigest
                        + ", ordinaryVanillaFluidFlowed=true"
                        + ", ordinaryVanillaFluidUntracked=true"
                        + ", baseColumnsPreserved=true"
                        + ", sampleFluid=" + samplePosition
                        + ", sampleState=" + sampleState + ".");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("carveTransformDigest", initialEvidence.carveTransformDigest()),
                        java.util.Map.entry("carveDigest", initialEvidence.carveDigest()),
                        java.util.Map.entry("springTransformDigest", initialEvidence.springTransformDigest()),
                        java.util.Map.entry("attemptedFeatures", initialEvidence.attemptedFeatures()),
                        java.util.Map.entry("successfulFeatures", initialEvidence.successfulFeatures()),
                        java.util.Map.entry("initialTrackedFluids", initialEvidence.initialTrackedFluids()),
                        java.util.Map.entry("finalTrackedFluids", finalProvenance.trackedPositions()),
                        java.util.Map.entry("matchingPersistentFluids", matchingPersistentFluids),
                        java.util.Map.entry("capturedSchedules", finalProvenance.capturedSchedules()),
                        java.util.Map.entry("propagationTicks", finalProvenance.propagationTicks()),
                        java.util.Map.entry("hiddenBoundaryReads", finalProvenance.hiddenBoundaryReads()),
                        java.util.Map.entry("rejectedBoundaryWrites", finalProvenance.rejectedBoundaryWrites()),
                        java.util.Map.entry("scheduledOutsideOwner", finalProvenance.scheduledOutsideOwner()),
                        java.util.Map.entry("provenanceDigest", provenanceDigest),
                        java.util.Map.entry("mappedOutsideVolume", initialEvidence.mappedSamplesOutsideVolume()),
                        java.util.Map.entry("ordinaryVanillaFluidFlowed", true),
                        java.util.Map.entry("ordinaryVanillaFluidUntracked", true),
                        java.util.Map.entry("baseColumnsPreserved", true),
                        java.util.Map.entry("sampleFluidPos", Long.toString(samplePosition.asLong())),
                        java.util.Map.entry("sampleFluidState", sampleState)));
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

    private static Set<Long> captureOwnerAir(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            List<ProofChunk> proofChunks) {
        var bounds = SkyforgeNeoForge1211SurfaceStage.volumeBounds(volumeId).orElseThrow();
        int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.ceil(bounds.minimumY()));
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                (int) Math.floor(bounds.maximumY()));
        Set<Long> result = new HashSet<>();
        for (ProofChunk proofChunk : proofChunks) {
            ChunkPos chunkPos = proofChunk.chunk().getPos();
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    for (int y = minimumY; y <= maximumY; y++) {
                        BlockPos position = new BlockPos(x, y, z);
                        if (compiledOwner(volumeId, position) && level.getBlockState(position).isAir()) {
                            result.add(position.asLong());
                        }
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean adjacentToAny(
            BlockPos position,
            Set<Long> positions) {
        return positions.contains(position.above().asLong())
                || positions.contains(position.below().asLong())
                || positions.contains(position.north().asLong())
                || positions.contains(position.south().asLong())
                || positions.contains(position.east().asLong())
                || positions.contains(position.west().asLong());
    }

    private static OrdinaryFluidProbe createOrdinaryFluidProbe(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            List<ProofChunk> proofChunks,
            int maximumEnvelopeY) {
        int y = Math.min(level.getMaxBuildHeight() - 2, maximumEnvelopeY + 8);
        for (ProofChunk proofChunk : proofChunks) {
            ChunkPos chunk = proofChunk.chunk().getPos();
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    if (hasOwnerColumn(level, volumeId, x, z)) {
                        continue;
                    }
                    BlockPos source = new BlockPos(x, y, z);
                    BlockPos target = source.below();
                    BlockPos floor = target.below();
                    if (!level.getBlockState(source).isAir() || !level.getBlockState(target).isAir()) {
                        continue;
                    }
                    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(source, Blocks.WATER.defaultBlockState(), 3);
                    level.scheduleTick(source, Fluids.WATER, 1);
                    return new OrdinaryFluidProbe(source, target, floor);
                }
            }
        }
        throw new IllegalStateException(
                "SF-IMP-0063 could not find an already-loaded unowned column for vanilla-fluid control");
    }

    private static boolean hasOwnerColumn(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            int x,
            int z) {
        return SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId,
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight())
                .isPresent();
    }

    private static void cleanupOrdinaryProbe(
            ServerLevel level,
            OrdinaryFluidProbe probe) {
        level.setBlock(probe.source(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(probe.flowTarget(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(probe.floor(), Blocks.AIR.defaultBlockState(), 3);
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
                        "SF-IMP-0063 runtime binding disappeared during ownership scan"));
    }

    private static List<BaseColumnSnapshot> captureBaseColumns(
            ServerLevel level,
            List<ProofChunk> proofChunks) {
        int minimumY = Math.max(level.getMinBuildHeight(), BASE_COLUMN_MINIMUM_Y);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, BASE_COLUMN_MAXIMUM_Y);
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
                            "SF-IMP-0063 mutated vertically unrelated BASE_WORLD terrain at BlockPos{x="
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

    private record BaseColumnSnapshot(int x, int z, int minimumY, List<BlockState> states) {}

    private record OrdinaryFluidProbe(BlockPos source, BlockPos flowTarget, BlockPos floor) {}

    private record InitialEvidence(
            int carveCalls,
            int carvedBlocks,
            String carveTransformDigest,
            String carveDigest,
            int attemptedFeatures,
            int successfulFeatures,
            List<ResourceLocation> featureKeys,
            List<ResourceLocation> successfulFeatureKeys,
            int heightRangeSamples,
            int mappedSamplesOutsideVolume,
            int acceptedWritePreflights,
            int acceptedWriteAttempts,
            String springTransformDigest,
            SkyforgeGeneratedFluidPropagationStage.Snapshot initialProvenance,
            int initialTrackedFluids,
            int springsAdjacentToCarvedAir) {}
}
