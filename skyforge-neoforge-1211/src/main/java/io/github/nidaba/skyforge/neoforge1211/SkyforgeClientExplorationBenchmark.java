package io.github.nidaba.skyforge.neoforge1211;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-only integrated-server half of the PERF-0502 player-like exploration harness.
 *
 * <p>The current executable product slice does not yet contain every Bootstrap Province gameplay
 * phase in one world. This first tranche therefore drives the real quick-play player through a
 * deterministic chunk-crossing route and labels that movement as load pressure, never as real
 * glider/aircraft physics. The client half separately records real rendered-frame intervals.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgeClientExplorationBenchmark {
    static final String ENABLE_PROPERTY = "skyforge.dev.clientExplorationBenchmark";
    static final String ENABLE_ENV = "SKYFORGE_CLIENT_EXPLORATION_BENCHMARK";

    private static final int SPAWN_IDLE_TICKS = 40;
    private static final int WALK_TICKS = 120;
    private static final int GLIDE_PROXY_TICKS = 120;
    private static final int FLIGHT_PROXY_TICKS = 200;
    private static final int SETTLE_TICKS = 40;
    private static final double WALK_BLOCKS_PER_TICK = 0.22d;
    private static final double GLIDE_PROXY_BLOCKS_PER_TICK = 0.85d;
    private static final double FLIGHT_PROXY_BLOCKS_PER_TICK = 2.10d;
    private static final double ROUTE_Y = 320.0d;

    private static final Set<Long> VISITED_CHUNKS = new LinkedHashSet<>();
    private static long routeTick;
    private static long phaseStartTick;
    private static long phaseStartNanos;
    private static double routeX;
    private static double routeZ = 112.0d;
    private static Phase phase = Phase.WAITING_FOR_PLAYER;
    private static boolean playerReady;
    private static volatile boolean serverProofComplete;
    private static final Map<String, Long> PHASE_NANOS = new LinkedHashMap<>();
    private static final Map<String, Long> PHASE_TICKS = new LinkedHashMap<>();

    private SkyforgeClientExplorationBenchmark() {}

    static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY)
                || Boolean.parseBoolean(System.getenv().getOrDefault(ENABLE_ENV, "false"));
    }

    static boolean serverProofComplete() {
        return serverProofComplete;
    }

    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.teleportTo(routeX, ROUTE_Y, routeZ);
        player.setYRot(180.0f);
        player.setXRot(12.0f);
        VISITED_CHUNKS.clear();
        PHASE_NANOS.clear();
        PHASE_TICKS.clear();
        routeTick = 0L;
        recordVisited(player);
        playerReady = true;
        transition(Phase.SPAWN_IDLE, event.getEntity().level().getGameTime());
    }

    @SubscribeEvent
    static void onServerTickPre(ServerTickEvent.Pre event) {
        if (!enabled() || !playerReady || serverProofComplete) {
            return;
        }
        TickClock.begin();
    }

    @SubscribeEvent
    static void onServerTickPost(ServerTickEvent.Post event) {
        if (!enabled() || !playerReady || serverProofComplete) {
            return;
        }
        long tickNanos = TickClock.finish();
        if (tickNanos > 0L) {
            SkyforgeRuntimePerformanceMetrics.recordDistributionSample(
                    "clientExploration.serverTickNanos", tickNanos);
        }

        ServerLevel level = null;
        for (ServerLevel candidate : event.getServer().getAllLevels()) {
            if (candidate.dimension().equals(Level.OVERWORLD)) {
                level = candidate;
                break;
            }
        }
        if (level == null || level.players().isEmpty()) {
            return;
        }
        ServerPlayer player = level.players().getFirst();
        long gameTime = level.getGameTime();
        long ticksInPhase = gameTime - phaseStartTick;

        switch (phase) {
            case SPAWN_IDLE -> {
                if (ticksInPhase >= SPAWN_IDLE_TICKS) {
                    transition(Phase.WALK_TRAVERSAL, gameTime);
                }
            }
            case WALK_TRAVERSAL -> {
                advance(player, WALK_BLOCKS_PER_TICK, 0.0d);
                if (ticksInPhase >= WALK_TICKS) {
                    transition(Phase.GLIDE_LOAD_PROXY, gameTime);
                }
            }
            case GLIDE_LOAD_PROXY -> {
                advance(player, GLIDE_PROXY_BLOCKS_PER_TICK, 0.18d);
                if (ticksInPhase >= GLIDE_PROXY_TICKS) {
                    transition(Phase.FRESH_TERRAIN_FLIGHT_LOAD_PROXY, gameTime);
                }
            }
            case FRESH_TERRAIN_FLIGHT_LOAD_PROXY -> {
                advance(player, FLIGHT_PROXY_BLOCKS_PER_TICK, 0.42d);
                if (ticksInPhase >= FLIGHT_PROXY_TICKS) {
                    transition(Phase.SETTLE, gameTime);
                }
            }
            case SETTLE -> {
                if (ticksInPhase >= SETTLE_TICKS) {
                    finish(player, level);
                }
            }
            case WAITING_FOR_PLAYER, COMPLETE -> {
                // no-op
            }
        }
        routeTick++;
    }

    private static void advance(ServerPlayer player, double blocksPerTick, double zFraction) {
        routeX += blocksPerTick;
        routeZ += blocksPerTick * zFraction;
        player.teleportTo(routeX, ROUTE_Y, routeZ);
        recordVisited(player);
    }

    private static void recordVisited(ServerPlayer player) {
        VISITED_CHUNKS.add(player.chunkPosition().toLong());
    }

    private static void transition(Phase next, long gameTime) {
        if (phase != Phase.WAITING_FOR_PLAYER && phase != Phase.COMPLETE) {
            PHASE_NANOS.put(phase.key, Math.max(0L, System.nanoTime() - phaseStartNanos));
            PHASE_TICKS.put(phase.key, Math.max(0L, gameTime - phaseStartTick));
        }
        phase = next;
        phaseStartTick = gameTime;
        phaseStartNanos = System.nanoTime();
    }

    private static void finish(ServerPlayer player, ServerLevel level) {
        transition(Phase.COMPLETE, level.getGameTime());
        serverProofComplete = true;

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("clientExplorationServerPass", true);
        evidence.put("benchmarkSchema", "perf-0502-v1");
        evidence.put("spawnIdleMode", "EXECUTED_REAL");
        evidence.put("walkTraversalMode", "EXECUTED_LOAD_PROXY");
        evidence.put("glideTraversalMode", "EXECUTED_LOAD_PROXY");
        evidence.put("freshTerrainFlightMode", "EXECUTED_LOAD_PROXY");
        evidence.put("activeMachineryCargoMode", "UNAVAILABLE_CURRENT_SLICE");
        evidence.put("saveReloadMode", "UNAVAILABLE_CURRENT_SLICE");
        evidence.put("routeTicks", routeTick);
        evidence.put("visitedChunks", VISITED_CHUNKS.size());
        evidence.put("finalChunkX", player.chunkPosition().x);
        evidence.put("finalChunkZ", player.chunkPosition().z);
        evidence.put("serverEntityCount", countEntities(level));
        PHASE_NANOS.forEach((key, value) -> evidence.put("phase." + key + ".wallNanos", value));
        PHASE_TICKS.forEach((key, value) -> evidence.put("phase." + key + ".ticks", value));
        evidence.putAll(SkyforgeRuntimePerformanceMetrics.evidence());
        SkyforgeAutomatedAcceptanceHarness.record(evidence);
    }

    private static long countEntities(ServerLevel level) {
        long count = 0L;
        for (Entity ignored : level.getAllEntities()) {
            count++;
        }
        return count;
    }

    private enum Phase {
        WAITING_FOR_PLAYER("waiting"),
        SPAWN_IDLE("spawn_idle"),
        WALK_TRAVERSAL("walk_traversal"),
        GLIDE_LOAD_PROXY("glide_traversal"),
        FRESH_TERRAIN_FLIGHT_LOAD_PROXY("fresh_terrain_flight"),
        SETTLE("settle"),
        COMPLETE("complete");

        private final String key;

        Phase(String key) {
            this.key = key;
        }
    }

    private static final class TickClock {
        private static final ThreadLocal<Long> START = new ThreadLocal<>();

        private TickClock() {}

        static void begin() {
            START.set(System.nanoTime());
        }

        static long finish() {
            Long start = START.get();
            START.remove();
            return start == null ? 0L : Math.max(0L, System.nanoTime() - start);
        }
    }
}
