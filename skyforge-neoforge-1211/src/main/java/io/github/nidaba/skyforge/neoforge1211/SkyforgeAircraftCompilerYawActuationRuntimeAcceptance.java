package io.github.nidaba.skyforge.neoforge1211;

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

/** AIRCRAFT-001 v0.14: real Create kinetic drive of the Simulated Swivel extra cog. */
final class SkyforgeAircraftCompilerYawActuationRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerYawActuationRuntimeAcceptance.class.getName());

    private static final ResourceLocation CREATIVE_MOTOR_ID = id("create:creative_motor");
    private static final ResourceLocation COGWHEEL_ID = id("create:cogwheel");
    private static final double RPM_TOLERANCE = 0.01;
    private static final double ANGLE_TOLERANCE_DEGREES = 0.05;
    private static final double DEGREES_PER_TICK_PER_RPM = 0.3;

    private SkyforgeAircraftCompilerYawActuationRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            BlockPos movedSwivelBearingPos,
            BlockPos movedDriveCogPos,
            BlockPos movedCreativeMotorPos,
            int expectedMotorRpmMagnitude,
            int networkSettleTicks,
            int commandTicks,
            int stopSettleTicks,
            int holdTicks)
            throws ReflectiveOperationException {
        assertTrue("drive cog fixture position initially air", level.getBlockState(movedDriveCogPos).isAir());
        assertTrue("creative motor fixture position initially air", level.getBlockState(movedCreativeMotorPos).isAir());
        assertTrue("drive cog is one block +Z from Swivel",
                movedDriveCogPos.equals(movedSwivelBearingPos.offset(0, 0, 1)));
        assertTrue("creative motor is one block below drive cog",
                movedCreativeMotorPos.equals(movedDriveCogPos.below()));

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
        assertBlockId("placed drive cog", level, movedDriveCogPos, COGWHEEL_ID);
        assertBlockId("placed creative motor", level, movedCreativeMotorPos, CREATIVE_MOTOR_ID);

        BlockEntity bearing = requireBlockEntity(level, movedSwivelBearingPos, "SwivelBearingBlockEntity");
        BlockEntity driveCog = requireBlockEntity(level, movedDriveCogPos, null);
        BlockEntity motor = requireBlockEntity(level, movedCreativeMotorPos, "CreativeMotorBlockEntity");
        Object extraCog = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        assertTrue("Swivel exposes hidden extra kinetic cog", extraCog != null);

        for (int i = 0; i < networkSettleTicks; i++) {
            tick(motor);
            tick(driveCog);
            tick(bearing);
        }

        double motorRpm = number(publicMethod(motor, "getGeneratedSpeed").invoke(motor));
        double driveCogRpm = number(publicMethod(driveCog, "getSpeed").invoke(driveCog));
        double extraCogRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        assertAbsNear("Creative Motor generated RPM", expectedMotorRpmMagnitude, motorRpm, RPM_TOLERANCE);
        assertAbsNear("drive cog RPM", expectedMotorRpmMagnitude, driveCogRpm, RPM_TOLERANCE);
        assertAbsNear("Swivel extra-cog RPM", expectedMotorRpmMagnitude, extraCogRpm, RPM_TOLERANCE);
        assertTrue("small-cog mesh reverses drive sign", Math.signum(driveCogRpm) == -Math.signum(extraCogRpm));

        double targetBeforeCommand = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        for (int i = 0; i < commandTicks; i++) {
            tick(motor);
            tick(driveCog);
            tick(bearing);
        }
        double targetAfterCommand = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double observedDelta = signedDeltaDegrees(targetBeforeCommand, targetAfterCommand);
        double expectedDelta = extraCogRpm * DEGREES_PER_TICK_PER_RPM * commandTicks;
        assertTrue("Swivel target angle changes under real kinetic drive", Math.abs(observedDelta) > 1.0e-6);
        assertTrue("Swivel target-angle sign follows observed extra-cog RPM",
                Math.signum(observedDelta) == Math.signum(extraCogRpm));
        assertNear("Swivel target-angle delta follows Create rpm conversion",
                expectedDelta, observedDelta, ANGLE_TOLERANCE_DEGREES);

        level.setBlock(movedCreativeMotorPos, Blocks.AIR.defaultBlockState(), 3);
        assertTrue("temporary Creative Motor removed", level.getBlockState(movedCreativeMotorPos).isAir());
        for (int i = 0; i < stopSettleTicks; i++) {
            tick(driveCog);
            tick(bearing);
        }
        double stoppedExtraCogRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
        assertNear("Swivel extra-cog RPM stops after source removal", 0.0, stoppedExtraCogRpm, RPM_TOLERANCE);
        BlockState stoppedBearingState = level.getBlockState(movedSwivelBearingPos);
        assertTrue("Swivel exposes POWERED locking state", stoppedBearingState.hasProperty(BlockStateProperties.POWERED));
        assertTrue("LOCKED_DEFAULT Swivel is locked with no redstone signal",
                stoppedBearingState.getValue(BlockStateProperties.POWERED));

        double heldTargetStart = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        for (int i = 0; i < holdTicks; i++) {
            tick(driveCog);
            tick(bearing);
        }
        double heldTargetEnd = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        assertNear("locked Swivel target holds after kinetic command stops",
                0.0, signedDeltaDegrees(heldTargetStart, heldTargetEnd), ANGLE_TOLERANCE_DEGREES);

        level.setBlock(movedDriveCogPos, Blocks.AIR.defaultBlockState(), 3);
        assertTrue("temporary drive cog removed", level.getBlockState(movedDriveCogPos).isAir());

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_YAW_ACTUATION PASS"
                        + " motorRpm=" + motorRpm
                        + " driveCogRpm=" + driveCogRpm
                        + " extraCogRpm=" + extraCogRpm
                        + " commandTicks=" + commandTicks
                        + " targetBeforeDegrees=" + targetBeforeCommand
                        + " targetAfterDegrees=" + targetAfterCommand
                        + " observedDeltaDegrees=" + observedDelta
                        + " expectedDeltaDegrees=" + expectedDelta
                        + " stoppedExtraCogRpm=" + stoppedExtraCogRpm
                        + " lockedHold=true"
                        + " neutralReturnVerified=false"
                        + " yawForceVerified=false"
                        + " productionControlBindingVerified=false");
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            fail("required runtime fixture block not registered: " + id);
        }
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String expectedClassSuffix) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            fail("missing block entity at " + pos);
        }
        if (expectedClassSuffix != null && !be.getClass().getName().endsWith(expectedClassSuffix)) {
            fail("wrong block entity type at " + pos + ": expectedSuffix=" + expectedClassSuffix
                    + " actual=" + be.getClass().getName());
        }
        return be;
    }

    private static void assertBlockId(String label, ServerLevel level, BlockPos pos, ResourceLocation expected) {
        ResourceLocation actual = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!expected.equals(actual)) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void tick(Object target) throws ReflectiveOperationException {
        publicMethod(target, "tick").invoke(target);
    }

    private static double signedDeltaDegrees(double from, double to) {
        double delta = (to - from) % 360.0;
        if (delta > 180.0) {
            delta -= 360.0;
        } else if (delta <= -180.0) {
            delta += 360.0;
        }
        return delta;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("invalid resource location " + value);
        }
        return id;
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) {
            fail("expected numeric value, got " + value);
        }
        return ((Number) value).doubleValue();
    }

    private static void assertAbsNear(String label, double expectedMagnitude, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(Math.abs(actual) - expectedMagnitude) > tolerance) {
            fail(label + ": expectedAbs=" + expectedMagnitude + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertNear(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            fail(label + ": expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_YAW_ACTUATION FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 yaw-actuation runtime acceptance failed: " + reason);
    }
}
