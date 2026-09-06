package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Production-lifecycle stacked exact-volume acceptance for SF-IMP-0069. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionInteriorPopulationStackedDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionInteriorPopulationStacked";

    private static final int SURFACE_MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final int INTERIOR_MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionInteriorPopulationStackedDevRuntime.class.getName());
    private static final SkyforgeNeoForge1211ProductionComposedCaveFixture.Stacked FIXTURE =
            SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentSurfacePopulationBinding;
    private static AutoCloseable persistentComposedBinding;
    private static AutoCloseable persistentInteriorBinding;
    private static int previousLowerPending = Integer.MAX_VALUE;
    private static int previousUpperPending = Integer.MAX_VALUE;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ProductionInteriorPopulationStackedDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()
                || persistentTerrainBinding != null
                || persistentAdmissionBinding != null
                || persistentSurfacePopulationBinding != null
                || persistentComposedBinding != null
                || persistentInteriorBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0069 stacked production proof requires isolated production bindings");
        }

        SkyIslandWorldVolume lower = FIXTURE.lower();
        SkyIslandWorldVolume upper = FIXTURE.upper();
        var resolver = (SkyforgeExactVolumeBiomeResolver) (volumeId, x, y, z) -> {
            if (!volumeId.equals(lower.id()) && !volumeId.equals(upper.id())) {
                throw new IllegalArgumentException(
                        "SF-IMP-0069 stacked resolver received unknown volume " + volumeId.path());
            }
            return x < 0 ? Biomes.RIVER : Biomes.DRIPSTONE_CAVES;
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
        persistentSurfacePopulationBinding = SkyforgeNativeSurfacePopulationStage.install((chunkPos, minimumY, height) -> {
            List<SkyforgeNativeSurfacePopulationPlan> plans = new ArrayList<>(2);
            if (lowerChunks.contains(chunkPos.toLong())) {
                plans.add(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                        lower.id(), resolver, SURFACE_MAXIMUM_ATTACHMENT_DEPTH));
            }
            if (upperChunks.contains(chunkPos.toLong())) {
                plans.add(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                        upper.id(), resolver, SURFACE_MAXIMUM_ATTACHMENT_DEPTH));
            }
            return List.copyOf(plans);
        });
        persistentComposedBinding = SkyforgeComposedCaveStage.install(List.of(
                new SkyforgeComposedCavePlan(lower, FIXTURE.field()),
                new SkyforgeComposedCavePlan(upper, FIXTURE.field())));
        persistentInteriorBinding = SkyforgeNativeInteriorPopulationStage.install(List.of(
                SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(
                        lower.id(), INTERIOR_MAXIMUM_ATTACHMENT_DEPTH),
                SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(
                        upper.id(), INTERIOR_MAXIMUM_ATTACHMENT_DEPTH)));

        var lowerInitial = SkyforgeNativeInteriorPopulationStage.snapshot(lower.id());
        var upperInitial = SkyforgeNativeInteriorPopulationStage.snapshot(upper.id());
        if (lowerInitial.totalObligations() != lowerChunks.size()
                || upperInitial.totalObligations() != upperChunks.size()
                || lowerInitial.pendingObligations() != lowerChunks.size()
                || upperInitial.pendingObligations() != upperChunks.size()
                || lowerInitial.completedObligations() != 0
                || upperInitial.completedObligations() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 stacked native-interior ledgers did not start independently pending: lower="
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
        var lowerCave = SkyforgeComposedCaveStage.snapshot(lower.id());
        var upperCave = SkyforgeComposedCaveStage.snapshot(upper.id());
        var lowerInterior = SkyforgeNativeInteriorPopulationStage.snapshot(lower.id());
        var upperInterior = SkyforgeNativeInteriorPopulationStage.snapshot(upper.id());

        if (lowerInterior.pendingObligations() > previousLowerPending
                || upperInterior.pendingObligations() > previousUpperPending) {
            throw new IllegalStateException(
                    "SF-IMP-0069 stacked interior pending ledger increased: lower="
                            + previousLowerPending + "->" + lowerInterior.pendingObligations()
                            + ", upper=" + previousUpperPending + "->" + upperInterior.pendingObligations());
        }
        previousLowerPending = lowerInterior.pendingObligations();
        previousUpperPending = upperInterior.pendingObligations();

        if (lowerInterior.completedObligations() > 0 && lowerCave.pendingObligations() > 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 lower stacked interior population began before lower composed caves completed");
        }
        if (upperInterior.completedObligations() > 0 && upperCave.pendingObligations() > 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 upper stacked interior population began before upper composed caves completed");
        }

        var lowerAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(lower.id());
        var upperAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(upper.id());
        if (lowerAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || upperAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(lower.id()).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upper.id()).isEmpty()
                || lowerCave.pendingObligations() != 0
                || upperCave.pendingObligations() != 0
                || lowerInterior.pendingObligations() != 0
                || upperInterior.pendingObligations() != 0) {
            return;
        }

        Aggregate lowerAggregate = aggregate(lower.id());
        Aggregate upperAggregate = aggregate(upper.id());
        if (!lowerAggregate.valid() || !upperAggregate.valid()) {
            throw new IllegalStateException(
                    "SF-IMP-0069 stacked native-interior population did not produce discriminating output: lower="
                            + lowerAggregate + ", upper=" + upperAggregate);
        }

        TrackedSample lowerFluid = trackedSample(level, lower.id());
        TrackedSample upperFluid = trackedSample(level, upper.id());
        proveFluidIsolation(level, lower.id(), lowerFluid, upperFluid);
        proveFluidIsolation(level, upper.id(), upperFluid, lowerFluid);

        LevelChunk replayChunk = firstLoadedRequiredChunk(
                level, SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(lower.id()));
        if (replayChunk == null) {
            return;
        }
        var beforeReplay = SkyforgeNativeInteriorPopulationStage.snapshot();
        var replay = SkyforgeNativeInteriorPopulationStage.service(
                level, replayChunk, level.getChunkSource().getGenerator());
        var afterReplay = SkyforgeNativeInteriorPopulationStage.snapshot();
        if (replay.worked() || !replay.completions().isEmpty() || !beforeReplay.equals(afterReplay)) {
            throw new IllegalStateException("SF-IMP-0069 stacked completed interior obligations replayed");
        }

        var lowerFluidSnapshot = SkyforgeGeneratedFluidPropagationStage.snapshot(level, lower.id());
        var upperFluidSnapshot = SkyforgeGeneratedFluidPropagationStage.snapshot(level, upper.id());
        if (lowerFluidSnapshot.scheduledOutsideOwner() != 0
                || upperFluidSnapshot.scheduledOutsideOwner() != 0
                || lowerFluidSnapshot.rejectedBoundaryWrites() < 1
                || upperFluidSnapshot.rejectedBoundaryWrites() < 1) {
            throw new IllegalStateException(
                    "SF-IMP-0069 stacked fluid isolation counters are incomplete: lower="
                            + lowerFluidSnapshot + ", upper=" + upperFluidSnapshot);
        }

        Plausibility lowerPlausibility = scanPlausibility(
                level,
                lower,
                SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(lower.id()));
        Plausibility upperPlausibility = scanPlausibility(
                level,
                upper,
                SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(upper.id()));
        if (lowerPlausibility == null || upperPlausibility == null) {
            return;
        }
        if (!lowerPlausibility.valid() || !upperPlausibility.valid()) {
            throw new IllegalStateException(
                    "SF-IMP-0078 native interior plausibility failed: lower="
                            + lowerPlausibility + ", upper=" + upperPlausibility);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0069 PRODUCTION INTERIOR STACKED PASS: lowerCompleted="
                        + lowerInterior.completedObligations()
                        + ", upperCompleted=" + upperInterior.completedObligations()
                        + ", lowerSuccessful=" + lowerAggregate.successfulFeatures()
                        + ", upperSuccessful=" + upperAggregate.successfulFeatures()
                        + ", lowerTrackedFluids=" + lowerFluidSnapshot.trackedPositions()
                        + ", upperTrackedFluids=" + upperFluidSnapshot.trackedPositions()
                        + ", lowerSampleY=" + lowerFluid.position().getY()
                        + ", upperSampleY=" + upperFluid.position().getY()
                        + ", lowerGlowLichen=" + lowerPlausibility.glowLichen()
                        + ", upperGlowLichen=" + upperPlausibility.glowLichen()
                        + ", lowerMaxGlowLichenPerChunk=" + lowerPlausibility.maximumGlowLichenPerChunk()
                        + ", upperMaxGlowLichenPerChunk=" + upperPlausibility.maximumGlowLichenPerChunk()
                        + ", interiorShellPlausibility=true"
                        + ", independentLedgers=true, foreignFluidRejected=true, noReplay=true.");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("lowerRequired", lowerAdmission.requiredChunks()),
                        java.util.Map.entry("upperRequired", upperAdmission.requiredChunks()),
                        java.util.Map.entry("lowerCompleted", lowerInterior.completedObligations()),
                        java.util.Map.entry("upperCompleted", upperInterior.completedObligations()),
                        java.util.Map.entry("lowerResultChunks", lowerAggregate.resultChunks()),
                        java.util.Map.entry("upperResultChunks", upperAggregate.resultChunks()),
                        java.util.Map.entry("lowerSuccessful", lowerAggregate.successfulFeatures()),
                        java.util.Map.entry("upperSuccessful", upperAggregate.successfulFeatures()),
                        java.util.Map.entry("lowerTrackedFluids", lowerFluidSnapshot.trackedPositions()),
                        java.util.Map.entry("upperTrackedFluids", upperFluidSnapshot.trackedPositions()),
                        java.util.Map.entry("lowerSampleY", lowerFluid.position().getY()),
                        java.util.Map.entry("upperSampleY", upperFluid.position().getY()),
                        java.util.Map.entry("lowerFinalPending", lowerInterior.pendingObligations()),
                        java.util.Map.entry("upperFinalPending", upperInterior.pendingObligations()),
                        java.util.Map.entry("independentLedgers", true),
                        java.util.Map.entry("foreignFluidRejected", true),
                        java.util.Map.entry("cavesCompleteBeforeInterior", true),
                        java.util.Map.entry("monotonicPending", true),
                        java.util.Map.entry("noReplay", true),
                        java.util.Map.entry("lowerGlowLichen", lowerPlausibility.glowLichen()),
                        java.util.Map.entry("upperGlowLichen", upperPlausibility.glowLichen()),
                        java.util.Map.entry("lowerUnsupportedGlowLichen", lowerPlausibility.unsupportedGlowLichen()),
                        java.util.Map.entry("upperUnsupportedGlowLichen", upperPlausibility.unsupportedGlowLichen()),
                        java.util.Map.entry("lowerBoundaryGlowLichen", lowerPlausibility.boundaryGlowLichen()),
                        java.util.Map.entry("upperBoundaryGlowLichen", upperPlausibility.boundaryGlowLichen()),
                        java.util.Map.entry("lowerBoundarySpringFluids", lowerPlausibility.boundaryTrackedFluids()),
                        java.util.Map.entry("upperBoundarySpringFluids", upperPlausibility.boundaryTrackedFluids()),
                        java.util.Map.entry("lowerMaxGlowLichenPerChunk", lowerPlausibility.maximumGlowLichenPerChunk()),
                        java.util.Map.entry("upperMaxGlowLichenPerChunk", upperPlausibility.maximumGlowLichenPerChunk()),
                        java.util.Map.entry("interiorShellPlausibility", true)));
    }

    private static Plausibility scanPlausibility(
            ServerLevel level,
            SkyIslandWorldVolume volume,
            Set<Long> requiredChunks) {
        int minimumY = Math.max(
                level.getMinBuildHeight(),
                (int) Math.floor(volume.bounds().minimumY()));
        int maximumY = Math.min(
                level.getMaxBuildHeight() - 1,
                (int) Math.ceil(volume.bounds().maximumY()));
        int glowLichen = 0;
        int unsupportedGlowLichen = 0;
        int boundaryGlowLichen = 0;
        int maximumGlowLichenPerChunk = 0;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (long chunkKey : requiredChunks) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey),
                    ChunkPos.getZ(chunkKey));
            if (chunk == null) {
                return null;
            }
            int chunkGlowLichen = 0;
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                    for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
                        position.set(x, y, z);
                        var state = chunk.getBlockState(position);
                        if (!state.is(Blocks.GLOW_LICHEN)) {
                            continue;
                        }
                        glowLichen++;
                        chunkGlowLichen++;
                        BlockPos immutable = position.immutable();
                        if (!state.canSurvive(level, immutable)) {
                            unsupportedGlowLichen++;
                        }
                        if (!SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(
                                volume.id(), immutable)) {
                            boundaryGlowLichen++;
                        }
                    }
                }
            }
            maximumGlowLichenPerChunk = Math.max(maximumGlowLichenPerChunk, chunkGlowLichen);
        }

        int liveTrackedFluids = 0;
        int liveSpringFluids = 0;
        int boundarySpringFluids = 0;
        for (var tracked : SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volume.id())) {
            BlockPos trackedPosition = BlockPos.of(tracked.position());
            if (level.getFluidState(trackedPosition).isEmpty()) {
                continue;
            }
            liveTrackedFluids++;
            if (tracked.boundaryPolicy()
                    != SkyforgeGeneratedFluidPropagationStage.BoundaryPolicy.INTERIOR_SHELL) {
                continue;
            }
            liveSpringFluids++;
            if (!SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(
                    volume.id(), trackedPosition)) {
                boundarySpringFluids++;
            }
        }
        return new Plausibility(
                glowLichen,
                unsupportedGlowLichen,
                boundaryGlowLichen,
                maximumGlowLichenPerChunk,
                liveTrackedFluids,
                liveSpringFluids,
                boundarySpringFluids);
    }

    private static Aggregate aggregate(SkyIslandWorldVolumeId volumeId) {
        int resultChunks = 0;
        int attemptedFeatures = 0;
        int successfulFeatures = 0;
        EnumMap<GenerationStep.Decoration, Integer> phaseCalls =
                new EnumMap<>(GenerationStep.Decoration.class);
        List<GenerationStep.Decoration> expected =
                SkyforgeNativeInteriorPopulationPhasePolicy.admittedPhases();

        for (var completion : SkyforgeNativeInteriorPopulationStage.completed()) {
            if (!completion.volumeId().equals(volumeId) || completion.phaseResults().isEmpty()) {
                continue;
            }
            resultChunks++;
            if (completion.phaseResults().size() != expected.size()) {
                return Aggregate.invalid();
            }
            for (int index = 0; index < expected.size(); index++) {
                var result = completion.phaseResults().get(index);
                GenerationStep.Decoration phase = expected.get(index);
                if (result.generationStep() != phase) {
                    return Aggregate.invalid();
                }
                phaseCalls.merge(phase, 1, Math::addExact);
                attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
                successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            }
        }
        boolean allPhasesCalled = expected.stream().allMatch(phase -> phaseCalls.getOrDefault(phase, 0) > 0);
        return new Aggregate(resultChunks, attemptedFeatures, successfulFeatures, allPhasesCalled);
    }

    private static TrackedSample trackedSample(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        for (var tracked : SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volumeId)) {
            BlockPos position = BlockPos.of(tracked.position());
            var state = level.getFluidState(position);
            if (state.isEmpty()) {
                continue;
            }
            var actualKey = BuiltInRegistries.FLUID.getKey(state.getType());
            if (tracked.fluidKey().equals(actualKey)) {
                return new TrackedSample(position, state);
            }
        }
        throw new IllegalStateException(
                "SF-IMP-0069 stacked volume has no live tracked generated-fluid sample: " + volumeId.path());
    }

    private static void proveFluidIsolation(
            ServerLevel level,
            SkyIslandWorldVolumeId ownerId,
            TrackedSample owner,
            TrackedSample foreign) {
        SkyforgeGeneratedFluidPropagationStage.beginFluidTick(level, owner.position(), owner.state());
        try {
            if (!SkyforgeGeneratedFluidPropagationStage.propagationActive()
                    || !SkyforgeGeneratedFluidPropagationStage.isVisible(owner.position())
                    || !SkyforgeGeneratedFluidPropagationStage.acceptWrite(owner.position())) {
                throw new IllegalStateException(
                        "SF-IMP-0069 stacked generated-fluid provenance rejected its owner volume");
            }
            if (SkyforgeGeneratedFluidPropagationStage.isVisible(foreign.position())
                    || SkyforgeGeneratedFluidPropagationStage.acceptWrite(foreign.position())) {
                throw new IllegalStateException(
                        "SF-IMP-0069 stacked generated-fluid provenance admitted a foreign volume: owner="
                                + ownerId.path() + ", foreign=" + foreign.position());
            }
        } finally {
            SkyforgeGeneratedFluidPropagationStage.endFluidTick();
        }
    }

    private static LevelChunk firstLoadedRequiredChunk(ServerLevel level, Set<Long> keys) {
        List<Long> ordered = new ArrayList<>(keys);
        ordered.sort(java.util.Comparator
                .comparingInt((Long key) -> ChunkPos.getX(key))
                .thenComparingInt(key -> ChunkPos.getZ(key)));
        for (long key : ordered) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
                return chunk;
            }
        }
        return null;
    }

    private record TrackedSample(BlockPos position, net.minecraft.world.level.material.FluidState state) {}

    private record Plausibility(
            int glowLichen,
            int unsupportedGlowLichen,
            int boundaryGlowLichen,
            int maximumGlowLichenPerChunk,
            int liveTrackedFluids,
            int liveSpringFluids,
            int boundarySpringFluids) {
        private boolean valid() {
            return glowLichen > 0
                    && unsupportedGlowLichen == 0
                    && boundaryGlowLichen == 0
                    && liveTrackedFluids > 0
                    && liveSpringFluids > 0
                    && boundarySpringFluids == 0;
        }
    }

    private record Aggregate(
            int resultChunks,
            int attemptedFeatures,
            int successfulFeatures,
            boolean allPhasesCalled) {
        private static Aggregate invalid() {
            return new Aggregate(0, 0, 0, false);
        }

        boolean valid() {
            return resultChunks > 0
                    && attemptedFeatures > 0
                    && successfulFeatures > 0
                    && allPhasesCalled;
        }
    }
}
