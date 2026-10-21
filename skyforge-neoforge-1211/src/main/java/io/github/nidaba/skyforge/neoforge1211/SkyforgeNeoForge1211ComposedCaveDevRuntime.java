package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only SF-IMP-0067 proof that registry-native cave AIR and AUTH-0030 authored cave AIR
 * compose additively under an explicit native-first/authored-last ordering contract.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ComposedCaveDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.composedCave";

    private static final int INTERIOR_MARGIN = 8;
    private static final int BASE_CONTROL_Y = -60;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ComposedCaveDevRuntime.class.getName());

    private static final SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture FIXTURE =
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.fixtureDefinition();

    private static AutoCloseable persistentTerrainBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ComposedCaveDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 composed cave proof requires an isolated terrain binding");
        }
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0067 composed-cave specimen enabled: AUTH-0030 islandKey="
                        + FIXTURE.islandKey()
                        + ", morphology=" + FIXTURE.descriptor().morphologyFamily()
                        + ", proofChunks=" + FIXTURE.proofChunks().size()
                        + ". Registry-native taiga AIR carvers run first in deterministic proof chunks; "
                        + "AUTH-0030 realization runs second and must remain a required subset of final AIR.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                observeLoaded(level);
            }
        }
    }

    private static synchronized void observeLoaded(ServerLevel level) {
        if (proofComplete) {
            return;
        }
        List<LevelChunk> chunks = loadedProofChunks(level);
        if (chunks.isEmpty()) {
            return;
        }
        prove(level, chunks);
    }

    private static void prove(
            ServerLevel level,
            List<LevelChunk> chunks) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            throw new IllegalStateException(
                    "SF-IMP-0067 requires the active Minecraft noise generator");
        }

        SkyIslandWorldVolume volume = FIXTURE.volume();
        SkyIslandExteriorConnectedCaveVolumeField field = FIXTURE.field();
        List<StableControl> baseControls = captureBaseControls(level, chunks);

        var resolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volume.id())) {
                throw new IllegalArgumentException(
                        "SF-IMP-0067 resolved unexpected volume " + candidateId.path());
            }
            return Biomes.TAIGA;
        };

        Set<Long> composedChunks = new HashSet<>();
        int composedAttempts = 0;
        int nativeChangedBlocks = 0;
        int nativeSuccessfulCalls = 0;
        int nativeOnlyAir = 0;
        int nativeAuthoredAirOverlap = 0;
        int nativeRejectedWrites = 0;
        int nativeMappedOutsideTarget = 0;
        long nativeTransformDigest = FNV_OFFSET_BASIS;
        long nativeCarveDigest = FNV_OFFSET_BASIS;
        BlockPos nativeOnlySample = null;
        BlockPos nativeOverlapSample = null;
        LevelChunk selectedNativeChunk = null;

        int authoredPositive = 0;
        int authoredBasePositive = 0;
        int authoredExposurePositive = 0;
        int authoredUnsafe = 0;
        int authoredChangedBlocks = 0;
        int authoredMouthCells = 0;
        long authoredChangedDigest = FNV_OFFSET_BASIS;
        long authoredProvenanceDigest = FNV_OFFSET_BASIS;
        BlockPos firstMouth = null;
        SkyIslandCaveExposureSide firstMouthSide = null;

        // Search the finite authored proof footprint deterministically for a chunk where unchanged
        // final-registry native carvers contribute AIR outside AUTH-0030. Every attempted chunk is
        // still completed with the authored pass, so the final state always respects precedence.
        for (LevelChunk chunk : chunks) {
            OwnerSpan span = widestOwnerSpan(volume, chunk);
            if (span == null
                    || span.maximumY() - span.minimumY() <= INTERIOR_MARGIN * 2) {
                continue;
            }
            List<Long> nativeOnlyCandidates =
                    captureInitialOwnerOutsideAuthored(level, volume, field, chunk);
            composedAttempts++;

            var result = SkyforgeComposedCaveRealizer.realize(
                    level,
                    noiseGenerator,
                    resolver,
                    volume,
                    field,
                    chunk,
                    new BlockPos(
                            span.x(),
                            (span.minimumY() + span.maximumY()) / 2,
                            span.z()),
                    span.minimumY() + INTERIOR_MARGIN,
                    span.maximumY() - INTERIOR_MARGIN);
            composedChunks.add(chunk.getPos().toLong());

            var nativeResult = result.nativeResult();
            var authoredResult = result.authoredResult();
            nativeChangedBlocks = Math.addExact(nativeChangedBlocks, nativeResult.changedBlocks());
            nativeSuccessfulCalls = Math.addExact(nativeSuccessfulCalls, nativeResult.successfulCalls());
            nativeRejectedWrites = Math.addExact(nativeRejectedWrites, nativeResult.rejectedWrites());
            nativeMappedOutsideTarget =
                    Math.addExact(nativeMappedOutsideTarget, nativeResult.mappedOutsideTarget());
            nativeTransformDigest = mix(nativeTransformDigest, chunk.getPos().toLong());
            nativeTransformDigest = mix(nativeTransformDigest, nativeResult.transformDigest());
            nativeCarveDigest = mix(nativeCarveDigest, chunk.getPos().toLong());
            nativeCarveDigest = mix(nativeCarveDigest, nativeResult.changedPositionDigest());

            int candidateNativeOnly = 0;
            for (long packed : nativeOnlyCandidates) {
                BlockPos position = BlockPos.of(packed);
                if (level.getBlockState(position).isAir()) {
                    candidateNativeOnly++;
                    if (nativeOnlySample == null) {
                        nativeOnlySample = position.immutable();
                    }
                }
            }
            nativeOnlyAir = Math.addExact(nativeOnlyAir, candidateNativeOnly);

            int overlap = Math.max(
                    0,
                    authoredResult.ownerAuthorizedSamples() - authoredResult.changedBlocks());
            nativeAuthoredAirOverlap = Math.addExact(nativeAuthoredAirOverlap, overlap);
            if (overlap > 0 && nativeOverlapSample == null) {
                nativeOverlapSample = findAuthoredAirAlreadyPresent(
                        level, volume, field, chunk);
            }

            authoredPositive = Math.addExact(
                    authoredPositive, authoredResult.positiveSamples());
            authoredBasePositive = Math.addExact(
                    authoredBasePositive, authoredResult.basePositiveSamples());
            authoredExposurePositive = Math.addExact(
                    authoredExposurePositive, authoredResult.exposurePositiveSamples());
            authoredUnsafe = Math.addExact(
                    authoredUnsafe, authoredResult.unsafePositiveSamples());
            authoredChangedBlocks = Math.addExact(
                    authoredChangedBlocks, authoredResult.changedBlocks());
            authoredMouthCells = Math.addExact(
                    authoredMouthCells, authoredResult.mouthCells());
            authoredChangedDigest = mix(authoredChangedDigest, chunk.getPos().toLong());
            authoredChangedDigest = mix(
                    authoredChangedDigest, authoredResult.changedPositionDigest());
            authoredProvenanceDigest = mix(authoredProvenanceDigest, chunk.getPos().toLong());
            authoredProvenanceDigest = mix(
                    authoredProvenanceDigest, authoredResult.provenanceDigest());
            if (firstMouth == null && authoredResult.firstMouthPosition() != null) {
                firstMouth = authoredResult.firstMouthPosition();
                firstMouthSide = authoredResult.firstMouthSide();
            }

            if (candidateNativeOnly > 0 && nativeResult.changedBlocks() > 0) {
                selectedNativeChunk = chunk;
                break;
            }
        }

        if (selectedNativeChunk == null || nativeOnlyAir <= 0 || nativeOnlySample == null) {
            throw new IllegalStateException(
                    "SF-IMP-0067 found no deterministic native-only AIR contribution inside the "
                            + "AUTH-0030 proof footprint: attempts=" + composedAttempts
                            + ", nativeChanged=" + nativeChangedBlocks
                            + ", nativeOnlyAir=" + nativeOnlyAir);
        }

        // Complete AUTH-0030 over the rest of the finite proof footprint without additional native
        // carving. This makes the acceptance topology directly comparable to SF-IMP-0066.
        for (LevelChunk chunk : chunks) {
            if (composedChunks.contains(chunk.getPos().toLong())) {
                continue;
            }
            var authoredResult =
                    SkyforgeExteriorConnectedCaveRealizer.realize(level, volume, field, chunk);
            if (!authoredResult.accepted()) {
                throw new IllegalStateException(
                        "SF-IMP-0067 authored completion rejected chunk " + chunk.getPos());
            }
            authoredPositive = Math.addExact(
                    authoredPositive, authoredResult.positiveSamples());
            authoredBasePositive = Math.addExact(
                    authoredBasePositive, authoredResult.basePositiveSamples());
            authoredExposurePositive = Math.addExact(
                    authoredExposurePositive, authoredResult.exposurePositiveSamples());
            authoredUnsafe = Math.addExact(
                    authoredUnsafe, authoredResult.unsafePositiveSamples());
            authoredChangedBlocks = Math.addExact(
                    authoredChangedBlocks, authoredResult.changedBlocks());
            authoredMouthCells = Math.addExact(
                    authoredMouthCells, authoredResult.mouthCells());
            authoredChangedDigest = mix(authoredChangedDigest, chunk.getPos().toLong());
            authoredChangedDigest = mix(
                    authoredChangedDigest, authoredResult.changedPositionDigest());
            authoredProvenanceDigest = mix(authoredProvenanceDigest, chunk.getPos().toLong());
            authoredProvenanceDigest = mix(
                    authoredProvenanceDigest, authoredResult.provenanceDigest());
            if (firstMouth == null && authoredResult.firstMouthPosition() != null) {
                firstMouth = authoredResult.firstMouthPosition();
                firstMouthSide = authoredResult.firstMouthSide();
            }
        }

        FinalAuthoredEvidence finalAuthored =
                verifyAuthoredMinimum(level, volume, field, chunks);
        if (finalAuthored.positiveSamples() != authoredPositive
                || finalAuthored.finalAirSamples() != authoredPositive
                || finalAuthored.basePositiveSamples() != authoredBasePositive
                || finalAuthored.exposurePositiveSamples() != authoredExposurePositive
                || authoredUnsafe != 0
                || authoredMouthCells <= 0
                || firstMouth == null
                || firstMouthSide == null) {
            throw new IllegalStateException(
                    "SF-IMP-0067 final authored minimum is incomplete: aggregatePositive="
                            + authoredPositive + ", verifiedPositive=" + finalAuthored.positiveSamples()
                            + ", finalAir=" + finalAuthored.finalAirSamples()
                            + ", unsafe=" + authoredUnsafe
                            + ", mouthCells=" + authoredMouthCells);
        }

        BlockPos outward = firstMouthSide == SkyIslandCaveExposureSide.UPPER_SURFACE
                ? firstMouth.above()
                : firstMouth.below();
        if (!level.getBlockState(firstMouth).isAir()
                || !level.getBlockState(outward).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 authored mouth lost exterior adjacency after composed carving");
        }
        Connectivity connectivity = verifyConnectivity(level, firstMouth, chunks);
        if (!connectivity.reachedBaseCave() || connectivity.basePosition() == null) {
            throw new IllegalStateException(
                    "SF-IMP-0067 final authored mouth component does not reach BASE_CAVE provenance");
        }

        requireBaseControlsPreserved(level, baseControls);
        if (!level.getBlockState(nativeOnlySample).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0067 native-only AIR did not survive authored-last pass at "
                            + nativeOnlySample);
        }

        long composedDigest = FNV_OFFSET_BASIS;
        composedDigest = mix(composedDigest, nativeTransformDigest);
        composedDigest = mix(composedDigest, nativeCarveDigest);
        composedDigest = mix(composedDigest, authoredChangedDigest);
        composedDigest = mix(composedDigest, authoredProvenanceDigest);
        composedDigest = mix(composedDigest, nativeOnlyAir);
        composedDigest = mix(composedDigest, authoredPositive);

        proofComplete = true;
        String nativeTransformDigestText = Long.toUnsignedString(nativeTransformDigest, 16);
        String nativeCarveDigestText = Long.toUnsignedString(nativeCarveDigest, 16);
        String authoredChangedDigestText = Long.toUnsignedString(authoredChangedDigest, 16);
        String authoredProvenanceDigestText = Long.toUnsignedString(authoredProvenanceDigest, 16);
        String composedDigestText = Long.toUnsignedString(composedDigest, 16);

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0067 COMPOSED CAVE PASS: islandKey=" + FIXTURE.islandKey()
                        + ", nativeBiome=minecraft:taiga"
                        + ", composedAttempts=" + composedAttempts
                        + ", selectedNativeChunk=" + selectedNativeChunk.getPos()
                        + ", nativeChangedBlocks=" + nativeChangedBlocks
                        + ", nativeSuccessfulCalls=" + nativeSuccessfulCalls
                        + ", nativeOnlyAir=" + nativeOnlyAir
                        + ", nativeAuthoredAirOverlap=" + nativeAuthoredAirOverlap
                        + ", nativeRejectedWrites=" + nativeRejectedWrites
                        + ", nativeMappedOutsideTarget=" + nativeMappedOutsideTarget
                        + ", nativeTransformDigest=" + nativeTransformDigestText
                        + ", nativeCarveDigest=" + nativeCarveDigestText
                        + ", authoredPositive=" + authoredPositive
                        + ", authoredBasePositive=" + authoredBasePositive
                        + ", authoredExposurePositive=" + authoredExposurePositive
                        + ", authoredChangedBlocks=" + authoredChangedBlocks
                        + ", authoredChangedDigest=" + authoredChangedDigestText
                        + ", authoredProvenanceDigest=" + authoredProvenanceDigestText
                        + ", finalAuthoredAir=" + finalAuthored.finalAirSamples()
                        + ", mouth=" + firstMouth
                        + ", outward=" + outward
                        + ", base=" + connectivity.basePosition()
                        + ", nativeOnlySample=" + nativeOnlySample
                        + ", composedDigest=" + composedDigestText
                        + ", baseWorldPreserved=true"
                        + ", finalUnion=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("islandKey", FIXTURE.islandKey()),
                        java.util.Map.entry("nativeBiome", "minecraft:taiga"),
                        java.util.Map.entry("composedAttempts", composedAttempts),
                        java.util.Map.entry("selectedNativeChunk", selectedNativeChunk.getPos().toLong()),
                        java.util.Map.entry("nativeChangedBlocks", nativeChangedBlocks),
                        java.util.Map.entry("nativeSuccessfulCalls", nativeSuccessfulCalls),
                        java.util.Map.entry("nativeOnlyAir", nativeOnlyAir),
                        java.util.Map.entry("nativeAuthoredAirOverlap", nativeAuthoredAirOverlap),
                        java.util.Map.entry("nativeRejectedWrites", nativeRejectedWrites),
                        java.util.Map.entry("nativeMappedOutsideTarget", nativeMappedOutsideTarget),
                        java.util.Map.entry("nativeTransformDigest", nativeTransformDigestText),
                        java.util.Map.entry("nativeCarveDigest", nativeCarveDigestText),
                        java.util.Map.entry("authoredPositive", authoredPositive),
                        java.util.Map.entry("authoredBasePositive", authoredBasePositive),
                        java.util.Map.entry("authoredExposurePositive", authoredExposurePositive),
                        java.util.Map.entry("authoredUnsafe", authoredUnsafe),
                        java.util.Map.entry("authoredChangedBlocks", authoredChangedBlocks),
                        java.util.Map.entry("authoredChangedDigest", authoredChangedDigestText),
                        java.util.Map.entry("authoredProvenanceDigest", authoredProvenanceDigestText),
                        java.util.Map.entry("finalAuthoredAir", finalAuthored.finalAirSamples()),
                        java.util.Map.entry("mouthPos", Long.toString(firstMouth.asLong())),
                        java.util.Map.entry("outwardPos", Long.toString(outward.asLong())),
                        java.util.Map.entry("baseCavePos", Long.toString(connectivity.basePosition().asLong())),
                        java.util.Map.entry("nativeOnlyPos", Long.toString(nativeOnlySample.asLong())),
                        java.util.Map.entry(
                                "nativeOverlapPos",
                                nativeOverlapSample == null ? "" : Long.toString(nativeOverlapSample.asLong())),
                        java.util.Map.entry("composedDigest", composedDigestText),
                        java.util.Map.entry("baseWorldPreserved", true),
                        java.util.Map.entry("finalUnion", true)));
    }

    private static List<LevelChunk> loadedProofChunks(ServerLevel level) {
        List<LevelChunk> result = new ArrayList<>();
        for (long packed : FIXTURE.proofChunks()) {
            var pos = new net.minecraft.world.level.ChunkPos(packed);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                return List.of();
            }
            result.add(chunk);
        }
        return List.copyOf(result);
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
                    if (ownerSolid(volume, x, y, z)) {
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

    private static List<Long> captureInitialOwnerOutsideAuthored(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        List<Long> result = new ArrayList<>();
        var realized = realized(volume, field);
        var descriptor = volume.compiledVolume().descriptor();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    if (!ownerSolid(volume, x, y, z)) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    if (level.getBlockState(position).isAir()) {
                        continue;
                    }
                    var sample = realized.sample(new SkyIslandRealizedSubsurfacePosition(local, y));
                    if (!sample.inside()) {
                        result.add(position.asLong());
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos findAuthoredAirAlreadyPresent(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        var realized = realized(volume, field);
        var descriptor = volume.compiledVolume().descriptor();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                var local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    BlockPos position = new BlockPos(x, y, z);
                    var sample = realized.sample(new SkyIslandRealizedSubsurfacePosition(local, y));
                    if (sample.inside() && level.getBlockState(position).isAir()) {
                        return position.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static FinalAuthoredEvidence verifyAuthoredMinimum(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            List<LevelChunk> chunks) {
        var realized = realized(volume, field);
        var descriptor = volume.compiledVolume().descriptor();
        int positive = 0;
        int base = 0;
        int exposure = 0;
        int finalAir = 0;

        for (LevelChunk chunk : chunks) {
            int minimumY = Math.max(
                    chunk.getMinBuildHeight(),
                    (int) Math.ceil(volume.bounds().minimumY()));
            int maximumY = Math.min(
                    chunk.getMaxBuildHeight() - 1,
                    (int) Math.floor(volume.bounds().maximumY()));
            for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                    var local = new SkyIslandLocalPosition(
                            x - descriptor.centerX(),
                            z - descriptor.centerZ());
                    for (int y = minimumY; y <= maximumY; y++) {
                        var sample = realized.sample(
                                new SkyIslandRealizedSubsurfacePosition(local, y));
                        if (!sample.inside()) {
                            continue;
                        }
                        positive++;
                        if (sample.sourceKind()
                                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE) {
                            base++;
                        } else if (sample.sourceKind()
                                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION) {
                            exposure++;
                        }
                        if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                            finalAir++;
                        }
                    }
                }
            }
        }
        return new FinalAuthoredEvidence(positive, base, exposure, finalAir);
    }

    private static Connectivity verifyConnectivity(
            ServerLevel level,
            BlockPos mouth,
            List<LevelChunk> chunks) {
        Set<Long> allowedChunks = new HashSet<>();
        for (LevelChunk chunk : chunks) {
            allowedChunks.add(chunk.getPos().toLong());
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(mouth.immutable());

        var realized = realized(FIXTURE.volume(), FIXTURE.field());
        var descriptor = FIXTURE.volume().compiledVolume().descriptor();
        BlockPos basePosition = null;

        while (!queue.isEmpty()) {
            BlockPos position = queue.removeFirst();
            if (!visited.add(position.asLong())) {
                continue;
            }
            long chunkKey = new net.minecraft.world.level.ChunkPos(position).toLong();
            if (!allowedChunks.contains(chunkKey) || !level.getBlockState(position).isAir()) {
                continue;
            }
            var local = new SkyIslandLocalPosition(
                    position.getX() - descriptor.centerX(),
                    position.getZ() - descriptor.centerZ());
            var sample = realized.sample(
                    new SkyIslandRealizedSubsurfacePosition(local, position.getY()));
            if (!sample.inside()) {
                continue;
            }
            if (sample.sourceKind()
                    == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE) {
                basePosition = position.immutable();
                break;
            }
            queue.add(position.above());
            queue.add(position.below());
            queue.add(position.north());
            queue.add(position.south());
            queue.add(position.east());
            queue.add(position.west());
        }
        return new Connectivity(basePosition != null, visited.size(), basePosition);
    }

    private static SkyIslandRealizedExteriorConnectedCaveVolumeField realized(
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field) {
        return new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
    }

    private static boolean ownerSolid(
            SkyIslandWorldVolume volume,
            int x,
            int y,
            int z) {
        return SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(volume.id(), x, y, z)
                .orElseThrow(() -> new IllegalStateException(
                        "SF-IMP-0067 terrain binding disappeared"));
    }

    private static List<StableControl> captureBaseControls(
            ServerLevel level,
            List<LevelChunk> chunks) {
        List<StableControl> result = new ArrayList<>();
        for (LevelChunk chunk : chunks) {
            BlockPos position = new BlockPos(
                    chunk.getPos().getMiddleBlockX(),
                    Math.max(level.getMinBuildHeight() + 4, BASE_CONTROL_Y),
                    chunk.getPos().getMiddleBlockZ());
            result.add(new StableControl(position.immutable(), level.getBlockState(position)));
        }
        return List.copyOf(result);
    }

    private static void requireBaseControlsPreserved(
            ServerLevel level,
            List<StableControl> controls) {
        for (StableControl control : controls) {
            BlockState actual = level.getBlockState(control.position());
            if (!actual.equals(control.state())) {
                throw new IllegalStateException(
                        "SF-IMP-0067 mutated vertically unrelated BASE_WORLD at "
                                + control.position() + ": before=" + control.state()
                                + ", after=" + actual);
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

    private record OwnerSpan(int x, int z, int minimumY, int maximumY) {}

    private record StableControl(BlockPos position, BlockState state) {}

    private record FinalAuthoredEvidence(
            int positiveSamples,
            int basePositiveSamples,
            int exposurePositiveSamples,
            int finalAirSamples) {}

    private record Connectivity(
            boolean reachedBaseCave,
            int visited,
            BlockPos basePosition) {}
}
