package io.github.nidaba.skyforge.neoforge1211;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * #1180 diagnostic-only observer for the owner-reported hawk descent and sub-perceptual glider lift.
 *
 * <p>This fixture never changes consumer thresholds or atmosphere physics. It waits for a trusted
 * A4MC sample, spawns the same bounded hawk batch in control/treatment worlds, observes movement,
 * and projects the already-accepted C7 pure coupling against live A4MC samples.
 */
final class SkyforgeConsumerBehaviorDiagnostic {
    static final String ENABLE_PROPERTY = "skyforge.dev.consumerBehaviorDiagnostic";
    static final String OUTPUT_PROPERTY = "skyforge.dev.consumerBehaviorDiagnosticOutput";
    private static final String HAWK_ID = "fowlplay:hawk";
    private static final int HAWK_COUNT = 8;
    private static final long OBSERVATION_TICKS = 600L;
    private static final long READINESS_TIMEOUT_TICKS = 4_800L;
    private static final double ISLAND_EDGE_RADIUS = 140.0;
    private static final double BELOW_ISLAND_Y = 220.0;
    private static final Vec3 FIELD_CENTER = new Vec3(-32.0, 340.0, 32.0);
    private static final Vec3 LIFT_BIASED = new Vec3(160.0, 365.0, -32.0);
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeConsumerBehaviorDiagnostic.class.getName());

    private static String arm;
    private static Path output;
    private static SkyforgeAtmosphereView atmosphere;
    private static long serverStartTick = Long.MIN_VALUE;
    private static long observationStartTick = Long.MIN_VALUE;
    private static boolean installed;
    private static boolean completed;
    private static final Map<UUID, HawkObservation> hawks = new LinkedHashMap<>();
    private static long gliderSamples;
    private static long gliderTrustedSamples;
    private static double gliderCumulativeDeltaCenter;
    private static double gliderCumulativeDeltaLiftBiased;
    private static double updraftSumCenter;
    private static double updraftSumLiftBiased;
    private static double maxUpdraft = Double.NEGATIVE_INFINITY;

    private SkyforgeConsumerBehaviorDiagnostic() {}

    static synchronized void installFromSystemProperty() {
        String configured = System.getProperty(ENABLE_PROPERTY, "").trim();
        if (configured.isEmpty() || installed) {
            return;
        }
        if (!configured.equals("control") && !configured.equals("treatment")) {
            throw new IllegalArgumentException("#1180 arm must be control or treatment: " + configured);
        }
        arm = configured;
        String outputPath = System.getProperty(OUTPUT_PROPERTY, "").trim();
        if (outputPath.isEmpty()) {
            throw new IllegalArgumentException("#1180 diagnostic requires " + OUTPUT_PROPERTY);
        }
        output = Path.of(outputPath);
        NeoForge.EVENT_BUS.addListener(SkyforgeConsumerBehaviorDiagnostic::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeConsumerBehaviorDiagnostic::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(SkyforgeConsumerBehaviorDiagnostic::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(SkyforgeConsumerBehaviorDiagnostic::onServerTickPost);
        installed = true;
        LOGGER.log(System.Logger.Level.INFO, "SF_IMP_1180_DIAGNOSTIC armed arm=" + arm);
    }

    private static synchronized void onServerStarted(ServerStartedEvent event) {
        try {
            atmosphere = SkyforgeA4mcAtmosphereBridge.create();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("#1180 could not bind accepted A4MC gameplay API", failure);
        }
        MinecraftServer server = event.getServer();
        serverStartTick = server.overworld().getGameTime();
        var source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
        server.getCommands().performPrefixedCommand(source, "time set 2000");
        server.getCommands().performPrefixedCommand(source, "forceload add -128 -128 127 127");
        LOGGER.log(System.Logger.Level.INFO, "SF_IMP_1180_DIAGNOSTIC waiting_for_trusted_a4mc arm=" + arm);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (completed || atmosphere == null || serverStartTick == Long.MIN_VALUE) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        long now = level.getGameTime();

        if (observationStartTick == Long.MIN_VALUE) {
            SkyforgeAtmosphereView.Sample sample = atmosphere.sample(level, FIELD_CENTER);
            if (sample.trustedForGameplay()) {
                beginObservation(event.getServer(), now);
                return;
            }
            if (now - serverStartTick >= READINESS_TIMEOUT_TICKS) {
                fail("A4MC never became trusted before readiness timeout");
            }
            return;
        }

        sampleGliderProjection(level);
        if (now - observationStartTick >= OBSERVATION_TICKS) {
            finish(level, now);
        }
    }

    private static void beginObservation(MinecraftServer server, long now) {
        observationStartTick = now;
        var source = server.createCommandSourceStack();
        double[][] offsets = {
            {-48, -48}, {-16, -48}, {16, -48}, {48, -48},
            {-48, 48}, {-16, 48}, {16, 48}, {48, 48}
        };
        for (double[] offset : offsets) {
            double x = FIELD_CENTER.x + offset[0];
            double z = FIELD_CENTER.z + offset[1];
            server.getCommands().performPrefixedCommand(
                    source, "summon fowlplay:hawk " + x + " 340 " + z + " {PersistenceRequired:1b}");
        }
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF_IMP_1180_DIAGNOSTIC observation_started arm=" + arm + " tick=" + now);
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (observationStartTick == Long.MIN_VALUE
                || !(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof Mob mob)
                || !isHawk(mob)) {
            return;
        }
        mob.setPersistenceRequired();
        hawks.putIfAbsent(mob.getUUID(), new HawkObservation(mob.position()));
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        if (observationStartTick == Long.MIN_VALUE || !(event.getEntity() instanceof Mob mob) || !isHawk(mob)) {
            return;
        }
        HawkObservation observation = hawks.get(mob.getUUID());
        if (observation == null) {
            return;
        }
        observation.observe(mob);
    }

    private static void sampleGliderProjection(ServerLevel level) {
        SkyforgeAtmosphereView.Sample center = atmosphere.sample(level, FIELD_CENTER);
        SkyforgeAtmosphereView.Sample lift = atmosphere.sample(level, LIFT_BIASED);
        gliderSamples++;
        if (center.trustedForGameplay() && lift.trustedForGameplay()) {
            gliderTrustedSamples++;
            double baseline = -SkyforgeGliderLiftCoupling.RELIABLE_GLIDER_BASELINE_SINK_BLOCKS_PER_TICK;
            gliderCumulativeDeltaCenter +=
                    SkyforgeGliderLiftCoupling.apply(baseline, true, center.updraftMetersPerSecond()) - baseline;
            gliderCumulativeDeltaLiftBiased +=
                    SkyforgeGliderLiftCoupling.apply(baseline, true, lift.updraftMetersPerSecond()) - baseline;
            updraftSumCenter += center.updraftMetersPerSecond();
            updraftSumLiftBiased += lift.updraftMetersPerSecond();
            maxUpdraft = Math.max(maxUpdraft, Math.max(center.updraftMetersPerSecond(), lift.updraftMetersPerSecond()));
        }
    }

    private static void finish(ServerLevel level, long now) {
        completed = true;
        SkyforgeWaveC6SoaringFaunaDevRuntime.DiagnosticSnapshot c6 =
                SkyforgeWaveC6SoaringFaunaDevRuntime.diagnosticSnapshot();

        int edgeDepartures = 0;
        int belowIsland = 0;
        int below128 = 0;
        double minY = Double.POSITIVE_INFINITY;
        double meanFinalY = 0.0;
        int observed = 0;
        List<String> hawkJson = new ArrayList<>();
        for (HawkObservation h : hawks.values()) {
            observed++;
            if (h.edgeDeparture) edgeDepartures++;
            if (h.minY < BELOW_ISLAND_Y) belowIsland++;
            if (h.minY < 128.0) below128++;
            minY = Math.min(minY, h.minY);
            meanFinalY += h.last.y;
            hawkJson.add(h.toJson());
        }
        if (observed > 0) {
            meanFinalY /= observed;
        }

        double meanUpdraftCenter = gliderTrustedSamples == 0 ? 0.0 : updraftSumCenter / gliderTrustedSamples;
        double meanUpdraftLift = gliderTrustedSamples == 0 ? 0.0 : updraftSumLiftBiased / gliderTrustedSamples;
        String json = "{\n"
                + "  \"artifact_kind\": \"SKYFORGE_SF_IMP_1180_CONSUMER_AB\",\n"
                + "  \"arm\": \"" + arm + "\",\n"
                + "  \"observation_ticks\": " + (now - observationStartTick) + ",\n"
                + "  \"hawk_count\": " + observed + ",\n"
                + "  \"hawk_edge_departures\": " + edgeDepartures + ",\n"
                + "  \"hawk_below_island\": " + belowIsland + ",\n"
                + "  \"hawk_below_128\": " + below128 + ",\n"
                + "  \"hawk_min_y\": " + finiteOrZero(minY) + ",\n"
                + "  \"hawk_mean_final_y\": " + meanFinalY + ",\n"
                + "  \"c6_adapted\": " + c6.adapted() + ",\n"
                + "  \"c6_soaring\": " + c6.soaring() + ",\n"
                + "  \"c6_transitions\": " + c6.transitions() + ",\n"
                + "  \"c6_steering_commands\": " + c6.steeringCommands() + ",\n"
                + "  \"glider_samples\": " + gliderSamples + ",\n"
                + "  \"glider_trusted_samples\": " + gliderTrustedSamples + ",\n"
                + "  \"glider_mean_updraft_center_mps\": " + meanUpdraftCenter + ",\n"
                + "  \"glider_mean_updraft_lift_biased_mps\": " + meanUpdraftLift + ",\n"
                + "  \"glider_max_updraft_mps\": " + finiteOrZero(maxUpdraft) + ",\n"
                + "  \"glider_cumulative_vertical_delta_center_blocks\": " + gliderCumulativeDeltaCenter + ",\n"
                + "  \"glider_cumulative_vertical_delta_lift_biased_blocks\": " + gliderCumulativeDeltaLiftBiased + ",\n"
                + "  \"hawks\": [" + String.join(",", hawkJson) + "]\n"
                + "}\n";
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, json, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("#1180 could not write diagnostic artifact " + output, failure);
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "SF_IMP_1180_DIAGNOSTIC PASS arm=" + arm
                        + " hawks=" + observed
                        + " edgeDepartures=" + edgeDepartures
                        + " belowIsland=" + belowIsland
                        + " below128=" + below128
                        + " minY=" + finiteOrZero(minY)
                        + " c6Adapted=" + c6.adapted()
                        + " c6Transitions=" + c6.transitions()
                        + " c6Steering=" + c6.steeringCommands()
                        + " trustedGliderSamples=" + gliderTrustedSamples
                        + " maxUpdraft=" + finiteOrZero(maxUpdraft)
                        + " gliderDeltaCenter=" + gliderCumulativeDeltaCenter
                        + " gliderDeltaLiftBiased=" + gliderCumulativeDeltaLiftBiased);
    }

    private static void fail(String reason) {
        completed = true;
        LOGGER.log(System.Logger.Level.ERROR, "SF_IMP_1180_DIAGNOSTIC FAIL arm=" + arm + " reason=" + reason);
    }

    private static boolean isHawk(Entity entity) {
        return HAWK_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static final class HawkObservation {
        final Vec3 start;
        Vec3 last;
        double minY;
        double maxY;
        double sumY;
        long samples;
        double maxHorizontalDistance;
        boolean edgeDeparture;

        HawkObservation(Vec3 start) {
            this.start = start;
            this.last = start;
            this.minY = start.y;
            this.maxY = start.y;
        }

        void observe(Mob hawk) {
            last = hawk.position();
            minY = Math.min(minY, last.y);
            maxY = Math.max(maxY, last.y);
            sumY += last.y;
            samples++;
            double distance = Math.hypot(last.x - FIELD_CENTER.x, last.z - FIELD_CENTER.z);
            maxHorizontalDistance = Math.max(maxHorizontalDistance, distance);
            if (distance >= ISLAND_EDGE_RADIUS) {
                edgeDeparture = true;
            }
        }

        String toJson() {
            double meanY = samples == 0 ? start.y : sumY / samples;
            return "{\"start_y\":" + start.y
                    + ",\"final_y\":" + last.y
                    + ",\"min_y\":" + minY
                    + ",\"max_y\":" + maxY
                    + ",\"mean_y\":" + meanY
                    + ",\"max_horizontal_distance\":" + maxHorizontalDistance
                    + ",\"edge_departure\":" + edgeDeparture + "}";
        }
    }
}
