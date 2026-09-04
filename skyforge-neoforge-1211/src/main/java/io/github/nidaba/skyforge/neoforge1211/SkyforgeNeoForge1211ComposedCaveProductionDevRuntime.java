package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeSample;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * Development-only SF-IMP-0068 proof of the production composed-cave lifecycle stage.
 *
 * <p>Unlike SF-IMP-0067 this runtime never invokes SkyforgeComposedCaveRealizer directly. It
 * installs production-facing plans, drives ordinary physical admission, and observes the catch-up
 * service drain those obligations.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ComposedCaveProductionDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.composedCaveProduction";

    private static final int MAXIMUM_ATTACHMENT_DEPTH = 16;
    private static final int IDEMPOTENCE_TICKS = 5;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ComposedCaveProductionDevRuntime.class.getName());

    private static final SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.Fixture FIXTURE =
            SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.fixtureDefinition();

    private static AutoCloseable terrainBinding;
    private static AutoCloseable admissionBinding;
    private static AutoCloseable surfacePopulationBinding;
    private static AutoCloseable composedCaveBinding;

    private static boolean admissionSeeded;
    private static int initialPending;
    private static int previousPending = Integer.MAX_VALUE;
    private static int zeroPendingTicks;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ComposedCaveProductionDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || terrainBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCavePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production proof requires isolated runtime bindings");
        }

        terrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        admissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(FIXTURE.catalog());

        SkyforgeExactVolumeBiomeResolver biomeResolver =
                (volumeId, x, y, z) -> Biomes.TAIGA;
        surfacePopulationBinding = SkyforgeNativeSurfacePopulationStage.install(
                (chunkPos, minimumY, height) -> chunkIntersectsVolume(
                                chunkPos,
                                FIXTURE.volume())
                        ? List.of(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                                FIXTURE.volume().id(),
                                biomeResolver,
                                MAXIMUM_ATTACHMENT_DEPTH))
                        : List.of());

        composedCaveBinding = SkyforgeComposedCavePopulationStage.install(
                List.of(new SkyforgeComposedCavePopulationStage.Plan(
                        FIXTURE.volume(),
                        FIXTURE.field())));

        initialPending = SkyforgeComposedCavePopulationStage.pendingCount();
        previousPending = initialPending;
        if (initialPending <= 0) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production plan created no finite chunk obligations");
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0068 production composed-cave proof enabled: islandKey="
                        + FIXTURE.islandKey()
                        + ", initialPending=" + initialPending
                        + ", AUTH-0030 side=" + FIXTURE.connection().side()
                        + ". No development composed-cave realizer property is enabled; "
                        + "the proof will proceed only through physical admission and the production stage.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD)) {
                continue;
            }
            if (!admissionSeeded) {
                seedAdmissionWhenLoaded(level);
            } else {
                observeProgress(level);
            }
        }
    }

    private static synchronized void seedAdmissionWhenLoaded(ServerLevel level) {
        if (admissionSeeded || proofComplete) {
            return;
        }

        List<Long> keys = new ArrayList<>(
                SkyforgeComposedCavePopulationStage.pendingChunkKeys(FIXTURE.volume().id()));
        keys.sort(Comparator.naturalOrder());
        if (keys.size() != initialPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production pending set changed before admission");
        }

        List<LevelChunk> chunks = new ArrayList<>(keys.size());
        for (long key : keys) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                return;
            }
            chunks.add(chunk);
        }

        if (!SkyforgeComposedCavePopulationStage.eligibleChunkKeys().isEmpty()
                || SkyforgeComposedCavePopulationStage.completedCount() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0068 cave production became eligible before physical admission");
        }

        // Reproduce ordinary generation-time realization over already-loaded disposable chunks.
        // Every chunk before the terminal observation remains deferred and must not trigger caves.
        for (int index = 0; index < chunks.size() - 1; index++) {
            SkyforgeNeoForge1211SurfaceStage.realize(chunks.get(index));
            if (!SkyforgeComposedCavePopulationStage.eligibleChunkKeys().isEmpty()
                    || SkyforgeComposedCavePopulationStage.completedCount() != 0) {
                throw new IllegalStateException(
                        "SF-IMP-0068 cave production escaped before terminal admission");
            }
        }

        LevelChunk terminalChunk = chunks.getLast();
        SkyforgeNeoForge1211SurfaceStage.realize(terminalChunk);
        if (!SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(FIXTURE.volume().id())) {
            throw new IllegalStateException(
                    "SF-IMP-0068 fixture did not transition to ADMITTED after complete survey");
        }

        // The terminal chunk was realized immediately rather than through deferred catch-up.
        // Service its normal native surface-population identity before the cave stage can run.
        SkyforgeNativeSurfacePopulationStage.populateDeferred(
                level,
                terminalChunk,
                level.getChunkSource().getGenerator());

        if (SkyforgeComposedCavePopulationStage.completedCount() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production cave stage completed inside admission seeding");
        }

        admissionSeeded = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 PRODUCTION ADMISSION PASS: initialPending=" + initialPending
                        + ", terminalChunk=" + terminalChunk.getPos()
                        + ", caveCompletedBeforeAdmission=0"
                        + ", deferredTerrainChunks="
                        + SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(
                                FIXTURE.volume().id()).size()
                        + ".");
    }

    private static synchronized void observeProgress(ServerLevel level) {
        int pending = SkyforgeComposedCavePopulationStage.pendingCount();
        int completed = SkyforgeComposedCavePopulationStage.completedCount();
        if (pending > previousPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production cave pending ledger increased: previous="
                            + previousPending + ", current=" + pending);
        }
        if (pending + completed != initialPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production cave ledger lost an obligation: initial="
                            + initialPending + ", pending=" + pending + ", completed=" + completed);
        }
        previousPending = pending;

        if (pending != 0) {
            zeroPendingTicks = 0;
            return;
        }

        zeroPendingTicks++;
        if (zeroPendingTicks < IDEMPOTENCE_TICKS) {
            return;
        }

        // A completed production ledger exposes no further eligible work and therefore cannot replay
        // after repeated server ticks.
        if (!SkyforgeComposedCavePopulationStage.eligibleChunkKeys().isEmpty()
                || SkyforgeComposedCavePopulationStage.completedCount() != initialPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 completed production ledger replayed or became eligible again");
        }

        FinalEvidence evidence = verifyFinalUnion(level);
        if (evidence.authoredPositive() != 89068
                || evidence.authoredFinalAir() != 89068
                || evidence.nativeOnlyAir() <= 0
                || evidence.nativeOnlySample() == null) {
            throw new IllegalStateException(
                    "SF-IMP-0068 final production union evidence incomplete: " + evidence);
        }

        BlockPos mouth = new BlockPos(-14, 174, -3);
        BlockPos outward = mouth.below();
        BlockPos base = new BlockPos(-14, 185, -3);
        if (!level.getBlockState(mouth).isAir()
                || !level.getBlockState(outward).isAir()
                || !level.getBlockState(base).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production lifecycle lost accepted AUTH-0030 mouth connectivity");
        }

        int nativeChanged = 0;
        int nativeSuccessful = 0;
        int authoredChanged = 0;
        int authoredUnsafe = 0;
        int emptyObligations = 0;
        for (var result : SkyforgeComposedCavePopulationStage.completedResults(FIXTURE.volume().id())) {
            nativeChanged = Math.addExact(nativeChanged, result.nativeChangedBlocks());
            nativeSuccessful = Math.addExact(nativeSuccessful, result.nativeSuccessfulCalls());
            authoredChanged = Math.addExact(authoredChanged, result.authoredChangedBlocks());
            authoredUnsafe = Math.addExact(authoredUnsafe, result.authoredUnsafeSamples());
            if (result.empty()) {
                emptyObligations++;
            }
        }
        if (nativeChanged <= 0 || nativeSuccessful <= 0 || authoredUnsafe != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0068 production service results lack composed cave activity");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 PRODUCTION COMPOSED CAVE PASS: initialPending=" + initialPending
                        + ", completed=" + SkyforgeComposedCavePopulationStage.completedCount()
                        + ", pending=0"
                        + ", idempotenceTicks=" + zeroPendingTicks
                        + ", nativeChangedBlocks=" + nativeChanged
                        + ", nativeSuccessfulCalls=" + nativeSuccessful
                        + ", nativeOnlyAir=" + evidence.nativeOnlyAir()
                        + ", authoredPositive=" + evidence.authoredPositive()
                        + ", authoredFinalAir=" + evidence.authoredFinalAir()
                        + ", authoredChangedBlocks=" + authoredChanged
                        + ", authoredUnsafe=0"
                        + ", emptyObligations=" + emptyObligations
                        + ", nativeOnlySample=" + evidence.nativeOnlySample()
                        + ", mouth=" + mouth
                        + ", outward=" + outward
                        + ", base=" + base
                        + ", noReplay=true, noForcedChunks=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("islandKey", FIXTURE.islandKey()),
                        java.util.Map.entry("initialPending", initialPending),
                        java.util.Map.entry(
                                "completed",
                                SkyforgeComposedCavePopulationStage.completedCount()),
                        java.util.Map.entry("pending", 0),
                        java.util.Map.entry("idempotenceTicks", zeroPendingTicks),
                        java.util.Map.entry("nativeChangedBlocks", nativeChanged),
                        java.util.Map.entry("nativeSuccessfulCalls", nativeSuccessful),
                        java.util.Map.entry("nativeOnlyAir", evidence.nativeOnlyAir()),
                        java.util.Map.entry("authoredPositive", evidence.authoredPositive()),
                        java.util.Map.entry("authoredFinalAir", evidence.authoredFinalAir()),
                        java.util.Map.entry("authoredChangedBlocks", authoredChanged),
                        java.util.Map.entry("authoredUnsafe", authoredUnsafe),
                        java.util.Map.entry("emptyObligations", emptyObligations),
                        java.util.Map.entry(
                                "nativeOnlyPos",
                                Long.toString(evidence.nativeOnlySample().asLong())),
                        java.util.Map.entry("mouthPos", Long.toString(mouth.asLong())),
                        java.util.Map.entry("outwardPos", Long.toString(outward.asLong())),
                        java.util.Map.entry("baseCavePos", Long.toString(base.asLong())),
                        java.util.Map.entry("mouthState", level.getBlockState(mouth).toString()),
                        java.util.Map.entry("outwardState", level.getBlockState(outward).toString()),
                        java.util.Map.entry("baseCaveState", level.getBlockState(base).toString()),
                        java.util.Map.entry("noReplay", true),
                        java.util.Map.entry("noForcedChunks", true)));
    }

    private static FinalEvidence verifyFinalUnion(ServerLevel level) {
        SkyIslandWorldVolume volume = FIXTURE.volume();
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                FIXTURE.field(),
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();

        int authoredPositive = 0;
        int authoredFinalAir = 0;
        int nativeOnlyAir = 0;
        BlockPos nativeOnlySample = null;

        for (long packed : FIXTURE.proofChunks()) {
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                throw new IllegalStateException(
                        "SF-IMP-0068 proof chunk unloaded before final verification: " + chunkPos);
            }
            int minimumY = Math.max(
                    chunk.getMinBuildHeight(),
                    (int) Math.ceil(volume.bounds().minimumY()));
            int maximumY = Math.min(
                    chunk.getMaxBuildHeight() - 1,
                    (int) Math.floor(volume.bounds().maximumY()));
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    var local = new SkyIslandLocalPosition(
                            x - descriptor.centerX(),
                            z - descriptor.centerZ());
                    for (int y = minimumY; y <= maximumY; y++) {
                        if (!SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                        volume.id(), x, y, z)
                                .orElseThrow()) {
                            continue;
                        }
                        BlockPos position = new BlockPos(x, y, z);
                        SkyIslandExteriorConnectedCaveVolumeSample sample = realized.sample(
                                new SkyIslandRealizedSubsurfacePosition(local, y));
                        if (sample.inside()) {
                            authoredPositive++;
                            if (level.getBlockState(position).isAir()) {
                                authoredFinalAir++;
                            }
                        } else if (level.getBlockState(position).isAir()) {
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
                authoredFinalAir,
                nativeOnlyAir,
                nativeOnlySample);
    }

    private static boolean chunkIntersectsVolume(
            ChunkPos chunkPos,
            SkyIslandWorldVolume volume) {
        return chunkPos.getMaxBlockX() >= volume.bounds().minimumX()
                && chunkPos.getMinBlockX() <= volume.bounds().maximumX()
                && chunkPos.getMaxBlockZ() >= volume.bounds().minimumZ()
                && chunkPos.getMinBlockZ() <= volume.bounds().maximumZ();
    }

    private record FinalEvidence(
            int authoredPositive,
            int authoredFinalAir,
            int nativeOnlyAir,
            BlockPos nativeOnlySample) {}
}
