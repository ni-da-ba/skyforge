package io.github.nidaba.skyforge.neoforge1211;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated dedicated-server A/B probe for Tectonic's active Overworld terrain profile. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeTectonicTerrainAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.tectonicTerrain";
    static final String TREATMENT_PROPERTY = ENABLE_PROPERTY + ".treatment";
    private static final double EXPECTED_TECTONIC_VERTICAL_SCALE = 6.0;
    private static final double EXPECTED_TECTONIC_ELEVATION_BOOST = 2.0;
    private static final double EXPECTED_TECTONIC_CONTINENTS_SCALE = 0.5;
    private static final double EXPECTED_TECTONIC_EROSION_SCALE = 1.0;
    private static final double EXPECTED_TECTONIC_RIDGE_SCALE = 1.0;
    private static final double EXPECTED_TECTONIC_FLAT_TERRAIN_SKEW = 0.1;
    private static final double EXPECTED_TECTONIC_OCEAN_OFFSET = -0.1;
    private static final int EXTERIOR_CHUNK_X = 12;
    private static final int PROFILE_RADIUS = 8;
    private static final int MINIMUM_TECTONIC_ISLAND_RELIEF_BLOCKS = 48;
    private static final int MINIMUM_TECTONIC_ISLAND_RIDGE_TOP_Y = 300;
    private static final int MAXIMUM_TECTONIC_ISLAND_COLUMNS_AT_OR_ABOVE_RIDGE = 128;
    private static boolean complete;
    private static final Set<UUID> REVIEW_RELOCATED_PLAYERS = new HashSet<>();
    private static final Map<UUID, Integer> REVIEW_RELOCATION_ATTEMPTS = new HashMap<>();

    private SkyforgeTectonicTerrainAcceptance() {}

    static void installFromSystemProperty() {
        // This fixture is entirely inert unless a dedicated ModDev run opts in.
    }

    @SubscribeEvent
    static void placeReviewPlayerAtSavedSpawn(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        relocateReviewPlayer(player, player.serverLevel(), "login");
    }

    private static void relocateReviewPlayer(ServerPlayer player, ServerLevel level, String trigger) {
        UUID playerId = player.getUUID();
        if (REVIEW_RELOCATED_PLAYERS.contains(playerId)) {
            return;
        }
        int attempt = REVIEW_RELOCATION_ATTEMPTS.merge(playerId, 1, Math::addExact);
        BlockPos spawn = level.getSharedSpawnPos();
        boolean teleported = player.teleportTo(
                level,
                spawn.getX() + 0.5,
                spawn.getY(),
                spawn.getZ() + 0.5,
                java.util.Set.of(),
                player.getYRot(),
                player.getXRot());
        if (teleported || attempt >= 20) {
            java.util.logging.Logger.getLogger(SkyforgeTectonicTerrainAcceptance.class.getName())
                    .info("Tectonic terrain review spawn relocation: trigger="
                            + trigger
                            + ", attempt="
                            + attempt
                            + ", success="
                            + teleported
                            + ", target="
                            + spawn.toShortString()
                            + ", player="
                            + player.blockPosition().toShortString());
        }
        if (teleported || attempt >= 20) {
            REVIEW_RELOCATED_PLAYERS.add(playerId);
            REVIEW_RELOCATION_ATTEMPTS.remove(playerId);
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel() == overworld) {
                    // Login can precede final player placement when opening an old local save.
                    // Retry once on the server tick, using the exact saved review spawn.
                    relocateReviewPlayer(player, overworld, "server-tick");
                }
            }
        }
        if (complete
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || !SkyforgeAutomatedAcceptanceHarness.warmupComplete()) {
            return;
        }

        try {
            ServerLevel level = server.getLevel(Level.OVERWORLD);
            if (level == null) {
                fail(server, "dedicated server has no Overworld");
                return;
            }

            boolean treatment = Boolean.getBoolean(TREATMENT_PROPERTY);
            boolean tectonicLoaded = ModList.get().isLoaded("tectonic");
            if (tectonicLoaded != treatment) {
                fail(server, "Tectonic loaded state differs from requested A/B profile: treatment="
                        + treatment + ", loaded=" + tectonicLoaded);
                return;
            }
            boolean tectonicPackSelected = server.getPackRepository().getSelectedIds().stream()
                    .anyMatch(id -> id.toLowerCase(java.util.Locale.ROOT).contains("tectonic"));
            if (treatment && !tectonicPackSelected) {
                fail(server, "Tectonic mod loaded but its bundled server data pack is not selected");
                return;
            }
            String tectonicVerticalScale = "not-applicable";
            String tectonicElevationBoost = "not-applicable";
            String tectonicContinentsScale = "not-applicable";
            String tectonicErosionScale = "not-applicable";
            String tectonicRidgeScale = "not-applicable";
            String tectonicFlatTerrainSkew = "not-applicable";
            String tectonicOceanOffset = "not-applicable";
            if (treatment) {
                tectonicVerticalScale = configuredTectonicVerticalScale();
                tectonicElevationBoost = configuredTectonicElevationBoost();
                tectonicContinentsScale = configuredTectonicContinentsScale();
                tectonicErosionScale = configuredTectonicErosionScale();
                tectonicRidgeScale = configuredTectonicRidgeScale();
                tectonicFlatTerrainSkew = configuredTectonicFlatTerrainSkew();
                tectonicOceanOffset = configuredTectonicOceanOffset();
                if (!Double.toString(EXPECTED_TECTONIC_VERTICAL_SCALE).equals(tectonicVerticalScale)
                        || !Double.toString(EXPECTED_TECTONIC_ELEVATION_BOOST).equals(tectonicElevationBoost)
                        || !Double.toString(EXPECTED_TECTONIC_CONTINENTS_SCALE).equals(tectonicContinentsScale)
                        || !Double.toString(EXPECTED_TECTONIC_EROSION_SCALE).equals(tectonicErosionScale)
                        || !Double.toString(EXPECTED_TECTONIC_RIDGE_SCALE).equals(tectonicRidgeScale)
                        || !Double.toString(EXPECTED_TECTONIC_FLAT_TERRAIN_SKEW).equals(tectonicFlatTerrainSkew)
                        || !Double.toString(EXPECTED_TECTONIC_OCEAN_OFFSET).equals(tectonicOceanOffset)) {
                    fail(server, "Tectonic treatment config differs from the pinned terrain profile: "
                            + "vertical_scale=" + tectonicVerticalScale
                            + ", elevation_boost=" + tectonicElevationBoost
                            + ", continents_scale=" + tectonicContinentsScale
                            + ", erosion_scale=" + tectonicErosionScale
                            + ", ridge_scale=" + tectonicRidgeScale
                            + ", flat_terrain_skew=" + tectonicFlatTerrainSkew
                            + ", ocean_offset=" + tectonicOceanOffset);
                    return;
                }
            }

            ChunkGenerator activeGenerator = level.getChunkSource().getGenerator();
            if (!(activeGenerator instanceof SkyforgeNoiseBasedChunkGenerator generator)) {
                fail(server, "development Overworld is not using SkyforgeNoiseBasedChunkGenerator");
                return;
            }
            String settingsKey = generator.terrainProviderSettingsKey().toString();
            if (!"minecraft:overworld".equals(settingsKey)) {
                fail(server, "unexpected terrain provider settings key: " + settingsKey);
                return;
            }

            ChunkAccess islandChunk = level.getChunk(new ChunkPos(0, 0).getMiddleBlockPosition(0));
            RandomState randomState =
                    generator.terrainProviderRandomState(level.getSeed(), level.registryAccess());
            ProfileEvidence profile = sampleProfile(
                    level,
                    islandChunk,
                    generator.terrainProviderGenerator(),
                    randomState);
            if (profile.samples() == 0) {
                fail(server, "terrain provider returned no solid surface columns in the origin profile");
                return;
            }
            ProfileEvidence realizedSurface = captureRealizedIslandSurface(islandChunk);
            if (realizedSurface.samples() == 0) {
                fail(server, "Skyforge island has no realized top-surface columns in the origin chunk");
                return;
            }
            boolean reopening = Boolean.getBoolean(ENABLE_PROPERTY + ".reopen");
            MinecraftNativeSurfaceSnapshot providerSurfaceSnapshot = reopening
                    ? null
                    : SkyforgeNativeSurfaceSnapshotStage.consumeAcceptanceSnapshot(islandChunk)
                            .orElseThrow(() -> new IllegalStateException(
                                    "provider surface snapshot was not retained for the acceptance fixture"));
            String providerTerrainDatumY = providerSurfaceSnapshot == null
                    ? "not-recomputed-on-reopen"
                    : Integer.toString(providerSurfaceSnapshot.baselineY());
            int realizedIslandRelief = realizedSurface.maximumY() - realizedSurface.minimumY();
            int realizedIslandColumnsAtOrAboveRidge = countSurfaceColumnsAtOrAbove(
                    islandChunk, MINIMUM_TECTONIC_ISLAND_RIDGE_TOP_Y);
            String capturedProviderSurfaceRange = providerSurfaceSnapshot == null
                    ? "not-recomputed-on-reopen"
                    : describeProviderSurfaceRange(providerSurfaceSnapshot);
            if (treatment && realizedIslandRelief < MINIMUM_TECTONIC_ISLAND_RELIEF_BLOCKS) {
                fail(server, "Tectonic provider profile did not create meaningful realized island relief: "
                        + realizedIslandRelief + " blocks (minimum "
                        + MINIMUM_TECTONIC_ISLAND_RELIEF_BLOCKS + "), provider base-column source Y="
                        + profile.minimumY() + ".." + profile.maximumY() + " ("
                        + (profile.maximumY() - profile.minimumY()) + " blocks), captured provider surface="
                        + capturedProviderSurfaceRange + ", datum Y="
                        + providerTerrainDatumY + ", realized island Y="
                        + realizedSurface.minimumY() + ".." + realizedSurface.maximumY());
                return;
            }
            if (treatment && realizedSurface.maximumY() < MINIMUM_TECTONIC_ISLAND_RIDGE_TOP_Y) {
                fail(server, "Tectonic provider profile did not raise a visible ridge above the authored island cap: "
                        + "realized maximum Y=" + realizedSurface.maximumY()
                        + " (minimum " + MINIMUM_TECTONIC_ISLAND_RIDGE_TOP_Y + "), datum Y="
                        + providerTerrainDatumY + ", provider source Y="
                        + profile.minimumY() + ".." + profile.maximumY());
                return;
            }
            if (treatment
                    && realizedIslandColumnsAtOrAboveRidge
                            > MAXIMUM_TECTONIC_ISLAND_COLUMNS_AT_OR_ABOVE_RIDGE) {
                fail(server, "Tectonic treatment remains too plateau-like: "
                        + realizedIslandColumnsAtOrAboveRidge + "/"
                        + realizedSurface.samples() + " island columns reached ridge height Y="
                        + MINIMUM_TECTONIC_ISLAND_RIDGE_TOP_Y + " (maximum "
                        + MAXIMUM_TECTONIC_ISLAND_COLUMNS_AT_OR_ABOVE_RIDGE + "), provider source Y="
                        + profile.minimumY() + ".." + profile.maximumY() + ", realized island Y="
                        + realizedSurface.minimumY() + ".." + realizedSurface.maximumY());
                return;
            }
            int grassSurfaceColumns = countTopMaterial(islandChunk, Blocks.GRASS_BLOCK.defaultBlockState());
            String providerSurfaceRuleMaterial = "not-recomputed-on-reopen";
            String providerSurfaceRuleMaterialAdaptation = "not-recomputed-on-reopen";
            int providerSurfaceRuleMaterialColumns = 0;
            int providerSurfaceRuleMaterialUnsupportedColumns = 0;
            if (!reopening) {
                var generatedSurfaceMaterial = providerSurfaceSnapshot.mostCommonSurfaceMaterial();
                if (generatedSurfaceMaterial == null) {
                    fail(server, "selected provider produced no non-fluid surface material");
                    return;
                }
                boolean fallingProviderMaterial = BuiltInRegistries.BLOCK.get(generatedSurfaceMaterial)
                        instanceof FallingBlock;
                providerSurfaceRuleMaterialColumns = countMatchingProviderSurfaceTop(
                        islandChunk, providerSurfaceSnapshot, generatedSurfaceMaterial);
                if (fallingProviderMaterial) {
                    providerSurfaceRuleMaterialUnsupportedColumns = countUnsupportedFallingProviderSurfaceTop(
                            level, islandChunk, providerSurfaceSnapshot, generatedSurfaceMaterial);
                    if (providerSurfaceRuleMaterialUnsupportedColumns != 0) {
                        fail(server, "falling provider surface material reached unsupported island tops: "
                                + generatedSurfaceMaterial
                                + " columns=" + providerSurfaceRuleMaterialColumns
                                + " unsupported=" + providerSurfaceRuleMaterialUnsupportedColumns);
                        return;
                    }
                }
                if (!fallingProviderMaterial && providerSurfaceRuleMaterialColumns == 0) {
                    fail(server, "provider surface-rule material did not reach any admitted island top: "
                            + generatedSurfaceMaterial
                            + ", datum Y=" + providerTerrainDatumY
                            + ", source Y=" + profile.minimumY() + ".." + profile.maximumY()
                            + ", realized Y=" + realizedSurface.minimumY() + ".." + realizedSurface.maximumY());
                    return;
                }
                providerSurfaceRuleMaterial = generatedSurfaceMaterial.toString();
                providerSurfaceRuleMaterialAdaptation = fallingProviderMaterial
                        ? (providerSurfaceRuleMaterialColumns == 0
                                ? "skipped_falling_material"
                                : "supported_falling_material")
                        : "inherited";
            }

            int interiorSolidSamples = 0;
            int undersideSolidSamples = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = 97; y <= 160; y++) {
                BlockState state = level.getBlockState(cursor.set(0, y, 0));
                if (!state.isAir()) {
                    interiorSolidSamples++;
                    if (y <= 128) {
                        undersideSolidSamples++;
                    }
                }
            }
            if (interiorSolidSamples == 0 || undersideSolidSamples == 0) {
                fail(server, "Skyforge island body or underside witness was not materialized at the origin");
                return;
            }
            BlockPos reviewSpawn = findReviewSpawn(islandChunk);
            if (reviewSpawn == null) {
                fail(server, "Skyforge origin island has no safe surface position for owner review spawn");
                return;
            }
            BlockPos reviewSpawnSupportPos = reviewSpawn.below();
            BlockState reviewSpawnSupport = level.getBlockState(reviewSpawnSupportPos);
            BlockPos reviewSpawnAnchorPos = reviewSpawnSupportPos.below();
            BlockState reviewSpawnAnchor = level.getBlockState(reviewSpawnAnchorPos);
            boolean fallingSupportUnanchored = reviewSpawnSupport.getBlock() instanceof FallingBlock
                    && (reviewSpawnAnchor.getBlock() instanceof FallingBlock
                            || !reviewSpawnAnchor.isFaceSturdy(
                                    level, reviewSpawnAnchorPos, net.minecraft.core.Direction.UP));
            if (fallingSupportUnanchored
                    || !reviewSpawnSupport.isFaceSturdy(
                            level, reviewSpawnSupportPos, net.minecraft.core.Direction.UP)) {
                fail(server, "Skyforge review spawn resolved onto unstable surface support: support="
                        + reviewSpawnSupport + ", anchor=" + reviewSpawnAnchor);
                return;
            }
            // The review save is created by a dedicated server without players. Pin its shared
            // spawn to an admitted island surface so the local quick-play reviewer does not enter
            // the surrounding void. The review-client property also relocates an existing local
            // player on login, since player data can outlive a replaced downloaded save.
            level.setDefaultSpawnPos(reviewSpawn, 0.0F);

            ChunkAccess exteriorChunk = level.getChunk(new ChunkPos(EXTERIOR_CHUNK_X, 0).getMiddleBlockPosition(0));
            int exteriorNonAirBlocks = countNonAir(exteriorChunk);
            if (exteriorNonAirBlocks != 0) {
                fail(server, "void exterior chunk contains " + exteriorNonAirBlocks + " non-air blocks");
                return;
            }

            int persistedGrassColumns = countTopMaterial(islandChunk, Blocks.GRASS_BLOCK.defaultBlockState());
            int persistedMutationColumns = countTopMaterial(islandChunk, Blocks.COBBLESTONE.defaultBlockState());
            if (!treatment) {
                if (reopening) {
                    if (persistedGrassColumns == 0 || persistedMutationColumns == 0) {
                        fail(server, "provider surface or post-generation block mutation did not persist across reopen"
                                + ": grass=" + persistedGrassColumns + ", mutation=" + persistedMutationColumns);
                        return;
                    }
                } else {
                    BlockPos mutationPosition = findTopMaterial(islandChunk, Blocks.GRASS_BLOCK.defaultBlockState());
                    if (mutationPosition == null) {
                        fail(server, "vanilla provider produced no grass-block island top to mutate");
                        return;
                    }
                    level.setBlock(mutationPosition, Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put(
                    "runtimeModSet",
                    ModList.get().getMods().stream()
                            .map(mod -> mod.getModId() + "@" + mod.getVersion())
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")));
            evidence.put("profile", treatment ? "tectonic" : "vanilla");
            evidence.put("tectonicModLoaded", tectonicLoaded);
            evidence.put("tectonicPackSelected", tectonicPackSelected);
            evidence.put("tectonicVerticalScale", tectonicVerticalScale);
            evidence.put("tectonicElevationBoost", tectonicElevationBoost);
            evidence.put("tectonicContinentsScale", tectonicContinentsScale);
            evidence.put("tectonicErosionScale", tectonicErosionScale);
            evidence.put("tectonicRidgeScale", tectonicRidgeScale);
            evidence.put("tectonicFlatTerrainSkew", tectonicFlatTerrainSkew);
            evidence.put("tectonicOceanOffset", tectonicOceanOffset);
            evidence.put("terrainProviderSettings", settingsKey);
            evidence.put("terrainProfileDatumY", providerTerrainDatumY);
            evidence.put("terrainProfileDatumSampling", "world_fixed_origin_chunk_minimum");
            evidence.put("providerReliefScale", MinecraftNativeSurfaceTopAdapter.PROVIDER_RELIEF_SCALE);
            evidence.put("terrainProfileDigest", profile.digest());
            evidence.put("terrainProfileHeightDigest", profile.heightDigest());
            evidence.put("terrainProfileSamples", profile.samples());
            evidence.put("terrainProfileMinimumSurfaceY", profile.minimumY());
            evidence.put("terrainProfileMaximumSurfaceY", profile.maximumY());
            evidence.put("terrainProfileReliefRangeBlocks", profile.maximumY() - profile.minimumY());
            evidence.put("realizedIslandSurfaceDigest", realizedSurface.digest());
            evidence.put("realizedIslandSurfaceHeightDigest", realizedSurface.heightDigest());
            evidence.put("realizedIslandSurfaceColumns", realizedSurface.samples());
            evidence.put("realizedIslandSurfaceMinimumY", realizedSurface.minimumY());
            evidence.put("realizedIslandSurfaceMaximumY", realizedSurface.maximumY());
            evidence.put("realizedIslandSurfaceReliefRangeBlocks", realizedSurface.maximumY() - realizedSurface.minimumY());
            evidence.put("realizedIslandColumnsAtOrAboveRidge", realizedIslandColumnsAtOrAboveRidge);
            evidence.put("providerSurfaceRuleMaterial", providerSurfaceRuleMaterial);
            evidence.put("providerSurfaceRuleMaterialAdaptation", providerSurfaceRuleMaterialAdaptation);
            evidence.put("providerSurfaceRuleMaterialColumns", providerSurfaceRuleMaterialColumns);
            evidence.put(
                    "providerSurfaceRuleMaterialUnsupportedColumns",
                    providerSurfaceRuleMaterialUnsupportedColumns);
            evidence.put("reopenPass", reopening);
            evidence.put("persistedProviderSurfaceColumns", persistedGrassColumns);
            evidence.put("persistedPostGenerationMutationColumns", persistedMutationColumns);
            evidence.put("islandInteriorSolidSamples", interiorSolidSamples);
            evidence.put("islandUndersideSolidSamples", undersideSolidSamples);
            evidence.put("exteriorChunk", new ChunkPos(EXTERIOR_CHUNK_X, 0).toString());
            evidence.put("exteriorNonAirBlocks", exteriorNonAirBlocks);
            evidence.put("worldSeed", level.getSeed());
            evidence.put("reviewSpawn", reviewSpawn.toShortString());
            evidence.put("reviewSpawnSupport", BuiltInRegistries.BLOCK.getKey(reviewSpawnSupport.getBlock()));
            evidence.put("reviewSpawnAnchor", BuiltInRegistries.BLOCK.getKey(reviewSpawnAnchor.getBlock()));
            evidence.put("reviewSpawnSupportStable", true);
            complete = true;
            SkyforgeAutomatedAcceptanceHarness.completeServerCase(server, evidence);
        } catch (RuntimeException failure) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    server,
                    "Tectonic terrain acceptance failed: "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static String configuredTectonicVerticalScale() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject globalTerrain = config.getAsJsonObject("global_terrain");
            return globalTerrain.get("vertical_scale").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic terrain configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicElevationBoost() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject globalTerrain = config.getAsJsonObject("global_terrain");
            return globalTerrain.get("elevation_boost").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic elevation configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicContinentsScale() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject continents = config.getAsJsonObject("continents");
            return continents.get("continents_scale").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic continents configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicErosionScale() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject continents = config.getAsJsonObject("continents");
            return continents.get("erosion_scale").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic erosion configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicRidgeScale() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject continents = config.getAsJsonObject("continents");
            return continents.get("ridge_scale").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic ridge configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicFlatTerrainSkew() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject continents = config.getAsJsonObject("continents");
            return continents.get("flat_terrain_skew").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic flat-terrain configuration at "
                    + configPath, failure);
        }
    }

    private static String configuredTectonicOceanOffset() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve("tectonic.json");
        try {
            JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            JsonObject continents = config.getAsJsonObject("continents");
            return continents.get("ocean_offset").getAsString();
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the pinned Tectonic ocean configuration at "
                    + configPath, failure);
        }
    }

    private static ProfileEvidence sampleProfile(
            ServerLevel level,
            ChunkAccess chunk,
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator provider,
            RandomState randomState) {
        MessageDigest digest = newSha256();
        MessageDigest heightDigest = newSha256();
        int samples = 0;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                NoiseColumn column = provider.getBaseColumn(worldX, worldZ, level, randomState);
                for (int worldY = level.getMaxBuildHeight() - 1;
                        worldY >= level.getMinBuildHeight();
                        worldY--) {
                    BlockState state = column.getBlock(worldY);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key != null) {
                        digest.update((worldX + "," + worldY + "," + worldZ + ":" + key + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                        heightDigest.update((worldX + "," + worldY + "," + worldZ + ":" + worldY + "\n")
                                .getBytes(StandardCharsets.UTF_8));
                        samples++;
                        minimumY = Math.min(minimumY, worldY);
                        maximumY = Math.max(maximumY, worldY);
                    }
                    break;
                }
            }
        }
        return new ProfileEvidence(
                HexFormat.of().formatHex(digest.digest()),
                HexFormat.of().formatHex(heightDigest.digest()),
                samples,
                minimumY,
                maximumY);
    }

    private static String describeProviderSurfaceRange(MinecraftNativeSurfaceSnapshot snapshot) {
        int samples = 0;
        int belowDatum = 0;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                var surface = snapshot.surface(localX, localZ);
                if (surface.isEmpty()) {
                    continue;
                }
                int worldY = surface.orElseThrow().worldY();
                samples++;
                minimumY = Math.min(minimumY, worldY);
                maximumY = Math.max(maximumY, worldY);
                if (worldY <= snapshot.baselineY()) {
                    belowDatum++;
                }
            }
        }
        return "Y=" + minimumY + ".." + maximumY
                + ", samples=" + samples
                + ", at-or-below-datum=" + belowDatum;
    }

    private static BlockPos findReviewSpawn(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        int bestDistanceSquared = Integer.MAX_VALUE;
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 3;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockPos supportPos = cursor.set(worldX, worldY, worldZ).immutable();
                    BlockState support = chunk.getBlockState(supportPos);
                    if (support.isAir() || !support.getFluidState().isEmpty()
                            || !support.isFaceSturdy(chunk, supportPos, net.minecraft.core.Direction.UP)) {
                        continue;
                    }
                    if (support.getBlock() instanceof FallingBlock) {
                        BlockPos anchorPos = supportPos.below();
                        BlockState anchor = chunk.getBlockState(anchorPos);
                        if (anchor.getBlock() instanceof FallingBlock
                                || !anchor.isFaceSturdy(chunk, anchorPos, net.minecraft.core.Direction.UP)) {
                            // Skip gravity surfaces without a stable block directly below them.
                            break;
                        }
                    }
                    BlockPos feetPos = new BlockPos(worldX, worldY + 1, worldZ);
                    BlockPos headPos = new BlockPos(worldX, worldY + 2, worldZ);
                    if (chunk.getBlockState(feetPos).isAir() && chunk.getBlockState(headPos).isAir()) {
                        int distanceSquared = worldX * worldX + worldZ * worldZ;
                        if (distanceSquared < bestDistanceSquared) {
                            best = feetPos;
                            bestDistanceSquared = distanceSquared;
                        }
                    }
                    // Only consider the highest sturdy surface in this column; do not choose an
                    // interior cave floor when the island's true top has insufficient clearance.
                    break;
                }
            }
        }
        return best;
    }

    private static BlockPos findTopMaterial(ChunkAccess chunk, BlockState expected) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (expected.equals(state)) {
                        return cursor.immutable();
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static int countMatchingProviderSurfaceTop(
            ChunkAccess chunk,
            MinecraftNativeSurfaceSnapshot providerSurfaceSnapshot,
            net.minecraft.resources.ResourceLocation expected) {
        int matches = 0;
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                if (!providerSurfaceSnapshot.hasSurfaceMaterial(localX, localZ, expected)) {
                    continue;
                }
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (expected.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                        matches++;
                    }
                    break;
                }
            }
        }
        return matches;
    }

    private static int countUnsupportedFallingProviderSurfaceTop(
            ServerLevel level,
            ChunkAccess chunk,
            MinecraftNativeSurfaceSnapshot providerSurfaceSnapshot,
            net.minecraft.resources.ResourceLocation expected) {
        int unsupported = 0;
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                if (!providerSurfaceSnapshot.hasSurfaceMaterial(localX, localZ, expected)) {
                    continue;
                }
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockPos topPos = cursor.set(worldX, worldY, worldZ);
                    BlockState state = chunk.getBlockState(topPos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (expected.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                        if (worldY <= chunk.getMinBuildHeight()) {
                            unsupported++;
                        } else {
                            BlockPos supportPos = topPos.below();
                            BlockState support = chunk.getBlockState(supportPos);
                            if (support.getBlock() instanceof FallingBlock
                                    || !support.isFaceSturdy(
                                            level, supportPos, net.minecraft.core.Direction.UP)) {
                                unsupported++;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return unsupported;
    }

    private static int countTopMaterial(ChunkAccess chunk, BlockState expected) {
        int matches = 0;
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (expected.equals(state)) {
                        matches++;
                    }
                    break;
                }
            }
        }
        return matches;
    }

    private static int countSurfaceColumnsAtOrAbove(ChunkAccess chunk, int minimumSurfaceY) {
        int matches = 0;
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = chunk.getBlockState(cursor.set(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (worldY >= minimumSurfaceY) {
                        matches++;
                    }
                    break;
                }
            }
        }
        return matches;
    }

    private static ProfileEvidence captureRealizedIslandSurface(ChunkAccess chunk) {
        MessageDigest digest = newSha256();
        MessageDigest heightDigest = newSha256();
        int samples = 0;
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        ChunkPos chunkPos = chunk.getPos();
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                for (int worldY = chunk.getMaxBuildHeight() - 1;
                        worldY >= chunk.getMinBuildHeight();
                        worldY--) {
                    BlockState state = chunk.getBlockState(new BlockPos(worldX, worldY, worldZ));
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        continue;
                    }
                    var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if (key != null) {
                        digest.update((worldX + "," + worldY + "," + worldZ + ":" + key + "\\n")
                                .getBytes(StandardCharsets.UTF_8));
                        heightDigest.update((worldX + "," + worldY + "," + worldZ + ":" + worldY + "\\n")
                                .getBytes(StandardCharsets.UTF_8));
                        samples++;
                        minimumY = Math.min(minimumY, worldY);
                        maximumY = Math.max(maximumY, worldY);
                    }
                    break;
                }
            }
        }
        return new ProfileEvidence(
                HexFormat.of().formatHex(digest.digest()),
                HexFormat.of().formatHex(heightDigest.digest()),
                samples,
                minimumY,
                maximumY);
    }

    private static int countNonAir(ChunkAccess chunk) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int count = 0;
        for (int worldY = chunk.getMinBuildHeight(); worldY < chunk.getMaxBuildHeight(); worldY++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = chunk.getPos().getMinBlockZ() + localZ;
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = chunk.getPos().getMinBlockX() + localX;
                    if (!chunk.getBlockState(cursor.set(worldX, worldY, worldZ)).isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void fail(MinecraftServer server, String reason) {
        complete = true;
        SkyforgeAutomatedAcceptanceHarness.fail(server, reason);
    }

    private record ProfileEvidence(
            String digest, String heightDigest, int samples, int minimumY, int maximumY) {}
}
