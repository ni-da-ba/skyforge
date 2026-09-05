package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolume;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only SF-IMP-0069 proof of production post-cave native interior population.
 *
 * <p>The runtime never invokes a native interior feature directly. It installs the ordinary
 * terrain/admission/surface/composed-cave/interior production stages, then observes their ledgers
 * while stable-chunk catch-up performs all mutations through {@link
 * SkyforgePhysicalVolumeCatchupService}.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionInteriorPopulationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionInteriorPopulation";

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int SURFACE_MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final int INTERIOR_MAXIMUM_ATTACHMENT_DEPTH = 0;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionInteriorPopulationDevRuntime.class.getName());

    private static final SkyforgeNeoForge1211ProductionComposedCaveFixture.Single FIXTURE =
            SkyforgeNeoForge1211ProductionComposedCaveFixture.single();

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentSurfacePopulationBinding;
    private static AutoCloseable persistentComposedBinding;
    private static AutoCloseable persistentInteriorBinding;
    private static SkyforgeNativeInteriorPopulationStage.Snapshot initialInterior;
    private static int previousInteriorPending = Integer.MAX_VALUE;
    private static boolean observedInteriorBeforeCavesComplete;
    private static boolean proofComplete;
    private static int observationTicks;

    private SkyforgeNeoForge1211ProductionInteriorPopulationDevRuntime() {}

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
                    "SF-IMP-0069 production interior proof requires isolated production bindings");
        }

        SkyIslandWorldVolume volume = FIXTURE.volume();
        SkyIslandWorldVolumeId volumeId = volume.id();
        var biomeResolver = (SkyforgeExactVolumeBiomeResolver) (candidateId, x, y, z) -> {
            if (!candidateId.equals(volumeId)) {
                throw new IllegalArgumentException(
                        "SF-IMP-0069 resolved unexpected volume " + candidateId.path());
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

        Set<Long> plannedChunks = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(volumeId);
        persistentSurfacePopulationBinding = SkyforgeNativeSurfacePopulationStage.install(
                (chunkPos, minimumY, height) -> plannedChunks.contains(chunkPos.toLong())
                        ? List.of(SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                                volumeId,
                                biomeResolver,
                                SURFACE_MAXIMUM_ATTACHMENT_DEPTH))
                        : List.of());
        persistentComposedBinding = SkyforgeComposedCaveStage.install(
                List.of(new SkyforgeComposedCavePlan(volume, FIXTURE.field())));
        persistentInteriorBinding = SkyforgeNativeInteriorPopulationStage.install(
                List.of(SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(
                        volumeId,
                        INTERIOR_MAXIMUM_ATTACHMENT_DEPTH)));

        var cave = SkyforgeComposedCaveStage.snapshot(volumeId);
        initialInterior = SkyforgeNativeInteriorPopulationStage.snapshot(volumeId);
        if (cave.totalObligations() != plannedChunks.size()
                || cave.pendingObligations() != plannedChunks.size()
                || initialInterior.totalObligations() != plannedChunks.size()
                || initialInterior.pendingObligations() != plannedChunks.size()
                || initialInterior.completedObligations() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 production ledgers did not start finite/all-pending: cave="
                            + cave + ", interior=" + initialInterior);
        }
        previousInteriorPending = initialInterior.pendingObligations();

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0069 production interior specimen enabled: islandKey="
                        + FIXTURE.islandKey()
                        + ", obligations=" + plannedChunks.size()
                        + ", biomes=[minecraft:river,minecraft:dripstone_caves]. "
                        + "Interior population remains ledger-gated behind whole-volume composed caves.");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled() || proofComplete || initialInterior == null) {
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

        SkyIslandWorldVolumeId volumeId = FIXTURE.volume().id();
        var cave = SkyforgeComposedCaveStage.snapshot(volumeId);
        var interior = SkyforgeNativeInteriorPopulationStage.snapshot(volumeId);

        if (interior.pendingObligations() > previousInteriorPending) {
            throw new IllegalStateException(
                    "SF-IMP-0069 interior pending obligations increased: before="
                            + previousInteriorPending + ", after=" + interior.pendingObligations());
        }
        previousInteriorPending = interior.pendingObligations();
        if (interior.completedObligations() > 0 && cave.pendingObligations() > 0) {
            observedInteriorBeforeCavesComplete = true;
            throw new IllegalStateException(
                    "SF-IMP-0069 native interior population ran before whole-volume composed caves completed");
        }

        observationTicks++;
        if (observationTicks % 200 == 0) {
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-0069 PRODUCTION PROGRESS: cave=" + cave + ", interior=" + interior);
        }

        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        if (admission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()
                || cave.pendingObligations() != 0
                || interior.pendingObligations() != 0) {
            return;
        }
        if (interior.totalObligations() != admission.requiredChunks()
                || interior.completedObligations() != admission.requiredChunks()) {
            throw new IllegalStateException(
                    "SF-IMP-0069 interior footprint differs from physical admission: interior="
                            + interior + ", admission=" + admission);
        }

        List<SkyforgeNativeInteriorPopulationStage.Completion> completions =
                SkyforgeNativeInteriorPopulationStage.completed();
        if (completions.size() != interior.totalObligations()) {
            throw new IllegalStateException(
                    "SF-IMP-0069 completion evidence count differs from production ledger");
        }

        List<GenerationStep.Decoration> expectedPhases =
                SkyforgeNativeInteriorPopulationPhasePolicy.admittedPhases();
        var phaseCalls = new EnumMap<GenerationStep.Decoration, Integer>(GenerationStep.Decoration.class);
        var attempted = new EnumMap<GenerationStep.Decoration, Integer>(GenerationStep.Decoration.class);
        var successful = new EnumMap<GenerationStep.Decoration, Integer>(GenerationStep.Decoration.class);
        Set<String> biomeKeys = new LinkedHashSet<>();
        Set<String> successfulFeatureKeys = new LinkedHashSet<>();
        int resultChunks = 0;
        int emptyChunks = 0;
        int unsupportedLakeFeatures = 0;
        long phaseDigest = FNV_OFFSET_BASIS;

        for (var completion : completions) {
            if (!completion.volumeId().equals(volumeId)) {
                throw new IllegalStateException(
                        "SF-IMP-0069 completion belongs to unexpected volume "
                                + completion.volumeId().path());
            }
            if (completion.phaseResults().isEmpty()) {
                emptyChunks++;
                continue;
            }
            resultChunks++;
            if (completion.phaseResults().size() != expectedPhases.size()) {
                throw new IllegalStateException(
                        "SF-IMP-0069 non-empty completion did not execute every admitted phase: "
                                + completion);
            }
            for (int index = 0; index < expectedPhases.size(); index++) {
                var result = completion.phaseResults().get(index);
                var expected = expectedPhases.get(index);
                if (result.generationStep() != expected) {
                    throw new IllegalStateException(
                            "SF-IMP-0069 phase order drifted at " + completion.chunkPos()
                                    + ": expected=" + expected + ", actual=" + result.generationStep());
                }
                phaseCalls.merge(expected, 1, Math::addExact);
                attempted.merge(expected, result.attemptedFeatures(), Math::addExact);
                successful.merge(expected, result.successfulFeatures(), Math::addExact);
                biomeKeys.add(result.biomeKey().location().toString());
                unsupportedLakeFeatures = Math.addExact(
                        unsupportedLakeFeatures,
                        result.lakeEvidence().unsupportedPlacedFeatures());

                phaseDigest = mix(phaseDigest, completion.chunkPos().toLong());
                phaseDigest = mix(phaseDigest, expected.ordinal());
                phaseDigest = mix(phaseDigest, result.attemptedFeatures());
                phaseDigest = mix(phaseDigest, result.successfulFeatures());
                for (var feature : result.featureResults()) {
                    phaseDigest = mix(phaseDigest, feature.featureKey().toString().hashCode());
                    phaseDigest = mix(phaseDigest, feature.placed() ? 1L : 0L);
                    if (feature.placed()) {
                        successfulFeatureKeys.add(feature.featureKey().toString());
                    }
                }
            }
        }

        for (GenerationStep.Decoration phase : expectedPhases) {
            if (phaseCalls.getOrDefault(phase, 0) <= 0
                    || attempted.getOrDefault(phase, 0) <= 0
                    || successful.getOrDefault(phase, 0) <= 0) {
                throw new IllegalStateException(
                        "SF-IMP-0069 mixed-biome fixture did not discriminate admitted phase "
                                + phase + ": calls=" + phaseCalls.getOrDefault(phase, 0)
                                + ", attempted=" + attempted.getOrDefault(phase, 0)
                                + ", successful=" + successful.getOrDefault(phase, 0));
            }
        }
        if (!biomeKeys.contains(Biomes.RIVER.location().toString())
                || !biomeKeys.contains(Biomes.DRIPSTONE_CAVES.location().toString())) {
            throw new IllegalStateException(
                    "SF-IMP-0069 did not reuse both expected final-registry biome identities: " + biomeKeys);
        }
        if (unsupportedLakeFeatures != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 encountered unsupported LAKES configured-feature classes: "
                            + unsupportedLakeFeatures);
        }

        var fluids = SkyforgeGeneratedFluidPropagationStage.snapshot(level, volumeId);
        if (fluids.trackedPositions() <= 0
                || fluids.scheduledOutsideOwner() != 0
                || fluids.rejectedBoundaryWrites() != 0) {
            throw new IllegalStateException(
                    "SF-IMP-0069 generated-fluid containment evidence is incomplete: " + fluids);
        }

        long replayChunkKey = completions.stream()
                .filter(completion -> !completion.phaseResults().isEmpty())
                .findFirst()
                .orElseThrow()
                .chunkPos()
                .toLong();
        var replayChunk = level.getChunkSource().getChunkNow(
                net.minecraft.world.level.ChunkPos.getX(replayChunkKey),
                net.minecraft.world.level.ChunkPos.getZ(replayChunkKey));
        if (replayChunk == null) {
            return;
        }
        boolean replayWorked = SkyforgeNativeInteriorPopulationStage.service(
                level,
                replayChunk,
                level.getChunkSource().getGenerator()).worked();
        if (replayWorked) {
            throw new IllegalStateException("SF-IMP-0069 completed interior obligation replayed");
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0069 PRODUCTION INTERIOR PASS: obligations="
                        + interior.totalObligations()
                        + ", resultChunks=" + resultChunks
                        + ", emptyChunks=" + emptyChunks
                        + ", phaseDigest=" + Long.toUnsignedString(phaseDigest, 16)
                        + ", biomes=" + biomeKeys
                        + ", successfulFeatureKeys=" + successfulFeatureKeys
                        + ", trackedFluids=" + fluids.trackedPositions() + ".");

        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("productionStage", true),
                        java.util.Map.entry("islandKey", FIXTURE.islandKey()),
                        java.util.Map.entry("requiredChunks", admission.requiredChunks()),
                        java.util.Map.entry("initialInteriorTotal", initialInterior.totalObligations()),
                        java.util.Map.entry("initialInteriorPending", initialInterior.pendingObligations()),
                        java.util.Map.entry("finalInteriorPending", interior.pendingObligations()),
                        java.util.Map.entry("finalInteriorCompleted", interior.completedObligations()),
                        java.util.Map.entry("resultChunks", resultChunks),
                        java.util.Map.entry("emptyChunks", emptyChunks),
                        java.util.Map.entry("cavesCompleteBeforeInterior", !observedInteriorBeforeCavesComplete),
                        java.util.Map.entry("monotonicPending", true),
                        java.util.Map.entry("noReplay", true),
                        java.util.Map.entry("biomes", String.join(",", biomeKeys)),
                        java.util.Map.entry("phaseDigest", Long.toUnsignedString(phaseDigest, 16)),
                        java.util.Map.entry("lakesAttempted", attempted.getOrDefault(GenerationStep.Decoration.LAKES, 0)),
                        java.util.Map.entry("lakesSuccessful", successful.getOrDefault(GenerationStep.Decoration.LAKES, 0)),
                        java.util.Map.entry("localModificationsAttempted", attempted.getOrDefault(GenerationStep.Decoration.LOCAL_MODIFICATIONS, 0)),
                        java.util.Map.entry("localModificationsSuccessful", successful.getOrDefault(GenerationStep.Decoration.LOCAL_MODIFICATIONS, 0)),
                        java.util.Map.entry("oresAttempted", attempted.getOrDefault(GenerationStep.Decoration.UNDERGROUND_ORES, 0)),
                        java.util.Map.entry("oresSuccessful", successful.getOrDefault(GenerationStep.Decoration.UNDERGROUND_ORES, 0)),
                        java.util.Map.entry("decorationAttempted", attempted.getOrDefault(GenerationStep.Decoration.UNDERGROUND_DECORATION, 0)),
                        java.util.Map.entry("decorationSuccessful", successful.getOrDefault(GenerationStep.Decoration.UNDERGROUND_DECORATION, 0)),
                        java.util.Map.entry("springsAttempted", attempted.getOrDefault(GenerationStep.Decoration.FLUID_SPRINGS, 0)),
                        java.util.Map.entry("springsSuccessful", successful.getOrDefault(GenerationStep.Decoration.FLUID_SPRINGS, 0)),
                        java.util.Map.entry("trackedFluids", fluids.trackedPositions()),
                        java.util.Map.entry("fluidDigest", Long.toUnsignedString(fluids.digest(), 16)),
                        java.util.Map.entry("scheduledOutsideOwner", fluids.scheduledOutsideOwner()),
                        java.util.Map.entry("rejectedBoundaryWrites", fluids.rejectedBoundaryWrites()),
                        java.util.Map.entry("successfulFeatureKeys", String.join(",", successfulFeatureKeys))));
    }

    private static long mix(long digest, long value) {
        long mixed = digest;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixed ^= (value >>> shift) & 0xffL;
            mixed *= FNV_PRIME;
        }
        return mixed;
    }
}
