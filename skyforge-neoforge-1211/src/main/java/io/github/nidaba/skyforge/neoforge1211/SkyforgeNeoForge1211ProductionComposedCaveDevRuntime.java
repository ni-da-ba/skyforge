package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only SF-IMP-0068 proof of the production admitted-volume composed-cave stage.
 *
 * <p>This fixture never calls {@link SkyforgeComposedCaveRealizer} directly. It installs the normal
 * terrain, physical-admission, native-surface-population, and composed-cave production stages and
 * then observes their ledgers while {@link SkyforgePhysicalVolumeCatchupService} services only
 * independently loaded stable chunks.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionComposedCaveDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionComposedCave";

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionComposedCaveDevRuntime.class.getName());

    private static final SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture FIXTURE =
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.fixtureDefinition();

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentPopulationBinding;
    private static AutoCloseable persistentComposedBinding;
    private static SkyforgeComposedCaveStage.Snapshot initialSnapshot;
    private static int previousPending = Integer.MAX_VALUE;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionComposedCaveDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()
                || persistentTerrainBinding != null
                || persistentAdmissionBinding != null
                || persistentPopulationBinding != null
                || persistentComposedBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production composed-cave proof requires isolated production bindings");
        }

        SkyIslandWorldVolume volume = FIXTURE.volume();
        SkyIslandWorldVolumeId volumeId = volume.id();
        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0068 resolved unexpected volume " + candidateId.path());
            }
            return Biomes.TAIGA;
        };

        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(FIXTURE.catalog());

        Set<Long> plannedChunks = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId);
        persistentPopulationBinding = SkyforgeNativeSurfacePopulationStage.install(
                (chunkPos, minimumY, height) -> plannedChunks.contains(chunkPos.toLong())
                        ? List.of(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                                volumeId,
                                biomeResolver,
                                MAXIMUM_ATTACHMENT_DEPTH))
                        : List.of());
        persistentComposedBinding = SkyforgeComposedCaveStage.install(
                List.of(new SkyforgeComposedCavePlan(volume, FIXTURE.field())));

        initialSnapshot = SkyforgeComposedCaveStage.snapshot();
        if (initialSnapshot.totalObligations() != plannedChunks.size()
                || initialSnapshot.pendingObligations() != plannedChunks.size()
                || initialSnapshot.completedObligations() != 0
                || SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId).state()
                        != SkyforgePhysicalVolumeAdmissionState.PLANNED) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production stage did not start as a finite all-pending PLANNED ledger: "
                            + initialSnapshot);
        }
        previousPending = initialSnapshot.pendingObligations();

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0068 production composed-cave specimen enabled: islandKey="
                        + FIXTURE.islandKey()
                        + ", volume=" + volumeId.path()
                        + ", obligations=" + initialSnapshot.totalObligations()
                        + ". The acceptance runtime does not invoke composed realization directly; "
                        + "normal admitted-volume stable-chunk service owns execution.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete || initialSnapshot == null) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension().equals(Level.OVERWORLD)) {
                observe(level);
            }
        }
    }

    private static synchronized void observe(ServerLevel level) {
        if (proofComplete) {
            return;
        }

        SkyforgeComposedCaveStage.Snapshot stage = SkyforgeComposedCaveStage.snapshot();
        if (stage.pendingObligations() > previousPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production pending obligations increased: before="
                            + previousPending + ", after=" + stage.pendingObligations());
        }
        previousPending = stage.pendingObligations();

        SkyIslandWorldVolume volume = FIXTURE.volume();
        SkyIslandWorldVolumeId volumeId = volume.id();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()
                || stage.pendingObligations() != 0) {
            return;
        }
        if (stage.totalObligations() != admission.requiredChunks()
                || stage.completedObligations() != admission.requiredChunks()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production stage completed a different footprint than admission: stage="
                            + stage + ", admission=" + admission);
        }

        Set<Long> requiredChunks = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId);
        List<LevelChunk> chunks = loadedChunks(level, requiredChunks);
        if (chunks.isEmpty()) {
            return;
        }

        List<SkyforgeComposedCaveStage.Completion> completions = SkyforgeComposedCaveStage.completed();
        if (completions.size() != stage.totalObligations()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 completed evidence count differs from production ledger");
        }

        int resultChunks = 0;
        int emptyChunks = 0;
        int nativeChangedBlocks = 0;
        int nativeSuccessfulCalls = 0;
        int nativeRejectedWrites = 0;
        int nativeMappedOutsideTarget = 0;
        int authoredPositive = 0;
        int authoredBasePositive = 0;
        int authoredExposurePositive = 0;
        int authoredUnsafe = 0;
        int authoredChangedBlocks = 0;
        long nativeTransformDigest = FNV_OFFSET_BASIS;
        long nativeCarveDigest = FNV_OFFSET_BASIS;
        long authoredChangedDigest = FNV_OFFSET_BASIS;
        long authoredProvenanceDigest = FNV_OFFSET_BASIS;
        BlockPos mouth = null;
        io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide mouthSide = null;

        for (SkyforgeComposedCaveStage.Completion completion : completions) {
            if (!completion.volumeId().equals(volumeId)) {
                throw new IllegalStateException(
                        "SF-IMP-0068 production completion belongs to unexpected volume "
                                + completion.volumeId().path());
            }
            if (completion.result().isEmpty()) {
                emptyChunks++;
                continue;
            }
            resultChunks++;
            var result = completion.result().orElseThrow();
            var nativeResult = result.nativeResult();
            var authoredResult = result.authoredResult();
            nativeChangedBlocks = Math.addExact(nativeChangedBlocks, nativeResult.changedBlocks());
            nativeSuccessfulCalls = Math.addExact(nativeSuccessfulCalls, nativeResult.successfulCalls());
            nativeRejectedWrites = Math.addExact(nativeRejectedWrites, nativeResult.rejectedWrites());
            nativeMappedOutsideTarget =
                    Math.addExact(nativeMappedOutsideTarget, nativeResult.mappedOutsideTarget());
            authoredPositive = Math.addExact(authoredPositive, authoredResult.positiveSamples());
            authoredBasePositive = Math.addExact(authoredBasePositive, authoredResult.basePositiveSamples());
            authoredExposurePositive =
                    Math.addExact(authoredExposurePositive, authoredResult.exposurePositiveSamples());
            authoredUnsafe = Math.addExact(authoredUnsafe, authoredResult.unsafePositiveSamples());
            authoredChangedBlocks = Math.addExact(authoredChangedBlocks, authoredResult.changedBlocks());

            nativeTransformDigest = mix(nativeTransformDigest, completion.chunkPos().toLong());
            nativeTransformDigest = mix(nativeTransformDigest, nativeResult.transformDigest());
            nativeCarveDigest = mix(nativeCarveDigest, completion.chunkPos().toLong());
            nativeCarveDigest = mix(nativeCarveDigest, nativeResult.changedPositionDigest());
            authoredChangedDigest = mix(authoredChangedDigest, completion.chunkPos().toLong());
            authoredChangedDigest = mix(authoredChangedDigest, authoredResult.changedPositionDigest());
            authoredProvenanceDigest = mix(authoredProvenanceDigest, completion.chunkPos().toLong());
            authoredProvenanceDigest = mix(authoredProvenanceDigest, authoredResult.provenanceDigest());

            if (mouth == null && authoredResult.firstMouthPosition() != null) {
                mouth = authoredResult.firstMouthPosition();
                mouthSide = authoredResult.firstMouthSide();
            }
        }

        FinalEvidence finalEvidence = verifyFinalUnion(level, volume, chunks);
        if (resultChunks <= 0
                || nativeChangedBlocks <= 0
                || nativeSuccessfulCalls <= 0
                || nativeRejectedWrites != 0
                || nativeMappedOutsideTarget != 0
                || authoredPositive <= 0
                || authoredBasePositive <= 0
                || authoredExposurePositive <= 0
                || authoredUnsafe != 0
                || finalEvidence.authoredPositive() != authoredPositive
                || finalEvidence.finalAuthoredAir() != authoredPositive
                || finalEvidence.nativeOnlyAir() <= 0
                || finalEvidence.nativeOnlySample() == null
                || finalEvidence.baseCaveSample() == null
                || mouth == null
                || mouthSide == null) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production composed-cave evidence incomplete: resultChunks=" + resultChunks
                            + ", emptyChunks=" + emptyChunks
                            + ", nativeChanged=" + nativeChangedBlocks
                            + ", nativeSuccessful=" + nativeSuccessfulCalls
                            + ", rejected=" + nativeRejectedWrites
                            + ", mappedOutside=" + nativeMappedOutsideTarget
                            + ", authoredPositive=" + authoredPositive
                            + ", authoredUnsafe=" + authoredUnsafe
                            + ", final=" + finalEvidence
                            + ", mouth=" + mouth);
        }

        BlockPos outward = mouthSide
                        == io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide.UPPER_SURFACE
                ? mouth.above()
                : mouth.below();
        if (!level.getBlockState(mouth).isAir()
                || !level.getBlockState(outward).isAir()
                || !level.getBlockState(finalEvidence.baseCaveSample()).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 representative authored connection is not AIR in final state");
        }

        // Completed obligations are the idempotence authority. Re-service one already-loaded
        // completed chunk and require no result and no ledger change.
        SkyforgeComposedCaveStage.Snapshot beforeReplay = SkyforgeComposedCaveStage.snapshot();
        LevelChunk replayChunk = chunks.getFirst();
        List<SkyforgeComposedCaveStage.Completion> replay = SkyforgeComposedCaveStage.service(
                level,
                replayChunk,
                level.getChunkSource().getGenerator());
        SkyforgeComposedCaveStage.Snapshot afterReplay = SkyforgeComposedCaveStage.snapshot();
        if (!replay.isEmpty() || !beforeReplay.equals(afterReplay)) {
            throw new IllegalStateException(
                    "SF-IMP-0068 completed production obligation replayed: before="
                            + beforeReplay + ", after=" + afterReplay + ", replay=" + replay);
        }

        long composedDigest = FNV_OFFSET_BASIS;
        composedDigest = mix(composedDigest, nativeTransformDigest);
        composedDigest = mix(composedDigest, nativeCarveDigest);
        composedDigest = mix(composedDigest, authoredChangedDigest);
        composedDigest = mix(composedDigest, authoredProvenanceDigest);
        composedDigest = mix(composedDigest, finalEvidence.nativeOnlyAir());
        composedDigest = mix(composedDigest, finalEvidence.finalAuthoredAir());

        String nativeTransformDigestText = Long.toUnsignedString(nativeTransformDigest, 16);
        String nativeCarveDigestText = Long.toUnsignedString(nativeCarveDigest, 16);
        String authoredChangedDigestText = Long.toUnsignedString(authoredChangedDigest, 16);
        String authoredProvenanceDigestText = Long.toUnsignedString(authoredProvenanceDigest, 16);
        String composedDigestText = Long.toUnsignedString(composedDigest, 16);

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 PRODUCTION COMPOSED CAVE PASS: obligations=" + stage.totalObligations()
                        + ", resultChunks=" + resultChunks
                        + ", emptyChunks=" + emptyChunks
                        + ", nativeChangedBlocks=" + nativeChangedBlocks
                        + ", nativeOnlyAir=" + finalEvidence.nativeOnlyAir()
                        + ", authoredPositive=" + authoredPositive
                        + ", finalAuthoredAir=" + finalEvidence.finalAuthoredAir()
                        + ", noReplay=true, monotonicPending=true"
                        + ", composedDigest=" + composedDigestText + ".");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("islandKey", FIXTURE.islandKey()),
                        java.util.Map.entry("nativeBiome", "minecraft:taiga"),
                        java.util.Map.entry("initialTotal", initialSnapshot.totalObligations()),
                        java.util.Map.entry("initialPending", initialSnapshot.pendingObligations()),
                        java.util.Map.entry("initialCompleted", initialSnapshot.completedObligations()),
                        java.util.Map.entry("requiredChunks", admission.requiredChunks()),
                        java.util.Map.entry("finalPending", stage.pendingObligations()),
                        java.util.Map.entry("finalCompleted", stage.completedObligations()),
                        java.util.Map.entry("resultChunks", resultChunks),
                        java.util.Map.entry("emptyChunks", emptyChunks),
                        java.util.Map.entry("nativeChangedBlocks", nativeChangedBlocks),
                        java.util.Map.entry("nativeSuccessfulCalls", nativeSuccessfulCalls),
                        java.util.Map.entry("nativeOnlyAir", finalEvidence.nativeOnlyAir()),
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
                        java.util.Map.entry("finalAuthoredAir", finalEvidence.finalAuthoredAir()),
                        java.util.Map.entry("nativeOnlyPos", Long.toString(finalEvidence.nativeOnlySample().asLong())),
                        java.util.Map.entry("mouthPos", Long.toString(mouth.asLong())),
                        java.util.Map.entry("outwardPos", Long.toString(outward.asLong())),
                        java.util.Map.entry("baseCavePos", Long.toString(finalEvidence.baseCaveSample().asLong())),
                        java.util.Map.entry("composedDigest", composedDigestText),
                        java.util.Map.entry("admittedBeforeCompletion", true),
                        java.util.Map.entry("terrainCatchupEmptyBeforeCompletion", true),
                        java.util.Map.entry("monotonicPending", true),
                        java.util.Map.entry("noReplay", true),
                        java.util.Map.entry("productionStage", true)));
    }

    private static List<LevelChunk> loadedChunks(
            ServerLevel level,
            Set<Long> chunkKeys) {
        List<Long> ordered = new ArrayList<>(chunkKeys);
        ordered.sort(java.util.Comparator
                .comparingInt((Long key) -> ChunkPos.getX(key))
                .thenComparingInt(key -> ChunkPos.getZ(key)));
        List<LevelChunk> result = new ArrayList<>();
        for (long chunkKey : ordered) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey),
                    ChunkPos.getZ(chunkKey));
            if (chunk == null) {
                return List.of();
            }
            result.add(chunk);
        }
        return List.copyOf(result);
    }

    private static FinalEvidence verifyFinalUnion(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            List<LevelChunk> chunks) {
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                FIXTURE.field(),
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();

        int authoredPositive = 0;
        int finalAuthoredAir = 0;
        int nativeOnlyAir = 0;
        BlockPos nativeOnlySample = null;
        BlockPos baseCaveSample = null;
        Set<Long> visited = new HashSet<>();

        for (LevelChunk chunk : chunks) {
            int minimumY = Math.max(
                    chunk.getMinBuildHeight(),
                    (int) Math.floor(volume.bounds().minimumY()));
            int maximumY = Math.min(
                    chunk.getMaxBuildHeight() - 1,
                    (int) Math.ceil(volume.bounds().maximumY()));
            for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                    SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                            x - descriptor.centerX(),
                            z - descriptor.centerZ());
                    for (int y = minimumY; y <= maximumY; y++) {
                        BlockPos position = new BlockPos(x, y, z);
                        var sample = realized.sample(
                                new SkyIslandRealizedSubsurfacePosition(local, y));
                        if (sample.inside()) {
                            authoredPositive++;
                            if (level.getBlockState(position).isAir()) {
                                finalAuthoredAir++;
                            }
                            if (baseCaveSample == null
                                    && sample.sourceKind()
                                            == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE) {
                                baseCaveSample = position.immutable();
                            }
                            continue;
                        }

                        boolean ownerSolid = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                        volume.id(), x, y, z)
                                .orElseThrow(() -> new IllegalStateException(
                                        "SF-IMP-0068 terrain binding disappeared during final verification"));
                        if (ownerSolid
                                && level.getBlockState(position).isAir()
                                && visited.add(position.asLong())) {
                            nativeOnlyAir++;
                            if (nativeOnlySample == null) {
                                nativeOnlySample = position.immutable();
                            }
                        }
                    }
                }
            }
        }
        return new FinalEvidence(
                authoredPositive,
                finalAuthoredAir,
                nativeOnlyAir,
                nativeOnlySample,
                baseCaveSample);
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }

    private record FinalEvidence(
            int authoredPositive,
            int finalAuthoredAir,
            int nativeOnlyAir,
            BlockPos nativeOnlySample,
            BlockPos baseCaveSample) {}
}
