package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.InvocationTargetException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** PLATFORM-009 exact-stack qualification for one real Create water-wheel environmental source. */
final class SkyforgeWaterWheelSourceLifecycleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformWaterWheelSourceLifecycle";
    static final String CAPABILITY = "CREATE_WATER_WHEEL_SOURCE_LIFECYCLE";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaterWheelSourceLifecycleAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_WATER_WHEEL_SOURCE_LIFECYCLE";

    private static final ResourceLocation WATER_WHEEL_ID = id("create:water_wheel");
    private static final ResourceLocation SHAFT_ID = id("create:shaft");
    private static final BlockPos WHEEL = new BlockPos(0, 200, 0);
    private static final BlockPos SHAFT = new BlockPos(1, 200, 0);
    private static final BlockPos WATER_SOURCE = new BlockPos(0, 201, -1);
    private static final BlockPos FLOW_CELL = new BlockPos(0, 201, 0);
    private static final BlockPos DRAIN_CELL = new BlockPos(0, 201, 1);
    private static final long ACTIVE_DEADLINE_TICKS = 100L;
    private static final long DISABLED_DEADLINE_TICKS = 140L;
    private static final long RECOVERY_DEADLINE_TICKS = 140L;

    private static ServerLevel level;
    private static SkyforgeCompilerIntegrationDiagnostic waitDiagnostic;
    private static Stage stage;
    private static boolean complete;
    private static float initialWheelSpeed;
    private static float initialShaftSpeed;

    private enum Stage {
        ACTIVE,
        DISABLED,
        RECOVERED
    }

    private SkyforgeWaterWheelSourceLifecycleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaterWheelSourceLifecycleAcceptance::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyforgeWaterWheelSourceLifecycleAcceptance::onServerTickPost);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        long now = level.getGameTime();
        LOGGER.log(System.Logger.Level.INFO,
                PREFIX + " START capability=" + CAPABILITY + " startTick=" + now + " clientState=headless");
        try {
            requireRuntimePreconditions();
            prepareFixture();
            stage = Stage.ACTIVE;
            waitDiagnostic = diagnostic(
                    "real flowing-water precondition drives nonzero water-wheel and shaft speed",
                    now,
                    now + ACTIVE_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "contained source placed; awaiting natural water propagation and scheduled wheel evaluation");
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_PLACEMENT,
                    diagnostic(
                            "minimal contained water-wheel fixture is placed",
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
                case ACTIVE -> pollActive(now);
                case DISABLED -> pollDisabled(now);
                case RECOVERED -> pollRecovered(now);
            }
        } catch (ReflectiveOperationException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException()
                    : exception;
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic("reflectionFailure=" + cause),
                    "Create water-wheel runtime reflection failed: " + cause);
        } catch (RuntimeException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_NETWORK,
                    finalDiagnostic(exception.toString()),
                    "water-wheel source lifecycle failed: " + exception);
        }
    }

    private static void pollActive(long now) throws ReflectiveOperationException {
        Object wheel = requireExpectedBlockEntity(WHEEL, "WaterWheelBlockEntity", now);
        Object shaft = requireKineticBlockEntity(SHAFT, now);
        KineticState wheelState = kineticState(wheel);
        KineticState shaftState = kineticState(shaft);
        Vec3 flow = flowVector(FLOW_CELL);

        if (hasActiveWaterFlow(flow)
                && Math.abs(wheelState.speed()) > 0.0f
                && Math.abs(shaftState.speed()) > 0.0f
                && shaftState.hasSource()
                && shaftState.hasNetwork()) {
            initialWheelSpeed = wheelState.speed();
            initialShaftSpeed = shaftState.speed();
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " ACTIVE wheelSpeed=" + wheelState.speed()
                            + " shaftSpeed=" + shaftState.speed()
                            + " flowCell=" + level.getFluidState(FLOW_CELL)
                            + " flowVector=" + flow
                            + " shaftHasSource=true shaftHasNetwork=true tick=" + now);
            level.setBlock(WATER_SOURCE, Blocks.AIR.defaultBlockState(), 3);
            stage = Stage.DISABLED;
            waitDiagnostic = diagnostic(
                    "removing only the upstream water source drains the flow cell and stops generation",
                    now,
                    now + DISABLED_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "initialWheelSpeed=" + initialWheelSpeed + " initialShaftSpeed=" + initialShaftSpeed
                            + " sourceRemoved=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_BUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "wheel=" + wheelState + " shaft=" + shaftState + " flowVector=" + flow),
                    "water-wheel source did not establish nonzero generation before deadline");
        }
    }

    private static void pollDisabled(long now) throws ReflectiveOperationException {
        Object wheel = requireExpectedBlockEntity(WHEEL, "WaterWheelBlockEntity", now);
        Object shaft = requireKineticBlockEntity(SHAFT, now);
        KineticState wheelState = kineticState(wheel);
        KineticState shaftState = kineticState(shaft);
        FluidState flowState = level.getFluidState(FLOW_CELL);
        Vec3 flow = flowVector(FLOW_CELL);

        if (flowState.isEmpty()
                && Math.abs(wheelState.speed()) == 0.0f
                && Math.abs(shaftState.speed()) == 0.0f
                && !shaftState.hasSource()) {
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " DISABLED wheelSpeed=0.0 shaftSpeed=0.0 flowCellEmpty=true"
                            + " shaftHasSource=false shaftHasNetwork=" + shaftState.hasNetwork() + " tick=" + now);
            if (!level.setBlock(WATER_SOURCE, Blocks.WATER.defaultBlockState(), 3)) {
                throw new IllegalStateException("failed to restore upstream water source at " + WATER_SOURCE);
            }
            level.scheduleTick(WATER_SOURCE, Fluids.WATER, 1);
            stage = Stage.RECOVERED;
            waitDiagnostic = diagnostic(
                    "restoring the same water source re-establishes real flow and nonzero generation",
                    now,
                    now + RECOVERY_DEADLINE_TICKS,
                    fixtureIds(),
                    safeServerState(),
                    "sourceRestored=true");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_DISCONNECT,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "wheel=" + wheelState + " shaft=" + shaftState + " flowState=" + flowState
                                    + " flowVector=" + flow),
                    "water-wheel generation did not stop after environmental source removal");
        }
    }

    private static void pollRecovered(long now) throws ReflectiveOperationException {
        Object wheel = requireExpectedBlockEntity(WHEEL, "WaterWheelBlockEntity", now);
        Object shaft = requireKineticBlockEntity(SHAFT, now);
        KineticState wheelState = kineticState(wheel);
        KineticState shaftState = kineticState(shaft);
        Vec3 flow = flowVector(FLOW_CELL);

        if (hasActiveWaterFlow(flow)
                && Math.abs(wheelState.speed()) > 0.0f
                && Math.abs(shaftState.speed()) > 0.0f
                && shaftState.hasSource()
                && shaftState.hasNetwork()) {
            complete = true;
            LOGGER.log(System.Logger.Level.INFO,
                    PREFIX + " PASS capability=" + CAPABILITY
                            + " initialWheelSpeed=" + initialWheelSpeed
                            + " initialShaftSpeed=" + initialShaftSpeed
                            + " recoveredWheelSpeed=" + wheelState.speed()
                            + " recoveredShaftSpeed=" + shaftState.speed()
                            + " flowVector=" + flow
                            + " environmentalDisableObserved=true environmentalRecoveryObserved=true"
                            + " shaftHasSource=true shaftHasNetwork=true clientState=headless");
            return;
        }

        if (waitDiagnostic.expired(now)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.TIMEOUT_KINETIC_REBUILD,
                    waitDiagnostic.withFinalState(
                            fixtureIds(), safeServerState(), "headless",
                            "wheel=" + wheelState + " shaft=" + shaftState + " flowVector=" + flow),
                    "water-wheel source did not recover after environmental source restoration");
        }
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
        requireBlock(WATER_WHEEL_ID);
        requireBlock(SHAFT_ID);
        try {
            Class.forName("com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity");
            Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        } catch (ClassNotFoundException exception) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_DEPENDENCY_RESOLUTION,
                    diagnostic(
                            "required exact-stack Create water-wheel classes resolve",
                            level.getGameTime(), level.getGameTime(),
                            "class=" + exception.getMessage(), safeServerState(), exception.toString()),
                    "required Create water-wheel runtime class unavailable");
        }
    }

    private static void prepareFixture() {
        level.getChunk(0, 0);
        for (int x = -2; x <= 3; x++) {
            for (int y = 198; y <= 203; y++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // One-cell-wide channel: the only fluid cell adjacent to the wheel is FLOW_CELL directly above it.
        // A source one cell north naturally flows south across FLOW_CELL; stone floor/walls prevent side/down spill.
        level.setBlock(new BlockPos(0, 201, -2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(new BlockPos(0, 200, -2), Blocks.STONE.defaultBlockState(), 3);
        for (int z = -1; z <= 2; z++) {
            if (z != 0) {
                level.setBlock(new BlockPos(0, 200, z), Blocks.STONE.defaultBlockState(), 3);
            }
            level.setBlock(new BlockPos(-1, 201, z), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(1, 201, z), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(new BlockPos(0, 200, 2), Blocks.STONE.defaultBlockState(), 3);

        BlockState wheelState = withProperty(requireBlock(WATER_WHEEL_ID).defaultBlockState(), "facing", "east");
        BlockState shaftState = withProperty(requireBlock(SHAFT_ID).defaultBlockState(), "axis", "x");
        if (!level.setBlock(WHEEL, wheelState, 3)
                || !level.setBlock(SHAFT, shaftState, 3)
                || !level.setBlock(WATER_SOURCE, Blocks.WATER.defaultBlockState(), 3)) {
            throw new IllegalStateException("failed to place complete water-wheel source fixture");
        }
        level.scheduleTick(WATER_SOURCE, Fluids.WATER, 1);
    }

    private static Object requireExpectedBlockEntity(BlockPos pos, String suffix, long now) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("missing block entity at " + pos + " tick=" + now),
                    "required block entity missing at " + pos);
        }
        if (!blockEntity.getClass().getName().endsWith(suffix)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("unexpected block entity=" + blockEntity.getClass().getName() + " at " + pos),
                    "unexpected block entity at " + pos);
        }
        return blockEntity;
    }

    private static Object requireKineticBlockEntity(BlockPos pos, long now) throws ReflectiveOperationException {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("missing kinetic block entity at " + pos + " tick=" + now),
                    "required kinetic block entity missing at " + pos);
        }
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        if (!kineticClass.isInstance(blockEntity)) {
            fail(
                    SkyforgeCompilerIntegrationFailure.FAIL_BLOCK_ENTITY_INIT,
                    finalDiagnostic("not a KineticBlockEntity=" + blockEntity.getClass().getName() + " at " + pos),
                    "unexpected kinetic block entity at " + pos);
        }
        return blockEntity;
    }

    private static KineticState kineticState(Object blockEntity) throws ReflectiveOperationException {
        Class<?> kineticClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
        float speed = ((Number) kineticClass.getDeclaredMethod("getSpeed").invoke(blockEntity)).floatValue();
        float theoretical = ((Number) kineticClass.getDeclaredMethod("getTheoreticalSpeed").invoke(blockEntity)).floatValue();
        boolean hasSource = (Boolean) kineticClass.getDeclaredMethod("hasSource").invoke(blockEntity);
        boolean hasNetwork = (Boolean) kineticClass.getDeclaredMethod("hasNetwork").invoke(blockEntity);
        return new KineticState(speed, theoretical, hasSource, hasNetwork);
    }

    private static Vec3 flowVector(BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.isEmpty() ? Vec3.ZERO : fluid.getFlow(level, pos);
    }

    private static boolean hasActiveWaterFlow(Vec3 flow) {
        return !level.getFluidState(FLOW_CELL).isEmpty() && Math.abs(flow.z) > 0.5;
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
            return diagnostic("water-wheel lifecycle reaches terminal state", now, now, fixtureIds(), safeServerState(), dump);
        }
        return waitDiagnostic.withFinalState(fixtureIds(), safeServerState(), "headless", dump);
    }

    private static String fixtureIds() {
        return "wheel=" + WHEEL + " shaft=" + SHAFT + " waterSource=" + WATER_SOURCE
                + " flowCell=" + FLOW_CELL + " drainCell=" + DRAIN_CELL;
    }

    private static String safeServerState() {
        if (level == null) {
            return "overworld=null";
        }
        return "gameTime=" + level.getGameTime()
                + " wheel=" + level.getBlockState(WHEEL)
                + " shaft=" + level.getBlockState(SHAFT)
                + " waterSource=" + level.getBlockState(WATER_SOURCE)
                + " flowCell=" + level.getBlockState(FLOW_CELL)
                + " flowFluid=" + level.getFluidState(FLOW_CELL)
                + " flowVector=" + flowVector(FLOW_CELL)
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
