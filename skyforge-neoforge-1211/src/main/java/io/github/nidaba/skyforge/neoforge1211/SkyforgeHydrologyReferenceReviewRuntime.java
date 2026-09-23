package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationKind;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Fast single-island visual loop for the hydrology overhaul.
 *
 * <p>The fixture uses the normal exact-volume terrain, hydrology, cave, native-surface, ecology and
 * interior-population stages. It differs from the DR-70 atlas only by fixing one reference island
 * and omitting corpus bookkeeping/background preparation.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeHydrologyReferenceReviewRuntime {
    private static final int TICKET_DISTANCE = 3;
    private static final int WARM_CHUNKS_PER_TICK = 16;
    private static final int FLUID_SETTLE_TICKS = 100;
    static final long FOREGROUND_PREPARATION_TIME_BUDGET_NANOS = 40_000_000L;
    private static final TicketType<ChunkPos> REVIEW_TICKET = TicketType.create(
            "skyforge_hydrology_reference_review",
            Comparator.comparingLong(ChunkPos::toLong));

    private static AutoCloseable terrainBinding;
    private static AutoCloseable admissionBinding;
    private static AutoCloseable populationBinding;
    private static AutoCloseable caveBinding;
    private static AutoCloseable interiorBinding;
    private static Preparation preparation;
    private static boolean ready;
    private static long tickCounter;

    private SkyforgeHydrologyReferenceReviewRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(SkyforgeHydrologyReferenceReviewFixture.ENABLE_PROPERTY);
    }

    static synchronized void installFromSystemProperty() {
        if (!enabled()) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "hydrology reference review requires isolated production bindings");
        }
        ready = false;
        preparation = null;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setGameMode(GameType.SPECTATOR);
        if (ready) {
            move(player, "above");
            return;
        }
        if (preparation == null) {
            installPipeline(player.getUUID());
        }
        say(player, "Preparing hydrology reference island key "
                + SkyforgeHydrologyReferenceReviewFixture.ISLAND_KEY
                + ". Commands: /skyforge_hydrology_ref status|info|above|approach|river|lake|outlet|below");
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        var root = Commands.literal("skyforge_hydrology_ref")
                .executes(context -> info(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException())))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource().getPlayerOrException())));
        for (String stop : List.of("above", "approach", "river", "lake", "outlet", "below")) {
            root.then(Commands.literal(stop)
                    .executes(context -> move(context.getSource().getPlayerOrException(), stop)));
        }
        event.getDispatcher().register(root);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled()) {
            return;
        }
        tickCounter++;

        if (preparation == null
                && !ready
                && Boolean.getBoolean(
                        SkyforgeHydrologyReferenceReviewFixture.HEADLESS_BOOTSTRAP_PROPERTY)) {
            ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
            if (level != null) {
                event.getServer().saveEverything(false, true, true);
                event.getServer().halt(false);
            }
            return;
        }

        Preparation active = preparation;
        if (active == null) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(active.playerId());
        if (player == null) {
            return;
        }

        int warmed = 0;
        while (active.cursor() < active.chunkKeys().size() && warmed < WARM_CHUNKS_PER_TICK) {
            long key = active.chunkKeys().get(active.cursor());
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            level.getChunkSource().addRegionTicket(REVIEW_TICKET, pos, TICKET_DISTANCE, pos);
            LevelChunk chunk = level.getChunk(pos.x, pos.z);
            SkyforgeNeoForge1211SurfaceStage.realize(chunk);
            active.advance();
            warmed++;
        }
        if (active.cursor() < active.chunkKeys().size()) {
            reportProgress(player, active);
            return;
        }

        if (!lifecycleReady(active.fixture())) {
            reportLifecycle(player, active);
            return;
        }
        if (active.readySinceTick() < 0L) {
            active.markReadySince(tickCounter);
            say(player, "Production lifecycle complete; settling generated fluids.");
            return;
        }
        if (tickCounter - active.readySinceTick() < FLUID_SETTLE_TICKS) {
            return;
        }
        finalizePrepared(level, player, active);
    }

    static synchronized boolean foregroundPreparationActive() {
        return enabled() && preparation != null;
    }

    static boolean suppressNativeStructureRuntime() {
        return enabled();
    }

    private static synchronized void installPipeline(UUID playerId) {
        if (preparation != null || hasBindings()) {
            throw new IllegalStateException("hydrology reference pipeline already active");
        }
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                fixture.descriptorsByVolumeId());
        terrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                terrain,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        admissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog());

        var ecology = new SkyforgeProductionEcologyResolver(
                SkyIslandAuthoredRealizationAssociation.of(
                        fixture.descriptor(),
                        fixture.volume()));
        var surfacePlan = SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                fixture.volume().id(),
                ecology,
                24);
        populationBinding = SkyforgeNativeSurfacePopulationStage.install(
                (chunkPos, minimumY, height) -> {
                    var region = new MinecraftChunkBounds(chunkPos, minimumY, height).worldBounds();
                    return fixture.catalog().query(region).isEmpty()
                            ? List.of()
                            : List.of(surfacePlan);
                });
        caveBinding = SkyforgeComposedCaveStage.install(List.of(
                new SkyforgeComposedCavePlan(fixture.volume(), fixture.caveField())));
        interiorBinding = SkyforgeNativeInteriorPopulationStage.install(List.of(
                SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(
                        fixture.volume().id(),
                        0)));

        List<Long> chunkKeys = new ArrayList<>(
                SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(fixture.volume().id()));
        chunkKeys.sort(Comparator
                .comparingLong((Long key) -> squaredChunkDistance(key, 0, 0))
                .thenComparingInt(ChunkPos::getX)
                .thenComparingInt(ChunkPos::getZ));
        preparation = new Preparation(fixture, List.copyOf(chunkKeys), playerId);
    }

    private static boolean lifecycleReady(
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture) {
        var volumeId = fixture.volume().id();
        if (SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId).state()
                != SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
            return false;
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage
                        .pendingBiomePresentationChunks(volumeId)
                        .isEmpty()) {
            return false;
        }
        if (SkyforgeComposedCaveStage.snapshot(volumeId).pendingObligations() != 0) {
            return false;
        }
        return SkyforgeNativeInteriorPopulationStage.snapshot(volumeId).pendingObligations() == 0;
    }

    private static synchronized void finalizePrepared(
            ServerLevel level,
            ServerPlayer player,
            Preparation active) {
        if (!level.getServer().saveEverything(false, true, true)) {
            throw new IllegalStateException("hydrology reference review failed to save prepared island");
        }
        releaseTickets(level, active.chunkKeys());
        closePipeline();
        ready = true;
        move(player, "above");
        say(player, "READY. Inspect river geometry, lake/spill behavior, edge discharge, banks, "
                + "native river surfacing, cave mouths and vegetation support.");
    }

    private static int status(ServerPlayer player) {
        if (ready) {
            say(player, "READY.");
            return 1;
        }
        Preparation active = preparation;
        if (active == null) {
            say(player, "Waiting for preparation to start.");
            return 1;
        }
        var volumeId = active.fixture().volume().id();
        say(player, "warm=" + active.cursor() + "/" + active.chunkKeys().size()
                + ", admission=" + SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId).state()
                + ", terrainPending="
                + SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).size()
                + ", surfacePending="
                + SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId).size()
                + ", cavePending="
                + SkyforgeComposedCaveStage.snapshot(volumeId).pendingObligations()
                + ", interiorPending="
                + SkyforgeNativeInteriorPopulationStage.snapshot(volumeId).pendingObligations());
        return 1;
    }

    private static int info(ServerPlayer player) {
        var fixture = preparation != null
                ? preparation.fixture()
                : SkyforgeHydrologyReferenceReviewFixture.create();
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        say(player, "key=" + SkyforgeHydrologyReferenceReviewFixture.ISLAND_KEY
                + ", morphology=" + fixture.descriptor().morphologyFamily().identifier()
                + ", radius=" + String.format(Locale.ROOT, "%.1f", fixture.descriptor().nominalRadius())
                + ", channels=" + hydrology.channels().size()
                + ", retainedWater=" + hydrology.retainedWater().size()
                + ", interiorDrops="
                + hydrology.drops().stream()
                        .filter(drop -> drop.kind()
                                != SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                        .count()
                + ", edgeDischarges="
                + hydrology.drops().stream()
                        .filter(drop -> drop.kind()
                                == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                        .count());
        return 1;
    }

    private static int move(ServerPlayer player, String stopName) {
        if (!ready) {
            say(player, "Reference island is still preparing.");
            return 0;
        }
        Stop stop = stop(player.serverLevel(), stopName);
        if (stop == null) {
            say(player, "No authored " + stopName + " stop exists on this reference island.");
            return 0;
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        say(player, stopName + ": " + stop.description());
        return 1;
    }

    private static Stop stop(ServerLevel level, String name) {
        var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
        double radius = fixture.descriptor().nominalRadius();
        return switch (name) {
            case "above" -> new Stop(
                    0.0,
                    Math.min(315.0, SkyforgeHydrologyReferenceReviewFixture.SUSPENSION_Y + 92.0),
                    0.0,
                    0.0f,
                    90.0f,
                    "planform, drainage network, retained water and valley shape");
            case "approach" -> new Stop(
                    0.0,
                    SkyforgeHydrologyReferenceReviewFixture.SUSPENSION_Y + 24.0,
                    radius * 1.45 + 48.0,
                    180.0f,
                    8.0f,
                    "silhouette, exposed banks and river-to-terrain integration");
            case "river" -> riverStop(level, fixture);
            case "lake" -> lakeStop(level, fixture);
            case "outlet" -> outletStop(level, fixture);
            case "below" -> new Stop(
                    0.0,
                    SkyforgeHydrologyReferenceReviewFixture.SUSPENSION_Y - 132.0,
                    radius * 0.15,
                    0.0f,
                    -72.0f,
                    "underside continuity and cave/hydrology interactions");
            default -> throw new IllegalArgumentException("unknown hydrology reference stop " + name);
        };
    }

    private static Stop riverStop(
            ServerLevel level,
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture) {
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        var channel = hydrology.channels().stream()
                .max(Comparator.comparingDouble(intent ->
                        intent.path().profile().segment().relativeDischarge()))
                .orElse(null);
        if (channel == null || channel.path().points().isEmpty()) {
            return null;
        }
        var point = channel.path().points().get(channel.path().points().size() / 2);
        return localSurfaceStop(
                level,
                point.x(),
                point.z(),
                18.0,
                135.0f,
                14.0f,
                "largest-discharge reach: bed, banks, native river surface and valley section");
    }

    private static Stop lakeStop(
            ServerLevel level,
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture) {
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        var retained = hydrology.retainedWater().stream()
                .max(Comparator.comparingInt(intent -> intent.footprint().cells().size()))
                .orElse(null);
        if (retained == null || retained.footprint().cells().isEmpty()) {
            return null;
        }
        double x = retained.footprint().cells().stream()
                .mapToDouble(cell -> cell.position().x())
                .average()
                .orElseThrow();
        double z = retained.footprint().cells().stream()
                .mapToDouble(cell -> cell.position().z())
                .average()
                .orElseThrow();
        return localSurfaceStop(
                level,
                x,
                z,
                22.0,
                135.0f,
                18.0f,
                "largest retained basin: shoreline, level surface, spillway and riparian transition");
    }

    private static Stop outletStop(
            ServerLevel level,
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture) {
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(fixture.descriptor());
        var outlet = hydrology.drops().stream()
                .filter(drop ->
                        drop.kind() == SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE)
                .max(Comparator.comparingDouble(drop -> drop.drop().dischargePotential()))
                .orElse(null);
        if (outlet == null) {
            return null;
        }
        var point = outlet.drop().position();
        return localSurfaceStop(
                level,
                point.x(),
                point.z(),
                26.0,
                135.0f,
                8.0f,
                "authored edge discharge: outlet gorge/lip, containment and Lower-Sea fall");
    }

    private static Stop localSurfaceStop(
            ServerLevel level,
            double localX,
            double localZ,
            double offset,
            float yaw,
            float pitch,
            String description) {
        int worldX = (int) Math.round(localX);
        int worldZ = (int) Math.round(localZ);
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        return new Stop(
                worldX + offset,
                surfaceY + offset,
                worldZ + offset,
                yaw,
                pitch,
                description);
    }

    private static void reportProgress(ServerPlayer player, Preparation active) {
        if (tickCounter % 20L == 0L) {
            say(player, "chunk warmup " + active.cursor() + "/" + active.chunkKeys().size());
        }
    }

    private static void reportLifecycle(ServerPlayer player, Preparation active) {
        if (tickCounter % 40L == 0L) {
            status(player);
        }
    }

    private static long squaredChunkDistance(long key, int centerChunkX, int centerChunkZ) {
        long dx = (long) ChunkPos.getX(key) - centerChunkX;
        long dz = (long) ChunkPos.getZ(key) - centerChunkZ;
        return dx * dx + dz * dz;
    }

    private static void releaseTickets(ServerLevel level, List<Long> chunkKeys) {
        for (long key : chunkKeys) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            level.getChunkSource().removeRegionTicket(REVIEW_TICKET, pos, TICKET_DISTANCE, pos);
        }
    }

    private static boolean hasBindings() {
        return terrainBinding != null
                || admissionBinding != null
                || populationBinding != null
                || caveBinding != null
                || interiorBinding != null;
    }

    private static synchronized void closePipeline() {
        closeBinding(interiorBinding, "interior");
        interiorBinding = null;
        closeBinding(caveBinding, "cave");
        caveBinding = null;
        closeBinding(populationBinding, "surface population");
        populationBinding = null;
        closeBinding(admissionBinding, "admission");
        admissionBinding = null;
        closeBinding(terrainBinding, "terrain");
        terrainBinding = null;
        preparation = null;
    }

    private static void closeBinding(AutoCloseable binding, String name) {
        if (binding == null) {
            return;
        }
        try {
            binding.close();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "failed to close hydrology reference " + name + " binding",
                    exception);
        }
    }

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[Hydrology Ref] " + message));
    }

    private record Stop(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String description) {}

    private static final class Preparation {
        private final SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture;
        private final List<Long> chunkKeys;
        private final UUID playerId;
        private int cursor;
        private long readySinceTick = -1L;

        private Preparation(
                SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture,
                List<Long> chunkKeys,
                UUID playerId) {
            this.fixture = fixture;
            this.chunkKeys = chunkKeys;
            this.playerId = playerId;
        }

        SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture() {
            return fixture;
        }

        List<Long> chunkKeys() {
            return chunkKeys;
        }

        UUID playerId() {
            return playerId;
        }

        int cursor() {
            return cursor;
        }

        long readySinceTick() {
            return readySinceTick;
        }

        void advance() {
            cursor++;
        }

        void markReadySince(long value) {
            readySinceTick = value;
        }
    }
}
