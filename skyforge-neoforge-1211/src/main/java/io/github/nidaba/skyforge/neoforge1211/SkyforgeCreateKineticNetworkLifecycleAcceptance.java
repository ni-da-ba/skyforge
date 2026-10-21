package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** PLATFORM-002 exact-stack qualification for the fixed-world Create kinetic network lifecycle. */
final class SkyforgeCreateKineticNetworkLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformCreateKineticNetworkLifecycle";
    static final String CAPABILITY = "CREATE_KINETIC_NETWORK_LIFECYCLE";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeCreateKineticNetworkLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_CREATE_KINETIC_NETWORK_LIFECYCLE";

    private static final ResourceLocation MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");
    private static final ResourceLocation ENDPOINT_ID = id("create:gearbox");

    private static final BlockPos MOTOR = new BlockPos(0, 200, 0);
    private static final BlockPos SHAFT = new BlockPos(1, 200, 0);
    private static final BlockPos ENDPOINT = new BlockPos(2, 200, 0);
    private static final long INIT_DEADLINE_TICKS = 60L;
    private static final long DISCONNECT_DEADLINE_TICKS = 60L;
    private static final long REBUILD_DEADLINE_TICKS = 60L;

    private static ServerLevel level;
    private static BlockState shaftState;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static Stage stage;
    private static boolean complete;
    private static float initialSpeed;

    private enum Stage {
        INITIAL_BUILD,
        DISCONNECTED,
        REBUILT
    }

    private SkyforgeCreateKineticNetworkLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeCreateKineticNetworkLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeCreateKineticNetworkLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            prepareFixture();
            stage = Stage.INITIAL_BUILD;
            waitDiagnostic = diagnostic(
                    "downstream Create kinetic endpoint receives nonzero propagated speed",
                    now,
                    now + INIT_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "fixture placed; awaiting kinetic attachment");
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic(
                            "minimal fixed-world Create kinetic fixture is placed",
                            now,
                            now,
                            fixtureIds(),
                            safeServerState(),
                            exception.toString()),
                    "fixture preparation failed: " + exception);
            throw exception;
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        if (complete || level == null || waitDiagnostic == null || stage == null) {
            return;
        }
        long now = level.getGameTime();
        try {
            switch (stage) {
                case INITIAL_BUILD -> pollInitialBuild(now);
                case DISCONNECTED -> pollDisconnected(now);
                case REBUILT -> pollRebuilt(now);
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : exception;
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic("reflectionFailure=" + cause),
                    "Create kinetic runtime reflection failed: " + cause);
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                    finalDiagnostic(exception.toString()),
                    "Create kinetic lifecycle failed: " + exception);
        }
    }

    private static void pollInitialBuild(long now) throws ReflectiveOperationException {
        Object motor = requireExpectedBlockEntity(MOTOR, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(ENDPOINT, "GearboxBlockEntity", now);
        KineticState sourceState = kineticState(motor);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) > 0.0f && endpointState.hasSource() && endpointState.hasNetwork()) {
            initialSpeed = endpointState.speed();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " INITIAL_NETWORK speed=" + endpointState.speed()
                            + " theoreticalSpeed=" + endpointState.theoreticalSpeed()
                            + " sourceSpeed=" + sourceState.speed()
                            + " endpointHasSource=true endpointHasNetwork=true tick=" + now);
            if (!level.setBlock(SHAFT, Blocks.AIR.defaultBlockState(), 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to remove required shaft at " + SHAFT),
                        "could not sever kinetic connection");
            }
            stage = Stage.DISCONNECTED;
            waitDiagnostic = diagnostic(
                    "severed required shaft removes downstream propagated speed",
                    now,
                    now + DISCONNECT_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " shaftRemoved=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "initial Create kinetic network did not propagate before deadline");
        }
    }

    private static void pollDisconnected(long now) throws ReflectiveOperationException {
        Object motor = requireExpectedBlockEntity(MOTOR, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(ENDPOINT, "GearboxBlockEntity", now);
        KineticState sourceState = kineticState(motor);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) == 0.0f && !endpointState.hasSource()) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " DISCONNECTED endpointSpeed=0.0 endpointHasSource=false"
                            + " endpointHasNetwork=" + endpointState.hasNetwork()
                            + " sourceSpeed=" + sourceState.speed() + " tick=" + now);
            if (!level.setBlock(SHAFT, shaftState, 3)) {
                fail(
                        SkyforgeCompilerIntegrationFailure.FAIL_KINETIC_REBUILD,
                        finalDiagnostic("failed to restore required shaft at " + SHAFT),
                        "could not restore kinetic connection");
            }
            stage = Stage.REBUILT;
            waitDiagnostic = diagnostic(
                    "restored shaft rebuilds network and restores downstream propagated speed",
                    now,
                    now + REBUILD_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "initialSpeed=" + initialSpeed + " shaftRestored=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "severed Create kinetic network did not disconnect before deadline");
        }
    }

    private static void pollRebuilt(long now) throws ReflectiveOperationException {
        Object motor = requireExpectedBlockEntity(MOTOR, "CreativeMotorBlockEntity", now);
        Object endpoint = requireExpectedBlockEntity(ENDPOINT, "GearboxBlockEntity", now);
        KineticState sourceState = kineticState(motor);
        KineticState endpointState = kineticState(endpoint);

        if (Math.abs(endpointState.speed()) > 0.0f && endpointState.hasSource() && endpointState.hasNetwork()) {
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " initialSpeed=" + initialSpeed
                            + " rebuiltSpeed=" + endpointState.speed()
                            + " sourceSpeed=" + sourceState.speed()
                            + " severedObserved=true rebuiltObserved=true"
                            + " endpointHasSource=true endpointHasNetwork=true"
                            + " clientState=headless");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "source=" + sourceState + " endpoint=" + endpointState),
                    "restored Create kinetic network did not rebuild before deadline");
        }
    }

    private static Object requireExpectedBlockEntity(BlockPos pos, String suffix, long now) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "missing block entity at " + pos + " tick=" + now),
                    "required Create block entity missing at " + pos);
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "unexpected block entity=" + blockEntity.getClass().getName() + " at " + pos),
                    "unexpected Create block entity at " + pos);
        }
        return blockEntity;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kineticClass.isInstance(blockEntity)) {
            throw new IllegalStateException("not a Create KineticBlockEntity: " + blockEntity.getClass().getName());
        }
        float speed = ((Number) kineticClass.getDeclaredMethod("getSpeed").invoke(blockEntity)).floatValue();
        float theoretical = ((Number) kineticClass.getDeclaredMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue();
        boolean hasSource = (Boolean) kineticClass.getDeclaredMethod("hasSource").invoke(blockEntity);
        boolean hasNetwork = (Boolean) kineticClass.getDeclaredMethod("hasNetwork").invoke(blockEntity);
        return new KineticState(speed, theoretical, hasSource, hasNetwork);
    }

    private static void requireRuntimePreconditions() {
        if (!ModList.get().isLoaded("create")) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            "Create is loaded from the exact retained runtime",
                            level.getGameTime(), level.getGameTime(),
                            "modId=create", safeServerState(), "missing Create mod"),
                    "required exact-stack Create mod not loaded");
        }
        requireBlock(MOTOR_ID);
        requireBlock(SHAFT_ID);
        requireBlock(ENDPOINT_ID);
        try {
            Class.forName("com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity");
        } catch (ClassNotFoundException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            "required exact-stack Create kinetic classes resolve",
                            level.getGameTime(), level.getGameTime(),
                            "class=" + exception.getMessage(), safeServerState(), exception.toString()),
                    "required Create kinetic runtime class unavailable");
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = -2; x <= 4; x++) {
            for (int y = 198; y <= 202; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        BlockState motorState = withProperty(requireBlock(MOTOR_ID).defaultBlockState(), "facing", "east");
        shaftState = withProperty(requireBlock(SHAFT_ID).defaultBlockState(), "axis", "x");
        BlockState endpointState = withProperty(requireBlock(ENDPOINT_ID).defaultBlockState(), "axis", "y");
        if (!level.setBlock(MOTOR, motorState, 3)
                || !level.setBlock(SHAFT, shaftState, 3)
                || !level.setBlock(ENDPOINT, endpointState, 3)) {
            throw new IllegalStateException("failed to place complete Create kinetic fixture");
        }
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalStateException("required exact-stack block is not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return withParsedProperty(state, property, name, value);
            }
        }
        throw new IllegalStateException("missing block property " + name + " on " + state);
    }

    private static <T extends Comparable<T>> BlockState withParsedProperty(
            BlockState state, Property<T> property, String name, String value) {
        T parsed = property.getValue(value).orElseThrow(
                () -> new IllegalStateException("cannot parse block property " + name + "=" + value + " on " + state));
        return state.setValue(property, parsed);
    }

    private static SkyforgeCompilerIntegrationDiagnostic diagnostic(
            String expected, long startTick, long deadlineTick, String ids, String serverState, String dump) {
        return new SkyforgeCompilerIntegrationDiagnostic(
                CAPABILITY,
                SkyforgeCompilerIntegrationPhase.MECHANISM_INITIALIZATION,
                expected,
                startTick,
                deadlineTick,
                ids,
                serverState,
                "headless",
                dump);
    }

    private static SkyforgeCompilerIntegrationDiagnostic finalDiagnostic(String dump) {
        if (waitDiagnostic == null) {
            long now = level == null ? 0L : level.getGameTime();
            return diagnostic("kinetic lifecycle reaches terminal state", now, now, fixtureIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }

    private static String fixtureIds() {
        return "motor=" + MOTOR + " shaft=" + SHAFT + " endpoint=" + ENDPOINT;
    }

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        return "gameTime=" + level.getGameTime()
                + " motor=" + level.getBlockState(MOTOR)
                + " shaft=" + level.getBlockState(SHAFT)
                + " endpoint=" + level.getBlockState(ENDPOINT)
                + " stage=" + stage;
    }

    private static void fail(
            SkyforgeCompilerIntegrationFailure code,
            SkyforgeCompilerIntegrationDiagnostic diagnostic,
            String reason) {
        if (!complete) {
            complete = true;
            LOGGER.log(System.Logger.Level.ERROR,
                    PREFIX + " FAIL code=" + code + " " + diagnostic.render() + " reason=\"" + reason + "\"");
        }
        throw new IllegalStateException(CAPABILITY + " failed: " + code + ": " + reason);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    private record KineticState(float speed, float theoreticalSpeed, boolean hasSource, boolean hasNetwork) {}
}
