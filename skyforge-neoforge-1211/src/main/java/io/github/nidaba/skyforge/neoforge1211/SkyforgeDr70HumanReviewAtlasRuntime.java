package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.nidaba.skyforge.world.SkyIslandAuthoredRealizationAssociation;
import io.github.nidaba.skyforge.world.SkyIslandCaveExposureSide;
import io.github.nidaba.skyforge.world.SkyIslandCompiledVolumeColumnField;
import io.github.nidaba.skyforge.world.SkyIslandRealizedExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandVisibleHydrologicRealizationPlanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 * Interactive single-world human-review harness for the frozen DR-70 100-island corpus.
 *
 * <p>Only the currently preparing island owns live production mutation bindings. Fully prepared
 * islands are saved, bindings are closed, and those persisted chunks become inert review specimens.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeDr70HumanReviewAtlasRuntime {
    static final String ENABLE_PROPERTY = SkyforgeDr70HumanReviewAtlasFixture.ENABLE_PROPERTY;
    static final String WARM_CHUNKS_PER_TICK_PROPERTY = "skyforge.dev.dr70AtlasWarmChunksPerTick";
    static final String HEADLESS_BOOTSTRAP_PROPERTY = "skyforge.dev.dr70HumanReviewAtlasHeadlessBootstrap";

    private static final int DEFAULT_WARM_CHUNKS_PER_TICK = 4;
    private static final int MAX_WARM_CHUNKS_PER_TICK = 16;
    private static final int TICKET_DISTANCE = 3;
    private static final int FLUID_SETTLE_TICKS = 100;
    static final long FOREGROUND_PREPARATION_TIME_BUDGET_NANOS = 40_000_000L;
    private static final Path PREPARED_FILE = Path.of("dr70-human-review-prepared.txt");
    private static final Path RATINGS_FILE = Path.of("dr70-human-review-results.csv");
    private static final TicketType<ChunkPos> REVIEW_TICKET = TicketType.create(
            "skyforge_dr70_human_review",
            Comparator.comparingLong(ChunkPos::toLong));

    private static final Set<Integer> PREPARED = new HashSet<>();
    private static final Set<Integer> RATED = new HashSet<>();

    private static AutoCloseable terrainBinding;
    private static AutoCloseable admissionBinding;
    private static AutoCloseable populationBinding;
    private static AutoCloseable caveBinding;
    private static AutoCloseable interiorBinding;
    private static Preparation preparation;
    private static int currentReviewIndex = 1;
    private static long tickCounter;

    private SkyforgeDr70HumanReviewAtlasRuntime() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
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
            throw new IllegalStateException("DR-70 review atlas requires isolated production bindings");
        }
        PREPARED.clear();
        PREPARED.addAll(readPrepared());
        RATED.clear();
        RATED.addAll(readRated());
        currentReviewIndex = nextUnrated(0);
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setGameMode(GameType.SPECTATOR);
        sendHelp(player);
        if (RATED.size() >= SkyforgeDr70HumanReviewAtlasFixture.size()) {
            say(player, "All 100 specimens already have ratings.");
            return;
        }
        int resume = nextUnrated(0);
        if (PREPARED.contains(resume)) {
            currentReviewIndex = resume;
            move(player, "above");
        } else {
            queuePreparation(player, resume);
        }
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        event.getDispatcher().register(
                Commands.literal("skyforge_dr70_atlas")
                        .executes(context -> showHelp(context.getSource().getPlayerOrException()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("info")
                                .executes(context -> showInfo(context.getSource().getPlayerOrException(), false)))
                        .then(Commands.literal("reveal")
                                .executes(context -> showInfo(context.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("next")
                                .executes(context -> queuePreparation(
                                        context.getSource().getPlayerOrException(),
                                        nextUnrated(currentReviewIndex))))
                        .then(Commands.literal("prev")
                                .executes(context -> queuePreparation(
                                        context.getSource().getPlayerOrException(),
                                        previousReviewIndex(currentReviewIndex))))
                        .then(Commands.literal("go")
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> queuePreparation(
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "index")))))
                        .then(stopCommand("above"))
                        .then(stopCommand("approach"))
                        .then(stopCommand("orbit"))
                        .then(stopCommand("below"))
                        .then(stopCommand("river"))
                        .then(stopCommand("cave"))
                        .then(ratingCommand("pass"))
                        .then(ratingCommand("concern"))
                        .then(ratingCommand("fail")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            stopCommand(String name) {
        return Commands.literal(name)
                .executes(context -> move(context.getSource().getPlayerOrException(), name));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
            ratingCommand(String verdict) {
        return Commands.literal(verdict)
                .executes(context -> rate(context.getSource().getPlayerOrException(), verdict, ""))
                .then(Commands.argument("notes", StringArgumentType.greedyString())
                        .executes(context -> rate(
                                context.getSource().getPlayerOrException(),
                                verdict,
                                StringArgumentType.getString(context, "notes"))));
    }
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled()) {
            return;
        }
        tickCounter++;
        if (preparation == null && Boolean.getBoolean(HEADLESS_BOOTSTRAP_PROPERTY)) {
            ServerLevel bootstrapLevel = event.getServer().getLevel(Level.OVERWORLD);
            if (bootstrapLevel == null) {
                return;
            }
            event.getServer().saveEverything(false, true, true);
            event.getServer().halt(false);
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
        while (active.cursor() < active.chunkKeys().size() && warmed < warmChunksPerTick()) {
            long key = active.chunkKeys().get(active.cursor());
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            level.getChunkSource().addRegionTicket(REVIEW_TICKET, pos, TICKET_DISTANCE, pos);
            LevelChunk chunk = level.getChunk(pos.x, pos.z);
            MinecraftNativeSurfaceSnapshot nativeSurfaceSnapshot =
                    MinecraftNativeSurfaceSnapshot.empty(chunk.getPos());
            SkyforgeNeoForge1211SurfaceStage.realize(chunk, nativeSurfaceSnapshot);
            active.advance();
            warmed++;
        }
        reportWarmProgress(player, active);

        if (active.cursor() < active.chunkKeys().size() || tickCounter % 20L != 0L) {
            return;
        }
        if (!lifecycleReady(active.fixture())) {
            reportLifecycleProgress(player, active);
            return;
        }
        if (active.readySinceTick() < 0L) {
            active.markReadySince(tickCounter);
            say(player, "Production lifecycle complete for #" + active.fixture().member().reviewIndex()
                    + "; settling generated fluids.");
            return;
        }
        if (tickCounter - active.readySinceTick() < FLUID_SETTLE_TICKS) {
            return;
        }
        finalizePrepared(level, player, active);
    }

    private static synchronized int queuePreparation(ServerPlayer player, int index) {
        if (preparation != null) {
            if (preparation.fixture().member().reviewIndex() == index) {
                preparation.markActivateWhenReady();
                say(player, "#" + index + " is already preparing; it will open automatically when ready.");
                return 1;
            }
            say(player, "A different specimen is already preparing. Use /skyforge_dr70_atlas status.");
            return 0;
        }
        if (PREPARED.contains(index)) {
            currentReviewIndex = index;
            return move(player, "above");
        }
        installTargetPipeline(player.getUUID(), index, true);
        var active = preparation;
        say(player, "Preparing #" + index + "/100 (key "
                + active.fixture().member().islandKey()
                + "), morphology="
                + active.fixture().member().descriptor().morphologyFamily().identifier()
                + ", radius="
                + String.format(Locale.ROOT, "%.1f", active.fixture().member().descriptor().nominalRadius())
                + ", chunks=" + active.chunkKeys().size() + ".");
        return 1;
    }

    private static synchronized void queueBackgroundPreparation(ServerPlayer player, int index) {
        if (preparation != null || PREPARED.contains(index) || RATED.contains(index)) {
            return;
        }
        installTargetPipeline(player.getUUID(), index, false);
        var active = preparation;
        say(player, "Background preparing next specimen #" + index
                + " (radius="
                + String.format(Locale.ROOT, "%.1f", active.fixture().member().descriptor().nominalRadius())
                + ", chunks=" + active.chunkKeys().size() + ") while you review the current island.");
    }

    private static synchronized void installTargetPipeline(
            UUID playerId,
            int index,
            boolean activateWhenReady) {
        if (preparation != null || hasTargetBindings()) {
            throw new IllegalStateException("DR-70 atlas target pipeline already active");
        }
        var fixture = SkyforgeDr70HumanReviewAtlasFixture.runtimeFixture(index);
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                io.github.nidaba.skyforge.world.SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                fixture.descriptorsByVolumeId());
        terrainBinding = SkyforgeNeoForge1211SurfaceStage.installNativeSurfaceAdapted(
                terrain,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()));
        admissionBinding = SkyforgePhysicalVolumeAdmissionStage.install(fixture.catalog());

        var ecology = new SkyforgeProductionEcologyResolver(
                SkyIslandAuthoredRealizationAssociation.of(
                        fixture.member().descriptor(),
                        fixture.volume()));
        var surfacePlan = SkyforgeNativeSurfacePopulationPlan.surfaceEcology(
                fixture.volume().id(),
                ecology,
                24);
        populationBinding = SkyforgeNativeSurfacePopulationStage.install(
                (chunkPos, minimumY, height) -> {
                    var region = new MinecraftChunkBounds(chunkPos, minimumY, height).worldBounds();
                    return fixture.catalog().query(region).isEmpty() ? List.of() : List.of(surfacePlan);
                });

        caveBinding = SkyforgeComposedCaveStage.install(List.of(
                new SkyforgeComposedCavePlan(fixture.volume(), fixture.caveField())));
        interiorBinding = SkyforgeNativeInteriorPopulationStage.install(List.of(
                SkyforgeNativeInteriorPopulationPlan.acceptedNativeInterior(
                        fixture.volume().id(),
                        0)));

        List<Long> chunkKeys = new ArrayList<>(
                SkyforgePhysicalVolumeAdmissionStage.requiredChunkKeys(fixture.volume().id()));
        int centerChunkX = Math.floorDiv((int) Math.floor(fixture.member().centerX()), 16);
        int centerChunkZ = Math.floorDiv((int) Math.floor(fixture.member().centerZ()), 16);
        chunkKeys.sort(Comparator
                .comparingLong((Long key) -> squaredChunkDistance(key, centerChunkX, centerChunkZ))
                .thenComparingInt(ChunkPos::getX)
                .thenComparingInt(ChunkPos::getZ));
        preparation = new Preparation(
                fixture,
                List.copyOf(chunkKeys),
                playerId,
                activateWhenReady);
    }

    private static long squaredChunkDistance(long key, int centerChunkX, int centerChunkZ) {
        long dx = (long) ChunkPos.getX(key) - centerChunkX;
        long dz = (long) ChunkPos.getZ(key) - centerChunkZ;
        return dx * dx + dz * dz;
    }

    private static boolean lifecycleReady(SkyforgeDr70HumanReviewAtlasFixture.RuntimeFixture fixture) {
        var volumeId = fixture.volume().id();
        if (SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId).state()
                != SkyforgePhysicalVolumeAdmissionState.ADMITTED) {
            return false;
        }
        if (!SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).isEmpty()
                || !SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId).isEmpty()) {
            return false;
        }
        if (SkyforgeComposedCaveStage.snapshot(volumeId).pendingObligations() != 0) {
            return false;
        }
        return SkyforgeNativeInteriorPopulationStage.snapshot(volumeId).pendingObligations() == 0;
    }

    private static void reportWarmProgress(ServerPlayer player, Preparation active) {
        int percent = (int) ((100L * active.cursor()) / active.chunkKeys().size());
        if (percent >= active.lastReportedPercent() + 10) {
            active.markReportedPercent(percent);
            say(player, "#" + active.fixture().member().reviewIndex()
                    + " chunk warmup " + active.cursor() + "/" + active.chunkKeys().size()
                    + " (" + percent + "%).");
        }
    }

    private static void reportLifecycleProgress(ServerPlayer player, Preparation active) {
        if (tickCounter % 40L != 0L) {
            return;
        }
        var volumeId = active.fixture().volume().id();
        var admission = SkyforgePhysicalVolumeAdmissionStage.snapshot(volumeId);
        var caves = SkyforgeComposedCaveStage.snapshot(volumeId);
        var interior = SkyforgeNativeInteriorPopulationStage.snapshot(volumeId);
        say(player, "#" + active.fixture().member().reviewIndex()
                + " lifecycle: admission=" + admission.state()
                + ", terrainPending="
                + SkyforgePhysicalVolumeAdmissionStage.pendingCatchupChunks(volumeId).size()
                + ", biomePending="
                + SkyforgePhysicalVolumeAdmissionStage.pendingBiomePresentationChunks(volumeId).size()
                + ", cavePending=" + caves.pendingObligations()
                + ", interiorPending=" + interior.pendingObligations());
    }

    private static synchronized void finalizePrepared(
            ServerLevel level,
            ServerPlayer player,
            Preparation active) {
        if (!level.getServer().saveEverything(false, true, true)) {
            throw new IllegalStateException("DR-70 atlas failed to save fully prepared specimen");
        }
        int index = active.fixture().member().reviewIndex();
        boolean activateWhenReady = active.activateWhenReady();
        PREPARED.add(index);
        writePrepared();
        releaseTickets(level, active.chunkKeys());
        closeTargetPipeline();
        if (activateWhenReady) {
            currentReviewIndex = index;
            move(player, "above");
            say(player, "READY #" + index + "/100. Inspect freely, then rate with "
                    + "/skyforge_dr70_atlas pass|concern|fail [notes].");
            int next = nextUnrated(index);
            if (next != index) {
                queueBackgroundPreparation(player, next);
            }
        } else {
            say(player, "Background specimen #" + index + " is READY and will open when selected.");
        }
    }
    private static int move(ServerPlayer player, String stopName) {
        if (!PREPARED.contains(currentReviewIndex)) {
            say(player, "Current specimen is not prepared yet.");
            return 0;
        }
        Stop stop = stop(player.serverLevel(), currentReviewIndex, stopName);
        if (stop == null) {
            say(player, "#" + currentReviewIndex + " has no " + stopName + " review stop.");
            return 0;
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(stop.x(), stop.y(), stop.z());
        player.setYRot(stop.yaw());
        player.setXRot(stop.pitch());
        say(player, "#" + currentReviewIndex + " " + stopName + ": " + stop.description());
        return 1;
    }

    private static Stop stop(ServerLevel level, int index, String name) {
        var member = SkyforgeDr70HumanReviewAtlasFixture.member(index);
        double radius = member.descriptor().nominalRadius();
        return switch (name) {
            case "above" -> new Stop(
                    member.centerX(),
                    Math.min(315.0, SkyforgeDr70HumanReviewAtlasFixture.SUSPENSION_Y + 92.0),
                    member.centerZ(),
                    0.0f,
                    90.0f,
                    "planform, morphology, drainage, water retention, and ecology");
            case "approach" -> new Stop(
                    member.centerX(),
                    SkyforgeDr70HumanReviewAtlasFixture.SUSPENSION_Y + 24.0,
                    member.centerZ() + radius * 1.45 + 48.0,
                    180.0f,
                    8.0f,
                    "horizon silhouette, banks, vegetation, and exposed terrain");
            case "orbit" -> new Stop(
                    member.centerX() + radius * 1.25 + 40.0,
                    SkyforgeDr70HumanReviewAtlasFixture.SUSPENSION_Y + 12.0,
                    member.centerZ() + radius * 1.25 + 40.0,
                    135.0f,
                    2.0f,
                    "oblique free-flight starting point");
            case "below" -> new Stop(
                    member.centerX(),
                    SkyforgeDr70HumanReviewAtlasFixture.SUSPENSION_Y - 132.0,
                    member.centerZ() + radius * 0.15,
                    0.0f,
                    -72.0f,
                    "underside morphology and exterior cave exposure");
            case "river" -> riverStop(level, member);
            case "cave" -> caveStop(member);
            default -> throw new IllegalArgumentException("unknown DR-70 review stop " + name);
        };
    }

    private static Stop riverStop(
            ServerLevel level,
            SkyforgeDr70HumanReviewAtlasFixture.Member member) {
        var hydrology = SkyIslandVisibleHydrologicRealizationPlanner.plan(member.descriptor());
        var channel = hydrology.channels().stream()
                .max(Comparator.comparingDouble(intent ->
                        intent.path().profile().segment().relativeDischarge()))
                .orElse(null);
        if (channel == null || channel.path().points().isEmpty()) {
            return null;
        }
        var point = channel.path().points().get(channel.path().points().size() / 2);
        int worldX = (int) Math.round(member.centerX() + point.x());
        int worldZ = (int) Math.round(member.centerZ() + point.z());
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        return new Stop(
                worldX + 18.0,
                surfaceY + 18.0,
                worldZ + 18.0,
                135.0f,
                32.0f,
                "highest-discharge accepted visible channel");
    }

    private static Stop caveStop(SkyforgeDr70HumanReviewAtlasFixture.Member member) {
        var fixture = SkyforgeDr70HumanReviewAtlasFixture.runtimeFixture(member.reviewIndex());
        if (fixture.caveField().exposureGeometry().connections().isEmpty()) {
            return null;
        }
        var connection = fixture.caveField().exposureGeometry().connections().getFirst();
        var realized = new SkyIslandRealizedExteriorConnectedCaveVolumeField(
                fixture.caveField(),
                new SkyIslandCompiledVolumeColumnField(fixture.volume().compiledVolume()));
        var physical = realized.transform().toPhysical(connection.mouthPoint().position()).orElse(null);
        if (physical == null) {
            return null;
        }
        double worldX = fixture.volume().compiledVolume().descriptor().centerX() + physical.localX();
        double worldZ = fixture.volume().compiledVolume().descriptor().centerZ() + physical.localZ();
        double offsetY = connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? 14.0 : -14.0;
        float pitch = connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE ? 55.0f : -55.0f;
        return new Stop(
                worldX + 8.0,
                physical.physicalY() + offsetY,
                worldZ + 8.0,
                135.0f,
                pitch,
                connection.side().name().toLowerCase(Locale.ROOT) + " authored exterior cave mouth");
    }

    private static int showStatus(ServerPlayer player) {
        String active = preparation == null
                ? "idle"
                : "preparing=#" + preparation.fixture().member().reviewIndex()
                        + " " + preparation.cursor() + "/" + preparation.chunkKeys().size();
        say(player, "prepared=" + PREPARED.size() + "/100, rated=" + RATED.size()
                + "/100, current=#" + currentReviewIndex + ", " + active);
        return 1;
    }

    private static int showInfo(ServerPlayer player, boolean revealBucket) {
        var member = SkyforgeDr70HumanReviewAtlasFixture.member(currentReviewIndex);
        String message = "#" + currentReviewIndex
                + " key=" + member.islandKey()
                + ", morphology=" + member.descriptor().morphologyFamily().identifier()
                + ", radius=" + String.format(Locale.ROOT, "%.1f", member.descriptor().nominalRadius())
                + ", moisture=" + String.format(Locale.ROOT, "%.2f", member.descriptor().moistureTendency())
                + ", hydrology="
                + String.format(Locale.ROOT, "%.2f", member.descriptor().hydrologicalPotential());
        if (revealBucket) {
            message += ", selectionBucket=" + member.selectionBucket();
        }
        say(player, message);
        return 1;
    }

    private static int showHelp(ServerPlayer player) {
        sendHelp(player);
        return 1;
    }

    private static void sendHelp(ServerPlayer player) {
        say(player, "100-island frozen human-review corpus.");
        say(player, "/skyforge_dr70_atlas next | prev | go <1-100> | status | info | reveal");
        say(player, "/skyforge_dr70_atlas above | approach | orbit | below | river | cave");
        say(player, "/skyforge_dr70_atlas pass|concern|fail [notes]");
        say(player, "Judge morphology, hydrology, water, caves, ecology/materials, "
                + "cross-system conflicts, and overall gameplay acceptability. N/A is valid.");
    }

    private static synchronized int rate(ServerPlayer player, String verdict, String notes) {
        if (!PREPARED.contains(currentReviewIndex)) {
            say(player, "Only a fully prepared current specimen can be rated.");
            return 0;
        }
        if (RATED.contains(currentReviewIndex)) {
            say(player, "#" + currentReviewIndex + " is already rated; existing result is preserved.");
            return 0;
        }
        var member = SkyforgeDr70HumanReviewAtlasFixture.member(currentReviewIndex);
        appendRating(member, verdict, notes);
        RATED.add(currentReviewIndex);
        int ratedIndex = currentReviewIndex;
        say(player, "Recorded " + verdict.toUpperCase(Locale.ROOT) + " for #" + ratedIndex + ".");
        if (RATED.size() >= SkyforgeDr70HumanReviewAtlasFixture.size()) {
            say(player, "Human corpus complete: 100/100 rated.");
            return 1;
        }
        int next = nextUnrated(ratedIndex);
        queuePreparation(player, next);
        return 1;
    }

    private static int nextUnrated(int afterIndex) {
        List<Integer> order = SkyforgeDr70HumanReviewAtlasFixture.reviewOrder();
        int start = afterIndex <= 0 ? -1 : order.indexOf(afterIndex);
        for (int offset = 1; offset <= order.size(); offset++) {
            int candidate = order.get((start + offset) % order.size());
            if (!RATED.contains(candidate)) {
                return candidate;
            }
        }
        return Math.max(1, Math.min(SkyforgeDr70HumanReviewAtlasFixture.size(), currentReviewIndex));
    }

    private static int previousReviewIndex(int currentIndex) {
        List<Integer> order = SkyforgeDr70HumanReviewAtlasFixture.reviewOrder();
        int position = order.indexOf(currentIndex);
        if (position < 0) {
            return order.getFirst();
        }
        return order.get((position - 1 + order.size()) % order.size());
    }
    static synchronized boolean foregroundPreparationActive() {
        return enabled() && preparation != null && preparation.activateWhenReady();
    }

    static boolean suppressNativeStructureRuntime() {
        return enabled();
    }

    private static int warmChunksPerTick() {
        int value = Integer.getInteger(WARM_CHUNKS_PER_TICK_PROPERTY, DEFAULT_WARM_CHUNKS_PER_TICK);
        if (value < 1 || value > MAX_WARM_CHUNKS_PER_TICK) {
            throw new IllegalArgumentException(
                    WARM_CHUNKS_PER_TICK_PROPERTY + " must be in [1," + MAX_WARM_CHUNKS_PER_TICK + "]");
        }
        return value;
    }

    private static void releaseTickets(ServerLevel level, List<Long> chunkKeys) {
        for (long key : chunkKeys) {
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            level.getChunkSource().removeRegionTicket(REVIEW_TICKET, pos, TICKET_DISTANCE, pos);
        }
    }

    private static boolean hasTargetBindings() {
        return terrainBinding != null
                || admissionBinding != null
                || populationBinding != null
                || caveBinding != null
                || interiorBinding != null;
    }

    private static synchronized void closeTargetPipeline() {
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
            throw new IllegalStateException("failed to close DR-70 atlas " + name + " binding", exception);
        }
    }

    private static Set<Integer> readPrepared() {
        if (!Files.isRegularFile(PREPARED_FILE)) {
            return Set.of();
        }
        try {
            Set<Integer> result = new HashSet<>();
            for (String token : Files.readString(PREPARED_FILE, StandardCharsets.UTF_8).split("[,\\s]+")) {
                if (!token.isBlank()) {
                    result.add(Integer.parseInt(token));
                }
            }
            return Set.copyOf(result);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("failed to read DR-70 prepared-state file", exception);
        }
    }

    private static Set<Integer> readRated() {
        if (!Files.isRegularFile(RATINGS_FILE)) {
            return Set.of();
        }
        try {
            Set<Integer> result = new HashSet<>();
            for (String line : Files.readAllLines(RATINGS_FILE, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("reviewIndex,")) {
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma > 0) {
                    result.add(Integer.parseInt(line.substring(0, comma)));
                }
            }
            return Set.copyOf(result);
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalStateException("failed to read DR-70 rating file", exception);
        }
    }

    private static void writePrepared() {
        try {
            String text = PREPARED.stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","))
                    + System.lineSeparator();
            Files.writeString(
                    PREPARED_FILE,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write DR-70 prepared-state file", exception);
        }
    }

    private static void appendRating(
            SkyforgeDr70HumanReviewAtlasFixture.Member member,
            String verdict,
            String notes) {
        try {
            if (!Files.exists(RATINGS_FILE)) {
                Files.writeString(
                        RATINGS_FILE,
                        "reviewIndex,islandKey,selectionBucket,verdict,notes,timestampUtc\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
            }
            String row = member.reviewIndex()
                    + "," + member.islandKey()
                    + "," + csv(member.selectionBucket())
                    + "," + csv(verdict)
                    + "," + csv(notes)
                    + "," + csv(Instant.now().toString())
                    + "\n";
            Files.writeString(
                    RATINGS_FILE,
                    row,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to append DR-70 human review rating", exception);
        }
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[DR-70 Atlas] " + message));
    }

    private record Stop(
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String description) {}

    private static final class Preparation {
        private final SkyforgeDr70HumanReviewAtlasFixture.RuntimeFixture fixture;
        private final List<Long> chunkKeys;
        private final UUID playerId;
        private int cursor;
        private int lastReportedPercent = -1;
        private long readySinceTick = -1L;
        private boolean activateWhenReady;

        private Preparation(
                SkyforgeDr70HumanReviewAtlasFixture.RuntimeFixture fixture,
                List<Long> chunkKeys,
                UUID playerId,
                boolean activateWhenReady) {
            this.fixture = fixture;
            this.chunkKeys = chunkKeys;
            this.playerId = playerId;
            this.activateWhenReady = activateWhenReady;
        }

        SkyforgeDr70HumanReviewAtlasFixture.RuntimeFixture fixture() {
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

        int lastReportedPercent() {
            return lastReportedPercent;
        }

        long readySinceTick() {
            return readySinceTick;
        }

        boolean activateWhenReady() {
            return activateWhenReady;
        }

        void markActivateWhenReady() {
            activateWhenReady = true;
        }

        void advance() {
            cursor++;
        }

        void markReportedPercent(int value) {
            lastReportedPercent = value;
        }

        void markReadySince(long value) {
            readySinceTick = value;
        }
    }
}
