package io.github.nidaba.skyforge.neoforge1211;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated dedicated-server A/B probe for Tectonic's active Overworld terrain profile. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeTectonicTerrainAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.tectonicTerrain";
    static final String TREATMENT_PROPERTY = ENABLE_PROPERTY + ".treatment";
    private static final String REVIEW_SPAWN_PROPERTY = ENABLE_PROPERTY + ".reviewSpawn";
    private static final int EXTERIOR_CHUNK_X = 12;
    private static final int PROFILE_RADIUS = 8;
    private static boolean complete;

    private SkyforgeTectonicTerrainAcceptance() {}

    static void installFromSystemProperty() {
        // This fixture is entirely inert unless a dedicated ModDev run opts in.
    }

    @SubscribeEvent
    static void placeReviewPlayerAtSavedSpawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean(REVIEW_SPAWN_PROPERTY)
                || !(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
                || !player.level().dimension().equals(Level.OVERWORLD)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        boolean teleported = player.teleportTo(
                level,
                spawn.getX() + 0.5,
                spawn.getY(),
                spawn.getZ() + 0.5,
                java.util.Set.of(),
                player.getYRot(),
                player.getXRot());
        java.util.logging.Logger.getLogger(SkyforgeTectonicTerrainAcceptance.class.getName())
                .info("Tectonic terrain review spawn relocation: success="
                        + teleported
                        + ", target="
                        + spawn.toShortString()
                        + ", player="
                        + player.blockPosition().toShortString());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || complete
                || !SkyforgeAutomatedAcceptanceHarness.serverMode()
                || !SkyforgeAutomatedAcceptanceHarness.warmupComplete()) {
            return;
        }

        MinecraftServer server = event.getServer();
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
            int grassSurfaceColumns = countTopMaterial(islandChunk, Blocks.GRASS_BLOCK.defaultBlockState());
            boolean reopening = Boolean.getBoolean(ENABLE_PROPERTY + ".reopen");
            String providerSurfaceRuleMaterial = "not-recomputed-on-reopen";
            int providerSurfaceRuleMaterialColumns = 0;
            if (!reopening) {
                MinecraftNativeSurfaceSnapshot providerSurfaceSnapshot =
                        SkyforgeNativeSurfaceSnapshotStage.consumeAcceptanceSnapshot(islandChunk)
                                .orElseThrow(() -> new IllegalStateException(
                                        "provider surface snapshot was not retained for the acceptance fixture"));
                var generatedSurfaceMaterial = providerSurfaceSnapshot.mostCommonSurfaceMaterial();
                if (generatedSurfaceMaterial == null) {
                    fail(server, "selected provider produced no non-fluid surface material");
                    return;
                }
                providerSurfaceRuleMaterialColumns = countMatchingProviderSurfaceTop(
                        islandChunk, providerSurfaceSnapshot, generatedSurfaceMaterial);
                if (providerSurfaceRuleMaterialColumns == 0) {
                    fail(server, "provider surface-rule material did not reach any admitted island top: "
                            + generatedSurfaceMaterial);
                    return;
                }
                providerSurfaceRuleMaterial = generatedSurfaceMaterial.toString();
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
            evidence.put("terrainProviderSettings", settingsKey);
            evidence.put("terrainProfileDigest", profile.digest());
            evidence.put("terrainProfileSamples", profile.samples());
            evidence.put("realizedIslandSurfaceDigest", realizedSurface.digest());
            evidence.put("realizedIslandSurfaceColumns", realizedSurface.samples());
            evidence.put("providerSurfaceRuleMaterial", providerSurfaceRuleMaterial);
            evidence.put("providerSurfaceRuleMaterialColumns", providerSurfaceRuleMaterialColumns);
            evidence.put("reopenPass", reopening);
            evidence.put("persistedProviderSurfaceColumns", persistedGrassColumns);
            evidence.put("persistedPostGenerationMutationColumns", persistedMutationColumns);
            evidence.put("islandInteriorSolidSamples", interiorSolidSamples);
            evidence.put("islandUndersideSolidSamples", undersideSolidSamples);
            evidence.put("exteriorChunk", new ChunkPos(EXTERIOR_CHUNK_X, 0).toString());
            evidence.put("exteriorNonAirBlocks", exteriorNonAirBlocks);
            evidence.put("worldSeed", level.getSeed());
            evidence.put("reviewSpawn", reviewSpawn.toShortString());
            complete = true;
            SkyforgeAutomatedAcceptanceHarness.completeServerCase(server, evidence);
        } catch (RuntimeException failure) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    server,
                    "Tectonic terrain acceptance failed: "
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static ProfileEvidence sampleProfile(
            ServerLevel level,
            ChunkAccess chunk,
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator provider,
            RandomState randomState) {
        MessageDigest digest = newSha256();
        int samples = 0;
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
                        samples++;
                    }
                    break;
                }
            }
        }
        return new ProfileEvidence(HexFormat.of().formatHex(digest.digest()), samples);
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

    private static ProfileEvidence captureRealizedIslandSurface(ChunkAccess chunk) {
        MessageDigest digest = newSha256();
        int samples = 0;
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
                        samples++;
                    }
                    break;
                }
            }
        }
        return new ProfileEvidence(HexFormat.of().formatHex(digest.digest()), samples);
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

    private record ProfileEvidence(String digest, int samples) {}
}
