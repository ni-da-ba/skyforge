package io.github.nidaba.skyforge.neoforge1211;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Development-gated Wave C6 controller that gives Fowl Play's existing red-tailed hawk a truthful
 * thermal-soaring mode sourced from Aerodynamics4MC.
 */
final class SkyforgeWaveC6SoaringFaunaDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC6SoaringFauna";
    static final String ACCEPTANCE_PROPERTY = "skyforge.dev.waveC6Acceptance";
    private static final long ACCEPTANCE_TIMEOUT_TICKS = 320L;
    private static final String FOWL_PLAY_MOD_ID = "fowlplay";
    private static final String A4MC_MOD_ID = "aerodynamics4mc";
    private static final String HAWK_ID = "fowlplay:hawk";
    private static final long SAMPLE_PERIOD_TICKS = 20L;
    private static final long TARGET_PERIOD_TICKS = 40L;
    private static final double SEARCH_RADIUS = 12.0;
    private static final double ORBIT_RADIUS = 10.0;

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC6SoaringFaunaDevRuntime.class.getName());
    private static final Map<Mob, HawkState> HAWKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    @FunctionalInterface
    private interface LiftSampler {
        SkyforgeA4mcLiftBridge.Sample sample(ServerLevel level, Vec3 position);
    }

    private static boolean installed;
    private static SkyforgeA4mcLiftBridge atmosphere;
    private static SkyforgeFowlPlayHawkBridge birds;
    private static LiftSampler liftSampler;

    private static boolean acceptanceMode;
    private static long acceptanceStartTick = Long.MIN_VALUE;
    private static long acceptanceEnteredTick = Long.MIN_VALUE;
    private static Mob acceptanceHawk;
    private static int acceptanceJoinedHawks;
    private static int acceptanceAdaptations;
    private static int acceptanceSteeringCommands;
    private static boolean acceptanceEnteredSoar;
    private static boolean acceptanceExitedSoar;
    private static boolean acceptanceFinished;

    private SkyforgeWaveC6SoaringFaunaDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || installed) {
            return;
        }
        if (!ModList.get().isLoaded(FOWL_PLAY_MOD_ID) || !ModList.get().isLoaded(A4MC_MOD_ID)) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Wave C6 requested but required optional mods are absent; soaring-fauna bridge remains inert.");
            return;
        }

        // Fowl Play's FPActivities entries are DeferredHolders and are not yet bound while mod
        // constructors execute. Register inert hooks now, then resolve the optional APIs only after
        // NeoForge reports the server started and registries are fully available.
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC6SoaringFaunaDevRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC6SoaringFaunaDevRuntime::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC6SoaringFaunaDevRuntime::onEntityTickPost);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC6SoaringFaunaDevRuntime::onServerTickPost);
        acceptanceMode = Boolean.getBoolean(ACCEPTANCE_PROPERTY);
        installed = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge Wave C6 soaring-fauna hooks armed; optional API binding deferred until server start.");
    }

    private static synchronized void onServerStarted(ServerStartedEvent event) {
        if (atmosphere != null && birds != null) {
            return;
        }

        try {
            atmosphere = SkyforgeA4mcLiftBridge.create();
            birds = SkyforgeFowlPlayHawkBridge.create();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Wave C6 optional API surface no longer matches pinned Fowl Play/A4MC stack", failure);
        }

        liftSampler = atmosphere::sample;

        LOGGER.log(
                System.Logger.Level.INFO,
                "Skyforge Wave C6 Fowl Play/A4MC soaring-fauna compatibility enabled.");

        if (acceptanceMode) {
            acceptanceStartTick = event.getServer().overworld().getGameTime();
            liftSampler = SkyforgeWaveC6SoaringFaunaDevRuntime::sampleAcceptanceLift;

            var source = event.getServer().createCommandSourceStack();
            event.getServer().getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
            event.getServer().getCommands().performPrefixedCommand(source, "time set 2000");
            // Minecraft 1.21.1's performPrefixedCommand is void. The stronger proof is the
            // subsequent EntityJoinLevelEvent/adaptation evidence, so do not infer summon success
            // from a command return value that does not exist on this runtime.
            event.getServer().getCommands().performPrefixedCommand(
                    source, "summon fowlplay:hawk 0 120 0");
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob) || !isHawk(mob)) {
            return;
        }
        if (acceptanceMode) {
            // Command-summoned mobs are allowed to despawn under ordinary mob lifecycle rules.
            // This specimen must remain tickable long enough to prove the full enter/hold/exit
            // hysteresis cycle, so persistence is a fixture property rather than production logic.
            mob.setPersistenceRequired();
            acceptanceJoinedHawks++;
            if (acceptanceHawk == null) {
                acceptanceHawk = mob;
            }
        }
        ensureState(mob);
    }

    private static void onEntityTickPost(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof Mob mob) || !isHawk(mob)) {
            return;
        }

        HawkState state = ensureState(mob);
        if (state == null) {
            return;
        }

        long gameTick = level.getGameTime();
        if (gameTick < state.nextSampleTick) {
            return;
        }
        state.nextSampleTick = gameTick + SAMPLE_PERIOD_TICKS;

        SkyforgeA4mcLiftBridge.Sample sample = sampleLift(level, mob.position());
        SkyforgeThermalSoaringDecision.State next = SkyforgeThermalSoaringDecision.update(
                state.decision, sample.trusted(), sample.updraftMetersPerSecond(), gameTick);

        if (next.soaring() != state.decision.soaring()) {
            try {
                state.handle.useThermalSchedule(next.soaring());
            } catch (ReflectiveOperationException failure) {
                disableHawk(state, failure);
                return;
            }

            state.transitionCount++;
            if (acceptanceMode && mob == acceptanceHawk) {
                if (next.soaring()) {
                    acceptanceEnteredSoar = true;
                    acceptanceEnteredTick = gameTick;
                } else if (acceptanceEnteredSoar) {
                    acceptanceExitedSoar = true;
                }
            }
        }
        state.decision = next;

        if (!next.soaring()
                || !SkyforgeThermalSoaringDecision.isStockRaptorHuntWindow(level.getDayTime())
                || gameTick < state.nextTargetTick) {
            return;
        }

        state.nextTargetTick = gameTick + TARGET_PERIOD_TICKS;
        steerTowardThermal(level, mob, state);
    }

    private static HawkState ensureState(Mob mob) {
        if (birds == null || atmosphere == null) {
            return null;
        }

        HawkState existing = HAWKS.get(mob);
        if (existing != null) {
            return existing.disabled ? null : existing;
        }

        try {
            HawkState created = new HawkState(birds.adapt(mob));
            HAWKS.put(mob, created);
            if (acceptanceMode && mob == acceptanceHawk) {
                acceptanceAdaptations++;
            }
            return created;
        } catch (ReflectiveOperationException failure) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Wave C6 could not adapt Fowl Play hawk; this entity remains stock: " + failure);
            HawkState disabled = HawkState.disabled();
            HAWKS.put(mob, disabled);
            return null;
        }
    }

    private static void steerTowardThermal(ServerLevel level, Mob hawk, HawkState state) {
        Vec3 center = hawk.position();
        SkyforgeA4mcLiftBridge.Sample best = sampleLift(level, center);
        Vec3 bestPosition = center;

        Vec3[] candidates = {
            center.add(SEARCH_RADIUS, 0.0, 0.0),
            center.add(-SEARCH_RADIUS, 0.0, 0.0),
            center.add(0.0, 0.0, SEARCH_RADIUS),
            center.add(0.0, 0.0, -SEARCH_RADIUS)
        };

        for (Vec3 candidate : candidates) {
            SkyforgeA4mcLiftBridge.Sample sampled = sampleLift(level, candidate);
            if (sampled.trusted()
                    && (!best.trusted()
                            || sampled.updraftMetersPerSecond() > best.updraftMetersPerSecond())) {
                best = sampled;
                bestPosition = candidate;
            }
        }

        if (!best.trusted()) {
            return;
        }

        double phase = (level.getGameTime() * 0.035) + (hawk.getId() * 0.73);
        double climb = Math.clamp(best.updraftMetersPerSecond() * 1.5, 2.0, 8.0);
        double x = bestPosition.x + Math.cos(phase) * ORBIT_RADIUS;
        double z = bestPosition.z + Math.sin(phase) * ORBIT_RADIUS;
        double y = Math.max(hawk.getY() + climb, bestPosition.y + climb);

        try {
            state.handle.flyToward(x, y, z);
            state.steeringCommands++;
            if (acceptanceMode && hawk == acceptanceHawk) {
                acceptanceSteeringCommands++;
            }
        } catch (ReflectiveOperationException failure) {
            disableHawk(state, failure);
        }
    }

    private static SkyforgeA4mcLiftBridge.Sample sampleLift(ServerLevel level, Vec3 position) {
        LiftSampler sampler = liftSampler;
        return sampler == null ? SkyforgeA4mcLiftBridge.Sample.unavailable() : sampler.sample(level, position);
    }

    private static SkyforgeA4mcLiftBridge.Sample sampleAcceptanceLift(ServerLevel level, Vec3 position) {
        if (acceptanceEnteredTick != Long.MIN_VALUE
                && level.getGameTime() - acceptanceEnteredTick >= SkyforgeThermalSoaringDecision.MIN_HOLD_TICKS) {
            return new SkyforgeA4mcLiftBridge.Sample(true, 0.50);
        }

        return new SkyforgeA4mcLiftBridge.Sample(true, 2.00);
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (!acceptanceMode || acceptanceFinished || acceptanceStartTick == Long.MIN_VALUE) {
            return;
        }

        long elapsed = event.getServer().overworld().getGameTime() - acceptanceStartTick;
        if (acceptanceHawk != null && acceptanceHawk.isRemoved()) {
            failAcceptance(
                    "hawk removed before hysteresis cycle completed reason="
                            + acceptanceHawk.getRemovalReason());
            return;
        }

        HawkState state = acceptanceHawk == null ? null : HAWKS.get(acceptanceHawk);

        if (state != null
                && !state.disabled
                && acceptanceJoinedHawks == 1
                && acceptanceAdaptations == 1
                && acceptanceEnteredSoar
                && acceptanceExitedSoar
                && acceptanceSteeringCommands > 0
                && !state.decision.soaring()
                && !state.handle.thermalScheduleActive()
                && state.transitionCount == 2) {
            acceptanceFinished = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "WAVE_C6_ACCEPTANCE PASS joinedHawks="
                            + acceptanceJoinedHawks
                            + " adaptations="
                            + acceptanceAdaptations
                            + " transitions="
                            + state.transitionCount
                            + " steeringCommands="
                            + acceptanceSteeringCommands);
            return;
        }

        if (elapsed >= ACCEPTANCE_TIMEOUT_TICKS) {
            failAcceptance(
                    "timeout joinedHawks="
                            + acceptanceJoinedHawks
                            + " adaptations="
                            + acceptanceAdaptations
                            + " entered="
                            + acceptanceEnteredSoar
                            + " exited="
                            + acceptanceExitedSoar
                            + " steeringCommands="
                            + acceptanceSteeringCommands
                            + " state="
                            + (state == null ? "missing" : state.decision));
        }
    }

    private static void failAcceptance(String reason) {
        if (acceptanceFinished) {
            return;
        }

        acceptanceFinished = true;
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C6_ACCEPTANCE FAIL " + reason);
    }

    private static void disableHawk(HawkState state, ReflectiveOperationException failure) {
        state.disabled = true;
        LOGGER.log(
                System.Logger.Level.WARNING,
                "Wave C6 hawk compatibility failed closed; stock Fowl Play behavior resumes: " + failure);
    }

    private static boolean isHawk(Entity entity) {
        return HAWK_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    static int adaptedHawkCountForAcceptance() {
        synchronized (HAWKS) {
            return (int) HAWKS.values().stream().filter(state -> !state.disabled).count();
        }
    }

    private static final class HawkState {
        final SkyforgeFowlPlayHawkBridge.HawkHandle handle;
        SkyforgeThermalSoaringDecision.State decision = SkyforgeThermalSoaringDecision.State.inactive();
        long nextSampleTick;
        long nextTargetTick;
        int transitionCount;
        int steeringCommands;
        boolean disabled;

        HawkState(SkyforgeFowlPlayHawkBridge.HawkHandle handle) {
            this.handle = handle;
        }

        static HawkState disabled() {
            HawkState state = new HawkState(null);
            state.disabled = true;
            return state;
        }
    }
}
