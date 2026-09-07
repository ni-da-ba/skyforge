package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Presentation-only navigation for the persisted current-capability showcase world.
 *
 * <p>The viewer restores only the deterministic compiled terrain-ownership catalog required by
 * persisted generated-fluid provenance. Admission, biome/surface population, cave realization,
 * interior population, material realization, and all other mutation lifecycles remain inert.
 * Navigation therefore cannot manufacture or repair showcase content.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeNeoForge1211ShowcaseViewer {
    static final String ENABLE_PROPERTY = "skyforge.dev.showcaseViewer";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ShowcaseViewer.class.getName());
    private static final SkyforgeNeoForge1211ProductionComposedCaveFixture.Stacked FIXTURE =
            SkyforgeNeoForge1211ProductionComposedCaveFixture.stacked();

    private static AutoCloseable persistentTerrainOwnershipBinding;
    private static TrackedSample acceptanceSample;
    private static long acceptanceStartingPropagationTicks;
    private static volatile boolean acceptanceServerProofComplete;
    private static volatile String acceptanceSampleDescription = "pending";

    private static final Stop PANORAMA = new Stop(
            "panorama",
            0.0,
            320.0,
            112.0,
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
            "Lower forest surface: grass/soil plus persistent native trees and plants.");
    private static final Stop UPPER_SURFACE = new Stop(
            "upper_surface",
            36.0,
            304.0,
            38.0,
            -135.0f,
            18.0f,
            GameType.CREATIVE,
            "Upper taiga surface: independent grass/soil, trees and plants in the stacked volume.");
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
            "Legacy west approach: upper taiga ecology from the human-facing showcase configuration.");
    private static final Stop EAST_BIOME = new Stop(
            "east_biome",
            52.0,
            304.0,
            0.0,
            -90.0f,
            24.0f,
            GameType.SPECTATOR,
            "Legacy east approach: upper taiga ecology from the human-facing showcase configuration.");

    private SkyforgeNeoForge1211ShowcaseViewer() {}

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
                    "showcase viewer must begin without a live Skyforge mutation lifecycle binding");
        }

        persistentTerrainOwnershipBinding = SkyforgeNeoForge1211SurfaceStage.install(
                new SkyforgeNeoForge1211ChunkAdapter(
                        FIXTURE.catalog(),
                        SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette()),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge showcase viewer restored deterministic compiled terrain ownership only. "
                        + "Admission, surface population, cave realization, and interior population remain inert.");
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        move(player, PANORAMA);
        player.sendSystemMessage(Component.literal(
                "[Skyforge Showcase] Persisted production world loaded. Mutation bindings are inert; "
                        + "compiled terrain ownership is restored only for safe persisted fluid ticks."));
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
            provePersistedFluidReopen(level, event);
        }
    }

    private static synchronized void provePersistedFluidReopen(
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
                    "showcase viewer did not preserve ownership-only runtime isolation");
            return;
        }

        if (acceptanceSample == null) {
            acceptanceSample = firstLiveTrackedSample(level);
            if (acceptanceSample == null) {
                return;
            }
            var before = SkyforgeGeneratedFluidPropagationStage.snapshot(
                    level,
                    acceptanceSample.volumeId());
            acceptanceStartingPropagationTicks = before.propagationTicks();
            level.scheduleTick(
                    acceptanceSample.position(),
                    acceptanceSample.state().getType(),
                    1);
            return;
        }

        var after = SkyforgeGeneratedFluidPropagationStage.snapshot(
                level,
                acceptanceSample.volumeId());
        if (after.propagationTicks() <= acceptanceStartingPropagationTicks) {
            return;
        }

        acceptanceSampleDescription =
                acceptanceSample.volumeId().path() + "@" + acceptanceSample.position().toShortString();
        acceptanceServerProofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.record(
                java.util.Map.ofEntries(
                        java.util.Map.entry("viewerTerrainOwnershipRestored", true),
                        java.util.Map.entry("viewerMutationBindingsInert", true),
                        java.util.Map.entry("viewerGeneratedFluidPropagation", true),
                        java.util.Map.entry("viewerFluidSample", acceptanceSampleDescription),
                        java.util.Map.entry("viewerPropagationTicks", after.propagationTicks())));
        LOGGER.log(
                System.Logger.Level.INFO,
                "SKYFORGE SHOWCASE VIEWER SERVER PASS: persisted generated-fluid provenance ticked "
                        + "under restored compiled ownership without reinstalling mutation bindings; sample="
                        + acceptanceSampleDescription + ".");
    }

    private static TrackedSample firstLiveTrackedSample(ServerLevel level) {
        for (var volume : java.util.List.of(FIXTURE.lower(), FIXTURE.upper())) {
            for (var tracked : SkyforgeGeneratedFluidPropagationStage.trackedFluids(level, volume.id())) {
                BlockPos position = BlockPos.of(tracked.position());
                FluidState state = level.getFluidState(position);
                if (state.isEmpty()) {
                    continue;
                }
                var actualKey = BuiltInRegistries.FLUID.getKey(state.getType());
                if (tracked.fluidKey().equals(actualKey)) {
                    return new TrackedSample(volume.id(), position, state);
                }
            }
        }
        return null;
    }

    static boolean acceptanceServerProofComplete() {
        return acceptanceServerProofComplete;
    }

    static String acceptanceSampleDescription() {
        return acceptanceSampleDescription;
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[Skyforge Showcase] Guided stops:"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase panorama      - stacked-volume establishing view"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase lower_surface - lower forest ecology"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase upper_surface - upper taiga ecology"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase lower_caves   - lower composed caves/interior"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase upper_caves   - upper composed caves/interior"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase west_biome    - legacy west ecology approach"));
        player.sendSystemMessage(Component.literal(
                "  /skyforge_showcase east_biome    - legacy east ecology approach"));
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

    private record TrackedSample(
            SkyIslandWorldVolumeId volumeId,
            BlockPos position,
            FluidState state) {}

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
