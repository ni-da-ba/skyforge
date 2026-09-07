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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SF-IMP-0080 human-facing ecology specimen.
 *
 * <p>This fixture reuses the accepted SF-IMP-0054/0055 broad TABLELAND ecology geometry and its
 * forest/taiga native biome identities, translated upward into clear Overworld air so the pair can
 * pass the modern whole-volume physical-admission lifecycle. Unlike the historical standalone
 * population proof, the final world is admitted atomically, realized through deferred stable-chunk
 * catch-up, populated by the production coordinator, receives durable biome presentation, and is
 * then saved for a mutation-inert client reopen.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ShowcaseEcologyDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.showcaseEcology";

    private static final long ROOT_SEED = 0x5346494d50303080L;
    private static final int MAXIMUM_ATTACHMENT_DEPTH = 24;
    private static final int VEGETATION_SCAN_HEIGHT = 40;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.class.getName());

    private static AutoCloseable persistentTerrainBinding;
    private static AutoCloseable persistentAdmissionBinding;
    private static AutoCloseable persistentPopulationBinding;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211ShowcaseEcologyDevRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()
                || persistentTerrainBinding != null
                || persistentAdmissionBinding != null
                || persistentPopulationBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()) {
            throw new IllegalStateException(
                    "SF-IMP-0080 ecology specimen must begin without another terrain/admission/population binding");
        }

        SkyIslandWorldCatalog catalog = catalog();
        SkyIslandWorldVolumeId lowerId = lowerVolumeId(catalog);
        SkyIslandWorldVolumeId upperId = upperVolumeId(catalog);
        SkyforgeExactVolumeBiomeResolver resolver = biomeResolver(lowerId, upperId);

        // Keep the accepted Minecraft-owned native surface representation path. Because the
        // translated tablelands sit well above the native terrain, exposed island tops can inherit
        // the already-constructed native land surface without allowing decoration to become terrain.
        persistentTerrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        persistentAdmissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(catalog);

        Set<Long> lowerFootprint = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(lowerId);
        Set<Long> upperFootprint = SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(upperId);
        if (!lowerFootprint.equals(upperFootprint)) {
            throw new IllegalStateException("SF-IMP-0080 stacked ecology volumes must share one X/Z admission footprint");
        }
        persistentPopulationBinding = SkyforgeNativeSurfacePopulationStage.install((chunkPos, minimumY, height) -> {
            if (!lowerFootprint.contains(chunkPos.toLong())) {
                return List.of();
            }
            return List.of(
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            lowerId,
                            resolver,
                            MAXIMUM_ATTACHMENT_DEPTH),
                    SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                            upperId,
                            resolver,
                            MAXIMUM_ATTACHMENT_DEPTH));
        });

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0080 ecology specimen enabled: broad stacked TABLELAND surfaces use lower forest / upper "
                        + "taiga through physical admission, deferred production population, durable biome "
                        + "presentation, persistence, and a separate mutation-inert viewer.");
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
            evaluate(level, event);
        }
    }

    private static synchronized void evaluate(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (proofComplete) {
            return;
        }

        SkyIslandWorldCatalog catalog = catalog();
        SkyIslandWorldVolumeId lowerId = lowerVolumeId(catalog);
        SkyIslandWorldVolumeId upperId = upperVolumeId(catalog);
        var lowerAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(lowerId);
        var upperAdmission = SkyforgePhysicalVolumeAdmissionStage.snapshot(upperId);

        if (lowerAdmission.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED
                || upperAdmission.state() == SkyforgePhysicalVolumeAdmissionState.REJECTED) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 ecology volume collided with native terrain: lower="
                            + lowerAdmission + ", upper=" + upperAdmission);
            return;
        }
        if (lowerAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || upperAdmission.state() != SkyforgePhysicalVolumeAdmissionState.ADMITTED
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(lowerId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(upperId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(lowerId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(upperId).isEmpty()) {
            return;
        }

        int lowerExpected = expectedPopulatedChunks(level, lowerId);
        int upperExpected = expectedPopulatedChunks(level, upperId);
        List<SkyforgeNativeBiomePopulationRunner.Result> lowerResults =
                SkyforgeNativeSurfacePopulationStage.completedNativeResults(lowerId);
        List<SkyforgeNativeBiomePopulationRunner.Result> upperResults =
                SkyforgeNativeSurfacePopulationStage.completedNativeResults(upperId);
        if (lowerResults.size() < lowerExpected || upperResults.size() < upperExpected) {
            return;
        }
        if (lowerResults.size() != lowerExpected || upperResults.size() != upperExpected) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 population result count exceeded exact surface obligations: lower="
                            + lowerResults.size() + "/" + lowerExpected + ", upper="
                            + upperResults.size() + "/" + upperExpected);
            return;
        }

        PopulationEvidence lowerPopulation =
                populationEvidence(lowerResults, Biomes.FOREST, "lower");
        PopulationEvidence upperPopulation =
                populationEvidence(upperResults, Biomes.TAIGA, "upper");
        if (lowerPopulation.featureKeys().equals(upperPopulation.featureKeys())) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 forest and taiga exposed identical native vegetal feature identity");
            return;
        }

        Optional<Ecology> lowerEcology = scanLoadedEcology(level, lowerId);
        Optional<Ecology> upperEcology = scanLoadedEcology(level, upperId);
        if (lowerEcology.isEmpty() || upperEcology.isEmpty()) {
            return;
        }
        Ecology lower = lowerEcology.orElseThrow();
        Ecology upper = upperEcology.orElseThrow();
        if (!lower.legible() || !upper.legible()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 final-world ecology is incomplete: lower=" + lower + ", upper=" + upper);
            return;
        }
        if (!persistedBiomeMatches(level, lowerId, Biomes.FOREST)
                || !persistedBiomeMatches(level, upperId, Biomes.TAIGA)) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 durable biome presentation did not match lower forest / upper taiga");
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("lowerState", lowerAdmission.state()),
                        java.util.Map.entry("upperState", upperAdmission.state()),
                        java.util.Map.entry("lowerObserved", lowerAdmission.observedChunks()),
                        java.util.Map.entry("lowerRequired", lowerAdmission.requiredChunks()),
                        java.util.Map.entry("upperObserved", upperAdmission.observedChunks()),
                        java.util.Map.entry("upperRequired", upperAdmission.requiredChunks()),
                        java.util.Map.entry("lowerPopulationChunks", lowerResults.size()),
                        java.util.Map.entry("lowerExpectedPopulationChunks", lowerExpected),
                        java.util.Map.entry("upperPopulationChunks", upperResults.size()),
                        java.util.Map.entry("upperExpectedPopulationChunks", upperExpected),
                        java.util.Map.entry("lowerBiome", lowerPopulation.biomeKey().location()),
                        java.util.Map.entry("upperBiome", upperPopulation.biomeKey().location()),
                        java.util.Map.entry("lowerAttempted", lowerPopulation.attemptedFeatures()),
                        java.util.Map.entry("lowerSuccessful", lowerPopulation.successfulFeatures()),
                        java.util.Map.entry("upperAttempted", upperPopulation.attemptedFeatures()),
                        java.util.Map.entry("upperSuccessful", upperPopulation.successfulFeatures()),
                        java.util.Map.entry("distinctFeatureIdentity", true),
                        java.util.Map.entry("lowerFeatureKeys", lowerPopulation.featureKeys().size()),
                        java.util.Map.entry("upperFeatureKeys", upperPopulation.featureKeys().size()),
                        java.util.Map.entry("lowerSubstrate", lower.substrateBlocks()),
                        java.util.Map.entry("lowerGrass", lower.grassBlocks()),
                        java.util.Map.entry("lowerLogs", lower.logBlocks()),
                        java.util.Map.entry("lowerLeaves", lower.leafBlocks()),
                        java.util.Map.entry("lowerPlants", lower.plantBlocks()),
                        java.util.Map.entry("upperSubstrate", upper.substrateBlocks()),
                        java.util.Map.entry("upperGrass", upper.grassBlocks()),
                        java.util.Map.entry("upperLogs", upper.logBlocks()),
                        java.util.Map.entry("upperLeaves", upper.leafBlocks()),
                        java.util.Map.entry("upperPlants", upper.plantBlocks()),
                        java.util.Map.entry("persistentBiomePresentation", true),
                        java.util.Map.entry("pendingCatchup", 0),
                        java.util.Map.entry("pendingBiomePresentation", 0)));

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0080 SHOWCASE ECOLOGY PASS: lower={biome=forest, ecology=" + lower
                        + "}, upper={biome=taiga, ecology=" + upper
                        + "}. Broad land-biome surfaces survived the modern persistent exact-volume lifecycle.");
    }

    static SkyIslandWorldCatalog catalog() {
        long lowerSeed = ROOT_SEED ^ 0x464f52455354L;
        long upperSeed = ROOT_SEED ^ 0x5441494741L;
        var lowerId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0080-ecology", 0, 0, lowerSeed);
        var upperId = new SkyIslandWorldVolumeId(ROOT_SEED, "sf-imp-0080-ecology", 0, 1, upperSeed);
        var lower = new SkyIslandWorldVolume(
                lowerId,
                new WorldBounds(-144.0, 144.0, 176.0, 248.0, -144.0, 144.0),
                compileTableland(lowerSeed, 216.0));
        var upper = new SkyIslandWorldVolume(
                upperId,
                new WorldBounds(-144.0, 144.0, 236.0, 308.0, -144.0, 144.0),
                compileTableland(upperSeed, 276.0));
        return new SkyIslandWorldCatalog(ROOT_SEED, List.of(lower, upper));
    }

    static SkyIslandWorldVolumeId lowerVolumeId(SkyIslandWorldCatalog catalog) {
        requireTwoVolumes(catalog);
        return catalog.volumes().get(0).id();
    }

    static SkyIslandWorldVolumeId upperVolumeId(SkyIslandWorldCatalog catalog) {
        requireTwoVolumes(catalog);
        return catalog.volumes().get(1).id();
    }

    static SkyforgeExactVolumeBiomeResolver biomeResolver(
            SkyIslandWorldVolumeId lowerId,
            SkyIslandWorldVolumeId upperId) {
        Objects.requireNonNull(lowerId, "lowerId");
        Objects.requireNonNull(upperId, "upperId");
        return (volumeId, x, y, z) -> {
            if (volumeId.equals(lowerId)) {
                return Biomes.FOREST;
            }
            if (volumeId.equals(upperId)) {
                return Biomes.TAIGA;
            }
            throw new IllegalArgumentException("unknown SF-IMP-0080 ecology volume: " + volumeId.path());
        };
    }

    static Set<Long> footprintChunkKeys() {
        WorldBounds bounds = catalog().volumes().getFirst().bounds();
        int minimumChunkX = Math.floorDiv((int) Math.floor(bounds.minimumX()), 16);
        int maximumChunkX = Math.floorDiv((int) Math.floor(bounds.maximumX()), 16);
        int minimumChunkZ = Math.floorDiv((int) Math.floor(bounds.minimumZ()), 16);
        int maximumChunkZ = Math.floorDiv((int) Math.floor(bounds.maximumZ()), 16);
        Set<Long> keys = new LinkedHashSet<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                keys.add(new ChunkPos(chunkX, chunkZ).toLong());
            }
        }
        return Set.copyOf(keys);
    }

    static Optional<Ecology> scanLoadedEcology(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");

        int substrateBlocks = 0;
        int grassBlocks = 0;
        int logBlocks = 0;
        int leafBlocks = 0;
        int plantBlocks = 0;
        int minimumBuildY = level.getMinBuildHeight();
        int buildHeight = level.getHeight();
        int maximumBuildY = minimumBuildY + buildHeight - 1;

        for (long chunkKey : footprintChunkKeys()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                return Optional.empty();
            }
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            volumeId,
                            x,
                            z,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            minimumBuildY,
                            buildHeight);
                    if (claim.isEmpty()) {
                        continue;
                    }
                    int firstFreeY = claim.orElseThrow().height();
                    if (firstFreeY <= minimumBuildY || firstFreeY > maximumBuildY) {
                        continue;
                    }

                    BlockState substrate = level.getBlockState(new BlockPos(x, firstFreeY - 1, z));
                    if (isLandSubstrate(substrate)) {
                        substrateBlocks++;
                    }
                    if (substrate.is(Blocks.GRASS_BLOCK)) {
                        grassBlocks++;
                    }

                    int scanMaximumY = Math.min(maximumBuildY, firstFreeY + VEGETATION_SCAN_HEIGHT);
                    for (int y = firstFreeY; y <= scanMaximumY; y++) {
                        BlockState state = level.getBlockState(new BlockPos(x, y, z));
                        if (state.is(BlockTags.LOGS)) {
                            logBlocks++;
                        }
                        if (state.is(BlockTags.LEAVES)) {
                            leafBlocks++;
                        }
                        if (isSurfacePlant(state)) {
                            plantBlocks++;
                        }
                    }
                }
            }
        }
        return Optional.of(new Ecology(
                substrateBlocks,
                grassBlocks,
                logBlocks,
                leafBlocks,
                plantBlocks));
    }

    static boolean persistedBiomeMatches(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId,
            ResourceKey<Biome> expectedBiome) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(expectedBiome, "expectedBiome");
        var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                volumeId,
                0,
                0,
                Heightmap.Types.WORLD_SURFACE_WG,
                level.getMinBuildHeight(),
                level.getHeight());
        if (claim.isEmpty()) {
            return false;
        }
        BlockPos sample = new BlockPos(0, claim.orElseThrow().height(), 0);
        Holder<Biome> expected = level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolder(expectedBiome)
                .orElseThrow(() -> new IllegalStateException(
                        "expected SF-IMP-0080 biome absent from final registry: " + expectedBiome.location()));
        return level.getBiome(sample).equals(expected);
    }

    private static PopulationEvidence populationEvidence(
            List<SkyforgeNativeBiomePopulationRunner.Result> results,
            ResourceKey<Biome> expectedBiome,
            String label) {
        int attempted = 0;
        int successful = 0;
        Set<ResourceLocation> featureKeys = new LinkedHashSet<>();
        for (var result : results) {
            if (!result.biomeKey().equals(expectedBiome)) {
                throw new IllegalStateException(
                        "SF-IMP-0080 " + label + " population changed biome identity: " + result.biomeKey().location());
            }
            attempted = Math.addExact(attempted, result.attemptedFeatures());
            successful = Math.addExact(successful, result.successfulFeatures());
            featureKeys.addAll(result.featureKeys());
        }
        if (attempted == 0 || successful == 0 || featureKeys.isEmpty()) {
            throw new IllegalStateException(
                    "SF-IMP-0080 " + label + " biome produced no meaningful native surface population");
        }
        return new PopulationEvidence(expectedBiome, attempted, successful, Set.copyOf(featureKeys));
    }

    private static int expectedPopulatedChunks(
            ServerLevel level,
            SkyIslandWorldVolumeId volumeId) {
        int count = 0;
        for (long chunkKey : footprintChunkKeys()) {
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (containsExactSurface(
                    volumeId,
                    chunkPos,
                    level.getMinBuildHeight(),
                    level.getHeight())) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsExactSurface(
            SkyIslandWorldVolumeId volumeId,
            ChunkPos chunkPos,
            int minimumY,
            int height) {
        for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
            for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                if (SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                                volumeId,
                                x,
                                z,
                                Heightmap.Types.WORLD_SURFACE_WG,
                                minimumY,
                                height)
                        .isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLandSubstrate(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD);
    }

    private static boolean isSurfacePlant(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static CompiledSkyIslandVolume compileTableland(
            long seed,
            double elevation) {
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

    private static void requireTwoVolumes(SkyIslandWorldCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        if (catalog.volumes().size() != 2) {
            throw new IllegalStateException("SF-IMP-0080 ecology catalog requires exactly two stacked volumes");
        }
    }

    record Ecology(
            int substrateBlocks,
            int grassBlocks,
            int logBlocks,
            int leafBlocks,
            int plantBlocks) {
        Ecology {
            if (substrateBlocks < 0
                    || grassBlocks < 0
                    || logBlocks < 0
                    || leafBlocks < 0
                    || plantBlocks < 0
                    || grassBlocks > substrateBlocks) {
                throw new IllegalArgumentException("invalid SF-IMP-0080 ecology counts");
            }
        }

        boolean legible() {
            return substrateBlocks > 0
                    && logBlocks > 0
                    && leafBlocks > 0
                    && plantBlocks > 0;
        }
    }

    private record PopulationEvidence(
            ResourceKey<Biome> biomeKey,
            int attemptedFeatures,
            int successfulFeatures,
            Set<ResourceLocation> featureKeys) {
        private PopulationEvidence {
            Objects.requireNonNull(biomeKey, "biomeKey");
            Objects.requireNonNull(featureKeys, "featureKeys");
            featureKeys = Set.copyOf(featureKeys);
            if (attemptedFeatures <= 0 || successfulFeatures <= 0 || successfulFeatures > attemptedFeatures) {
                throw new IllegalArgumentException("invalid SF-IMP-0080 population evidence");
            }
        }
    }
}
