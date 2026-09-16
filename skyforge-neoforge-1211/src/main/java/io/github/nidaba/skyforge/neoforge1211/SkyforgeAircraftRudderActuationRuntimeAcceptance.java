package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

/** AIRCRAFT-RUNTIME-003: aircraft-specific consumer of the accepted Swivel control lifecycle. */
final class SkyforgeAircraftRudderActuationRuntimeAcceptance {
    private static final System.Logger LOGGER = System.getLogger(SkyforgeAircraftRudderActuationRuntimeAcceptance.class.getName());
    private static final ResourceLocation SWIVEL = id("simulated:swivel_bearing");
    private static final ResourceLocation RUDDER = id("simulated:white_symmetric_sail");
    private static final ResourceLocation MOTOR = id("create:creative_motor");
    private static final ResourceLocation COG = id("create:cogwheel");
    private static final double RPM_TOLERANCE = 0.01;

    private SkyforgeAircraftRudderActuationRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            Object parentSubLevel,
            BlockPos movedSwivel,
            List<BlockPos> movedRudder,
            List<BlockPos> movedParentMain,
            int commandRpm,
            int commandTicks,
            int holdTicks,
            double targetNeutralToleranceDegrees,
            double physicalNeutralToleranceDegrees,
            double minimumPhysicalDeflectionDegrees)
            throws ReflectiveOperationException {
        if (movedRudder.size() != 4 || new LinkedHashSet<>(movedRudder).size() != 4) fail("expected exactly four unique rudder cells");
        Set<BlockPos> parent = new LinkedHashSet<>(movedParentMain);
        if (parent.size() != 118 || !parent.contains(movedSwivel)) fail("expected 118-cell parent containing Swivel");
        if (!java.util.Collections.disjoint(parent, movedRudder)) fail("rudder payload overlaps retained parent");

        BlockEntity bearing = requireBlockEntity(level, movedSwivel, "SwivelBearingBlockEntity");
        assertBlock(level, movedSwivel, SWIVEL);
        BlockState bearingState = level.getBlockState(movedSwivel);
        if (!bearingState.hasProperty(BlockStateProperties.FACING) || bearingState.getValue(BlockStateProperties.FACING) != Direction.UP) {
            fail("production Swivel is not UP-facing");
        }
        double initialTarget = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        if (!Double.isFinite(initialTarget) || Math.abs(normalizeDegrees(initialTarget)) > targetNeutralToleranceDegrees) {
            fail("Swivel initial target is not neutral: " + initialTarget);
        }
        for (BlockPos pos : movedRudder) {
            assertBlock(level, pos, RUDDER);
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) != Direction.Axis.Z) {
                fail("rudder sail axis drifted at " + pos + ": " + state);
            }
        }

        publicMethod(bearing, "assemble").invoke(bearing);
        if (!Boolean.TRUE.equals(publicMethod(bearing, "isAssembled").invoke(bearing))) fail("Swivel did not assemble rudder child");
        for (BlockPos pos : movedRudder) if (!level.getBlockState(pos).isAir()) fail("rudder source cell not consumed: " + pos);
        Object child = attachedChild(bearing);
        double neutralPhysical = relativeYawDegrees(parentSubLevel, child);
        if (!Double.isFinite(neutralPhysical) || Math.abs(normalizeDegrees(neutralPhysical)) > physicalNeutralToleranceDegrees) {
            fail("rudder child did not begin physically neutral: " + neutralPhysical);
        }

        BlockPos driveCogPos = movedSwivel.offset(0, 0, 1);
        BlockPos motorPos = driveCogPos.below();
        if (!level.getBlockState(driveCogPos).isAir() || !level.getBlockState(motorPos).isAir()) {
            fail("temporary command fixture coordinates are occupied");
        }
        placeCommandNetwork(level, driveCogPos, motorPos);
        BlockEntity driveCog = requireBlockEntity(level, driveCogPos, null);
        BlockEntity motor = requireBlockEntity(level, motorPos, "CreativeMotorBlockEntity");
        Object extraCog = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        if (extraCog == null) fail("Swivel hidden extra cog unavailable");

        for (int i = 0; i < 10; i++) tick(motor, driveCog, bearing);
        double motorRpm = number(publicMethod(motor, "getGeneratedSpeed").invoke(motor));
        double driveRpm = number(publicMethod(driveCog, "getSpeed").invoke(driveCog));
        double extraRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        assertAbsNear("motor RPM", commandRpm, motorRpm);
        assertAbsNear("drive RPM", commandRpm, driveRpm);
        assertAbsNear("extra-cog RPM", commandRpm, extraRpm);
        if (Math.signum(driveRpm) == Math.signum(extraRpm)) fail("small-cog mesh did not reverse sign");

        double targetBefore = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        for (int i = 0; i < commandTicks; i++) tick(motor, driveCog, bearing);
        double targetDeflected = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double targetDelta = normalizeDegrees(targetDeflected - targetBefore);
        if (Math.abs(targetDelta) < 5.0 || Math.signum(targetDelta) != Math.signum(extraRpm)) {
            fail("real kinetic command did not produce signed target deflection: delta=" + targetDelta + " extraRpm=" + extraRpm);
        }
        settlePhysics(level, 10);
        double physicalDeflected = relativeYawDegrees(parentSubLevel, attachedChild(bearing));
        if (!Double.isFinite(physicalDeflected) || Math.abs(normalizeDegrees(physicalDeflected)) < minimumPhysicalDeflectionDegrees) {
            fail("rudder child did not physically deflect: " + physicalDeflected);
        }

        level.setBlock(motorPos, Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < 10; i++) tick(driveCog, bearing);
        double stoppedRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        if (Math.abs(stoppedRpm) > RPM_TOLERANCE) fail("extra cog did not stop: " + stoppedRpm);
        double heldStart = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        for (int i = 0; i < holdTicks; i++) tick(driveCog, bearing);
        double heldEnd = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        if (Math.abs(normalizeDegrees(heldEnd - heldStart)) > targetNeutralToleranceDegrees) fail("source-off target did not hold");

        level.setBlock(motorPos, motorState(), 3);
        motor = requireBlockEntity(level, motorPos, "CreativeMotorBlockEntity");
        Field generatedSpeedField = motor.getClass().getField("generatedSpeed");
        Object generatedSpeed = generatedSpeedField.get(motor);
        publicMethod(generatedSpeed, "setValue", int.class).invoke(generatedSpeed, -commandRpm);
        for (int i = 0; i < commandTicks; i++) tick(motor, driveCog, bearing);
        double targetReturned = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        if (Math.abs(normalizeDegrees(targetReturned)) > targetNeutralToleranceDegrees) {
            fail("inverse real kinetic command did not return target neutral: " + targetReturned);
        }
        level.setBlock(motorPos, Blocks.AIR.defaultBlockState(), 3);
        for (int i = 0; i < 10; i++) tick(driveCog, bearing);
        level.setBlock(driveCogPos, Blocks.AIR.defaultBlockState(), 3);
        settlePhysics(level, 10);
        double physicalReturned = relativeYawDegrees(parentSubLevel, attachedChild(bearing));
        if (!Double.isFinite(physicalReturned) || Math.abs(normalizeDegrees(physicalReturned)) > physicalNeutralToleranceDegrees) {
            fail("physical rudder did not return neutral: " + physicalReturned);
        }

        LOGGER.log(System.Logger.Level.INFO,
                "AIRCRAFT_V0131_RUDDER_ACTUATION_RUNTIME PASS"
                        + " targetInitial=" + initialTarget + " physicalInitial=" + neutralPhysical
                        + " motorRpm=" + motorRpm + " driveRpm=" + driveRpm + " extraRpm=" + extraRpm
                        + " targetDeflected=" + targetDeflected + " physicalDeflected=" + physicalDeflected
                        + " heldTarget=" + heldEnd + " targetReturned=" + targetReturned + " physicalReturned=" + physicalReturned
                        + " commandTicks=" + commandTicks + " holdTicks=" + holdTicks
                        + " directTargetMutation=false passiveSelfCenteringQualified=false yawForceQualified=false");
    }

    private static void placeCommandNetwork(ServerLevel level, BlockPos cogPos, BlockPos motorPos) {
        BlockState cog = requireBlock(COG).defaultBlockState();
        if (!cog.hasProperty(BlockStateProperties.AXIS)) fail("Create cog lacks AXIS");
        cog = cog.setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        level.setBlock(cogPos, cog, 3);
        level.setBlock(motorPos, motorState(), 3);
    }

    private static BlockState motorState() {
        BlockState state = requireBlock(MOTOR).defaultBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) fail("Creative Motor lacks FACING");
        return state.setValue(BlockStateProperties.FACING, Direction.UP);
    }

    private static Object attachedChild(BlockEntity bearing) throws ReflectiveOperationException {
        Object raw = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(raw instanceof Iterable<?>)) fail("Swivel dependencies unavailable");
        Iterable<?> dependencies = (Iterable<?>) raw;
        Object child = null;
        int count = 0;
        for (Object dep : dependencies) if (dep != null) { child = dep; count++; }
        if (count != 1 || child == null) fail("expected exactly one rudder child, got " + count);
        return child;
    }

    private static double relativeYawDegrees(Object parent, Object child) throws ReflectiveOperationException {
        Object parentPose = publicMethod(parent, "logicalPose").invoke(parent);
        Object childPose = publicMethod(child, "logicalPose").invoke(child);
        Quaterniondc parentQ = quaternion(publicMethod(parentPose, "orientation").invoke(parentPose));
        Quaterniondc childQ = quaternion(publicMethod(childPose, "orientation").invoke(childPose));
        Quaterniond relative = new Quaterniond(parentQ).conjugate().mul(new Quaterniond(childQ));
        Vector3d forward = relative.transform(new Vector3d(1.0, 0.0, 0.0));
        return Math.toDegrees(Math.atan2(-forward.z, forward.x));
    }

    private static void settlePhysics(ServerLevel level, int ticks) throws ReflectiveOperationException {
        Class<?> holder = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object container = holder.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Object physics = container.getClass().getDeclaredMethod("physicsSystem").invoke(container);
        Method tick = oneArgMethod(physics, "tick", container);
        for (int i = 0; i < ticks; i++) tick.invoke(physics, container);
    }

    private static void tick(Object... values) throws ReflectiveOperationException {
        for (Object value : values) publicMethod(value, "tick").invoke(value);
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String suffix) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || (suffix != null && !be.getClass().getName().endsWith(suffix))) fail("wrong/missing block entity at " + pos);
        return be;
    }

    private static void assertBlock(ServerLevel level, BlockPos pos, ResourceLocation expected) {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!expected.equals(actual)) fail("resource mismatch at " + pos + ": expected=" + expected + " actual=" + actual);
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) fail("required block unavailable: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static Method publicMethod(Object target, String name, Class<?>... types) throws NoSuchMethodException {
        return target.getClass().getMethod(name, types);
    }

    private static Method oneArgMethod(Object target, String name, Object arg) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(arg.getClass())) return method;
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Quaterniondc quaternion(Object value) {
        if (!(value instanceof Quaterniondc)) fail("expected Quaterniondc, got " + value);
        return (Quaterniondc) value;
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) fail("expected number, got " + value);
        return ((Number) value).doubleValue();
    }

    private static void assertAbsNear(String label, int expected, double actual) {
        if (!Double.isFinite(actual) || Math.abs(Math.abs(actual) - expected) > RPM_TOLERANCE) fail(label + " mismatch: " + actual);
    }

    private static double normalizeDegrees(double value) {
        double result = value % 360.0;
        if (result > 180.0) result -= 360.0;
        else if (result <= -180.0) result += 360.0;
        return result;
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_V0131_RUDDER_ACTUATION_RUNTIME FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-RUNTIME-003 rudder actuation failed: " + reason);
    }
}
