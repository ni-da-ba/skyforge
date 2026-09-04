package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandLocalPosition;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedSubsurfacePosition;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
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

/** Production-lifecycle stacked exact-volume acceptance for SF-IMP-0068. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionComposedCaveStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionComposedCaveStacked";

    private static final ChunkPos TARGET_CHUNK = new ChunkPos(-2, -2);
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionComposedCaveStackedDevRuntime.class.getName());
    private static final SkyforgeNeoForge1211ComposedCaveStackedDevRuntime.Fixture FIXTURE =
            SkyforgeNeoForge1211ComposedCaveStackedDevRuntime.fixtureDefinition();

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentPopulationBinding;
    private static AutoCloseable persistentComposedBinding;
    private static int previousLowerPending = Integer.MAX_VALUE;
    private static int previousUpperPending = Integer.MAX_VALUE;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionComposedCaveStackedDevRuntime() {}

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
                    "SF-IMP-0068 stacked production proof requires isolated production bindings");
        }

        SkyIslandWorldVolume lower = FIXTURE.lower();
        SkyIslandWorldVolume upper = FIXTURE.upper();
        var resolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(lower.id()) && !volumeId.equals(upper.id())) {
                throw new IllegalArgumentException(
                        "SF-IMP-0068 stacked resolver received unknown volume " + volumeId.path());
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

        Set<Long> lowerChunks = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(lower.id());
        Set<Long> upperChunks = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(upper.id());
        persistentPopulationBinding = SkyforgeNativeSurfacePopulationStage.install((chunkPos, minimumY, height) -> {
            List<SkyforgeNativeSurfacePopulationPlan> plans = new ArrayList<>(2);
            if (lowerChunks.contains(chunkPos.toLong())) {
                plans.add(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                        lower.id(), resolver, MAXIMUM_ATTACHMENT_DEPTH));
            }
            if (upperChunks.contains(chunkPos.toLong())) {
                plans.add(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                        upper.id(), resolver, MAXIMUM_ATTACHMENT_DEPTH));
            }
            return List.copyOf(plans);
        });
        persistentComposedBinding = SkyforgeComposedCaveStage.install(
                List.of(
                        new SkyforgeComposedCavePlan(lower, FIXTURE.base().field()),
                        new SkyforgeComposedCavePlan(upper, FIXTURE.base().field())));

        var lowerInitial = SkyforgeComposedCaveStage.snapshot(lower.id());
        var upperInitial = SkyforgeComposedCaveStage.snapshot(upper.id());
        if (lowerInitial.totalObligations() != lowerChunks.size()
                || upperInitial.totalObligations() != upperChunks.size()
                || lowerInitial.pendingObligations() != lowerChunks.size()
                || upperInitial.pendingObligations() != upperChunks.size()
                || lowerInitial.completedObligations() != 0
                || upperInitial.completedObligations() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0068 stacked production ledgers did not start independently pending: lower="
                            + lowerInitial + ", upper=" + upperInitial);
        }
        previousLowerPending = lowerInitial.pendingObligations();
        previousUpperPending = upperInitial.pendingObligations();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete) {
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
        SkyIslandWorldVolume lower = FIXTURE.lower();
        SkyIslandWorldVolume upper = FIXTURE.upper();
        var lowerLedger = SkyforgeComposedCaveStage.snapshot(lower.id());
        var upperLedger = SkyforgeComposedCaveStage.snapshot(upper.id());

        if (lowerLedger.pendingObligations() > previousLowerPending
                || upperLedger.pendingObligations() > previousUpperPending) {
            throw new IllegalStateException(
                    "SF-IMP-0068 stacked pending ledger increased: lower="
                            + previousLowerPending + "->" + lowerLedger.pendingObligations()
                            + ", upper=" + previousUpperPending + "->" + upperLedger.pendingObligations());
        }
        previousLowerPending = lowerLedger.pendingObligations();
        previousUpperPending = upperLedger.pendingObligations();

        var lowerAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(lower.id());
        var upperAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(upper.id());
        if (lowerAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || upperAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(lower.id()).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upper.id()).isEmpty()
                || lowerLedger.pendingObligations() != 0
                || upperLedger.pendingObligations() != 0) {
            return;
        }

        LevelChunk chunk = level.getChunkSource().getChunkNow(TARGET_CHUNK.x, TARGET_CHUNK.z);
        if (chunk == null) {
            return;
        }
        BlockPos lowerAnchor = firstDiscreteAuthoredPositive(lower, FIXTURE.base().field(), chunk);
        BlockPos upperAnchor = firstDiscreteAuthoredPositive(upper, FIXTURE.base().field(), chunk);
        if (lowerAnchor == null
                || upperAnchor == null
                || lowerAnchor.getY() == upperAnchor.getY()
                || !level.getBlockState(lowerAnchor).isAir()
                || !level.getBlockState(upperAnchor).isAir()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 stacked production authored anchors are not independently realized: lower="
                            + lowerAnchor + ", upper=" + upperAnchor);
        }

        Aggregate lowerAggregate = aggregate(lower.id());
        Aggregate upperAggregate = aggregate(upper.id());
        BlockPos lowerNativeOnly = firstNativeOnlyAir(level, lower, FIXTURE.base().field(), chunk);
        BlockPos upperNativeOnly = firstNativeOnlyAir(level, upper, FIXTURE.base().field(), chunk);
        if (!lowerAggregate.valid()
                || !upperAggregate.valid()
                || lowerNativeOnly == null
                || upperNativeOnly == null
                || !level.getBlockState(lowerNativeOnly).isAir()
                || !level.getBlockState(upperNativeOnly).isAir()
                || lowerLedger.totalObligations() != lowerAdmission.requiredChunks()
                || upperLedger.totalObligations() != upperAdmission.requiredChunks()
                || lowerLedger.completedObligations() != lowerLedger.totalObligations()
                || upperLedger.completedObligations() != upperLedger.totalObligations()) {
            throw new IllegalStateException(
                    "SF-IMP-0068 stacked production evidence incomplete: lower=" + lowerAggregate
                            + ", upper=" + upperAggregate
                            + ", lowerNativeOnly=" + lowerNativeOnly
                            + ", upperNativeOnly=" + upperNativeOnly
                            + ", lowerLedger=" + lowerLedger
                            + ", upperLedger=" + upperLedger);
        }

        var beforeReplay = SkyforgeComposedCaveStage.snapshot();
        var replay = SkyforgeComposedCaveStage.service(
                level, chunk, level.getChunkSource().getGenerator());
        var afterReplay = SkyforgeComposedCaveStage.snapshot();
        if (!replay.isEmpty() || !beforeReplay.equals(afterReplay)) {
            throw new IllegalStateException(
                    "SF-IMP-0068 stacked completed obligations replayed");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0068 PRODUCTION COMPOSED CAVE STACKED PASS: targetChunk=" + TARGET_CHUNK
                        + ", lowerCompleted=" + lowerLedger.completedObligations()
                        + ", upperCompleted=" + upperLedger.completedObligations()
                        + ", lowerNativeChanged=" + lowerAggregate.nativeChanged()
                        + ", upperNativeChanged=" + upperAggregate.nativeChanged()
                        + ", lowerAuthoredPositive=" + lowerAggregate.authoredPositive()
                        + ", upperAuthoredPositive=" + upperAggregate.authoredPositive()
                        + ", lowerAnchor=" + lowerAnchor
                        + ", upperAnchor=" + upperAnchor
                        + ", independentLedgers=true, foreignVolumePreserved=true, noReplay=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("targetChunk", TARGET_CHUNK.toLong()),
                        java.util.Map.entry("lowerRequired", lowerAdmission.requiredChunks()),
                        java.util.Map.entry("upperRequired", upperAdmission.requiredChunks()),
                        java.util.Map.entry("lowerCompleted", lowerLedger.completedObligations()),
                        java.util.Map.entry("upperCompleted", upperLedger.completedObligations()),
                        java.util.Map.entry("lowerNativeChanged", lowerAggregate.nativeChanged()),
                        java.util.Map.entry("upperNativeChanged", upperAggregate.nativeChanged()),
                        java.util.Map.entry("lowerAuthoredPositive", lowerAggregate.authoredPositive()),
                        java.util.Map.entry("upperAuthoredPositive", upperAggregate.authoredPositive()),
                        java.util.Map.entry("lowerUnsafe", lowerAggregate.authoredUnsafe()),
                        java.util.Map.entry("upperUnsafe", upperAggregate.authoredUnsafe()),
                        java.util.Map.entry("lowerAnchorY", lowerAnchor.getY()),
                        java.util.Map.entry("upperAnchorY", upperAnchor.getY()),
                        java.util.Map.entry("lowerNativeOnlyPos", Long.toString(lowerNativeOnly.asLong())),
                        java.util.Map.entry("upperNativeOnlyPos", Long.toString(upperNativeOnly.asLong())),
                        java.util.Map.entry("lowerFinalPending", lowerLedger.pendingObligations()),
                        java.util.Map.entry("upperFinalPending", upperLedger.pendingObligations()),
                        java.util.Map.entry("independentLedgers", true),
                        java.util.Map.entry("foreignVolumePreserved", true),
                        java.util.Map.entry("monotonicPending", true),
                        java.util.Map.entry("noReplay", true)));
    }

    private static Aggregate aggregate(SkyIslandWorldVolumeId volumeId) {
        int resultChunks = 0;
        int nativeChanged = 0;
        int authoredPositive = 0;
        int authoredUnsafe = 0;
        for (var completion : SkyforgeComposedCaveStage.completed()) {
            if (!completion.volumeId().equals(volumeId) || completion.result().isEmpty()) {
                continue;
            }
            resultChunks++;
            var result = completion.result().orElseThrow();
            nativeChanged = Math.addExact(nativeChanged, result.nativeResult().changedBlocks());
            authoredPositive = Math.addExact(authoredPositive, result.authoredResult().positiveSamples());
            authoredUnsafe = Math.addExact(authoredUnsafe, result.authoredResult().unsafePositiveSamples());
        }
        return new Aggregate(resultChunks, nativeChanged, authoredPositive, authoredUnsafe);
    }

    private static BlockPos firstDiscreteAuthoredPositive(
            SkyIslandWorldVolume volume,
            SkyIslandExteriorConnectedCaveVolumeField field,
            LevelChunk chunk) {
        int minimumY = Math.max(
                chunk.getMinBuildHeight(),
                (int) Math.ceil(volume.bounds().minimumY()));
        int maximumY = Math.min(
                chunk.getMaxBuildHeight() - 1,
                (int) Math.floor(volume.bounds().maximumY()));
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    boolean owner = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volume.id(), x, y, z)
                            .orElseThrow();
                    if (owner
                            && realized.sample(
                                            new SkyIslandRealizedSubsurfacePosition(local, y))
                                    .inside()) {
                        return new BlockPos(x, y, z);
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos firstNativeOnlyAir(
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
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                field,
                new SkyIslandCompiledVolumeColumnField(volume.compiledVolume()));
        var descriptor = volume.compiledVolume().descriptor();

        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                        x - descriptor.centerX(),
                        z - descriptor.centerZ());
                for (int y = minimumY; y <= maximumY; y++) {
                    boolean owner = SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                    volume.id(), x, y, z)
                            .orElseThrow();
                    if (!owner
                            || realized.sample(
                                            new SkyIslandRealizedSubsurfacePosition(local, y))
                                    .inside()) {
                        continue;
                    }
                    BlockPos position = new BlockPos(x, y, z);
                    if (level.getBlockState(position).isAir()) {
                        return position;
                    }
                }
            }
        }
        return null;
    }

    private record Aggregate(
            int resultChunks,
            int nativeChanged,
            int authoredPositive,
            int authoredUnsafe) {
        boolean valid() {
            return resultChunks > 0
                    && nativeChanged > 0
                    && authoredPositive > 0
                    && authoredUnsafe == 0;
        }
    }
}
