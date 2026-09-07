package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Mutation-inert viewer for the persisted SF-IMP-0080 forest/taiga ecology specimen.
 *
 * <p>The viewer restores only the deterministic compiled terrain-ownership catalog. Physical
 * admission, native population, and biome-presentation mutation remain absent, so the automated
 * reopen proof observes only state that survived the dedicated preparation server and disk save.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ShowcaseEcologyViewer {
    static final String ENABLE_PROPERTY = "skyforge.dev.showcaseEcologyViewer";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ShowcaseEcologyViewer.class.getName());

    private static final Stop PANORAMA = new Stop(
            "panorama",
            0.0,
            304.0,
            190.0,
            180.0f,
            12.0f,
            GameType.SPECTATOR,
            "Broad stacked forest and taiga TABLELAND ecology specimen.");
    private static final Stop LOWER_FOREST = new Stop(
            "lower_forest",
            -44.0,
            238.0,
            44.0,
            135.0f,
            24.0f,
            GameType.CREATIVE,
            "Lower forest surface: inspect land substrate, trees, foliage, and non-tree plants.");
    private static final Stop UPPER_TAIGA = new Stop(
            "upper_taiga",
            -44.0,
            308.0,
            44.0,
            135.0f,
            24.0f,
            GameType.CREATIVE,
            "Upper taiga surface: inspect the independent spruce/taiga ecology and compare with forest.");

    private static AutoCloseable persistentTerrainOwnershipBinding;
    private static volatile boolean acceptanceServerProofComplete;
    private static volatile String acceptanceEvidence = "pending";

    private SkyforgeNeoForge1211ShowcaseEcologyViewer() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled() || persistentTerrainOwnershipBinding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "SF-IMP-0080 ecology viewer must begin without a live Skyforge mutation lifecycle");
        }

        var catalog = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.catalog();
        persistentTerrainOwnershipBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        catalog,
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0080 ecology viewer restored compiled terrain ownership only; "
                        + "admission, population, and biome-presentation mutation remain inert.");
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        move(player, PANORAMA);
        player.sendSystemMessage(Component.literal(
                "[Skyforge Ecology] Persisted forest/taiga specimen loaded with mutation bindings inert."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Ecology] Use /skyforge_ecology for the guided review stops."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Ecology] Review guide: docs/showcase/ecology-showcase.md"));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        event.getDispatcher().register(
                Commands.literal("skyforge_ecology")
                        .executes(context -> showHelp(context.getSource().getPlayerOrException()))
                        .then(Commands.literal(PANORAMA.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), PANORAMA)))
                        .then(Commands.literal(LOWER_FOREST.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), LOWER_FOREST)))
                        .then(Commands.literal(UPPER_TAIGA.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), UPPER_TAIGA))));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()
                || acceptanceServerProofComplete) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (!level.dimension().equals(Level.OVERWORLD) || level.players().isEmpty()) {
                continue;
            }
            provePersistedEcology(level, event);
        }
    }

    private static synchronized void provePersistedEcology(
            ServerLevel level,
            ServerTickEvent.Post event) {
        if (acceptanceServerProofComplete) {
            return;
        }
        if (!SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 ecology viewer did not preserve ownership-only runtime isolation");
            return;
        }

        var catalog = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.catalog();
        SkyIslandWorldVolumeId lowerId =
                SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.lowerVolumeId(catalog);
        SkyIslandWorldVolumeId upperId =
                SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.upperVolumeId(catalog);
        var lower = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.scanLoadedEcology(level, lowerId);
        var upper = SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.scanLoadedEcology(level, upperId);
        if (lower.isEmpty() || upper.isEmpty()) {
            return;
        }
        if (!lower.orElseThrow().legible() || !upper.orElseThrow().legible()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 persisted ecology disappeared on actual-client reopen: lower="
                            + lower.orElseThrow() + ", upper=" + upper.orElseThrow());
            return;
        }
        if (!SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.persistedBiomeMatches(
                        level, lowerId, Biomes.FOREST)
                || !SkyforgeNeoForge1211ShowcaseEcologyDevRuntime.persistedBiomeMatches(
                        level, upperId, Biomes.TAIGA)) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0080 persisted forest/taiga biome presentation disappeared on actual-client reopen");
            return;
        }

        acceptanceEvidence = "lower=" + lower.orElseThrow() + "; upper=" + upper.orElseThrow();
        acceptanceServerProofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.ofEntries(
                        java.util.Map.entry("viewerTerrainOwnershipRestored", true),
                        java.util.Map.entry("viewerMutationBindingsInert", true),
                        java.util.Map.entry("viewerPersistentForest", true),
                        java.util.Map.entry("viewerPersistentTaiga", true),
                        java.util.Map.entry("viewerLowerSubstrate", lower.orElseThrow().substrateBlocks()),
                        java.util.Map.entry("viewerLowerLogs", lower.orElseThrow().logBlocks()),
                        java.util.Map.entry("viewerLowerLeaves", lower.orElseThrow().leafBlocks()),
                        java.util.Map.entry("viewerLowerPlants", lower.orElseThrow().plantBlocks()),
                        java.util.Map.entry("viewerUpperSubstrate", upper.orElseThrow().substrateBlocks()),
                        java.util.Map.entry("viewerUpperLogs", upper.orElseThrow().logBlocks()),
                        java.util.Map.entry("viewerUpperLeaves", upper.orElseThrow().leafBlocks()),
                        java.util.Map.entry("viewerUpperPlants", upper.orElseThrow().plantBlocks())));
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0080 SHOWCASE ECOLOGY VIEWER SERVER PASS: persisted final-world ecology and biome "
                        + "presentation survived reopen with mutation bindings inert; " + acceptanceEvidence);
    }

    static boolean acceptanceServerProofComplete() {
        return acceptanceServerProofComplete;
    }

    static String acceptanceEvidence() {
        return acceptanceEvidence;
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Skyforge Ecology] Guided stops:"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_ecology panorama     - stacked forest/taiga establishing view"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_ecology lower_forest - lower forest land ecology"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_ecology upper_taiga  - upper taiga land ecology"));
        return 1;
    }

    private static int move(ServerPlayer player, Stop stop) {
        player.setGameMode(stop.gameType());
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        player.sendSystemMessage(Component.literal(
                "[Skyforge Ecology] " + stop.name() + ": " + stop.description()));
        return 1;
    }

    private record Stop(
            String name,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            GameType gameType,
            String description) {}
}
