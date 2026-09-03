package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldCatalog;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import io.github.nidaba.skyforge.world.WorldBounds;
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

/** Development-only acceptance proof for SF-IMP-0060 registry-native local modifications. */
final class SkyforgeNeoForge1211LocalModificationsDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.localModifications";

    // Development evidence seed selected so the unchanged vanilla amethyst-geode rarity filter
    // deterministically admits the central owner-rich chunk. Production rarity and RNG are never
    // bypassed or replaced; only this disposable specimen identity is controlled.
    private static final long ROOT_SEED = 0x5346494d50304e20L;
    private static final long GEOMETRY_SEED = ROOT_SEED ^ 0x4c4f43414cL;
    private static final int PROOF_RADIUS_CHUNKS = 2;
    private static final int EXPECTED_REQUIRED_CHUNKS = 25;
    private static final int BASE_COLUMN_MINIMUM_Y = -64;
    private static final int BASE_COLUMN_MAXIMUM_Y = 150;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final int INSPECTION_X = 8;
    private static final int INSPECTION_Y = 280;
    private static final int INSPECTION_Z = 8;
    private static final ResourceLocation AMETHYST_GEODE =
            ResourceLocation.withDefaultNamespace("amethyst_geode");
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211LocalModificationsDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static boolean proofStarted;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211LocalModificationsDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null || persistentAdmissionBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0060 proof over another terrain binding");
        }
        if (SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException("cannot install SF-IMP-0060 proof over another physical-admission binding");
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
                "Skyforge SF-IMP-0060 admitted local-modifications specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and load the origin 5x5 chunk patch; if needed teleport to x="
                        + INSPECTION_X + ", y=" + INSPECTION_Y + ", z=" + INSPECTION_Z
                        + ". The high tableland remains absent until whole-volume physical admission completes. Its "
                        + "development seed was selected so the unchanged final-registry minecraft:amethyst_geode "
                        + "1-in-24 rarity gate deterministically exercises central owner terrain. No rarity bypass, "
                        + "feature copy or feature-ID production rule is installed.");
    }

    static synchronized void observeLoaded(ServerLevel level) {
        if (!enabled() || proofStarted || proofComplete) {
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
            throw new IllegalStateException("SF-IMP-0060 development volume admitted with unexpected footprint evidence: "
                    + "observed=" + admission.observedChunks() + ", required=" + admission.requiredChunks());
        }

        List<ProofChunk> proofChunks = loadedSurfaceChunks(level, volumeId);
        if (proofChunks.isEmpty()) {
            return;
        }
        if (proofChunks.stream().noneMatch(proofChunk -> proofChunk.chunk().getPos().equals(new ChunkPos(0, 0)))) {
            throw new IllegalStateException("SF-IMP-0060 deterministic rarity fixture lost its owner-rich origin chunk");
        }

        int minimumEnvelopeY = (int) Math.ceil(volume.bounds().minimumY());
        int maximumEnvelopeY = (int) Math.floor(volume.bounds().maximumY());
        List<BaseColumnSnapshot> baseColumnsBefore = captureBaseColumns(level, proofChunks);
        proofStarted = true;

        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException("SF-IMP-0060 proof resolved an unexpected volume: "
                        + candidateId.path());
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
                    + "one minecraft:amethyst_geode per owner-bearing proof chunk: chunks=" + proofChunks.size()
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
            throw new IllegalStateException("SF-IMP-0060 deterministic seed passed native rarity sampling but produced "
                    + "no persistent minecraft:amethyst_geode; inspect local terrain support rather than bypassing "
                    + "registered feature semantics");
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
                "SF-IMP-0060 LOCAL MODIFICATIONS PASS: volume=" + volumeId.path()
                        + ", admission={observedChunks=" + admission.observedChunks()
                        + ", requiredChunks=" + admission.requiredChunks()
                        + ", pendingCatchup=0}"
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
                        + "}, baseColumnsPreserved=true. Whole-volume admission completed before final-registry "
                        + "LOCAL_MODIFICATIONS consumed native placement randomness. Absolute HeightRangePlacement "
                        + "output was then mapped into the exact-volume frame and vertically unrelated BASE_WORLD proof "
                        + "columns remained unchanged.");
    }

    static SkyIslandWorldCatalog catalog() {
        var volumeId = new SkyIslandWorldVolumeId(
                ROOT_SEED,
                "sf-imp-0060-local-modifications",
                0,
                0,
                GEOMETRY_SEED);
        var volume = new SkyIslandWorldVolume(
                volumeId,
                new WorldBounds(-32.0, 47.0, 196.0, 268.0, -32.0, 47.0),
                compileTableland());
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(volume));
    }

    private static CompiledSkyIslandVolume compileTableland() {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                GEOMETRY_SEED,
                8.0,
                8.0,
                236.0,
                32.0,
                12.0,
                28.0,
                10.0,
                0.0,
                0.15,
                0.70,
                0.0,
                0.0,
                18.0);
        var provider = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.TABLELAND);
        return new EnrichedProviderMorphologySkyIslandVolumeRecipe().compile(
                descriptor,
                new ProviderMorphologyEnrichment(provider, 0.0, 0.0),
                SkyIslandMorphologyProviders.builtInRegistry());
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
