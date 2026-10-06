package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Diagnostic-only #1180 machine A/B for real-provider glider legibility and sky-island hawk
 * behavior.
 *
 * <p>The fixture never changes A4MC strength, the hawk hysteresis threshold, C7 smoothing, stock
 * bird AI, or terrain. Control and treatment reopen the same accepted DR-50 world. Each arm waits
 * a fixed number of game ticks after the real client player appears, then records a sky-island hawk
 * cohort, an ordinary-ground hawk cohort, and an immutable same-sample C7 vertical-response replay.
 */
final class SkyforgeSfImp1180ConsumerDiagnosticRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.sfImp1180ConsumerDiagnostic";
    static final String ARM_PROPERTY = "skyforge.dev.sfImp1180ConsumerDiagnosticArm";
    static final String OUTPUT_PROPERTY = "skyforge.dev.sfImp1180ConsumerDiagnosticOutput";

    private static final ResourceLocation HAWK_ID =
            ResourceLocation.fromNamespaceAndPath("fowlplay", "hawk");
    private static final long WARMUP_TICKS = 2_600L;
    private static final long OBSERVATION_TICKS = 600L;
    private static final long GLIDER_FRAME_PERIOD_TICKS = 20L;
    private static final double NATIVE_GLIDER_SINK_Y = -0.05;
    private static final double STRONGER_NATIVE_UPDRAFT_Y = 0.70;
    private static final int ISLAND_CENTER_X = -32;
    private static final int ISLAND_CENTER_Z = 32;
    private static final int GROUND_CENTER_X = 768;
    private static final int GROUND_CENTER_Z = 0;
    private static final int[][] HAWK_OFFSETS = {
        {-12, -8}, {-4, -8}, {4, -8}, {12, -8},
        {-12, 0}, {-4, 0}, {4, 0}, {12, 0},
        {-12, 8}, {-4, 8}, {4, 8}, {12, 8}
    };
    private static final int[] GLIDER_XZ_OFFSETS = {-192, -128, -64, 0, 64, 128, 192};
    private static final int[] GLIDER_SURFACE_OFFSETS = {32, 80};

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeSfImp1180ConsumerDiagnosticRuntime.class.getName());
    private static final List<TrackedHawk> HAWKS = new ArrayList<>();
    private static final List<GliderFrame> GLIDER_FRAMES = new ArrayList<>();

    private static boolean installed;
    private static boolean complete;
    private static boolean observationStarted;
    private static String arm;
    private static boolean c6Enabled;
    private static boolean c7Enabled;
    private static SkyforgeAtmosphereView atmosphere;
    private static long playerAnchorTick = Long.MIN_VALUE;
    private static long observationStartTick = Long.MIN_VALUE;
    private static Vec3 gliderProbe;
    private static double projectedGliderAltitudeDelta;
    private static int gliderProbeTrustedCandidates;

    private SkyforgeSfImp1180ConsumerDiagnosticRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || installed) {
            return;
        }

        arm = System.getProperty(ARM_PROPERTY, "").trim();
        if (!arm.equals("control") && !arm.equals("treatment")) {
            throw new IllegalStateException(
                    ARM_PROPERTY + " must be exactly control or treatment, got '" + arm + "'");
        }

        c6Enabled = Boolean.getBoolean(SkyforgeWaveC6SoaringFaunaDevRuntime.ENABLE_PROPERTY);
        c7Enabled = Boolean.getBoolean(SkyforgeWaveC7GliderLiftDevRuntime.ENABLE_PROPERTY);
        if (arm.equals("control") && (c6Enabled || c7Enabled)) {
            throw new IllegalStateException("#1180 control arm must leave C6 and C7 disabled");
        }
        if (arm.equals("treatment") && (!c6Enabled || !c7Enabled)) {
            throw new IllegalStateException("#1180 treatment arm must enable both C6 and C7");
        }

        NeoForge.EVENT_BUS.addListener(SkyforgeSfImp1180ConsumerDiagnosticRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeSfImp1180ConsumerDiagnosticRuntime::onServerTickPost);
        installed = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-1180 consumer diagnostic armed arm=" + arm
                        + " c6=" + c6Enabled + " c7=" + c7Enabled);
    }

    private static synchronized void onServerStarted(ServerStartedEvent event) {
        try {
            atmosphere = SkyforgeA4mcAtmosphereBridge.create();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "#1180 could not bind the pinned A4MC gameplay API", failure);
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || atmosphere == null) {
            return;
        }

        ServerLevel level = event.getServer().overworld();
        ServerPlayer player = level.players().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }

        long gameTick = level.getGameTime();
        if (playerAnchorTick == Long.MIN_VALUE) {
            playerAnchorTick = gameTick;
            prepareWorld(level);
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-1180 real-player anchor arm=" + arm
                            + " tick=" + playerAnchorTick
                            + " position=" + player.position());
            return;
        }

        if (!observationStarted) {
            if (gameTick - playerAnchorTick < WARMUP_TICKS) {
                return;
            }
            startObservation(level, gameTick);
            recordHawks(level, 0L);
            recordGliderFrame(level, 0L);
            return;
        }

        long elapsed = gameTick - observationStartTick;
        recordHawks(level, elapsed);
        if (elapsed > 0L && elapsed % GLIDER_FRAME_PERIOD_TICKS == 0L) {
            recordGliderFrame(level, elapsed);
        }

        if (elapsed >= OBSERVATION_TICKS) {
            finish(level);
        }
    }

    private static void prepareWorld(ServerLevel level) {
        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
        // Keep the bounded island and ordinary-terrain cohorts tickable without granting arbitrary
        // chunk-loading authority to the atmosphere adapter itself.
        level.getServer().getCommands().performPrefixedCommand(
                source, "forceload add -128 -64 64 128");
        level.getServer().getCommands().performPrefixedCommand(
                source, "forceload add 720 -48 816 48");
    }

    private static void startObservation(ServerLevel level, long gameTick) {
        if (!SkyforgeAtmosphereTerrainAuthority.active()) {
            fail("DR-50 semantic atmosphere terrain authority is not active");
        }
        SkyforgeA4mcTerrainBridge.Diagnostics terrainDiagnostics =
                SkyforgeA4mcTerrainBridge.diagnostics();
        if (!terrainDiagnostics.registered()) {
            fail("patched public A4MC terrain provider did not register");
        }

        var source = level.getServer().createCommandSourceStack().withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, "time set 2000");

        gliderProbe = selectGliderProbe(level);
        SkyforgeAtmosphereView.Sample readiness = atmosphere.sample(level, gliderProbe);
        if (!readiness.trustedForGameplay()) {
            fail("A4MC gameplay provider remained untrusted after fixed warmup at " + gliderProbe
                    + " source=" + readiness.sourceLevel()
                    + " authority=" + readiness.authority());
        }

        spawnHawks(level, "island", ISLAND_CENTER_X, ISLAND_CENTER_Z, true);
        spawnHawks(level, "ground", GROUND_CENTER_X, GROUND_CENTER_Z, false);

        observationStartTick = gameTick;
        observationStarted = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-1180 observation start arm=" + arm
                        + " tick=" + gameTick
                        + " gliderProbe=" + gliderProbe
                        + " probeUpdraft=" + readiness.updraftMetersPerSecond()
                        + " hawks=" + HAWKS.size());
    }

    private static Vec3 selectGliderProbe(ServerLevel level) {
        Vec3 bestPosition = null;
        double bestUpdraft = Double.NEGATIVE_INFINITY;
        int trusted = 0;

        for (int zOffset : GLIDER_XZ_OFFSETS) {
            for (int xOffset : GLIDER_XZ_OFFSETS) {
                int x = ISLAND_CENTER_X + xOffset;
                int z = ISLAND_CENTER_Z + zOffset;
                var semantic = SkyforgeAtmosphereTerrainAuthority.sample(
                        x, z, level.getMinBuildHeight(), level.getHeight());
                if (semantic.isEmpty()) {
                    continue;
                }
                int surface = semantic.orElseThrow().firstFreeHeight();
                for (int yOffset : GLIDER_SURFACE_OFFSETS) {
                    Vec3 position = new Vec3(x + 0.5, surface + yOffset, z + 0.5);
                    SkyforgeAtmosphereView.Sample sample = atmosphere.sample(level, position);
                    if (!sample.trustedForGameplay()) {
                        continue;
                    }
                    trusted++;
                    if (bestPosition == null
                            || sample.updraftMetersPerSecond() > bestUpdraft) {
                        bestPosition = position;
                        bestUpdraft = sample.updraftMetersPerSecond();
                    }
                }
            }
        }

        gliderProbeTrustedCandidates = trusted;
        if (bestPosition == null) {
            fail("no trusted real-provider glider probe candidate after fixed warmup");
        }
        return bestPosition;
    }

    private static void spawnHawks(
            ServerLevel level,
            String cohort,
            int centerX,
            int centerZ,
            boolean requireIslandSurface) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(HAWK_ID)) {
            fail("missing exact fowlplay:hawk entity type");
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(HAWK_ID);

        for (int index = 0; index < HAWK_OFFSETS.length; index++) {
            int x = centerX + HAWK_OFFSETS[index][0];
            int z = centerZ + HAWK_OFFSETS[index][1];
            int surfaceY;
            if (requireIslandSurface) {
                var semantic = SkyforgeAtmosphereTerrainAuthority.sample(
                        x, z, level.getMinBuildHeight(), level.getHeight());
                if (semantic.isEmpty()) {
                    fail("island hawk spawn lost semantic surface at x=" + x + " z=" + z);
                }
                surfaceY = semantic.orElseThrow().firstFreeHeight();
            } else {
                if (SkyforgeAtmosphereTerrainAuthority.sample(
                                x, z, level.getMinBuildHeight(), level.getHeight())
                        .isPresent()) {
                    fail("ordinary-ground hawk control overlaps Skyforge semantic terrain at x="
                            + x + " z=" + z);
                }
                surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }

            Entity entity = type.create(level);
            if (!(entity instanceof Mob mob)) {
                fail("fowlplay:hawk did not create a Mob entity");
                return;
            }
            double spawnX = x + 0.5;
            double spawnY = surfaceY + 48.0;
            double spawnZ = z + 0.5;
            mob.moveTo(spawnX, spawnY, spawnZ, 0.0F, 0.0F);
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob)) {
                fail("failed to add " + cohort + " hawk index=" + index);
            }
            HAWKS.add(new TrackedHawk(
                    cohort, index, mob, centerX + 0.5, centerZ + 0.5,
                    spawnX, spawnY, spawnZ));
        }
    }

    private static void recordHawks(ServerLevel level, long elapsed) {
        for (TrackedHawk tracked : HAWKS) {
            Mob hawk = tracked.hawk;
            if (hawk.isRemoved()) {
                tracked.removed = true;
                tracked.removalReason = String.valueOf(hawk.getRemovalReason());
                continue;
            }

            tracked.samples++;
            tracked.sumY += hawk.getY();
            tracked.minY = Math.min(tracked.minY, hawk.getY());
            tracked.maxY = Math.max(tracked.maxY, hawk.getY());
            tracked.maxHorizontalDistance = Math.max(
                    tracked.maxHorizontalDistance,
                    Math.hypot(
                            hawk.getX() - tracked.cohortCenterX,
                            hawk.getZ() - tracked.cohortCenterZ));

            boolean overIsland = SkyforgeAtmosphereTerrainAuthority.sample(
                            hawk.getBlockX(),
                            hawk.getBlockZ(),
                            level.getMinBuildHeight(),
                            level.getHeight())
                    .isPresent();
            if (tracked.cohort.equals("island") && !overIsland) {
                tracked.leftSemanticIsland = true;
                if (tracked.firstEdgeDepartureTick < 0L) {
                    tracked.firstEdgeDepartureTick = elapsed;
                }
            }

            if (diagnosticRegionGuaranteedLoaded(hawk.getBlockX(), hawk.getBlockZ())
                    && (!overIsland || tracked.cohort.equals("ground"))) {
                int worldSurface = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        hawk.getBlockX(),
                        hawk.getBlockZ());
                if (hawk.getY() <= worldSurface + 24.0) {
                    tracked.nearOrdinaryGround = true;
                    if (tracked.firstNearGroundTick < 0L) {
                        tracked.firstNearGroundTick = elapsed;
                    }
                }
            }

            SkyforgeWaveC6SoaringFaunaDevRuntime.DiagnosticSnapshot snapshot =
                    SkyforgeWaveC6SoaringFaunaDevRuntime.diagnosticSnapshot(hawk);
            if (snapshot != null) {
                tracked.c6Adapted |= snapshot.adapted();
                tracked.c6Disabled |= snapshot.disabled();
                tracked.c6SawSoaring |= snapshot.soaring();
                tracked.c6SawThermalSchedule |= snapshot.thermalScheduleActive();
                tracked.c6Transitions = Math.max(
                        tracked.c6Transitions, snapshot.transitionCount());
                tracked.c6SteeringCommands = Math.max(
                        tracked.c6SteeringCommands, snapshot.steeringCommands());
            }
        }
    }

    private static boolean diagnosticRegionGuaranteedLoaded(int blockX, int blockZ) {
        boolean islandRegion =
                blockX >= -128 && blockX <= 64 && blockZ >= -64 && blockZ <= 128;
        boolean groundRegion =
                blockX >= 720 && blockX <= 816 && blockZ >= -48 && blockZ <= 48;
        return islandRegion || groundRegion;
    }

    private static void recordGliderFrame(ServerLevel level, long elapsed) {
        SkyforgeAtmosphereView.Sample sample = atmosphere.sample(level, gliderProbe);
        double controlY = NATIVE_GLIDER_SINK_Y;
        double coupledY =
                SkyforgeWaveC7GliderLiftDevRuntime.diagnosticPostNativeY(controlY, sample);
        double strongerResult =
                SkyforgeWaveC7GliderLiftDevRuntime.diagnosticPostNativeY(
                        STRONGER_NATIVE_UPDRAFT_Y, sample);
        long interval = elapsed < OBSERVATION_TICKS
                ? Math.min(GLIDER_FRAME_PERIOD_TICKS, OBSERVATION_TICKS - elapsed)
                : 0L;
        double intervalDelta = (coupledY - controlY) * interval;
        projectedGliderAltitudeDelta += intervalDelta;

        GLIDER_FRAMES.add(new GliderFrame(
                level.getGameTime(),
                elapsed,
                sample,
                controlY,
                coupledY,
                coupledY - controlY,
                strongerResult,
                interval,
                intervalDelta,
                projectedGliderAltitudeDelta));
    }

    private static void finish(ServerLevel level) {
        if (complete) {
            return;
        }
        if (HAWKS.size() != HAWK_OFFSETS.length * 2) {
            fail("unexpected hawk cohort size " + HAWKS.size());
        }
        if (GLIDER_FRAMES.size() != 31) {
            fail("expected 31 glider frames, got " + GLIDER_FRAMES.size());
        }

        long adapted = HAWKS.stream().filter(hawk -> hawk.c6Adapted).count();
        if (arm.equals("control") && adapted != 0L) {
            fail("control arm unexpectedly adapted " + adapted + " hawks");
        }
        if (arm.equals("treatment") && adapted != HAWKS.size()) {
            fail("treatment arm adapted " + adapted + " of " + HAWKS.size() + " hawks");
        }

        Path output = outputPath();
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, encodeArtifact(level), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("failed to write #1180 diagnostic artifact " + output, failure);
        }

        complete = true;
        long transitions = HAWKS.stream().mapToLong(hawk -> hawk.c6Transitions).sum();
        long steering = HAWKS.stream().mapToLong(hawk -> hawk.c6SteeringCommands).sum();
        long islandDepartures = HAWKS.stream()
                .filter(hawk -> hawk.cohort.equals("island") && hawk.leftSemanticIsland)
                .count();
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF_IMP_1180_CONSUMER_DIAGNOSTIC PASS arm=" + arm
                        + " hawks=" + HAWKS.size()
                        + " islandDepartures=" + islandDepartures
                        + " c6Transitions=" + transitions
                        + " c6Steering=" + steering
                        + " gliderFrames=" + GLIDER_FRAMES.size()
                        + " projectedAltitudeDelta=" + projectedGliderAltitudeDelta
                        + " artifact=" + output);
    }

    private static String encodeArtifact(ServerLevel level) {
        SkyforgeA4mcTerrainBridge.Diagnostics terrain =
                SkyforgeA4mcTerrainBridge.diagnostics();
        StringBuilder json = new StringBuilder(48_000);
        json.append("{\n");
        json.append("  \"schema_version\": 1,\n");
        json.append("  \"artifact_kind\": \"SKYFORGE_SF_IMP_1180_CONSUMER_AB_ARM\",\n");
        json.append("  \"arm\": ").append(quote(arm)).append(",\n");
        json.append("  \"skyforge_source_sha\": ").append(quote(sourceSha())).append(",\n");
        json.append("  \"world_seed\": ").append(level.getSeed()).append(",\n");
        json.append("  \"player_anchor_tick\": ").append(playerAnchorTick).append(",\n");
        json.append("  \"warmup_ticks\": ").append(WARMUP_TICKS).append(",\n");
        json.append("  \"observation_start_tick\": ").append(observationStartTick).append(",\n");
        json.append("  \"observation_ticks\": ").append(OBSERVATION_TICKS).append(",\n");
        json.append("  \"c6_enabled\": ").append(c6Enabled).append(",\n");
        json.append("  \"c7_enabled\": ").append(c7Enabled).append(",\n");
        json.append("  \"terrain_provider\": {\n");
        json.append("    \"registered\": ").append(terrain.registered()).append(",\n");
        json.append("    \"queries\": ").append(terrain.queries()).append(",\n");
        json.append("    \"claims\": ").append(terrain.claims()).append(",\n");
        json.append("    \"declines\": ").append(terrain.declines()).append("\n");
        json.append("  },\n");
        json.append("  \"glider\": {\n");
        json.append("    \"model\": \"same-sample post-native vertical-response replay; no horizontal/airspeed model\",\n");
        json.append("    \"probe_position\": [")
                .append(number(gliderProbe.x)).append(", ")
                .append(number(gliderProbe.y)).append(", ")
                .append(number(gliderProbe.z)).append("],\n");
        json.append("    \"trusted_probe_candidates\": ")
                .append(gliderProbeTrustedCandidates).append(",\n");
        json.append("    \"native_baseline_y_per_tick\": ")
                .append(number(NATIVE_GLIDER_SINK_Y)).append(",\n");
        json.append("    \"stronger_native_y_per_tick\": ")
                .append(number(STRONGER_NATIVE_UPDRAFT_Y)).append(",\n");
        json.append("    \"projected_coupled_minus_control_altitude_blocks\": ")
                .append(number(projectedGliderAltitudeDelta)).append(",\n");
        json.append("    \"frames\": [\n");
        for (int i = 0; i < GLIDER_FRAMES.size(); i++) {
            GliderFrame frame = GLIDER_FRAMES.get(i);
            SkyforgeAtmosphereView.Sample sample = frame.sample;
            json.append("      {")
                    .append("\"game_tick\": ").append(frame.gameTick)
                    .append(", \"elapsed_ticks\": ").append(frame.elapsedTicks)
                    .append(", \"trusted\": ").append(sample.trustedForGameplay())
                    .append(", \"source_level\": ").append(quote(sample.sourceLevel()))
                    .append(", \"authority\": ").append(quote(sample.authority()))
                    .append(", \"updraft_mps\": ").append(number(sample.updraftMetersPerSecond()))
                    .append(", \"control_y\": ").append(number(frame.controlY))
                    .append(", \"coupled_y\": ").append(number(frame.coupledY))
                    .append(", \"delta_y\": ").append(number(frame.deltaY))
                    .append(", \"stronger_native_result_y\": ").append(number(frame.strongerNativeResultY))
                    .append(", \"interval_ticks\": ").append(frame.intervalTicks)
                    .append(", \"interval_altitude_delta\": ").append(number(frame.intervalAltitudeDelta))
                    .append(", \"cumulative_altitude_delta\": ").append(number(frame.cumulativeAltitudeDelta))
                    .append("}")
                    .append(i + 1 < GLIDER_FRAMES.size() ? "," : "")
                    .append("\n");
        }
        json.append("    ]\n");
        json.append("  },\n");
        json.append("  \"hawks\": [\n");
        for (int i = 0; i < HAWKS.size(); i++) {
            TrackedHawk hawk = HAWKS.get(i);
            double meanY = hawk.samples == 0L ? hawk.spawnY : hawk.sumY / hawk.samples;
            double minY = hawk.samples == 0L ? hawk.spawnY : hawk.minY;
            double maxY = hawk.samples == 0L ? hawk.spawnY : hawk.maxY;
            json.append("    {\n");
            json.append("      \"cohort\": ").append(quote(hawk.cohort)).append(",\n");
            json.append("      \"index\": ").append(hawk.index).append(",\n");
            json.append("      \"entity_id\": ").append(hawk.hawk.getId()).append(",\n");
            json.append("      \"uuid\": ").append(quote(hawk.hawk.getUUID().toString())).append(",\n");
            json.append("      \"spawn\": [")
                    .append(number(hawk.spawnX)).append(", ")
                    .append(number(hawk.spawnY)).append(", ")
                    .append(number(hawk.spawnZ)).append("],\n");
            json.append("      \"samples\": ").append(hawk.samples).append(",\n");
            json.append("      \"min_y\": ").append(number(minY)).append(",\n");
            json.append("      \"mean_y\": ").append(number(meanY)).append(",\n");
            json.append("      \"max_y\": ").append(number(maxY)).append(",\n");
            json.append("      \"max_horizontal_distance\": ")
                    .append(number(hawk.maxHorizontalDistance)).append(",\n");
            json.append("      \"left_semantic_island\": ").append(hawk.leftSemanticIsland).append(",\n");
            json.append("      \"first_edge_departure_tick\": ")
                    .append(hawk.firstEdgeDepartureTick).append(",\n");
            json.append("      \"near_ordinary_ground\": ").append(hawk.nearOrdinaryGround).append(",\n");
            json.append("      \"first_near_ground_tick\": ")
                    .append(hawk.firstNearGroundTick).append(",\n");
            json.append("      \"removed\": ").append(hawk.removed).append(",\n");
            json.append("      \"removal_reason\": ").append(quote(hawk.removalReason)).append(",\n");
            json.append("      \"c6_adapted\": ").append(hawk.c6Adapted).append(",\n");
            json.append("      \"c6_disabled\": ").append(hawk.c6Disabled).append(",\n");
            json.append("      \"c6_saw_soaring\": ").append(hawk.c6SawSoaring).append(",\n");
            json.append("      \"c6_saw_thermal_schedule\": ").append(hawk.c6SawThermalSchedule).append(",\n");
            json.append("      \"c6_transitions\": ").append(hawk.c6Transitions).append(",\n");
            json.append("      \"c6_steering_commands\": ").append(hawk.c6SteeringCommands).append("\n");
            json.append("    }")
                    .append(i + 1 < HAWKS.size() ? "," : "")
                    .append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static Path outputPath() {
        String configured = System.getProperty(OUTPUT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("missing system property " + OUTPUT_PROPERTY);
        }
        Path output = Path.of(configured).toAbsolutePath().normalize();
        if (output.getParent() == null) {
            throw new IllegalStateException("#1180 output has no parent: " + output);
        }
        return output;
    }

    private static String sourceSha() {
        String value = System.getenv("GITHUB_SHA");
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("non-finite #1180 artifact value " + value);
        }
        return String.format(Locale.ROOT, "%.17g", value);
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }

    private static void fail(String reason) {
        LOGGER.log(
                System.Logger.Level.ERROR,
                "SF_IMP_1180_CONSUMER_DIAGNOSTIC FAIL arm=" + arm + " " + reason);
        throw new IllegalStateException("#1180 consumer diagnostic failed: " + reason);
    }

    private static final class TrackedHawk {
        final String cohort;
        final int index;
        final Mob hawk;
        final double cohortCenterX;
        final double cohortCenterZ;
        final double spawnX;
        final double spawnY;
        final double spawnZ;
        long samples;
        double sumY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxHorizontalDistance;
        boolean leftSemanticIsland;
        long firstEdgeDepartureTick = -1L;
        boolean nearOrdinaryGround;
        long firstNearGroundTick = -1L;
        boolean removed;
        String removalReason = null;
        boolean c6Adapted;
        boolean c6Disabled;
        boolean c6SawSoaring;
        boolean c6SawThermalSchedule;
        int c6Transitions;
        int c6SteeringCommands;

        TrackedHawk(
                String cohort,
                int index,
                Mob hawk,
                double cohortCenterX,
                double cohortCenterZ,
                double spawnX,
                double spawnY,
                double spawnZ) {
            this.cohort = cohort;
            this.index = index;
            this.hawk = hawk;
            this.cohortCenterX = cohortCenterX;
            this.cohortCenterZ = cohortCenterZ;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
        }
    }

    private record GliderFrame(
            long gameTick,
            long elapsedTicks,
            SkyforgeAtmosphereView.Sample sample,
            double controlY,
            double coupledY,
            double deltaY,
            double strongerNativeResultY,
            long intervalTicks,
            double intervalAltitudeDelta,
            double cumulativeAltitudeDelta) {}
}
