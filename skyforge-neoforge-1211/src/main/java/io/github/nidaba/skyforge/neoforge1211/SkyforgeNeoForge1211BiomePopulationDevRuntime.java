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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Development-only proof that side-by-side exact volumes can consume native surface ecology. */
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
    private static final long ROOT_SEED = 0x5346494d50303054L;
    private static final ChunkPos EXTERIOR_WITNESS_CHUNK = new ChunkPos(5, 0);
    private static final int PROVIDER_PREFLIGHT_QUART_STEP = 4;
    private static final int VEGETATION_SCAN_HEIGHT = 40;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211BiomePopulationDevRuntime.class.getName());

    private static final Set<Long> scannedProofChunks = new HashSet<>();
    private static final Set<Long> providerBiomeCandidateProofChunks = new HashSet<>();
    private static final Set<Long> forestProofChunkKeys = new LinkedHashSet<>();
    private static final Set<Long> providerProofChunkKeys = new LinkedHashSet<>();
    private static final Set<Long> providerBiomeTargetChunkKeys = new LinkedHashSet<>();
    private static final Map<String, Integer> islandBiomeFieldColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomeSourceColumnDistribution = new TreeMap<>();
    private static final Map<String, Integer> providerBiomePreflightDistribution = new TreeMap<>();
    private static int providerBiomePreflightSurfaceSamples;
    private static int providerBiomePreflightTargetSamples;
    private static int providerBiomeSourceFieldMismatches;
    private static String providerBiomeSourceClass = "not-sampled";
    private static List<String> providerBiomeSourcePossibleBiomes = List.of();
    private static final Set<Long> eligibleProofChunks = new HashSet<>();
    private static final Aggregate lowerAggregate = new Aggregate();
    private static final Aggregate upperAggregate = new Aggregate();
    private static final SkyforgeNativeSurfacePopulationCoordinator surfaceEcologyCoordinator =
            new SkyforgeNativeSurfacePopulationCoordinator();
    private static AutoCloseable persistentBinding;
    private static boolean proofComplete;
    private static boolean acceptanceCompletionRequested;
    private static boolean providerWarmupPrepared;
    private static BlockPos providerReviewSpawn;
    private static ResourceKey<Biome> upperIslandBiomeFieldSampleKey;

    /**
     * Finds every occupied surface chunk on both islands and locates a provider-biome spawn before
     * the acceptance harness warms the complete bounded specimen and native feature attachment margin.
     */
    @SubscribeEvent
    static synchronized void prepareProviderAcceptanceWarmup(ServerTickEvent.Pre event) {
        if (!enabled()
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
            requireTectonicProviderStack();
            prepareProviderBiomeCandidates(level);
            if (providerProofChunkKeys.isEmpty() || providerReviewSpawn == null) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0054 island-biome preflight found no assignable provider-island surface: target="
                                + UPPER_BIOME.location()
                                + ", surfaceChunks=" + providerProofChunkKeys.size()
                                + ", sampledSurfacePoints=" + providerBiomePreflightSurfaceSamples
                                + ", providerBiomeSourceClass=" + providerBiomeSourceClass
                                + ", providerBiomeSourcePossibleBiomes=" + providerBiomeSourcePossibleBiomes);
                return;
            }

            Set<Long> islandChunkKeys = allProofChunkKeys();
            if (SkyforgeAutomatedAcceptanceHarness.serverMode()) {
                Set<Long> warmupChunkKeys = new LinkedHashSet<>(islandChunkKeys);
                addWarmupMargin(warmupChunkKeys, islandChunkKeys, 2);
                warmupChunkKeys.add(EXTERIOR_WITNESS_CHUNK.toLong());
                SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(warmupChunkKeys);
            }
            level.setDefaultSpawnPos(providerReviewSpawn, 0.0F);
            providerWarmupPrepared = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-0054 side-by-side preflight selected " + islandChunkKeys.size()
                            + " full-surface proof chunks (forest=" + forestProofChunkKeys.size()
                            + ", provider=" + providerProofChunkKeys.size() + ") for " + UPPER_BIOME.location()
                            + "; providerSurfaceSamples=" + providerBiomePreflightSurfaceSamples
                            + ", targetSurfaceSamples=" + providerBiomePreflightTargetSamples
                            + ", sourceDistribution=" + providerBiomePreflightDistribution
                            + ", proofChunks=" + providerProofChunkKeys
                            + ", reviewSpawn=" + providerReviewSpawn.toShortString()
                            + (SkyforgeAutomatedAcceptanceHarness.serverMode()
                                    ? ". The harness will warm both full island footprints, a two-chunk feature attachment margin, and the inter-island void witness."
                                    : ". The integrated review world will spawn on the provider island; explore west to compare the Forest island."));
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
                        Map.entry("islandBiomeField", UPPER_BIOME.location()),
                        Map.entry("tectonicLoaded", ModList.get().isLoaded("tectonic")),
                        Map.entry("lithostitchedLoaded", ModList.get().isLoaded("lithostitched")),
                        Map.entry("tectonicConfigVerified", tectonicConfigVerified()),
                        Map.entry("islandBiomeField", UPPER_BIOME.location()),
                        Map.entry("islandBiomeFieldMatchesPopulation", upperIslandBiomeFieldSampleKey != null
                                && upperAggregate.biomeCounts.containsKey(UPPER_BIOME.location())),
                        Map.entry("islandBiomeFieldColumnDistribution", islandBiomeFieldColumnDistribution.toString()),
                        Map.entry("providerBiomeSourceColumnDistribution", providerBiomeSourceColumnDistribution.toString()),
                        Map.entry("providerBiomeSourceFieldMismatches", providerBiomeSourceFieldMismatches),
                        Map.entry("providerBiomeSourceClass", providerBiomeSourceClass),
                        Map.entry("providerBiomeSourcePossibleBiomes", providerBiomeSourcePossibleBiomes.toString()),
                        Map.entry("providerBiomePreflightSurfaceSamples", providerBiomePreflightSurfaceSamples),
                        Map.entry("providerBiomePreflightTargetSamples", providerBiomePreflightTargetSamples),
                        Map.entry("providerBiomePreflightDistribution", providerBiomePreflightDistribution.toString()),
                        Map.entry("forestSurfaceChunks", forestProofChunkKeys.size()),
                        Map.entry("providerSurfaceChunks", providerProofChunkKeys.size()),
                        Map.entry("islandBiomeFieldChunks", providerBiomeTargetChunkKeys.size()),
                        Map.entry("forestPopulatedChunks", lowerAggregate.chunks),
                        Map.entry("providerPopulatedChunks", upperAggregate.chunks),
                        Map.entry("surfaceEcologyRoute", "VEGETAL_DECORATION/SURFACE_ECOLOGY"),
                        Map.entry("providerReviewSpawn", providerReviewSpawn == null
                                ? "disabled" : providerReviewSpawn.toShortString()),
                        Map.entry("scannedChunks", scannedProofChunks.size()),
                        Map.entry("providerBiomeCandidateChunks", providerBiomeCandidateProofChunks.size()),
                        Map.entry("eligibleChunks", eligibleProofChunks.size()),
                        Map.entry("lowerBiomeDistribution", lowerAggregate.biomeCounts.toString()),
                        Map.entry("lowerSuccessfulFeatures", lowerAggregate.successfulFeatures),
                        Map.entry("lowerPersistentTreeEvidence", lowerAggregate.hasTreeEvidence()),
                        Map.entry("upperSuccessfulFeatures", upperAggregate.successfulFeatures),
                        Map.entry("upperPersistentTreeEvidence", upperAggregate.hasTreeEvidence()),
                        Map.entry("exteriorWitnessChunk", EXTERIOR_WITNESS_CHUNK),
                        Map.entry("exteriorNonAirBlocks", exteriorNonAirBlocks)));
    }

    /**
     * Reopen verification reads the persisted proof footprint only. It deliberately does not invoke
     * the native population runner, so a same-world boot cannot reapply generation over saved edits.
     */
    private static void requireTectonicProviderStack() {
        if (!Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            return;
        }
        if (!ModList.get().isLoaded("tectonic") || !ModList.get().isLoaded("lithostitched")) {
            throw new IllegalStateException("combined provider/ecology specimen requires pinned Tectonic and Lithostitched mods");
        }
        if (!tectonicConfigVerified()) {
            throw new IllegalStateException("combined provider/ecology specimen lacks the pinned Tectonic treatment config");
        }
    }

    private static boolean tectonicConfigVerified() {
        Path config = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            if (!Files.isRegularFile(config)) {
                return false;
            }
            String json = Files.readString(config);
            return json.contains("\"vertical_scale\": 2.0")
                    && json.contains("\"elevation_boost\": 2.0")
                    && json.contains("\"ultrasmooth\": true");
        } catch (IOException failure) {
            throw new IllegalStateException("could not inspect the Tectonic integration config", failure);
        }
    }

    private static void completePersistedReopenAcceptance(ServerLevel level) {
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        if (volumes.size() != 2) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "SF-IMP-0054 reopen requires exactly two side-by-side proof volumes");
            return;
        }
        int scannedChunks = 0;
        int lowerLogBlocks = 0;
        int lowerLeafBlocks = 0;
        int upperLogBlocks = 0;
        int upperLeafBlocks = 0;

        List<Long> proofChunkKeys = proofChunkKeysForReopen();
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
            boolean forestIsland = forestProofChunkKeys.contains(chunkKey);
            boolean providerIsland = providerProofChunkKeys.contains(chunkKey);
            if (forestIsland == providerIsland) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        level.getServer(), "SF-IMP-0054 reopen chunk is ambiguously owned: " + chunkPos);
                return;
            }
            SkyIslandWorldVolumeId volumeId = forestIsland ? volumes.get(0).id() : volumes.get(1).id();
            var surface = SkyforgeNativeSurfacePopulationCoordinator.findSurface(level, volumeId, chunkPos);
            if (surface.isEmpty()) {
                SkyforgeAutomatedAcceptanceHarness.fail(
                        level.getServer(), "SF-IMP-0054 reopen lost owned surface in " + chunkPos);
                return;
            }
            VegetationEvidence vegetation = scanVegetation(
                    level, chunkPos, surface.orElseThrow().firstFreeY());
            if (forestIsland) {
                lowerLogBlocks += vegetation.logBlocks();
                lowerLeafBlocks += vegetation.leafBlocks();
            } else {
                upperLogBlocks += vegetation.logBlocks();
                upperLeafBlocks += vegetation.leafBlocks();
            }
        }

        boolean lowerTreeEvidence = lowerLogBlocks > 0 && lowerLeafBlocks > 0;
        boolean upperTreeEvidence = upperLogBlocks > 0 && upperLeafBlocks > 0;
        if (scannedChunks != forestProofChunkKeys.size() + providerProofChunkKeys.size()
                || !lowerTreeEvidence || !upperTreeEvidence) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    level.getServer(),
                    "SF-IMP-0054 reopen lost persisted full-surface vegetation evidence: scanned="
                            + scannedChunks + ", forestChunks=" + forestProofChunkKeys.size()
                            + ", providerChunks=" + providerProofChunkKeys.size() + ", lowerLogs=" + lowerLogBlocks
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
                        Map.entry("tectonicLoaded", ModList.get().isLoaded("tectonic")),
                        Map.entry("lithostitchedLoaded", ModList.get().isLoaded("lithostitched")),
                        Map.entry("tectonicConfigVerified", tectonicConfigVerified()),
                        Map.entry("scannedChunks", scannedChunks),
                        Map.entry("forestSurfaceChunks", forestProofChunkKeys.size()),
                        Map.entry("providerSurfaceChunks", providerProofChunkKeys.size()),
                        Map.entry("islandBiomeFieldChunks", providerBiomeTargetChunkKeys.size()),
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
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        addBoundsChunkKeys(volumes.get(0).bounds(), forestProofChunkKeys);
        addBoundsChunkKeys(volumes.get(1).bounds(), providerProofChunkKeys);
        Set<Long> islandChunkKeys = allProofChunkKeys();
        Set<Long> chunkKeys = new LinkedHashSet<>(islandChunkKeys);
        addWarmupMargin(chunkKeys, islandChunkKeys, 2);
        chunkKeys.add(EXTERIOR_WITNESS_CHUNK.toLong());
        SkyforgeAutomatedAcceptanceHarness.installWarmupChunkKeys(chunkKeys);
    }

    private static List<Long> proofChunkKeysForReopen() {
        return List.copyOf(allProofChunkKeys());
    }

    private static Set<Long> allProofChunkKeys() {
        Set<Long> keys = new LinkedHashSet<>(forestProofChunkKeys);
        keys.addAll(providerProofChunkKeys);
        return keys;
    }

    private static void addBoundsChunkKeys(WorldBounds bounds, Set<Long> target) {
        int minimumChunkX = Math.floorDiv((int) Math.ceil(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.ceil(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                target.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
    }

    private static void addWarmupMargin(Set<Long> target, Set<Long> footprint, int radius) {
        for (long key : footprint) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            for (int x = chunkX - radius; x <= chunkX + radius; x++) {
                for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                    target.add(new ChunkPos(x, z).toLong());
                }
            }
        }
    }

    private static void prepareProviderBiomeCandidates(ServerLevel level) {
        List<SkyIslandWorldVolume> volumes = catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0054 provider preflight requires exactly two island volumes");
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        var providerBiomeSource = generator.getBiomeSource();
        var providerBiomeSampler = level.getChunkSource().randomState().sampler();
        providerBiomeSourceClass = providerBiomeSource.getClass().getName();
        providerBiomeSourcePossibleBiomes = providerBiomeSource.possibleBiomes().stream()
                .map(holder -> holder.unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unregistered"))
                .sorted()
                .toList();

        for (SkyIslandWorldVolume volume : volumes) {
            WorldBounds bounds = volume.bounds();
            int minimumX = (int) Math.ceil(bounds.minimumX());
            int maximumX = (int) Math.floor(bounds.maximumX());
            int minimumZ = (int) Math.ceil(bounds.minimumZ());
            int maximumZ = (int) Math.floor(bounds.maximumZ());
            int firstX = Math.floorDiv(minimumX, PROVIDER_PREFLIGHT_QUART_STEP) * PROVIDER_PREFLIGHT_QUART_STEP;
            int firstZ = Math.floorDiv(minimumZ, PROVIDER_PREFLIGHT_QUART_STEP) * PROVIDER_PREFLIGHT_QUART_STEP;
            boolean providerIsland = volume.id().equals(volumes.get(1).id());
            Map<Long, BlockPos> fieldCandidates = new TreeMap<>(chunkKeyOrder());

            for (int worldX = firstX; worldX <= maximumX; worldX += PROVIDER_PREFLIGHT_QUART_STEP) {
                for (int worldZ = firstZ; worldZ <= maximumZ; worldZ += PROVIDER_PREFLIGHT_QUART_STEP) {
                    if (!bounds.contains(worldX, bounds.maximumY(), worldZ)) {
                        continue;
                    }
                    var surface = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            volume.id(),
                            worldX,
                            worldZ,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            level.getMinBuildHeight(),
                            level.getHeight());
                    if (surface.isEmpty()) {
                        continue;
                    }
                    int surfaceY = surface.orElseThrow().height();
                    long chunkKey = new ChunkPos(worldX >> 4, worldZ >> 4).toLong();
                    BlockPos sample = new BlockPos(worldX, surfaceY, worldZ);
                    if (providerIsland) {
                        providerProofChunkKeys.add(chunkKey);
                        providerBiomeTargetChunkKeys.add(chunkKey);
                        providerBiomePreflightSurfaceSamples = Math.addExact(
                                providerBiomePreflightSurfaceSamples, 1);
                        ResourceKey<Biome> sourceBiome = providerBiomeSource.getNoiseBiome(
                                        QuartPos.fromBlock(worldX),
                                        QuartPos.fromBlock(surfaceY),
                                        QuartPos.fromBlock(worldZ),
                                        providerBiomeSampler)
                                .unwrapKey()
                                .orElseThrow(() -> new IllegalStateException(
                                        "active provider biome source returned an unregistered biome at preflight surface sample"));
                        providerBiomePreflightDistribution.merge(
                                sourceBiome.location().toString(), 1, Math::addExact);
                        if (UPPER_BIOME.equals(sourceBiome)) {
                            providerBiomePreflightTargetSamples = Math.addExact(
                                    providerBiomePreflightTargetSamples, 1);
                        }
                    } else {
                        forestProofChunkKeys.add(chunkKey);
                    }
                }
            }

            if (providerIsland) {
                for (long chunkKey : providerProofChunkKeys) {
                    ChunkPos chunkPos = new ChunkPos(chunkKey);
                    int worldX = chunkPos.getMiddleBlockX();
                    int worldZ = chunkPos.getMiddleBlockZ();
                    var centerSurface = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            volume.id(),
                            worldX,
                            worldZ,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            level.getMinBuildHeight(),
                            level.getHeight());
                    if (centerSurface.isPresent()) {
                        fieldCandidates.put(
                                chunkKey,
                                new BlockPos(worldX, centerSurface.orElseThrow().height(), worldZ));
                    }
                }
                providerReviewSpawn = fieldCandidates.values().stream()
                        .min(Comparator.comparingLong(SkyforgeNeoForge1211BiomePopulationDevRuntime::distanceFromProviderIslandCenter))
                        .orElse(null);
            }
        }

        if (forestProofChunkKeys.isEmpty() || providerProofChunkKeys.isEmpty()) {
            throw new IllegalStateException("SF-IMP-0054 full-surface preflight found an island without surface chunks: forest="
                    + forestProofChunkKeys.size() + ", provider=" + providerProofChunkKeys.size());
        }
    }

    private static Comparator<Long> chunkKeyOrder() {
        return Comparator.comparingInt((Long key) -> ChunkPos.getX(key))
                .thenComparingInt(key -> ChunkPos.getZ(key));
    }

    private static long distanceFromProviderIslandCenter(BlockPos sample) {
        long dx = sample.getX() - 176L;
        long dz = sample.getZ();
        return dx * dx + dz * dz;
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
        if (!Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            List<SkyIslandWorldVolume> volumes = catalog().volumes();
            addBoundsChunkKeys(volumes.get(0).bounds(), forestProofChunkKeys);
            addBoundsChunkKeys(volumes.get(1).bounds(), providerProofChunkKeys);
        }
        if (SkyforgeAutomatedAcceptanceHarness.serverMode()
                && !Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)) {
            installServerAcceptanceWarmupChunks();
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge SF-IMP-0054 side-by-side surface-ecology specimen enabled. Create a NEW disposable "
                        + "Skyforge Development world and compare the same-height Forest island at x=0 with "
                        + "the provider-biome island at x=176, both centered near z=0. Each island runs its "
                        + "complete admitted VEGETAL_DECORATION surface-ecology route within exact volume ownership.");
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
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        boolean forestIsland = forestProofChunkKeys.contains(chunkKey);
        boolean providerIsland = providerProofChunkKeys.contains(chunkKey);
        boolean selectedProofChunk = forestIsland || providerIsland;
        if (!selectedProofChunk || !scannedProofChunks.add(chunkPos.toLong())) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()) {
            throw new IllegalStateException("SF-IMP-0054 biome proof ran without its Skyforge binding");
        }

        var volumes = catalog().volumes();
        if (volumes.size() != 2) {
            throw new IllegalStateException("SF-IMP-0054 fixture requires exactly two side-by-side volumes");
        }
        if (forestIsland == providerIsland) {
            throw new IllegalStateException("SF-IMP-0054 side-by-side proof chunk has ambiguous ownership: " + chunkPos);
        }
        SkyIslandWorldVolumeId volumeId = forestIsland ? volumes.get(0).id() : volumes.get(1).id();

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
        SkyforgeExactVolumeBiomeResolver resolver = providerMode
                ? islandBiomeFieldResolver(volumes.get(0).id(), volumes.get(1).id())
                : (candidateVolumeId, x, y, z) -> {
            if (candidateVolumeId.equals(volumes.get(0).id())) {
                return Biomes.FOREST;
            }
            if (candidateVolumeId.equals(volumes.get(1).id())) {
                return UPPER_BIOME;
            }
            throw new IllegalArgumentException(
                    "unknown SF-IMP-0054 proof volume: " + candidateVolumeId.path());
        };
        var surface = SkyforgeNativeSurfacePopulationCoordinator.findSurface(level, volumeId, chunkPos);
        if (surface.isEmpty()) {
            evaluateCompletion(volumes.get(0).id(), volumes.get(1).id());
            return;
        }
        var sample = surface.orElseThrow();
        var plan = SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                volumeId, resolver, MAXIMUM_ATTACHMENT_DEPTH);
        var population = surfaceEcologyCoordinator.populate(level, generator, plan, chunkPos);
        if (!population.terrainPresent() || population.phases().isEmpty()) {
            throw new IllegalStateException("SF-IMP-0054 surface-ecology coordinator did not process "
                    + volumeId.path() + " at " + chunkPos);
        }
        SkyforgeNativeBiomePopulationRunner.Result nativeResult = population.phases().get(0).nativeResult();
        ResourceKey<Biome> expectedBiome = forestIsland ? Biomes.FOREST : nativeResult.biomeKey();
        requireChunkResult(forestIsland ? "Forest island" : "provider island", nativeResult, expectedBiome);
        if (providerMode && providerBiomeTargetChunkKeys.contains(chunkKey)
                && UPPER_BIOME.equals(nativeResult.biomeKey())) {
            upperIslandBiomeFieldSampleKey = nativeResult.biomeKey();
            providerBiomeCandidateProofChunks.add(chunkKey);
        }
        if (providerMode) {
            recordProviderBiomeEvidence(
                    level, generator, sample.x(), sample.firstFreeY(), sample.z(), nativeResult.biomeKey());
        }
        VegetationEvidence vegetation = scanVegetation(level, chunkPos, sample.firstFreeY());
        if (forestIsland) {
            lowerAggregate.add(nativeResult, vegetation);
        } else {
            upperAggregate.add(nativeResult, vegetation);
        }
        eligibleProofChunks.add(chunkKey);
        evaluateCompletion(volumes.get(0).id(), volumes.get(1).id());
    }

    private static void recordProviderBiomeEvidence(
            WorldGenLevel level,
            ChunkGenerator generator,
            int x,
            int y,
            int z,
            ResourceKey<Biome> liveBiome) {
        islandBiomeFieldColumnDistribution.merge(liveBiome.location().toString(), 1, Math::addExact);
        ResourceKey<Biome> sourceBiome = generator.getBiomeSource().getNoiseBiome(
                        QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                        serverLevel(level).getChunkSource().randomState().sampler())
                .unwrapKey()
                .orElseThrow(() -> new IllegalStateException("active provider source returned an unregistered biome"));
        providerBiomeSourceColumnDistribution.merge(sourceBiome.location().toString(), 1, Math::addExact);
        if (!liveBiome.equals(sourceBiome)) {
            providerBiomeSourceFieldMismatches = Math.addExact(providerBiomeSourceFieldMismatches, 1);
        }
    }

    private static void evaluateCompletion(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        int expectedCandidateChunks = forestProofChunkKeys.size() + providerProofChunkKeys.size();
        if (scannedProofChunks.size() < expectedCandidateChunks) {
            return;
        }
        if (lowerAggregate.chunks != forestProofChunkKeys.size()
                || upperAggregate.chunks != providerProofChunkKeys.size()) {
            throw new IllegalStateException("SF-IMP-0054 did not populate every preflighted island surface chunk: "
                    + "forest=" + lowerAggregate.chunks + "/" + forestProofChunkKeys.size()
                    + ", provider=" + upperAggregate.chunks + "/" + providerProofChunkKeys.size()
                    + ", scanned=" + scannedProofChunks.size() + "/" + expectedCandidateChunks);
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate,
                Boolean.getBoolean(PROVIDER_BIOME_PROPERTY) ? UPPER_BIOME : null);
        if (Boolean.getBoolean(PROVIDER_BIOME_PROPERTY)
                && !providerBiomeCandidateProofChunks.containsAll(providerBiomeTargetChunkKeys)) {
            throw new IllegalStateException("SF-IMP-0054 provider target biome did not survive native population: "
                    + "targetChunks=" + providerBiomeTargetChunkKeys.size()
                    + ", matchingPopulationChunks=" + providerBiomeCandidateProofChunks.size());
        }
        completeProof(lowerId, upperId);
    }

    private static SkyforgeExactVolumeBiomeResolver islandBiomeFieldResolver(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        return (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return UPPER_BIOME;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0054 proof volume: " + volumeId.path());
        };
    }

    private static void completeProof(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        if (proofComplete) {
            return;
        }
        requireAggregate("lower", lowerAggregate, Biomes.FOREST);
        requireAggregate("upper", upperAggregate,
                Boolean.getBoolean(PROVIDER_BIOME_PROPERTY) ? UPPER_BIOME : null);
        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0054 SIDE-BY-SIDE SURFACE ECOLOGY PASS: scannedChunks=" + scannedProofChunks.size()
                        + ", eligibleChunks=" + eligibleProofChunks.size()
                        + ", lower={volume=" + lowerId.path()
                        + ", biomeDistribution=" + lowerAggregate.biomeCounts
                        + ", attempted=" + lowerAggregate.attemptedFeatures
                        + ", successful=" + lowerAggregate.successfulFeatures
                        + ", attachments=" + lowerAggregate.attachmentWrites
                        + ", logs=" + lowerAggregate.logBlocks
                        + ", leaves=" + lowerAggregate.leafBlocks
                        + ", surfaceChunks=" + lowerAggregate.chunks
                        + "}, upper={volume=" + upperId.path()
                        + ", biomeDistribution=" + upperAggregate.biomeCounts
                        + ", attempted=" + upperAggregate.attemptedFeatures
                        + ", successful=" + upperAggregate.successfulFeatures
                        + ", attachments=" + upperAggregate.attachmentWrites
                        + ", logs=" + upperAggregate.logBlocks
                        + ", leaves=" + upperAggregate.leafBlocks
                        + ", surfaceChunks=" + upperAggregate.chunks
                        + "}. Every preflighted surface chunk ran the admitted native surface-ecology route; "
                        + "both islands produced persistent native tree vegetation under separate exact-volume ownership.");
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel serverLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        if (level instanceof WorldGenRegion region) {
            return region.getLevel();
        }
        throw new IllegalStateException("provider biome diagnostics require ServerLevel or WorldGenRegion");
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
        if (expectedBiome != null && !aggregate.biomeCounts.containsKey(expectedBiome.location())) {
            throw new IllegalStateException(label + " proof aggregate resolved unexpected biome: "
                    + aggregate.biomeCounts + ", required=" + expectedBiome.location());
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
                new WorldBounds(-72.0, 72.0, 96.0, 168.0, -72.0, 72.0),
                compileTableland(lowerSeed, 0.0, 136.0));
        var upper = new SkyIslandWorldVolume(
                upperId,
                new WorldBounds(104.0, 248.0, 96.0, 168.0, -72.0, 72.0),
                compileTableland(upperSeed, 176.0, 136.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    private static SkyforgeNeoForge1211ChunkAdapter adapter() {
        return new SkyforgeNeoForge1211ChunkAdapter(
                catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette());
    }

    private static CompiledSkyIslandVolume compileTableland(long seed, double centerX, double elevation) {
        var descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                centerX,
                0.0,
                elevation,
                56.0,
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

    private record VegetationEvidence(int logBlocks, int leafBlocks) {
        VegetationEvidence {
            if (logBlocks < 0 || leafBlocks < 0) {
                throw new IllegalArgumentException("vegetation evidence counts must be non-negative");
            }
        }
    }

    private static final class Aggregate {
        private int chunks;
        private int attemptedFeatures;
        private int successfulFeatures;
        private int attachmentWrites;
        private int logBlocks;
        private int leafBlocks;
        private final Map<ResourceLocation, Integer> biomeCounts = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        private final Set<ResourceLocation> featureKeys = new LinkedHashSet<>();

        void add(
                SkyforgeNativeBiomePopulationRunner.Result result,
                VegetationEvidence vegetation) {
            chunks = Math.addExact(chunks, 1);
            biomeCounts.merge(result.biomeKey().location(), 1, Math::addExact);
            attemptedFeatures = Math.addExact(attemptedFeatures, result.attemptedFeatures());
            successfulFeatures = Math.addExact(successfulFeatures, result.successfulFeatures());
            attachmentWrites = Math.addExact(attachmentWrites, result.attachmentWrites());
            logBlocks = Math.addExact(logBlocks, vegetation.logBlocks());
            leafBlocks = Math.addExact(leafBlocks, vegetation.leafBlocks());
            featureKeys.addAll(result.featureKeys());
        }

        boolean hasTreeEvidence() {
            return logBlocks > 0 && leafBlocks > 0;
        }

    }
}

