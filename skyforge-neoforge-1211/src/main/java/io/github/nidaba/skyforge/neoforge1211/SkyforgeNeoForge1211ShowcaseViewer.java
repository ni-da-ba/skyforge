package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Presentation-only navigation for the persisted current-capability showcase world.
 *
 * <p>This class installs no terrain, admission, biome, cave, population, material, or fluid
 * binding. It exists only in a ModDev run after the accepted production world has been generated
 * and saved. Its commands therefore cannot manufacture or repair showcase content.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ShowcaseViewer {
    static final String ENABLE_PROPERTY = "skyforge.dev.showcaseViewer";

    private static final Stop PANORAMA = new Stop(
            "panorama",
            0.0,
            320.0,
            165.0,
            180.0f,
            14.0f,
            GameType.SPECTATOR,
            "Both vertically aligned production volumes in one frame.");
    private static final Stop LOWER_SURFACE = new Stop(
            "lower_surface",
            -36.0,
            214.0,
            38.0,
            135.0f,
            18.0f,
            GameType.CREATIVE,
            "Lower island surface: native surface adaptation and production ecology.");
    private static final Stop UPPER_SURFACE = new Stop(
            "upper_surface",
            36.0,
            304.0,
            38.0,
            -135.0f,
            18.0f,
            GameType.CREATIVE,
            "Upper island surface: independently admitted/populated stacked volume.");
    private static final Stop LOWER_CAVES = new Stop(
            "lower_caves",
            0.0,
            170.0,
            0.0,
            0.0f,
            0.0f,
            GameType.SPECTATOR,
            "Lower interior: native cave AIR plus authored exterior-connected cave topology.");
    private static final Stop UPPER_CAVES = new Stop(
            "upper_caves",
            0.0,
            260.0,
            0.0,
            0.0f,
            0.0f,
            GameType.SPECTATOR,
            "Upper interior: independent cave/geology/decoration/fluid realization.");
    private static final Stop WEST_BIOME = new Stop(
            "west_biome",
            -52.0,
            304.0,
            0.0,
            90.0f,
            24.0f,
            GameType.SPECTATOR,
            "West half of the exact-volume biome split (minecraft:river).");
    private static final Stop EAST_BIOME = new Stop(
            "east_biome",
            52.0,
            304.0,
            0.0,
            -90.0f,
            24.0f,
            GameType.SPECTATOR,
            "East half of the exact-volume biome split (minecraft:dripstone_caves).");

    private SkyforgeNeoForge1211ShowcaseViewer() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        move(player, PANORAMA);
        player.sendSystemMessage(Component.literal(
                "[Skyforge Showcase] Persisted production world loaded. Generation bindings are intentionally inert."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Showcase] Use /skyforge_showcase for the guided stops."));
        player.sendSystemMessage(Component.literal(
                "[Skyforge Showcase] Review guide: docs/showcase/current-capability-showcase.md"));
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        event.getDispatcher().register(
                Commands.literal("skyforge_showcase")
                        .executes(context -> showHelp(context.getSource().getPlayerOrException()))
                        .then(Commands.literal(PANORAMA.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), PANORAMA)))
                        .then(Commands.literal(LOWER_SURFACE.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), LOWER_SURFACE)))
                        .then(Commands.literal(UPPER_SURFACE.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), UPPER_SURFACE)))
                        .then(Commands.literal(LOWER_CAVES.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), LOWER_CAVES)))
                        .then(Commands.literal(UPPER_CAVES.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), UPPER_CAVES)))
                        .then(Commands.literal(WEST_BIOME.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), WEST_BIOME)))
                        .then(Commands.literal(EAST_BIOME.name())
                                .executes(context -> move(context.getSource().getPlayerOrException(), EAST_BIOME))));
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Skyforge Showcase] Guided stops:"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase panorama      - stacked-volume establishing view"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase lower_surface - lower production surface/ecology"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase upper_surface - upper production surface/ecology"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase lower_caves   - lower composed caves/interior"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase upper_caves   - upper composed caves/interior"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase west_biome    - river side of exact-volume biome split"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase east_biome    - dripstone-caves side of biome split"));
        return 1;
    }

    private static int move(ServerPlayer player, Stop stop) {
        player.setGameMode(stop.gameType());
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        player.sendSystemMessage(Component.literal(
                "[Skyforge Showcase] " + stop.name() + ": " + stop.description()));
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
