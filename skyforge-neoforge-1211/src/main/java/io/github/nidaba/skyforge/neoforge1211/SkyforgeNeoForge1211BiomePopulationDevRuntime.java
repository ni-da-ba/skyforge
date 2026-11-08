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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
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
    private static final int PROOF_RADIUS_CHUNKS = 8;
    private static final ChunkPos EXTERIOR_WITNESS_CHUNK = new ChunkPos(0, 40);
    private static final int MAXIMUM_PROVIDER_PROOF_CHUNKS = 8;
    private static final int MAXIMUM_CONTROL_PROOF_CHUNKS = 8;
    private static final int PROVIDER_PREFLIGHT_QUART_STEP = 4;
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
    private static final Set<Long> substantialTerrainProofChunks = new HashSet<>();
    private static final Set<Long> providerBiomeTargetProofChunks = new HashSet<>();
    private static final Set<Long> providerProofChunkKeys = new LinkedHashSet<>();
    private static final Set<Long> controlProofChunkKeys = new LinkedHashSet<>();
    private static final Map<String, Integer> providerBiomeLowerColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomeColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomeLowerNoiseSourceColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomeNoiseSourceColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomePreflightLowerDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomePreflightDistribution = new TreeMap<>();
    private static int providerBiomePreflightSurfaceSamples;
    private static int providerBiomePreflightLowerForestSourceSamples;
    private static int providerBiomePreflightTargetSamples;
    private static int providerBiomeUpperSourceMismatches;
    private static String providerBiomeSourceClass = "not-sampled";
    private static List<String> providerBiomeSourcePossibleBiomes = List.of();
    private static final Set<Long> eligibleProofChunks = new HashSet<>();
    private static final Aggregate lowerAggregate = new Aggregate();
    private static final Aggregate upperAggregate = new Aggregate();
    private static AutoCloseable persistentBinding;
    private static boolean proofComplete;
    private static boolean acceptanceCompletionRequested;
    private static boolean providerWarmupPrepared;
    private static BlockPos providerReviewSpawn;
    private static ResourceKey<Biome> providerBiomeSampleKey;

    /**
     * Finds provider-biome surface candidates before the acceptance harness warms any chunks.
     * The source-only scan is limited to exact shared island surfaces, then warms only a few
     * deterministic matching chunks instead of generating the entire island candidate rectangle.
     */
    @SubscribeEvent
    static synchronized void prepareProviderAcceptanceWarmup(ServerTickEvent.Pre event) {
        if (!enabled()
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || !Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                || providerWarmupPrepared) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(), "SF-IMP-0054 provider-biome preflight has no Overworld");
            return;
        }

        try {
            prepareProviderBiomeCandidates(level);
            if (controlProofChunkKeys.isEmpty() || providerProofChunkKeys.isEmpty() || providerReviewSpawn == null) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        event.getServer(),
                        "SF-IMP-0054 provider-biome preflight found no selected upper provider biome on a substantial "
                                + "side-by-side island surface: lower=" + Biomes.FOREST.location()
                                + ", upper=" + UPPER_BIOME.location()
                                + ", sampledSurfacePoints=" + providerBiomePreflightSurfaceSamples
                                + ", lowerForestSurfacePoints=" + providerBiomePreflightLowerForestSourceSamples
                                + ", upperTargetSurfacePoints=" + providerBiomePreflightTargetSamples
                                + ", lowerSourceDistribution=" + providerBiomePreflightLowerDistribution
                                + ", upperSourceDistribution=" + providerBiomePreflightDistribution
                                + ", providerBiomeSourceClass=" + providerBiomeSourceClass
                                + ", providerBiomeSourcePossibleBiomes=" + providerBiomeSourcePossibleBiomes);
                return;
            }

            Set<Long> warmupChunkKeys = new LinkedHashSet<>(controlProofChunkKeys);
            warmupChunkKeys.addAll(providerProofChunkKeys);
            warmupChunkKeys.add(EXTERIOR_WITNESS_CHUNK.toLong());
            SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(warmupChunkKeys);
            level.setDefaultSpawnPos(providerReviewSpawn, 0.0F);
            providerWarmupPrepared = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-0054 provider-biome preflight selected " + providerProofChunkKeys.size()
                            + " exact-volume proof chunks for " + UPPER_BIOME.location()
                            + "; sharedSurfaceSamples=" + providerBiomePreflightSurfaceSamples
                            + ", lowerForestSurfaceSamples=" + providerBiomePreflightLowerForestSourceSamples
                            + ", upperTargetSurfaceSamples=" + providerBiomePreflightTargetSamples
                            + ", lowerSourceDistribution=" + providerBiomePreflightLowerDistribution
                            + ", upperSourceDistribution=" + providerBiomePreflightDistribution
                            + ", proofChunks=" + providerProofChunkKeys
                            + ", reviewSpawn=" + providerReviewSpawn.toShortString()
                            + ". The harness will warm only these proof chunks plus the exterior witness.");
        } catch (RuntimeException failure) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0054 provider-biome source preflight failed: "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

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
        if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                && SkyforgeAutomatedAcceptanceHarness.warmupComplete()) {
            populateUnscannedProviderProofChunks(event.getServer().overworld());
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
                Map.ofEntries(
                        Map.entry("selectedUpperBiome", UPPER_BIOME.location()),
                        Map.entry("providerBiomeSource", providerBiomeSampleKey == null
                                ? "disabled" : providerBiomeSampleKey.location()),
                        Map.entry("providerBiomeMatchesPopulation", providerBiomeSampleKey != null
                                && providerBiomeSampleKey.equals(upperAggregate.biomeKey())),
                        Map.entry("providerBiomeLowerColumnDistribution", providerBiomeLowerColumnDistribution.toString()),
                        Map.entry("providerBiomeColumnDistribution", providerBiomeColumnDistribution.toString()),
                        Map.entry("providerBiomeLowerNoiseSourceColumnDistribution", providerBiomeLowerNoiseSourceColumnDistribution.toString()),
                        Map.entry("providerBiomeNoiseSourceColumnDistribution", providerBiomeNoiseSourceColumnDistribution.toString()),
                        Map.entry("providerBiomeUpperSourceMismatches", providerBiomeUpperSourceMismatches),
                        Map.entry("providerBiomeSourceClass", providerBiomeSourceClass),
                        Map.entry("providerBiomeSourcePossibleBiomes", providerBiomeSourcePossibleBiomes.toString()),
                        Map.entry("providerBiomePreflightSurfaceSamples", providerBiomePreflightSurfaceSamples),
                        Map.entry("providerBiomePreflightLowerForestSourceSamples", providerBiomePreflightLowerForestSourceSamples),
                        Map.entry("providerBiomePreflightTargetSamples", providerBiomePreflightTargetSamples),
                        Map.entry("providerBiomePreflightLowerDistribution", providerBiomePreflightLowerDistribution.toString()),
                        Map.entry("providerBiomePreflightDistribution", providerBiomePreflightDistribution.toString()),
                        Map.entry("providerBiomeProofChunks", providerProofChunkKeys.size()),
                        Map.entry("providerReviewSpawn", providerReviewSpawn == null
                                ? "disabled" : providerReviewSpawn.toShortString()),
                        Map.entry("scannedChunks", scannedProofChunks.size()),
                        Map.entry("substantialTerrainChunks", substantialTerrainProofChunks.size()),
                        Map.entry("providerBiomeTargetChunks", providerBiomeTargetProofChunks.size()),
                        Map.entry("eligibleChunks", eligibleProofChunks.size()),
                        Map.entry("lowerBiome", lowerAggregate.biomeKey().location()),
                        Map.entry("lowerSuccessfulFeatures", lowerAggregate.successfulFeatures),
                        Map.entry("lowerWritePreflightRejections", lowerAggregate.writePreflightRejections),
                        Map.entry("lowerWriteDecisionRejections", lowerAggregate.writeDecisionRejections),
                        Map.entry("lowerPersistentTreeEvidence", lowerAggregate.hasTreeEvidence()),
                        Map.entry("upperSuccessfulFeatures", upperAggregate.successfulFeatures),
                        Map.entry("upperWritePreflightRejections", upperAggregate.writePreflightRejections),
                        Map.entry("upperWriteDecisionRejections", upperAggregate.writeDecisionRejections),
                        Map.entry("upperPersistentTreeEvidence", upperAggregate.hasTreeEvidence()),
                        Map.entry("exteriorWitnessChunk", EXTERIOR_WITNESS_CHUNK),
                        Map.entry("exteriorNonAirBlocks", exteriorNonAirBlocks)));
    }

    /**
     * Reopen verification reads the persisted proof footprint only. It deliberately does not invoke
     * the native population runner, so a same-world boot cannot reapply generation over saved edits.
     */
    private static void completePersistedReopenAcceptance(ServerLevel level) {
        if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            completeSideBySideReopenAcceptance(level);
            return;
        }
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

        List<Long> proofChunkKeys = proofChunkKeysForReopen();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (long chunkKey : proofChunkKeys) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        level.getServer(),
                        "SF-IMP-0054 reopen proof chunk was not loaded: " + chunkPos);
                return;
            }
            scannedChunks++;
            Optional<SharedSurfaceSample> sample;
            if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
                var search = searchSharedSurface(level, lowerId, upperId, chunkPos, true, generator);
                sample = search.selected().map(selected -> new SharedSurfaceSample(
                        selected.x(), selected.z(), selected.lowerSurfaceY(), selected.upperSurfaceY(),
                        search.sharedColumns(), selected.upperProviderBiome()));
            } else {
                sample = sharedSurfaceSample(level, lowerId, upperId, chunkPos);
            }
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

        boolean lowerTreeEvidence = lowerLogBlocks > 0 && lowerLeafBlocks > 0;
        boolean upperTreeEvidence = upperLogBlocks > 0 && upperLeafBlocks > 0;
        int minimumEligibleChunks = Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                ? PROVIDER_MODE_MINIMUM_ELIGIBLE_CHUNKS
                : MINIMUM_ELIGIBLE_PROOF_CHUNKS;
        if (eligibleChunks < minimumEligibleChunks || !lowerTreeEvidence || !upperTreeEvidence) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "SF-IMP-0054 reopen lost persisted native tree evidence: eligible="
                            + eligibleChunks + ", required=" + minimumEligibleChunks + ", lowerLogs=" + lowerLogBlocks
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
                        Map.entry("providerBiomeProofChunks", providerProofChunkKeys.size()),
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

    private static List<Long> proofChunkKeysForReopen() {
        if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            Set<Long> keys = new LinkedHashSet<>(controlProofChunkKeys);
            keys.addAll(providerProofChunkKeys);
            return List.copyOf(keys);
        }
        List<Long> keys = new ArrayList<>(CANDIDATE_PROOF_CHUNKS);
        for (int x = -PROOF_RADIUS_CHUNKS; x <= PROOF_RADIUS_CHUNKS; x++) {
            for (int z = -PROOF_RADIUS_CHUNKS; z <= PROOF_RADIUS_CHUNKS; z++) keys.add(new ChunkPos(x, z).toLong());
        }
        return List.copyOf(keys);
    }

    /**
     * Some proof chunks can be generated while Minecraft is preparing its initial spawn, before
     * the provider preflight has installed the finite proof-key set. Once the harness has warmed
     * and ticketed the selected footprint, route only those still-unscanned chunks through the
     * same exact-volume population path. This avoids silently omitting the control island when it
     * overlaps the world's initial spawn region.
     */
    private static void populateUnscannedProviderProofChunks(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int recovered = 0;
        List<Long> proofKeys = new ArrayList<>(controlProofChunkKeys);
        proofKeys.addAll(providerProofChunkKeys);
        for (long key : proofKeys) {
            if (scannedProofChunks.contains(key)) continue;
            ChunkPos pos = new ChunkPos(key);
            ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                throw new IllegalStateException("warmed side-by-side proof chunk is not loaded: " + pos);
            }
            populateProviderSideBySide(level, chunk, generator);
            recovered++;
        }
        if (recovered > 0) {
            LOGGER.log(System.Logger.Level.INFO,
                    "SF-IMP-0054 recovered " + recovered
                            + " side-by-side proof chunks generated before proof selection was installed.");
        }
    }

    private static void populateProviderSideBySide(WorldGenLevel level, ChunkAccess chunk, ChunkGenerator generator) {
        ChunkPos pos = chunk.getPos();
        long key = pos.toLong();
        boolean control = controlProofChunkKeys.contains(key);
        boolean provider = providerProofChunkKeys.contains(key);
        if (!control && !provider) return;
        if (control && provider) throw new IllegalStateException("one proof chunk belongs to both side-by-side islands");
        if (!scannedProofChunks.add(key)) return;
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("side-by-side biome proof ran without its Skyforge binding");
        }
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        SkyIslandWorldVolume volume = volumes.get(provider ? 1 : 0);
        var sample = findSideBySideSurfaceSample(level, volume.id(), pos, provider, generator);
        if (sample.isEmpty()) {
            evaluateCompletion(volumes.get(0).id(), volumes.get(1).id());
            return;
        }
        SideBySideSurfaceSample s = sample.orElseThrow();
        SkyforgeExactVolumeBiomeResolver resolver = providerBackedProofResolver(volumes.get(0).id(), volumes.get(1).id());
        var result = SkyforgeNativeBiomePopulationRunner.populateStep(
                level, generator, resolver, volume.id(), pos, new BlockPos(s.x(), s.surfaceY(), s.z()),
                GenerationStep.Decoration.VEGETAL_DECORATION, MAXIMUM_ATTACHMENT_DEPTH);
        requireChunkResult(provider ? "provider" : "control", result, provider ? UPPER_BIOME : Biomes.FOREST);
        if (provider) {
            providerBiomeSampleKey = s.providerBiome();
            providerBiomeTargetProofChunks.add(key);
        }
        eligibleProofChunks.add(key);
        substantialTerrainProofChunks.add(key);
        VegetationEvidence evidence = scanVegetation(level, pos, s.surfaceY());
        (provider ? upperAggregate : lowerAggregate).add(result, evidence, s.ownedSurfaceColumns());
        evaluateCompletion(volumes.get(0).id(), volumes.get(1).id());
    }

    private static Optional<SideBySideSurfaceSample> findSideBySideSurfaceSample(
            WorldGenLevel level, SkyIslandWorldVolumeId volumeId, ChunkPos pos,
            boolean requireProvider, ChunkGenerator generator) {
        int owned = 0, surfaceY = -1;
        BlockPos.MutableBlockPos center =
                new BlockPos.MutableBlockPos(pos.getMiddleBlockX(), level.getMinBuildHeight(), pos.getMiddleBlockZ());
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId, x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(), level.getHeight());
                if (claim.isPresent()) {
                    owned++;
                    if (x == center.getX() && z == center.getZ()) surfaceY = claim.orElseThrow().height();
                }
            }
        }
        if (owned < MINIMUM_SHARED_COLUMNS_PER_CHUNK || surfaceY < 0) return Optional.empty();
        center.setY(surfaceY);
        if (!Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, center)) return Optional.empty();
        if (!requireProvider) return Optional.of(
                new SideBySideSurfaceSample(center.getX(), center.getZ(), surfaceY, owned, null));
        if (generator == null) throw new IllegalArgumentException("provider check requires the active generator");
        ServerLevel server = serverLevel(level);
        ResourceKey<Biome> actual = level.getBiome(center).unwrapKey().orElseThrow(
                () -> new IllegalStateException("unregistered biome at provider island surface"));
        ResourceKey<Biome> source = generator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(center.getX()), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(center.getZ()),
                        server.getChunkSource().randomState().sampler())
                .unwrapKey().orElseThrow(() -> new IllegalStateException("unregistered provider biome source key"));
        providerBiomeColumnDistribution.merge(actual.location().toString(), 1, Math::addExact);
        providerBiomeNoiseSourceColumnDistribution.merge(source.location().toString(), 1, Math::addExact);
        if (!actual.equals(source)) providerBiomeUpperSourceMismatches =
                Math.addExact(providerBiomeUpperSourceMismatches, 1);
        if (!UPPER_BIOME.equals(actual) || !UPPER_BIOME.equals(source)) return Optional.empty();
        return Optional.of(new SideBySideSurfaceSample(center.getX(), center.getZ(), surfaceY, owned, actual));
    }

    private static void completeSideBySideReopenAcceptance(ServerLevel level) {
        int scanned = 0, controlLogs = 0, controlLeaves = 0, providerLogs = 0, providerLeaves = 0;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        List<Long> keys = proofChunkKeysForReopen();
        for (long key : keys) {
            ChunkPos pos = new ChunkPos(key);
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                SkyforgeAutomatedAcceptanceHarness.fail(level.getServer(),
                        "side-by-side reopen proof chunk was not loaded: " + pos);
                return;
            }
            scanned++;
            boolean provider = providerProofChunkKeys.contains(key);
            SkyIslandWorldVolumeId id = catalog().volumes().get(provider ? 1 : 0).id();
            var sample = findSideBySideSurfaceSample(level, id, pos, provider, generator);
            if (sample.isEmpty()) {
                SkyforgeAutomatedAcceptanceHarness.fail(level.getServer(),
                        "side-by-side reopen surface witness missing in " + pos);
                return;
            }
            VegetationEvidence e = scanVegetation(level, pos, sample.orElseThrow().surfaceY());
            if (provider) { providerLogs += e.logBlocks(); providerLeaves += e.leafBlocks(); }
            else { controlLogs += e.logBlocks(); controlLeaves += e.leafBlocks(); }
        }
        boolean controlTrees = controlLogs > 0 && controlLeaves > 0;
        boolean providerTrees = providerLogs > 0 && providerLeaves > 0;
        if (scanned != keys.size() || !controlTrees || !providerTrees) {
            SkyforgeAutomatedAcceptanceHarness.fail(level.getServer(),
                    "side-by-side reopen lost vegetation: scanned=" + scanned + "/" + keys.size()
                            + ", controlLogs=" + controlLogs + ", controlLeaves=" + controlLeaves
                            + ", providerLogs=" + providerLogs + ", providerLeaves=" + providerLeaves);
            return;
        }
        int exterior = countExteriorWitnessNonAirBlocks(level);
        if (exterior != 0) {
            SkyforgeAutomatedAcceptanceHarness.fail(level.getServer(),
                    "exterior void witness contained " + exterior + " non-air blocks after reopen");
            return;
        }
        acceptanceCompletionRequested = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(level.getServer(), Map.ofEntries(
                Map.entry("selectedUpperBiome", UPPER_BIOME.location()),
                Map.entry("providerBiomeMatchesPopulation", UPPER_BIOME.equals(upperAggregate.biomeKey())),
                Map.entry("scannedChunks", scanned),
                Map.entry("eligibleChunks", eligibleProofChunks.size()),
                Map.entry("providerBiomeProofChunks", providerProofChunkKeys.size()),
                Map.entry("lowerPersistentTreeEvidence", controlTrees),
                Map.entry("upperPersistentTreeEvidence", providerTrees),
                Map.entry("lowerLogBlocks", controlLogs),
                Map.entry("lowerLeafBlocks", controlLeaves),
                Map.entry("upperLogBlocks", providerLogs),
                Map.entry("upperLeafBlocks", providerLeaves),
                Map.entry("exteriorNonAirBlocks", exterior)));
    }

    private record SideBySideSurfaceSample(
            int x, int z, int surfaceY, int ownedSurfaceColumns, ResourceKey<Biome> providerBiome) {}

    private static void prepareProviderBiomeCandidates(ServerLevel level) {
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        if (volumes.size() != 2) throw new IllegalStateException("provider preflight requires two side-by-side volumes");
        SkyIslandWorldVolume control = volumes.get(0), provider = volumes.get(1);
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        providerBiomeSourceClass = source.getClass().getName();
        providerBiomeSourcePossibleBiomes = source.possibleBiomes().stream()
                .map(h -> h.unwrapKey().map(k -> k.location().toString()).orElse("unregistered"))
                .sorted().toList();
        Map<Long, BlockPos> controls = new TreeMap<>();
        Map<Long, BlockPos> targets = new TreeMap<>();
        scanProviderCandidates(level, control.bounds(), control.id(), false, source, sampler, controls);
        scanProviderCandidates(level, provider.bounds(), provider.id(), true, source, sampler, targets);
        List<Long> controlKeys = orderCandidateChunks(controls, 8, 8);
        List<Long> targetKeys = orderCandidateChunks(targets, 344, 8);
        controlProofChunkKeys.clear();
        providerProofChunkKeys.clear();
        controlKeys.stream().limit(MAXIMUM_CONTROL_PROOF_CHUNKS).forEach(controlProofChunkKeys::add);
        targetKeys.stream().limit(MAXIMUM_PROVIDER_PROOF_CHUNKS).forEach(key -> {
            providerProofChunkKeys.add(key);
            if (providerReviewSpawn == null) providerReviewSpawn = targets.get(key);
        });
    }

    private static void scanProviderCandidates(
            ServerLevel level, WorldBounds bounds, SkyIslandWorldVolumeId volumeId, boolean providerTarget,
            net.minecraft.world.level.biome.BiomeSource source,
            net.minecraft.world.level.biome.Climate.Sampler sampler, Map<Long, BlockPos> candidates) {
        int firstX = Math.floorDiv((int)Math.ceil(bounds.minimumX()), PROVIDER_PREFLIGHT_QUART_STEP)
                * PROVIDER_PREFLIGHT_QUART_STEP;
        int firstZ = Math.floorDiv((int)Math.ceil(bounds.minimumZ()), PROVIDER_PREFLIGHT_QUART_STEP)
                * PROVIDER_PREFLIGHT_QUART_STEP;
        for (int x = firstX; x <= bounds.maximumX(); x += PROVIDER_PREFLIGHT_QUART_STEP) {
            for (int z = firstZ; z <= bounds.maximumZ(); z += PROVIDER_PREFLIGHT_QUART_STEP) {
                var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        volumeId, x, z, Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(), level.getHeight());
                if (claim.isEmpty()) continue;
                int y = claim.orElseThrow().height();
                providerBiomePreflightSurfaceSamples = Math.addExact(providerBiomePreflightSurfaceSamples, 1);
                ResourceKey<Biome> key = source.getNoiseBiome(
                                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler)
                        .unwrapKey().orElseThrow(() -> new IllegalStateException(
                                "biome source returned an unregistered side-by-side surface biome"));
                (providerTarget ? providerBiomePreflightDistribution : providerBiomePreflightLowerDistribution)
                        .merge(key.location().toString(), 1, Math::addExact);
                if (!providerTarget && Biomes.FOREST.equals(key)) {
                    providerBiomePreflightLowerForestSourceSamples =
                            Math.addExact(providerBiomePreflightLowerForestSourceSamples, 1);
                }
                if (providerTarget && UPPER_BIOME.equals(key)) {
                    providerBiomePreflightTargetSamples = Math.addExact(providerBiomePreflightTargetSamples, 1);
                }
                ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
                if (x != chunk.getMiddleBlockX() || z != chunk.getMiddleBlockZ()) continue;
                if (providerTarget && !UPPER_BIOME.equals(key)) continue;
                BlockPos sample = new BlockPos(x, y, z);
                candidates.putIfAbsent(chunk.toLong(), sample);
            }
        }
    }

    private static List<Long> orderCandidateChunks(Map<Long, BlockPos> candidates, int centerX, int centerZ) {
        return candidates.keySet().stream().sorted(Comparator
                .comparingInt((Long key) -> Math.abs(ChunkPos.getX(key) * 16 + 8 - centerX)
                        + Math.abs(ChunkPos.getZ(key) * 16 + 8 - centerZ))
                .thenComparingInt(key -> ChunkPos.getX(key)).thenComparingInt(key -> ChunkPos.getZ(key))).toList();
    }

    private static int surfaceSpawnPreference(BlockPos left, BlockPos right) {
        int leftDistance = Math.abs(left.getX()) + Math.abs(left.getZ());
        int rightDistance = Math.abs(right.getX()) + Math.abs(right.getZ());
        int distance = Integer.compare(leftDistance, rightDistance);
        if (distance != 0) {
            return distance;
        }
        int x = Integer.compare(left.getX(), right.getX());
        return x != 0 ? x : Integer.compare(left.getZ(), right.getZ());
    }

    private static int countSharedSurfaceColumns(
            ServerLevel level,
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId,
            ChunkPos chunkPos) {
        int sharedColumns = 0;
        for (int worldX = chunkPos.getMinBlockX(); worldX <= chunkPos.getMaxBlockX(); worldX++) {
            for (int worldZ = chunkPos.getMinBlockZ(); worldZ <= chunkPos.getMaxBlockZ(); worldZ++) {
                var lowerClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        lowerId,
                        worldX,
                        worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (lowerClaim.isEmpty()) {
                    continue;
                }
                var upperClaim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                        upperId,
                        worldX,
                        worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level.getMinBuildHeight(),
                        level.getHeight());
                if (upperClaim.isPresent()) {
                    sharedColumns = Math.addExact(sharedColumns, 1);
                }
            }
        }
        return sharedColumns;
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
        if (SkyforgeAutomatedAcceptanceHarness.serverMode()
                && !Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            installServerAcceptanceWarmupChunks();
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0054 exact-volume biome specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and inspect the two vertically aligned tableland islands near "
                        + "x=" + PROOF_X + ", z=" + PROOF_Z
                        + ". Lower resolves minecraft:forest; upper resolves " + UPPER_BIOME.location() + ". A deterministic "
                        + (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                                ? "a source-only locator selects up to " + MAXIMUM_PROVIDER_PROOF_CHUNKS
                                        + " provider-biome chunks over the exact shared island surface"
                                : (PROOF_RADIUS_CHUNKS * 2 + 1) + "x" + (PROOF_RADIUS_CHUNKS * 2 + 1)
                                        + " candidate patch is scanned")
                        + ". A chunk is eligible only when at least " + MINIMUM_SHARED_COLUMNS_PER_CHUNK
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
        boolean providerMode = Boolean.getBoolean(PROVIDER_BIOME_PROPERTY);
        if (providerMode
                && SkyforgeAutomatedAcceptanceHarness.serverMode()
                && !providerWarmupPrepared) {
            return;
        }
        if (providerMode) {
            if (SkyforgeAutomatedAcceptanceHarness.serverMode() && !providerWarmupPrepared) return;
            populateProviderSideBySide(level, chunk, generator);
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        boolean selectedProofChunk = providerMode
                ? providerProofChunkKeys.contains(chunkPos.toLong())
                : isProofChunk(chunkPos);
        if (!selectedProofChunk || !scannedProofChunks.add(chunkPos.toLong())) {
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

        if (providerMode && "not-sampled".equals(providerBiomeSourceClass)) {
            var biomeSource = generator.getBiomeSource();
            providerBiomeSourceClass = biomeSource.getClass().getName();
            providerBiomeSourcePossibleBiomes = biomeSource.possibleBiomes().stream()
                    .map(holder -> holder.unwrapKey()
                            .map(key -> key.location().toString())
                            .orElse("unregistered"))
                    .sorted()
                    .toList();
        }
        SkyforgeSharedSurfaceBiomeSampleSelector.SearchResult search =
                searchSharedSurface(level, lowerId, upperId, chunkPos, providerMode, generator);
        if (search.sharedColumns() >= MINIMUM_SHARED_COLUMNS_PER_CHUNK) {
            substantialTerrainProofChunks.add(chunkPos.toLong());
            if (providerMode && search.providerBiomeMatches() > 0) {
                providerBiomeTargetProofChunks.add(chunkPos.toLong());
            }
        }
        if (search.selected().isEmpty()) {
            evaluateCompletion(lowerId, upperId);
            return;
        }
        SkyforgeSharedSurfaceBiomeSampleSelector.Column selected = search.selected().orElseThrow();
        if (providerMode && !selected.lowerTreePlantingSupport()) {
            // Do not aggregate a provider-biome sample whose deterministic native tree location
            // is bare stone; it cannot prove biome-driven vegetation placement.
            evaluateCompletion(lowerId, upperId);
            return;
        }
        SharedSurfaceSample shared = new SharedSurfaceSample(
                selected.x(), selected.z(), selected.lowerSurfaceY(), selected.upperSurfaceY(),
                search.sharedColumns(), selected.upperProviderBiome());
        ResourceKey<Biome> providerUpperBiome = null;
        if (providerMode) {
            providerUpperBiome = shared.providerBiome();
            if (!UPPER_BIOME.equals(providerUpperBiome)) {
                throw new IllegalStateException("provider biome sample selector returned an unexpected biome: "
                        + providerUpperBiome);
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
                new BlockPos(shared.x(), shared.lowerSurfaceY(), shared.z()),
                GenerationStep.Decoration.VEGETAL_DECORATION,
                MAXIMUM_ATTACHMENT_DEPTH);
        var upper = SkyforgeNativeBiomePopulationRunner.populateStep(
                level,
                generator,
                resolver,
                upperId,
                chunkPos,
                new BlockPos(shared.x(), shared.upperSurfaceY(), shared.z()),
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
        if (enoughEligibleChunks && visibleTrees
                && (!Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                        || scannedProofChunks.size() >= providerProofChunkKeys.size() + controlProofChunkKeys.size())) {
            completeProof(lowerId, upperId);
            return;
        }
        int expectedCandidateChunks = Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                ? providerProofChunkKeys.size() + controlProofChunkKeys.size()
                : CANDIDATE_PROOF_CHUNKS;
        if (scannedProofChunks.size() < expectedCandidateChunks) {
            return;
        }
        if (!enoughEligibleChunks) {
            throw new IllegalStateException("SF-IMP-0054 candidate region exposed too little substantial shared terrain/provider biome: "
                    + "eligible=" + eligibleProofChunks.size()
                    + ", required=" + minimumEligibleProofChunks()
                    + ", scanned=" + scannedProofChunks.size()
                    + ", substantialTerrainChunks=" + substantialTerrainProofChunks.size()
                    + ", providerBiomeTargetChunks=" + providerBiomeTargetProofChunks.size()
                    + ", targetProviderBiome=" + (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                            ? UPPER_BIOME.location() : "disabled")
                    + ", minimumSharedColumnsPerChunk=" + MINIMUM_SHARED_COLUMNS_PER_CHUNK
                    + ", providerBiomeLowerColumnDistribution=" + providerBiomeLowerColumnDistribution
                    + ", providerBiomeColumnDistribution=" + providerBiomeColumnDistribution
                    + ", providerBiomeLowerNoiseSourceColumnDistribution=" + providerBiomeLowerNoiseSourceColumnDistribution
                    + ", providerBiomeNoiseSourceColumnDistribution=" + providerBiomeNoiseSourceColumnDistribution
                    + ", providerBiomeUpperSourceMismatches=" + providerBiomeUpperSourceMismatches
                    + ", providerBiomeSourceClass=" + providerBiomeSourceClass
                    + ", providerBiomeSourcePossibleBiomes=" + providerBiomeSourcePossibleBiomes);
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate, UPPER_BIOME);
        completeProof(lowerId, upperId);
    }

    private static int minimumEligibleProofChunks() {
        return Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                ? 2
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
                        + ", writePreflightRejections=" + lowerAggregate.writePreflightRejections
                        + ", writeDecisionRejections=" + lowerAggregate.writeDecisionRejections
                        + ", logs=" + lowerAggregate.logBlocks
                        + ", leaves=" + lowerAggregate.leafBlocks
                        + ", sharedColumns=" + lowerAggregate.sharedColumns
                        + "}, upper={volume=" + upperId.path()
                        + ", biome=" + upperAggregate.biomeKey().location()
                        + ", attempted=" + upperAggregate.attemptedFeatures
                        + ", successful=" + upperAggregate.successfulFeatures
                        + ", attachments=" + upperAggregate.attachmentWrites
                        + ", writePreflightRejections=" + upperAggregate.writePreflightRejections
                        + ", writeDecisionRejections=" + upperAggregate.writeDecisionRejections
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
        SkyforgeSharedSurfaceBiomeSampleSelector.SearchResult search =
                searchSharedSurface(level, lowerId, upperId, chunkPos, false, null);
        return search.selected().map(selected -> new SharedSurfaceSample(
                selected.x(), selected.z(), selected.lowerSurfaceY(), selected.upperSurfaceY(),
                search.sharedColumns(), selected.upperProviderBiome()));
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel serverLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        if (level instanceof WorldGenRegion region) {
            return region.getLevel();
        }
        throw new IllegalStateException(
                "provider biome source diagnostics require ServerLevel or WorldGenRegion");
    }

    private static SkyforgeSharedSurfaceBiomeSampleSelector.SearchResult searchSharedSurface(
            WorldGenLevel level,
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId,
            ChunkPos chunkPos,
            boolean requireProviderBiome,
            ChunkGenerator generator) {
        net.minecraft.world.level.biome.BiomeSource providerBiomeSource = null;
        net.minecraft.world.level.biome.Climate.Sampler providerBiomeSampler = null;
        if (requireProviderBiome) {
            if (generator == null) {
                throw new IllegalArgumentException("provider biome source diagnostics require the active generator");
            }
            ServerLevel serverLevel = serverLevel(level);
            providerBiomeSource = generator.getBiomeSource();
            providerBiomeSampler = serverLevel.getChunkSource().randomState().sampler();
        }
        List<SkyforgeSharedSurfaceBiomeSampleSelector.Column> sharedColumns = new ArrayList<>(256);
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

                int lowerSurfaceY = lowerClaim.orElseThrow().height();
                int upperSurfaceY = upperClaim.orElseThrow().height();
                if (lowerSurfaceY == upperSurfaceY) {
                    throw new IllegalStateException("SF-IMP-0054 stacked biome proof resolved one shared surface at x="
                            + x + ", z=" + z + ", chunk=" + chunkPos);
                }
                ResourceKey<Biome> lowerProviderBiome = null;
                ResourceKey<Biome> providerBiome = null;
                if (requireProviderBiome) {
                    lowerProviderBiome = level.getBiome(new BlockPos(x, lowerSurfaceY, z))
                            .unwrapKey()
                            .orElseThrow(() -> new IllegalStateException(
                                    "provider returned an unregistered biome at an admitted lower surface sample"));
                    providerBiome = level.getBiome(new BlockPos(x, upperSurfaceY, z))
                            .unwrapKey()
                            .orElseThrow(() -> new IllegalStateException(
                                    "provider returned an unregistered biome at an admitted upper surface sample"));
                    providerBiomeLowerColumnDistribution.merge(
                            lowerProviderBiome.location().toString(), 1, Math::addExact);
                    providerBiomeColumnDistribution.merge(
                            providerBiome.location().toString(), 1, Math::addExact);
                    ResourceKey<Biome> lowerSourceBiome = providerBiomeSource.getNoiseBiome(
                                    QuartPos.fromBlock(x),
                                    QuartPos.fromBlock(lowerSurfaceY),
                                    QuartPos.fromBlock(z),
                                    providerBiomeSampler)
                            .unwrapKey()
                            .orElseThrow(() -> new IllegalStateException(
                                    "active biome source returned an unregistered biome at an admitted lower surface sample"));
                    ResourceKey<Biome> sourceBiome = providerBiomeSource.getNoiseBiome(
                                    QuartPos.fromBlock(x),
                                    QuartPos.fromBlock(upperSurfaceY),
                                    QuartPos.fromBlock(z),
                                    providerBiomeSampler)
                            .unwrapKey()
                            .orElseThrow(() -> new IllegalStateException(
                                    "active biome source returned an unregistered biome at an admitted upper surface sample"));
                    providerBiomeLowerNoiseSourceColumnDistribution.merge(
                            lowerSourceBiome.location().toString(), 1, Math::addExact);
                    providerBiomeNoiseSourceColumnDistribution.merge(
                            sourceBiome.location().toString(), 1, Math::addExact);
                    if (!providerBiome.equals(sourceBiome)) {
                        providerBiomeUpperSourceMismatches = Math.addExact(providerBiomeUpperSourceMismatches, 1);
                    }
                }
                // The deterministic provider fixture's native tree probe lands at the chunk center.
                // Require its actual lower placement column to support a sapling; support elsewhere
                // in the chunk does not make the tree witness representative.
                boolean lowerTreePlantingSupport = x == chunkPos.getMiddleBlockX()
                        && z == chunkPos.getMiddleBlockZ()
                        && Blocks.OAK_SAPLING.defaultBlockState().canSurvive(
                                level, new BlockPos(x, lowerSurfaceY, z));
                sharedColumns.add(new SkyforgeSharedSurfaceBiomeSampleSelector.Column(
                        x,
                        z,
                        lowerSurfaceY,
                        upperSurfaceY,
                        lowerProviderBiome,
                        providerBiome,
                        lowerTreePlantingSupport));
            }
        }
        return SkyforgeSharedSurfaceBiomeSampleSelector.select(
                chunkPos,
                sharedColumns,
                MINIMUM_SHARED_COLUMNS_PER_CHUNK,
                null,
                requireProviderBiome ? UPPER_BIOME : null);
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
                    + aggregate.attemptedFeatures
                    + ", attachmentWrites=" + aggregate.attachmentWrites
                    + ", writePreflightRejections=" + aggregate.writePreflightRejections
                    + ", writeDecisionRejections=" + aggregate.writeDecisionRejections
                    + ", featureResults=" + aggregate.featureOutcomeEvidence
                    + ", writePreflightEvidence=" + SkyforgePopulationExecutionStage.writePreflightRejectionEvidence()
                    + ", features=" + aggregate.featureKeys);
        }
        if (!aggregate.hasTreeEvidence()) {
            throw new IllegalStateException(label + " proof biome reported native feature success but produced no "
                    + "persistent visible tree evidence across " + aggregate.chunks
                    + " eligible chunks; logs=" + aggregate.logBlocks
                    + ", leaves=" + aggregate.leafBlocks
                    + ", attachments=" + aggregate.attachmentWrites
                    + ", successfulFeatures=" + aggregate.successfulFeatures
                    + ", featureResults=" + aggregate.featureOutcomeEvidence
                    + ", writePreflightEvidence="
                    + SkyforgePopulationExecutionStage.writePreflightRejectionEvidence()
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
        if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            // Provider-biome comparison: same-height side-by-side islands separated by a void gap.
            var control = new SkyIslandWorldVolume(lowerId,
                    new WorldBounds(-136.0, 152.0, 196.0, 284.0, -136.0, 152.0),
                    compileTableland(lowerSeed, 8.0, 8.0, 236.0, 144.0, 40.0));
            var provider = new SkyIslandWorldVolume(upperId,
                    new WorldBounds(200.0, 488.0, 196.0, 284.0, -136.0, 152.0),
                    compileTableland(upperSeed, 344.0, 8.0, 236.0, 144.0, 40.0));
            return new SkyIslandWorldCatalog(ROOT_SEED, List.of(control, provider));
        }
        var lower = new SkyIslandWorldVolume(lowerId,
                new WorldBounds(-144.0, 144.0, 96.0, 184.0, -144.0, 144.0),
                compileTableland(lowerSeed, 0.0, 0.0, 136.0, 144.0, 40.0));
        var upper = new SkyIslandWorldVolume(upperId,
                new WorldBounds(-144.0, 144.0, 196.0, 284.0, -144.0, 144.0),
                compileTableland(upperSeed, 0.0, 0.0, 236.0, 144.0, 40.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    private static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    private static CompiledSkyIslandVolume compileTableland(
            long seed, double centerX, double centerZ, double elevation, double nominalRadius, double upperElevation) {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                centerX,
                centerZ,
                elevation,
                nominalRadius,
                upperElevation,
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
            int sharedColumns,
            ResourceKey<Biome> providerBiome) {}

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
        private int writePreflightRejections;
        private int writeDecisionRejections;
        private int sharedColumns;
        private final Set<ResourceLocation> featureKeys = new LinkedHashSet<>();
        private final List<String> featureOutcomeEvidence = new ArrayList<>();

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
            for (SkyforgeNativeBiomePopulationRunner.FeatureResult feature : result.featureResults()) {
                writePreflightRejections =
                        Math.addExact(writePreflightRejections, feature.writePreflightRejections());
                writeDecisionRejections =
                        Math.addExact(writeDecisionRejections, feature.writeDecisionRejections());
                featureOutcomeEvidence.add(feature.featureKey()
                        + "{placed=" + feature.placed()
                        + ",attachmentWrites=" + feature.attachmentWrites()
                        + ",writePreflightRejections=" + feature.writePreflightRejections()
                        + ",writeDecisionRejections=" + feature.writeDecisionRejections()
                        + ",treeProbe=" + feature.treeProbe()
                        + "}");
            }
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
