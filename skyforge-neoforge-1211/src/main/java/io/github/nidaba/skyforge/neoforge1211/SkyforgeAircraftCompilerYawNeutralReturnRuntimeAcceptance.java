package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** AIRCRAFT-001 v0.16: inverse real-kinetic command and physical rudder neutral return. */
final class SkyforgeAircraftCompilerYawNeutralReturnRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerYawNeutralReturnRuntimeAcceptance.class.getName());

    private static final ResourceLocation CREATIVE_MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation COGWHEEL_ID = id("create:cogwheel");
    private static final double RPM_TOLERANCE = 0.01;

    private SkyforgeAircraftCompilerYawNeutralReturnRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            Object parentSubLevel,
            BlockPos movedSwivelBearingPos,
            BlockPos movedDriveCogPos,
            BlockPos movedCreativeMotorPos,
            int expectedMotorRpmMagnitude,
            int reverseMotorSign,
            int inverseDriveTicks,
            int stopSettleTicks,
            int physicalSettlePhysicsTicks,
            double minimumStartingDeflectionDegrees,
            double targetNeutralToleranceDegrees,
            double physicalNeutralToleranceDegrees)
            throws ReflectiveOperationException {
        assertTrue("reverse motor sign is negative", reverseMotorSign == -1);
        assertTrue("drive cog fixture position reset to air", level.getBlockState(movedDriveCogPos).isAir());
        assertTrue("creative motor fixture position reset to air", level.getBlockState(movedCreativeMotorPos).isAir());

        BlockEntity bearing = requireBlockEntity(level, movedSwivelBearingPos, "SwivelBearingBlockEntity");
        double targetBeforeReturn = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertTrue("neutral-return probe begins from meaningful commanded deflection",
                Math.abs(targetBeforeReturn) >= minimumStartingDeflectionDegrees);

        Block cogBlock = requireBlock(COGWHEEL_ID);
        Block motorBlock = requireBlock(CREATIVE_MOTOR_ID);
        BlockState cogState = cogBlock.defaultBlockState();
        assertTrue("Create cogwheel exposes AXIS", cogState.hasProperty(BlockStateProperties.AXIS));
        cogState = cogState.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState motorState = motorBlock.defaultBlockState();
        assertTrue("Creative Motor exposes FACING", motorState.hasProperty(BlockStateProperties.FACING));
        motorState = motorState.setValue(BlockStateProperties.FACING, Direction.UP);
        level.setBlock(movedDriveCogPos, cogState, 3);
        level.setBlock(movedCreativeMotorPos, motorState, 3);

        BlockEntity driveCog = requireBlockEntity(level, movedDriveCogPos, null);
        BlockEntity motor = requireBlockEntity(level, movedCreativeMotorPos, "CreativeMotorBlockEntity");
        Object extraCog = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        assertTrue("Swivel exposes hidden extra kinetic cog", extraCog != null);

        Field generatedSpeedField = motor.getClass().getField("generatedSpeed");
        Object generatedSpeed = generatedSpeedField.get(motor);
        assertTrue("Creative Motor exposes generated-speed behaviour", generatedSpeed != null);
        publicMethod(generatedSpeed, "setValue", int.class)
                .invoke(generatedSpeed, reverseMotorSign * expectedMotorRpmMagnitude);

        for (int i = 0; i < inverseDriveTicks; i++) {
            tick(motor);
            tick(driveCog);
            tick(bearing);
        }

        double motorRpm = number(publicMethod(motor, "getGeneratedSpeed").invoke(motor));
        double driveCogRpm = number(publicMethod(driveCog, "getSpeed").invoke(driveCog));
        double extraCogRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        assertNear("reverse Creative Motor RPM",
                reverseMotorSign * expectedMotorRpmMagnitude, motorRpm, RPM_TOLERANCE);
        assertTrue("reverse drive cog follows motor sign", Math.signum(driveCogRpm) == Math.signum(motorRpm));
        assertTrue("small-cog mesh reverses sign into Swivel extra cog",
                Math.signum(extraCogRpm) == -Math.signum(driveCogRpm));
        assertTrue("reverse Swivel extra-cog RPM magnitude",
                Math.abs(Math.abs(extraCogRpm) - expectedMotorRpmMagnitude) <= RPM_TOLERANCE);

        double targetAfterInverseDrive = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertTrue("inverse kinetic command returns Swivel target near neutral",
                Math.abs(normalizeDegrees(targetAfterInverseDrive)) <= targetNeutralToleranceDegrees);
        assertTrue("inverse target change opposes initial deflection",
                Math.signum(signedDeltaDegrees(targetBeforeReturn, targetAfterInverseDrive))
                        == -Math.signum(targetBeforeReturn));

        level.setBlock(movedCreativeMotorPos, Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < stopSettleTicks; i++) {
            tick(driveCog);
            tick(bearing);
        }
        double stoppedExtraCogRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        assertNear("Swivel extra-cog stops after inverse source removal", 0.0, stoppedExtraCogRpm, RPM_TOLERANCE);
        level.setBlock(movedDriveCogPos, Blocks.AIR.defaultBlockState(), 3);

        Object childSubLevel = attachedChild(bearing);
        Object container = requireServerSubLevelContainer(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Object physicsSystem = serverContainerClass.getDeclaredMethod("physicsSystem").invoke(container);
        for (int i = 0; i < physicalSettlePhysicsTicks; i++) {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
        }

        double targetAfterPhysicalSettle = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertTrue("Swivel target remains near neutral after physical settle",
                Math.abs(normalizeDegrees(targetAfterPhysicalSettle)) <= targetNeutralToleranceDegrees);

        Object parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Object childPose = publicMethod(childSubLevel, "logicalPose").invoke(childSubLevel);
        Quaterniondc parentOrientation = quaternion(publicMethod(parentPose, "orientation").invoke(parentPose));
        Quaterniondc childOrientation = quaternion(publicMethod(childPose, "orientation").invoke(childPose));
        Quaterniond relative = new Quaterniond(parentOrientation).conjugate().mul(new Quaterniond(childOrientation));
        Vector3d childForwardInParent = relative.transform(new Vector3d(1.0, 0.0, 0.0));
        double physicalYawDegrees = Math.toDegrees(Math.atan2(-childForwardInParent.z, childForwardInParent.x));
        assertTrue("physical rudder yaw finite after neutral return", Double.isFinite(physicalYawDegrees));
        assertTrue("physical rudder body returns near neutral",
                Math.abs(normalizeDegrees(physicalYawDegrees)) <= physicalNeutralToleranceDegrees);

        // v0.15 deliberately injects a 10 m/s controlled-flow state. v0.16 advances
        // that state through a second servo-settle window. The accepted yaw result must
        // not contaminate downstream propulsion regressions, whose force-sign contract
        // assumes its own near-static inflow condition. Zero both linked bodies after
        // recording the physical neutral result; this is test-fixture cleanup, not part
        // of the aircraft control mechanism.
        Object parentHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel)
                .invoke(physicsSystem, parentSubLevel);
        Object childHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", childSubLevel)
                .invoke(physicsSystem, childSubLevel);
        assertTrue("parent physics handle available for probe cleanup", parentHandle != null);
        assertTrue("child physics handle available for probe cleanup", childHandle != null);
        zeroVelocity(parentHandle);
        zeroVelocity(childHandle);

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_YAW_NEUTRAL_RETURN PASS"
                        + " targetBeforeDegrees=" + targetBeforeReturn
                        + " reverseMotorRpm=" + motorRpm
                        + " reverseDriveCogRpm=" + driveCogRpm
                        + " reverseExtraCogRpm=" + extraCogRpm
                        + " inverseDriveTicks=" + inverseDriveTicks
                        + " targetAfterInverseDegrees=" + targetAfterInverseDrive
                        + " targetAfterSettleDegrees=" + targetAfterPhysicalSettle
                        + " physicalYawAfterSettleDegrees=" + physicalYawDegrees
                        + " stoppedExtraCogRpm=" + stoppedExtraCogRpm
                        + " commandedNeutralReturnVerified=true"
                        + " probeDynamicStateReset=true"
                        + " passiveSelfCenteringVerified=false"
                        + " productionControlBindingVerified=false"
                        + " stableFlightVerified=false");
    }

    private static Object attachedChild(BlockEntity bearing) throws ReflectiveOperationException {
        Object raw = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(raw instanceof Iterable<?>)) fail("Swivel did not expose child dependencies");
        Object child = null;
        int count = 0;
        for (Object dependency : (Iterable<?>) raw) {
            if (dependency != null) {
                child = dependency;
                count++;
            }
        }
        if (count != 1 || child == null) fail("expected exactly one rudder child dependency, got " + count);
        return child;
    }

    private static Object requireServerSubLevelContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(level)) fail("ServerLevel does not expose Sable SubLevelContainerHolder");
        Object container = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (container == null || !serverContainerClass.isInstance(container)) fail("Sable ServerSubLevelContainer unavailable");
        return container;
    }

    private static void zeroVelocity(Object handle) throws ReflectiveOperationException {
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", linear).invoke(handle, linear);
        oneArgMethod(handle, "getAngularVelocity", angular).invoke(handle, angular);
        linear.negate();
        angular.negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linear, angular)
                .invoke(handle, linear, angular);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) fail("required runtime fixture block not registered: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String expectedClassSuffix) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) fail("missing block entity at " + pos);
        if (expectedClassSuffix != null && !be.getClass().getName().endsWith(expectedClassSuffix)) {
            fail("wrong block entity type at " + pos + ": expectedSuffix=" + expectedClassSuffix
                    + " actual=" + be.getClass().getName());
        }
        return be;
    }

    private static void tick(Object target) throws ReflectiveOperationException {
        publicMethod(target, "tick").invoke(target);
    }

    private static double signedDeltaDegrees(double from, double to) {
        return normalizeDegrees(to - from);
    }

    private static double normalizeDegrees(double value) {
        double delta = value % 360.0;
        if (delta > 180.0) delta -= 360.0;
        else if (delta <= -180.0) delta += 360.0;
        return delta;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("invalid resource location " + value);
        return id;
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Method oneArgMethod(Object target, String name, Object arg) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(arg.getClass())) return method;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + " compatible with " + arg.getClass());
    }

    private static Method twoArgMethod(Object target, String name, Object a, Object b) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(a.getClass())
                    && method.getParameterTypes()[1].isAssignableFrom(b.getClass())) return method;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + " compatible args");
    }

    private static Quaterniondc quaternion(Object value) {
        if (!(value instanceof Quaterniondc)) fail("expected Quaterniondc, got " + value);
        return (Quaterniondc) value;
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) fail("expected numeric value, got " + value);
        return ((Number) value).doubleValue();
    }

    private static void assertNear(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            fail(label + ": expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) fail(label + ": expected true");
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_YAW_NEUTRAL_RETURN FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 yaw neutral-return runtime acceptance failed: " + reason);
    }
}
