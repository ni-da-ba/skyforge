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
    // A radius-0 region ticket is sufficient to promote the target to a stable FULL LevelChunk.
    // Radius 3 is an entity-ticking-strength ticket and causes a much larger generated/resident
    // neighborhood than this review harness needs.
    private static final int TICKET_RADIUS = 0;
    private static final int WARM_TICKET_WINDOW = 8;
    private static final int WARM_CHUNKS_PER_TICK = 4;
    private static final int FLUID_SETTLE_TICKS = 100;
    private static final long WATCHDOG_STALL_NANOS = 5_000_000_000L;
    private static final long WATCHDOG_REPEAT_NANOS = 10_000_000_000L;
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
    private static volatile PipelineBootstrap completedBootstrap;
    private static volatile Throwable bootstrapFailure;
    private static volatile boolean bootstrapStarted;
    private static volatile long bootstrapStartedNanos;
    private static volatile long bootstrapLastDumpNanos;
    private static volatile Thread bootstrapThread;
    private static boolean ready;
    private static long tickCounter;
    private static volatile Thread watchdogServerThread;
    private static volatile long watchdogHeartbeatNanos;
    private static volatile long watchdogLastDumpNanos;
    private static volatile String watchdogStage = "idle";
    private static volatile String watchdogPhase = "none";
    private static volatile int watchdogCursor = -1;
    private static volatile long watchdogChunkKey = Long.MIN_VALUE;
    private static volatile boolean watchdogStarted;

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
        completedBootstrap = null;
        bootstrapFailure = null;
        bootstrapStarted = false;
        bootstrapStartedNanos = 0L;
        bootstrapLastDumpNanos = 0L;
        bootstrapThread = null;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setGameMode(GameType.SPECTATOR);
        if (!ready) {
            // Keep the reviewer at the eventual overhead inspection location while immutable
            // authored hydrology is prepared; the blank bootstrap world otherwise spawns near the
            // void floor and looks like the review fixture failed to load.
            player.teleportTo(
                    0.0,
                    Math.min(315.0, SkyforgeHydrologyReferenceReviewFixture.SUSPENSION_Y + 92.0),
                    0.0);
        }
        if (ready) {
            move(player, "above");
            return;
        }
        if (preparation == null && !bootstrapStarted) {
            beginPipelineBootstrap(player.getUUID());
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
        watchdogServerThread = Thread.currentThread();
        watchdogHeartbeatNanos = System.nanoTime();
        watchdogStage = "server-tick-entry";

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
            if (bootstrapFailure != null) {
                throw new IllegalStateException("hydrology reference bootstrap failed", bootstrapFailure);
            }
            PipelineBootstrap bootstrap = completedBootstrap;
            if (bootstrap != null) {
                installPreparedPipeline(bootstrap, event.getServer());
                completedBootstrap = null;
                active = preparation;
            } else {
                reportBootstrapProgress(event);
                return;
            }
        }
        markWatchdog(active, "review-handler");
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }
        ServerPlayer player = event.getServer().getPlayerList().getPlayer(active.playerId());
        if (player == null) {
            return;
        }

        // Keep a small FULL-chunk look-ahead window live. One-ticket-at-a-time warmup creates
        // head-of-line blocking, while stronger/radius-3 tickets promote unnecessary neighboring
        // chunks into the simulation graph. Radius 0 + an eight-chunk window preserves generation
        // pipelining without turning the reference sweep into a moving force-loaded region.
        markWatchdog(active, "ticket-window");
        int ticketWindowEnd = Math.min(
                active.chunkKeys().size(),
                active.cursor() + WARM_TICKET_WINDOW);
        for (int index = active.cursor(); index < ticketWindowEnd; index++) {
            long ticketKey = active.chunkKeys().get(index);
            ChunkPos ticketPos =
                    new ChunkPos(ChunkPos.getX(ticketKey), ChunkPos.getZ(ticketKey));
            level.getChunkSource().addRegionTicket(
                    REVIEW_TICKET, ticketPos, TICKET_RADIUS, ticketPos);
        }

        int advanced = 0;
        while (active.cursor() < active.chunkKeys().size() && advanced < WARM_CHUNKS_PER_TICK) {
            long key = active.chunkKeys().get(active.cursor());
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));

            // Admission needs every exact footprint chunk to be observed, but it does not need
            // already-consumed chunks retained. Catch-up and downstream phases likewise release
            // each cursor ticket as soon as that chunk reaches its phase barrier.
            markWatchdog(active, "get-chunk-now");
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                break;
            }

            if (active.phase() == PreparationPhase.ADMISSION_SURVEY) {
                markWatchdog(active, "admission-realize");
                SkyforgeNeoForge1211SurfaceStage.realize(chunk);
            } else if (active.phase() == PreparationPhase.PRODUCTION_CATCHUP) {
                markWatchdog(active, "production-catchup-barrier");
                if (!productionCatchupComplete(level, active.fixture(), chunk)) {
                    break;
                }
            } else {
                markWatchdog(active, "downstream-population-barrier");
                if (!downstreamPopulationComplete(active.fixture(), key)) {
                    break;
                }
            }

            markWatchdog(active, "release-ticket");
            level.getChunkSource().removeRegionTicket(REVIEW_TICKET, pos, TICKET_RADIUS, pos);
            active.advance();
            advanced++;
        }
        if (active.cursor() < active.chunkKeys().size()) {
            reportProgress(player, active);
            return;
        }

        if (active.phase() == PreparationPhase.ADMISSION_SURVEY) {
            var state = SkyforgePhysicalVolumeAdmissionStage.snapshot(active.fixture().volume().id()).state();
            if (state != SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
                throw new IllegalStateException(
                        "hydrology reference admission survey completed without ADMITTED state: " + state);
            }
            active.beginProductionCatchup();
            say(player, "Admission survey complete; revisiting bounded chunks for terrain, caves and authored surfaces.");
            return;
        }
        if (active.phase() == PreparationPhase.PRODUCTION_CATCHUP) {
            active.beginDownstreamPopulation();
            say(player, "Terrain/cave/surface catch-up complete; running canonical native population and presentation.");
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

    private static synchronized void beginPipelineBootstrap(UUID playerId) {
        if (preparation != null || bootstrapStarted || hasBindings()) {
            return;
        }
        bootstrapStarted = true;
        bootstrapStartedNanos = System.nanoTime();
        Thread thread = new Thread(() -> {
            try {
                var fixture = SkyforgeHydrologyReferenceReviewFixture.create();
                var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                        fixture.catalog(),
                        io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                        new SkyforgeMinecraftBlockPalette(),
                        fixture.descriptorsByVolumeId());
                completedBootstrap = new PipelineBootstrap(fixture, terrain, playerId);
            } catch (Throwable failure) {
                bootstrapFailure = failure;
            }
        }, "Skyforge hydrology reference bootstrap");
        thread.setDaemon(true);
        bootstrapThread = thread;
        thread.start();
    }

    private static synchronized void installPreparedPipeline(
            PipelineBootstrap bootstrap,
            net.minecraft.server.MinecraftServer server) {
        if (preparation != null || hasBindings()) {
            throw new IllegalStateException("hydrology reference pipeline already active");
        }
        var fixture = bootstrap.fixture();
        var terrain = bootstrap.terrain();
        terrainBinding = SkyforgeNeoForge1211SurfaceStage.install(
                terrain,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        admissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(
                fixture.catalog(),
                java.util.Map.of(fixture.volume().id(), fixture.footprintChunkKeys()));

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

        List<Long> chunkKeys = new ArrayList<>(fixture.footprintChunkKeys());
        // Spatial locality matters more than center-out aesthetics here. Radius ordering causes
        // consecutive outer-annulus chunks to jump around the island circumference, so even a
        // bounded ticket window fans out into many independent vanilla generation neighborhoods.
        // Scan X columns deterministically and alternate Z direction to keep successive chunks
        // adjacent across column boundaries.
        chunkKeys.sort(Comparator
                .comparingInt((Long key) -> ChunkPos.getX(key))
                .thenComparingInt(key -> (ChunkPos.getX(key) & 1) == 0
                        ? ChunkPos.getZ(key)
                        : -ChunkPos.getZ(key)));
        preparation = new Preparation(fixture, List.copyOf(chunkKeys), bootstrap.playerId());
        bootstrapStarted = false;
        startWatchdog();

        ServerPlayer player = server.getPlayerList().getPlayer(bootstrap.playerId());
        if (player != null) {
            long elapsedMillis = (System.nanoTime() - bootstrapStartedNanos) / 1_000_000L;
            say(player, "Authored hydrology bootstrap complete in "
                    + elapsedMillis + " ms; beginning admission survey.");
        }
    }

    private static void reportBootstrapProgress(ServerTickEvent.Post event) {
        if (!bootstrapStarted) {
            return;
        }
        long now = System.nanoTime();
        long elapsedNanos = now - bootstrapStartedNanos;
        if (tickCounter % 100L == 0L) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                say(player, "Precomputing immutable authored hydrology off-thread; elapsed="
                        + (elapsedNanos / 1_000_000L) + " ms.");
            }
        }
        Thread thread = bootstrapThread;
        if (thread != null
                && elapsedNanos >= 10_000_000_000L
                && now - bootstrapLastDumpNanos >= 10_000_000_000L) {
            bootstrapLastDumpNanos = now;
            System.err.println("[Hydrology Ref BOOTSTRAP] authored hydrology precompute running "
                    + (elapsedNanos / 1_000_000L) + " ms; thread state=" + thread.getState());
            for (StackTraceElement frame : thread.getStackTrace()) {
                System.err.println("    at " + frame);
            }
        }
    }

    private static synchronized void startWatchdog() {
        if (watchdogStarted) {
            return;
        }
        watchdogStarted = true;
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Thread serverThread = watchdogServerThread;
                long heartbeat = watchdogHeartbeatNanos;
                Preparation active = preparation;
                if (serverThread == null || active == null || heartbeat == 0L) {
                    continue;
                }

                long now = System.nanoTime();
                long stalledFor = now - heartbeat;
                if (stalledFor < WATCHDOG_STALL_NANOS
                        || now - watchdogLastDumpNanos < WATCHDOG_REPEAT_NANOS) {
                    continue;
                }
                watchdogLastDumpNanos = now;
                dumpWatchdog(serverThread, stalledFor);
            }
        }, "Skyforge hydrology review watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    private static void markWatchdog(Preparation active, String stage) {
        watchdogStage = stage;
        watchdogPhase = active.phase().label();
        watchdogCursor = active.cursor();
        watchdogChunkKey = active.cursor() < active.chunkKeys().size()
                ? active.chunkKeys().get(active.cursor())
                : Long.MIN_VALUE;
    }

    private static void dumpWatchdog(Thread serverThread, long stalledForNanos) {
        long stalledMillis = stalledForNanos / 1_000_000L;
        long key = watchdogChunkKey;
        String chunk = key == Long.MIN_VALUE
                ? "complete"
                : "[" + ChunkPos.getX(key) + "," + ChunkPos.getZ(key) + "]";
        Runtime runtime = Runtime.getRuntime();
        long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long maxMiB = runtime.maxMemory() / (1024L * 1024L);

        System.err.println(
                "[Hydrology Ref WATCHDOG] server thread stalled " + stalledMillis + " ms"
                        + " phase=" + watchdogPhase
                        + " cursor=" + watchdogCursor
                        + " chunk=" + chunk
                        + " stage=" + watchdogStage
                        + " heap=" + usedMiB + "/" + maxMiB + " MiB");
        printThreadStack(serverThread);

        int workersPrinted = 0;
        for (var entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            String name = thread.getName().toLowerCase(Locale.ROOT);
            if (thread == serverThread
                    || (!name.contains("worldgen")
                            && !name.contains("worker")
                            && !name.contains("chunk"))) {
                continue;
            }
            System.err.println("[Hydrology Ref WATCHDOG] related thread "
                    + thread.getName() + " state=" + thread.getState());
            StackTraceElement[] stack = entry.getValue();
            for (int index = 0; index < Math.min(stack.length, 12); index++) {
                System.err.println("    at " + stack[index]);
            }
            workersPrinted++;
            if (workersPrinted >= 6) {
                break;
            }
        }
    }

    private static void printThreadStack(Thread thread) {
        System.err.println("[Hydrology Ref WATCHDOG] thread "
                + thread.getName() + " state=" + thread.getState());
        for (StackTraceElement frame : thread.getStackTrace()) {
            System.err.println("    at " + frame);
        }
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
            if (bootstrapFailure != null) {
                say(player, "Bootstrap failed: " + bootstrapFailure.getClass().getSimpleName()
                        + ": " + bootstrapFailure.getMessage());
            } else if (bootstrapStarted) {
                long elapsedMillis = (System.nanoTime() - bootstrapStartedNanos) / 1_000_000L;
                say(player, "Precomputing authored hydrology off-thread; elapsed="
                        + elapsedMillis + " ms.");
            } else {
                say(player, "Waiting for preparation to start.");
            }
            return 1;
        }
        var volumeId = active.fixture().volume().id();
        String currentChunk = active.cursor() < active.chunkKeys().size()
                ? new ChunkPos(active.chunkKeys().get(active.cursor())).toString()
                : "complete";
        say(player, "phase=" + active.phase().label()
                + ", warm=" + active.cursor() + "/" + active.chunkKeys().size()
                + ", current=" + currentChunk
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

    private static boolean productionCatchupComplete(
            ServerLevel level,
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture,
            LevelChunk chunk) {
        var volumeId = fixture.volume().id();
        long chunkKey = chunk.getPos().toLong();
        if (SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).contains(chunkKey)
                || !SkyforgeComposedCaveStage.completed(volumeId, chunkKey)) {
            return false;
        }
        boolean requiresAuthoredSurface =
                SkyforgeNativeSurfacePopulationStage.planForVolume(chunk, volumeId).isPresent();
        return !requiresAuthoredSurface
                || SkyforgeAuthoredNativeSurfaceStage.completed(level, volumeId, chunkKey);
    }

    private static boolean downstreamPopulationComplete(
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture,
            long chunkKey) {
        var volumeId = fixture.volume().id();
        return !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId)
                        .contains(chunkKey)
                && !SkyforgeNativeInteriorPopulationStage.pendingChunkKeys().contains(chunkKey);
    }

    private static void reportProgress(ServerPlayer player, Preparation active) {
        if (tickCounter % 20L == 0L) {
            long key = active.chunkKeys().get(active.cursor());
            Runtime runtime = Runtime.getRuntime();
            long usedMiB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
            long maxMiB = runtime.maxMemory() / (1024L * 1024L);
            say(player, active.phase().label() + " "
                    + active.cursor() + "/" + active.chunkKeys().size()
                    + " @ [" + ChunkPos.getX(key) + "," + ChunkPos.getZ(key) + "]"
                    + " heap=" + usedMiB + "/" + maxMiB + " MiB");
        }
    }

    private static void reportLifecycle(ServerPlayer player, Preparation active) {
        if (tickCounter % 40L == 0L) {
            status(player);
        }
    }

    private static void releaseTickets(ServerLevel level, List<Long> chunkKeys) {
        for (long key : chunkKeys) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            level.getChunkSource().removeRegionTicket(REVIEW_TICKET, pos, TICKET_RADIUS, pos);
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

    private record PipelineBootstrap(
            SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture,
            SkyforgeNeoForge1211ChunkAdapter terrain,
            UUID playerId) {}

    private enum PreparationPhase {
        ADMISSION_SURVEY("admission survey"),
        PRODUCTION_CATCHUP("production catch-up"),
        DOWNSTREAM_POPULATION("downstream population");

        private final String label;

        PreparationPhase(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static final class Preparation {
        private final SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture;
        private final List<Long> chunkKeys;
        private final UUID playerId;
        private PreparationPhase phase = PreparationPhase.ADMISSION_SURVEY;
        private int cursor;
        private long readySinceTick = -1L;

        private Preparation(
                SkyforgeHydrologyReferenceReviewFixture.RuntimeFixture fixture,
                List<Long> chunkKeys,
                UUID playerId) {
            this.fixture = fixture;
            this.chunkKeys = new ArrayList<>(chunkKeys);
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

        PreparationPhase phase() {
            return phase;
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

        void beginProductionCatchup() {
            phase = PreparationPhase.PRODUCTION_CATCHUP;
            cursor = 0;
        }

        void beginDownstreamPopulation() {
            phase = PreparationPhase.DOWNSTREAM_POPULATION;
            chunkKeys.sort(Comparator
                    .comparingInt((Long key) -> ChunkPos.getX(key))
                    .thenComparingInt(ChunkPos::getZ));
            cursor = 0;
        }

        void markReadySince(long value) {
            readySinceTick = value;
        }
    }
}
