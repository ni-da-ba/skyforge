package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.WorldBounds;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Mutation-inert actual-client viewer for the first AUTH-0083 Minecraft morphology specimen. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ProductionMorphologyMassifViewer {
    static final String ENABLE_PROPERTY = "skyforge.dev.productionMorphologyMassifViewer";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologyMassifViewer.class.getName());

    private static AutoCloseable persistentTerrainOwnershipBinding;
    private static volatile boolean acceptanceServerProofComplete;
    private static volatile String acceptanceEvidence = "pending";

    private SkyforgeNeoForge1211ProductionMorphologyMassifViewer() {}

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
                    "SF-IMP-0081 morphology viewer must begin without a live Skyforge mutation lifecycle");
        }

        var fixture = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.fixture();
        persistentTerrainOwnershipBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0081 morphology viewer restored exact AUTH-0083 terrain ownership only: member="
                        + fixture.memberId());
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        move(player, stop("above"));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Morphology] AUTH-0083 member "
                        + SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.MEMBER_ID
                        + " loaded with mutation bindings inert."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Morphology] Use /skyforge_morphology for above / approach / below / orbit review stops."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Morphology] Review guide: docs/showcase/production-morphology-massif.md"));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        event.getDispatcher().register(
                Commands.literal("skyforge_morphology")
                        .executes(context -> showHelp(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("above")
                                .executes(context -> move(
                                        context.getSource().getPlayerOrException(),
                                        stop("above"))))
                        .then(Commands.literal("approach")
                                .executes(context -> move(
                                        context.getSource().getPlayerOrException(),
                                        stop("approach"))))
                        .then(Commands.literal("below")
                                .executes(context -> move(
                                        context.getSource().getPlayerOrException(),
                                        stop("below"))))
                        .then(Commands.literal("orbit")
                                .executes(context -> move(
                                        context.getSource().getPlayerOrException(),
                                        stop("orbit")))));
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
            provePersistedMorphology(level, event);
        }
    }

    private static synchronized void provePersistedMorphology(
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
                    "SF-IMP-0081 morphology viewer did not preserve ownership-only runtime isolation");
            return;
        }

        var surface = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.scanLoadedSurface(level);
        if (surface.isEmpty()) {
            return;
        }
        if (!surface.orElseThrow().valid()) {
            SkyforgeAutomatedAcceptanceHarness.fail(
                    event.getServer(),
                    "SF-IMP-0081 production Massif changed across persistence/reopen: "
                            + surface.orElseThrow());
            return;
        }

        var evidence = surface.orElseThrow();
        acceptanceEvidence = evidence.toString();
        acceptanceServerProofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.ofEntries(
                        java.util.Map.entry(
                                "viewerMemberId",
                                SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.MEMBER_ID),
                        java.util.Map.entry("viewerTerrainOwnershipRestored", true),
                        java.util.Map.entry("viewerMutationBindingsInert", true),
                        java.util.Map.entry("viewerSampledClaims", evidence.claimedColumns()),
                        java.util.Map.entry("viewerStoredTop", evidence.storedTopBlocks()),
                        java.util.Map.entry("viewerAirAbove", evidence.airAboveColumns()),
                        java.util.Map.entry("viewerStoredUnderside", evidence.storedUndersideBlocks()),
                        java.util.Map.entry("viewerAirBelow", evidence.airBelowColumns()),
                        java.util.Map.entry("viewerHeightMismatches", evidence.heightMismatches()),
                        java.util.Map.entry("viewerLandTop", evidence.landTopBlocks()),
                        java.util.Map.entry("viewerGrassTop", evidence.grassTopBlocks()),
                        java.util.Map.entry("viewerSurfaceDigest", Long.toUnsignedString(evidence.digest()))));
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0081 PRODUCTION MORPHOLOGY VIEWER SERVER PASS: persisted exact member survived reopen; "
                        + acceptanceEvidence);
    }

    static boolean acceptanceServerProofComplete() {
        return acceptanceServerProofComplete;
    }

    static String acceptanceEvidence() {
        return acceptanceEvidence;
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Skyforge Morphology] Guided #214 stops:"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_morphology above    - high planform / upper-surface read"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_morphology approach - horizon and rim profile"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_morphology below    - underside elevation / low-angle read"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_morphology orbit    - oblique starting point for an around-and-under flight"));
        return 1;
    }

    private static int move(ServerPlayer player, Stop stop) {
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        player.sendSystemMessage(Component.literal(
                "[Skyforge Morphology] " + stop.name() + ": " + stop.description()));
        return 1;
    }

    private static Stop stop(String name) {
        var fixture = SkyforgeNeoForge1211ProductionMorphologyMassifDevRuntime.fixture();
        WorldBounds bounds = fixture.exactSupport().bounds();
        double centerX = (bounds.minimumX() + bounds.maximumX()) * 0.5;
        double centerZ = (bounds.minimumZ() + bounds.maximumZ()) * 0.5;
        double suspension = fixture.translatedDescriptor().suspensionElevation();
        double width = bounds.maximumX() - bounds.minimumX() + 1.0;
        double depth = bounds.maximumZ() - bounds.minimumZ() + 1.0;

        return switch (name) {
            case "above" -> new Stop(
                    name,
                    centerX,
                    Math.min(316.0, bounds.maximumY() + 58.0),
                    centerZ,
                    0.0f,
                    90.0f,
                    "Planform, upper-surface hierarchy, and long-range family identity.");
            case "approach" -> new Stop(
                    name,
                    centerX,
                    suspension + 18.0,
                    bounds.maximumZ() + Math.max(72.0, depth * 0.45),
                    180.0f,
                    8.0f,
                    "Horizon approach: inspect silhouette, rim transition, macro and meso form.");
            case "below" -> new Stop(
                    name,
                    centerX,
                    Math.max(-56.0, bounds.minimumY() - 46.0),
                    centerZ + depth * 0.18,
                    0.0f,
                    -72.0f,
                    "Underside view: judge taper, asymmetry, deliberate form, and detached-spike pathologies.");
            case "orbit" -> new Stop(
                    name,
                    bounds.maximumX() + Math.max(60.0, width * 0.30),
                    suspension - 18.0,
                    bounds.maximumZ() + Math.max(60.0, depth * 0.30),
                    135.0f,
                    -4.0f,
                    "Oblique orbit start: fly around the rim, descend, and pass underneath.");
            default -> throw new IllegalArgumentException("unknown morphology review stop: " + name);
        };
    }

    private record Stop(
            String name,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String description) {}
}
