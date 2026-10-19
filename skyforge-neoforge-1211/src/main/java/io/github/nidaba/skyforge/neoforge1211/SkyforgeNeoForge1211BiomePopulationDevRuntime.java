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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Development-only proof that stacked exact volumes can consume different native biome settings. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211BiomePopulationDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.biomePopulation";
    static final String UPPER_BIOME_PROPERTY = "skyforge.dev.biomePopulation.upperBiome";
    static final String PROVIDER_BIOME_PROPERTY = "skyforge.dev.biomePopulation.providerBiome";
    static final String WAIT_FOR_CLIENT_PROPERTY = "skyforge.dev.biomePopulation.waitForRemoteClient";
    static final String CLIENT_RESULT_FILE_PROPERTY = "skyforge.dev.biomePopulation.clientResultFile";
    private static final String REOPEN_ACCEPTANCE_CASE = "sf-imp-0054-modded-biome-reopen";
    private static final ResourceKey<Biome> UPPER_BIOME = configuredBiomeKey(
            System.getProperty(UPPER_BIOME_PROPERTY, "minecraft:taiga"));
    private static final int PROVIDER_MODE_MINIMUM_ELIGIBLE_CHUNKS = 1;
    private static final long ROOT_SEED = 0x5346494d50303054L;
    private static final int PROOF_RADIUS_CHUNKS = 4;
    private static final ChunkPos EXTERIOR_WITNESS_CHUNK = new ChunkPos(10, 0);
    private static final int CANDIDATE_PROOF_CHUNKS =
            (PROOF_RADIUS_CHUNKS * 2 + 1) * (PROOF_RADIUS_CHUNKS * 2 + 1);
    private static final int MINIMUM_ELIGIBLE_PROOF_CHUNKS = 25;
    private static final int MINIMUM_SHARED_COLUMNS_PER_CHUNK = 192;
    private static final int VEGETATION_SCAN_HEIGHT = 40;
    private static final int PROOF_X = 8;
    private static final int PROOF_Z = 8;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211BiomePopulationDevRuntime.class.getName());

    private static final Set<Long> scannedProofChunks = new HashSet<>();
    private static final Set<Long> eligibleProofChunks = new HashSet<>();
    private static final Aggregate lowerAggregate = new Aggregate();
    private static final Aggregate upperAggregate = new Aggregate();
    private static AutoCloseable persistentBinding;
    private static boolean proofComplete;
    private static boolean acceptanceCompletionRequested;
    private static ResourceKey<Biome> providerBiomeSampleKey;

    @SubscribeEvent
    static synchronized void completeServerAcceptance(ServerTickEvent.Post event) {
        if (!enabled()
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || acceptanceCompletionRequested) {
            return;
        }
        if (REOPEN_ACCEPTANCE_CASE.equals(SkyforgeAutomatedAcceptanceHarness.caseId())) {
            if (!SkyforgeAutomatedAcceptanceHarness.warmupComplete()) {
                return;
            }
            completePersistedReopenAcceptance(event.getServer().overworld());
            return;
        }
        if (!proofComplete || !remoteClientJoinPassed()) {
            return;
        }
        int exteriorNonAirBlocks = countExteriorWitnessNonAirBlocks(event.getServer().overworld());
        if (exteriorNonAirBlocks != 0) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "void exterior witness chunk contained " + exteriorNonAirBlocks + " non-air blocks");
            return;
        }
        acceptanceCompletionRequested = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "selectedUpperBiome", UPPER_BIOME.location(),
                        "providerBiomeSource", providerBiomeSampleKey == null
                                ? "disabled" : providerBiomeSampleKey.location(),
                        "providerBiomeMatchesPopulation", providerBiomeSampleKey != null
                                && providerBiomeSampleKey.equals(upperAggregate.biomeKey()),
                        "scannedChunks", scannedProofChunks.size(),
                        "eligibleChunks", eligibleProofChunks.size(),
                        "lowerBiome", lowerAggregate.biomeKey().location(),
                        "lowerSuccessfulFeatures", lowerAggregate.successfulFeatures,
                        "lowerPersistentTreeEvidence", lowerAggregate.hasTreeEvidence(),
                        "upperSuccessfulFeatures", upperAggregate.successfulFeatures,
                        "upperPersistentTreeEvidence", upperAggregate.hasTreeEvidence(),
                        "exteriorWitnessChunk", EXTERIOR_WITNESS_CHUNK,
                        "exteriorNonAirBlocks", exteriorNonAirBlocks));
    }

    /**
     * Reopen verification reads the persisted proof footprint only. It deliberately does not invoke
     * the native population runner, so a same-world boot cannot reapply generation over saved edits.
     */
    private static void completePersistedReopenAcceptance(ServerLevel level) {
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        if (volumes.size() != 2) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "SF-IMP-0054 reopen requires exactly two stacked proof volumes");
            return;
        }
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();
        int scannedChunks = 0;
        int eligibleChunks = 0;
        int lowerLogBlocks = 0;
        int lowerLeafBlocks = 0;
        int upperLogBlocks = 0;
        int upperLeafBlocks = 0;

        for (int x = -PROOF_RADIUS_CHUNKS; x <= PROOF_RADIUS_CHUNKS; x++) {
            for (int z = -PROOF_RADIUS_CHUNKS; z <= PROOF_RADIUS_CHUNKS; z++) {
                ChunkPos chunkPos = new ChunkPos(x, z);
                if (level.getChunkSource().getChunkNow(x, z) == null) {
                    SkyforgeAutomatedAcceptanceHarness.fail(
                            level.getServer(),
                            "SF-IMP-0054 reopen proof chunk was not loaded: " + chunkPos);
                    return;
                }
                scannedChunks++;
                Optional<SharedSurfaceSample> sample = sharedSurfaceSample(level, lowerId, upperId, chunkPos);
                if (sample.isEmpty()) {
                    continue;
                }
                SharedSurfaceSample shared = sample.orElseThrow();
                VegetationEvidence lower = scanVegetation(level, chunkPos, shared.lowerSurfaceY());
                VegetationEvidence upper = scanVegetation(level, chunkPos, shared.upperSurfaceY());
                eligibleChunks++;
                lowerLogBlocks += lower.logBlocks();
                lowerLeafBlocks += lower.leafBlocks();
                upperLogBlocks += upper.logBlocks();
                upperLeafBlocks += upper.leafBlocks();
            }
        }

        boolean lowerTreeEvidence = lowerLogBlocks > 0 && lowerLeafBlocks > 0;
        boolean upperTreeEvidence = upperLogBlocks > 0 && upperLeafBlocks > 0;
        if (eligibleChunks < MINIMUM_ELIGIBLE_PROOF_CHUNKS || !lowerTreeEvidence || !upperTreeEvidence) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "SF-IMP-0054 reopen lost persisted native tree evidence: eligible="
                            + eligibleChunks + ", lowerLogs=" + lowerLogBlocks
                            + ", lowerLeaves=" + lowerLeafBlocks + ", upperLogs=" + upperLogBlocks
                            + ", upperLeaves=" + upperLeafBlocks);
            return;
        }

        int exteriorNonAirBlocks = countExteriorWitnessNonAirBlocks(level);
        if (exteriorNonAirBlocks != 0) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "void exterior witness chunk contained " + exteriorNonAirBlocks + " non-air blocks after reopen");
            return;
        }

        acceptanceCompletionRequested = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                level.getServer(),
                Map.ofEntries(
                        Map.entry("selectedUpperBiome", UPPER_BIOME.location()),
                        Map.entry("scannedChunks", scannedChunks),
                        Map.entry("eligibleChunks", eligibleChunks),
                        Map.entry("lowerPersistentTreeEvidence", lowerTreeEvidence),
                        Map.entry("lowerLogBlocks", lowerLogBlocks),
                        Map.entry("lowerLeafBlocks", lowerLeafBlocks),
                        Map.entry("upperPersistentTreeEvidence", upperTreeEvidence),
                        Map.entry("upperLogBlocks", upperLogBlocks),
                        Map.entry("upperLeafBlocks", upperLeafBlocks),
                        Map.entry("generationReapplied", false),
                        Map.entry("exteriorWitnessChunk", EXTERIOR_WITNESS_CHUNK),
                        Map.entry("exteriorNonAirBlocks", exteriorNonAirBlocks)));
    }

    private static boolean remoteClientJoinPassed() {
        if (!Boolean.getBoolean(WAIT_FOR_CLIENT_PROPERTY)) {
            return true;
        }
        String configured = System.getProperty(CLIENT_RESULT_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "remote-client acceptance requires system property " + CLIENT_RESULT_FILE_PROPERTY);
        }
        Path result = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isRegularFile(result)) {
            return false;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(result)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read remote-client acceptance result " + result, exception);
        }
        return "PASS".equals(properties.getProperty("status"))
                && "true".equals(properties.getProperty("remoteServerJoin"))
                && "true".equals(properties.getProperty("spawnSupported"))
                && "false".equals(properties.getProperty("singleplayerServer"));
    }

    private static void installServerAcceptanceWarmupChunks() {
        Set<Long> chunkKeys = new HashSet<>();
        for (int x = -PROOF_RADIUS_CHUNKS; x <= PROOF_RADIUS_CHUNKS; x++) {
            for (int z = -PROOF_RADIUS_CHUNKS; z <= PROOF_RADIUS_CHUNKS; z++) {
                chunkKeys.add(new ChunkPos(x, z).toLong());
            }
        }
        chunkKeys.add(EXTERIOR_WITNESS_CHUNK.toLong());
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(chunkKeys);
    }

    private static int countExteriorWitnessNonAirBlocks(ServerLevel level) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(
                EXTERIOR_WITNESS_CHUNK.x,
                EXTERIOR_WITNESS_CHUNK.z);
        if (chunk == null) {
            throw new IllegalStateException("void exterior witness chunk was not loaded by acceptance warmup");
        }
        int nonAirBlocks = 0;
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int minimumY = level.getMinBuildHeight();
        int maximumY = minimumY + level.getHeight();
        for (int x = EXTERIOR_WITNESS_CHUNK.getMinBlockX(); x <= EXTERIOR_WITNESS_CHUNK.getMaxBlockX(); x++) {
            for (int z = EXTERIOR_WITNESS_CHUNK.getMinBlockZ(); z <= EXTERIOR_WITNESS_CHUNK.getMaxBlockZ(); z++) {
                for (int y = minimumY; y < maximumY; y++) {
                    position.set(x, y, z);
                    if (!chunk.getBlockState(position).isAir()) {
                        nonAirBlocks++;
                    }
                }
            }
        }
        return nonAirBlocks;
    }

    private SkyforgeNeoForge1211BiomePopulationDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("cannot install SF-IMP-0054 biome proof over another Skyforge binding");
        }
        persistentBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        if (SkyforgeAutomatedAcceptanceHarness.serverMode()) {
            installServerAcceptanceWarmupChunks();
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0054 exact-volume biome specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and inspect the two vertically aligned tableland islands near "
                        + "x=" + PROOF_X + ", z=" + PROOF_Z
                        + ". Lower resolves minecraft:forest; upper resolves " + UPPER_BIOME.location() + ". A deterministic "
                        + (PROOF_RADIUS_CHUNKS * 2 + 1) + "x" + (PROOF_RADIUS_CHUNKS * 2 + 1)
                        + " candidate patch is scanned. A chunk is eligible only when at least "
                        + MINIMUM_SHARED_COLUMNS_PER_CHUNK
                        + " of its 256 X/Z columns contain terrain in both exact volumes. Acceptance additionally "
                        + "requires persistent log and leaf blocks on both islands; PlacedFeature boolean success "
                        + "alone is not sufficient evidence of visible population.");
    }

    static synchronized void populate(
            WorldGenLevel level,
            ChunkAccess chunk,
            ChunkGenerator generator) {
        if (!enabled() || proofComplete) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        if (!isProofChunk(chunkPos) || !scannedProofChunks.add(chunkPos.toLong())) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("SF-IMP-0054 biome proof ran without its Skyforge binding");
        }

        var volumes = catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0054 fixture requires exactly two stacked volumes");
        }
        SkyIslandWorldVolumeId lowerId = volumes.get(0).id();
        SkyIslandWorldVolumeId upperId = volumes.get(1).id();

        Optional<SharedSurfaceSample> sample = sharedSurfaceSample(level, lowerId, upperId, chunkPos);
        if (sample.isEmpty()) {
            evaluateCompletion(lowerId, upperId);
            return;
        }
        SharedSurfaceSample shared = sample.orElseThrow();
        boolean providerMode = Boolean.getBoolean(PROVIDER_BIOME_PROPERTY);
        ResourceKey<Biome> providerUpperBiome = null;
        if (providerMode) {
            providerUpperBiome = level.getBiome(new BlockPos(shared.x(), shared.upperSurfaceY(), shared.z()))
                    .unwrapKey()
                    .orElseThrow(() -> new IllegalStateException(
                            "provider returned an unregistered biome at the admitted upper surface sample"));
            if (!UPPER_BIOME.equals(providerUpperBiome)) {
                evaluateCompletion(lowerId, upperId);
                return;
            }
            providerBiomeSampleKey = providerUpperBiome;
        }
        eligibleProofChunks.add(chunkPos.toLong());

        SkyforgeExactVolumeBiomeResolver resolver = providerMode
                ? providerBackedProofResolver(lowerId, upperId)
                : (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return UPPER_BIOME;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0054 proof volume: " + volumeId.path());
        };

        var lower = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                lowerId,
                chunkPos,
                shared.lowerSurfaceY(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);
        var upper = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                upperId,
                chunkPos,
                shared.upperSurfaceY(),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);

        requireChunkResult("lower", lower, Biomes.FOREST);
        requireChunkResult("upper", upper, UPPER_BIOME);
        if (lower.biomeKey().equals(upper.biomeKey())) {
            throw new IllegalStateException("stacked exact volumes resolved the same biome identity");
        }
        if (lower.featureKeys().equals(upper.featureKeys())) {
            throw new IllegalStateException("forest and configured-biome proof domains exposed identical vegetation feature lists");
        }

        VegetationEvidence lowerVegetation = scanVegetation(level, chunkPos, shared.lowerSurfaceY());
        VegetationEvidence upperVegetation = scanVegetation(level, chunkPos, shared.upperSurfaceY());
        lowerAggregate.add(lower, lowerVegetation, shared.sharedColumns());
        upperAggregate.add(upper, upperVegetation, shared.sharedColumns());
        evaluateCompletion(lowerId, upperId);
    }

    private static void evaluateCompletion(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        boolean enoughEligibleChunks = eligibleProofChunks.size() >= minimumEligibleProofChunks();
        boolean visibleTrees = lowerAggregate.hasTreeEvidence() && upperAggregate.hasTreeEvidence();
        if (enoughEligibleChunks && visibleTrees) {
            completeProof(lowerId, upperId);
            return;
        }
        if (scannedProofChunks.size() < CANDIDATE_PROOF_CHUNKS) {
            return;
        }
        if (!enoughEligibleChunks) {
            throw new IllegalStateException("SF-IMP-0054 candidate region exposed too little substantial shared terrain/provider biome: "
                    + "eligible=" + eligibleProofChunks.size()
                    + ", required=" + MINIMUM_ELIGIBLE_PROOF_CHUNKS
                    + ", scanned=" + scannedProofChunks.size()
                    + ", minimumSharedColumnsPerChunk=" + MINIMUM_SHARED_COLUMNS_PER_CHUNK);
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate, UPPER_BIOME);
        completeProof(lowerId, upperId);
    }

    private static int minimumEligibleProofChunks() {
        return Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                ? PROVIDER_MODE_MINIMUM_ELIGIBLE_CHUNKS
                : MINIMUM_ELIGIBLE_PROOF_CHUNKS;
    }

    private static SkyforgeExactVolumeBiomeResolver providerBackedProofResolver(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        SkyforgeExactVolumeBiomeResolver providerResolver = SkyforgeProviderBiomeResolver.create();
        return new SkyforgeExactVolumeBiomeResolver() {
            @Override
            public ResourceKey<Biome> resolve(SkyIslandWorldVolumeId volumeId, int x, int y, int z) {
                if (volumeId.equals(lowerId)) {
                    return Biomes.FOREST;
                }
                if (volumeId.equals(upperId)) {
                    throw new IllegalStateException("upper provider biome requires live generation context");
                }
                throw new IllegalArgumentException("unknown SF-IMP-0054 proof volume: " + volumeId.path());
            }

            @Override
            public ResourceKey<Biome> resolve(
                    WorldGenLevel level, SkyIslandWorldVolumeId volumeId, int x, int y, int z) {
                if (volumeId.equals(upperId)) {
                    return providerResolver.resolve(level, volumeId, x, y, z);
                }
                return resolve(volumeId, x, y, z);
            }
        };
    }

    private static void completeProof(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        if (proofComplete) {
            return;
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate, UPPER_BIOME);
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0054 BIOME POPULATION STACKED PASS: scannedChunks=" + scannedProofChunks.size()
                        + ", eligibleChunks=" + eligibleProofChunks.size()
                        + ", lower={volume=" + lowerId.path()
                        + ", biome=" + lowerAggregate.biomeKey().location()
                        + ", attempted=" + lowerAggregate.attemptedFeatures
                        + ", successful=" + lowerAggregate.successfulFeatures
                        + ", attachments=" + lowerAggregate.attachmentWrites
                        + ", logs=" + lowerAggregate.logBlocks
                        + ", leaves=" + lowerAggregate.leafBlocks
                        + ", sharedColumns=" + lowerAggregate.sharedColumns
                        + "}, upper={volume=" + upperId.path()
                        + ", biome=" + upperAggregate.biomeKey().location()
                        + ", attempted=" + upperAggregate.attemptedFeatures
                        + ", successful=" + upperAggregate.successfulFeatures
                        + ", attachments=" + upperAggregate.attachmentWrites
                        + ", logs=" + upperAggregate.logBlocks
                        + ", leaves=" + upperAggregate.leafBlocks
                        + ", sharedColumns=" + upperAggregate.sharedColumns
                        + "}. Both domains produced persistent native tree vegetation while consuming their final "
                        + "registered biome settings across substantial shared stacked terrain.");
    }

    private static boolean isProofChunk(ChunkPos chunkPos) {
        return Math.abs(chunkPos.x) <= PROOF_RADIUS_CHUNKS && Math.abs(chunkPos.z) <= PROOF_RADIUS_CHUNKS;
    }

    private static Optional<SharedSurfaceSample> sharedSurfaceSample(
            WorldGenLevel level,
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId,
            ChunkPos chunkPos) {
        int middleX = chunkPos.getMiddleBlockX();
        int middleZ = chunkPos.getMiddleBlockZ();
        SharedSurfaceSample best = null;
        int bestDistance = Integer.MAX_VALUE;
        int sharedColumns = 0;

        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                var lowerClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        lowerId,
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (lowerClaim.isEmpty()) {
                    continue;
                }
                var upperClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (upperClaim.isEmpty()) {
                    continue;
                }

                sharedColumns++;
                int lowerSurfaceY = lowerClaim.orElseThrow().height();
                int upperSurfaceY = upperClaim.orElseThrow().height();
                if (lowerSurfaceY == upperSurfaceY) {
                    throw new IllegalStateException("SF-IMP-0054 stacked biome proof resolved one shared surface at x="
                            + x + ", z=" + z + ", chunk=" + chunkPos);
                }
                int distance = Math.abs(x - middleX) + Math.abs(z - middleZ);
                if (distance < bestDistance) {
                    best = new SharedSurfaceSample(x, z, lowerSurfaceY, upperSurfaceY, 0);
                    bestDistance = distance;
                }
            }
        }
        if (best == null || sharedColumns < MINIMUM_SHARED_COLUMNS_PER_CHUNK) {
            return Optional.empty();
        }
        return Optional.of(new SharedSurfaceSample(
                best.x(), best.z(), best.lowerSurfaceY(), best.upperSurfaceY(), sharedColumns));
    }

    private static VegetationEvidence scanVegetation(
            WorldGenLevel level,
            ChunkPos chunkPos,
            int surfaceY) {
        int logs = 0;
        int leaves = 0;
        int minimumY = Math.max(level.getMinBuildHeight(), surfaceY);
        int maximumY = Math.min(
                level.getMinBuildHeight() + level.getHeight() - 1,
                surfaceY + VEGETATION_SCAN_HEIGHT);
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    var state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.is(BlockTags.LOGS)) {
                        logs++;
                    }
                    if (state.is(BlockTags.LEAVES)) {
                        leaves++;
                    }
                }
            }
        }
        return new VegetationEvidence(logs, leaves);
    }

    private static void requireChunkResult(
            String label,
            SkyforgeNativeBiomePopulationRunner.Result result,
            ResourceKey<Biome> expectedBiome) {
        if (!result.biomeKey().equals(expectedBiome)) {
            throw new IllegalStateException(label + " proof volume resolved unexpected biome: "
                    + result.biomeKey().location());
        }
        if (result.attemptedFeatures() == 0) {
            throw new IllegalStateException(label + " proof biome exposed no vegetal decoration features");
        }
    }

    private static void requireAggregate(
            String label,
            Aggregate aggregate,
            ResourceKey<Biome> expectedBiome) {
        if (!aggregate.biomeKey().equals(expectedBiome)) {
            throw new IllegalStateException(label + " proof aggregate resolved unexpected biome: "
                    + aggregate.biomeKey().location());
        }
        if (aggregate.successfulFeatures == 0) {
            throw new IllegalStateException(label + " proof biome produced no successful native vegetation placements "
                    + "across " + aggregate.chunks + " eligible deterministic chunks; attempted="
                    + aggregate.attemptedFeatures + ", features=" + aggregate.featureKeys);
        }
        if (!aggregate.hasTreeEvidence()) {
            throw new IllegalStateException(label + " proof biome reported native feature success but produced no "
                    + "persistent visible tree evidence across " + aggregate.chunks
                    + " eligible chunks; logs=" + aggregate.logBlocks
                    + ", leaves=" + aggregate.leafBlocks
                    + ", attachments=" + aggregate.attachmentWrites
                    + ", successfulFeatures=" + aggregate.successfulFeatures
                    + ", features=" + aggregate.featureKeys);
        }
    }

    static ResourceKey<Biome> configuredBiomeKey(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        String value = identifier.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("configured biome key must not be blank");
        }
        try {
            return ResourceKey.create(Registries.BIOME, ResourceLocation.parse(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid configured biome key: " + identifier, exception);
        }
    }

    static SkyIslandWorldCatalog catalog() {
        long lowerSeed = ROOT_SEED ^ 0x464f52455354L;
        long upperSeed = ROOT_SEED ^ 0x5441494741L;
        var lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0054-biomes", 0, 0, lowerSeed);
        var upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0054-biomes", 0, 1, upperSeed);
        var lower = new SkyIslandWorldVolume(
                lowerId,
                new WorldBounds(-144.0, 144.0, 96.0, 168.0, -144.0, 144.0),
                compileTableland(lowerSeed, 136.0));
        var upper = new SkyIslandWorldVolume(
                upperId,
                new WorldBounds(-144.0, 144.0, 196.0, 268.0, -144.0, 144.0),
                compileTableland(upperSeed, 236.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    private static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    private static CompiledSkyIslandVolume compileTableland(long seed, double elevation) {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                elevation,
                112.0,
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

    private record SharedSurfaceSample(
            int x,
            int z,
            int lowerSurfaceY,
            int upperSurfaceY,
            int sharedColumns) {}

    private record VegetationEvidence(int logBlocks, int leafBlocks) {
        VegetationEvidence {
            if (logBlocks < 0 || leafBlocks < 0) {
                throw new IllegalArgumentException("vegetation evidence counts must be non-negative");
            }
        }
    }

    private static final class Aggregate {
        private ResourceKey<Biome> biomeKey;
        private int chunks;
        private int attemptedFeatures;
        private int successfulFeatures;
        private int attachmentWrites;
        private int logBlocks;
        private int leafBlocks;
        private int sharedColumns;
        private final Set<ResourceLocation> featureKeys = new LinkedHashSet<>();

        void add(
                SkyforgeNativeBiomePopulationRunner.Result result,
                VegetationEvidence vegetation,
                int chunkSharedColumns) {
            if (biomeKey == null) {
                biomeKey = result.biomeKey();
            } else if (!biomeKey.equals(result.biomeKey())) {
                throw new IllegalStateException("biome identity changed inside one SF-IMP-0054 proof aggregate");
            }
            chunks = Math.addExact(chunks, 1);
            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
            logBlocks = Math.addExact(logBlocks, vegetation.logBlocks());
            leafBlocks = Math.addExact(leafBlocks, vegetation.leafBlocks());
            sharedColumns = Math.addExact(sharedColumns, chunkSharedColumns);
            featureKeys.addAll(result.featureKeys());
        }

        boolean hasTreeEvidence() {
            return logBlocks > 0 && leafBlocks > 0;
        }

        ResourceKey<Biome> biomeKey() {
            if (biomeKey == null) {
                throw new IllegalStateException("empty SF-IMP-0054 proof aggregate");
            }
            return biomeKey;
        }
    }
}
