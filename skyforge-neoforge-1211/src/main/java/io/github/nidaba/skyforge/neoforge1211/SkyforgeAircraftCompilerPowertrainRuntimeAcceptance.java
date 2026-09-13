package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** AIRCRAFT-001 v0.12 exact-stack 128-RPM propulsion-performance acceptance. */
final class SkyforgeAircraftCompilerPowertrainRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPowertrainRuntimeAcceptance.class.getName());

    private static final int ENGINE_BURN_TICKS = 1200;
    private static final float EXPECTED_ENGINE_RPM = 32.0f;
    private static final int GOVERNOR_TARGET_RPM = 128;
    private static final int NETWORK_SETTLE_TICKS = 24;
    private static final int PROPELLER_SMOOTHING_TICKS = 48;

    private SkyforgeAircraftCompilerPowertrainRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            BlockPos enginePortPos,
            BlockPos engineStarboardPos,
            BlockPos governorPos,
            BlockPos governorCogPos,
            BlockPos propShaftPos,
            BlockPos bearingPos)
            throws ReflectiveOperationException {
        BlockEntity enginePort = requireBlockEntity(level, enginePortPos, "PortableEngineBlockEntity", "simulated:red_portable_engine");
        BlockEntity engineStarboard = requireBlockEntity(level, engineStarboardPos, "PortableEngineBlockEntity", "simulated:red_portable_engine");
        BlockEntity governor = requireBlockEntity(level, governorPos, "SpeedControllerBlockEntity", "create:rotation_speed_controller");
        BlockEntity governorCog = requireBlockEntity(level, governorCogPos, "KineticBlockEntity", "create:large_cogwheel");
        BlockEntity propShaft = requireBlockEntity(level, propShaftPos, "KineticBlockEntity", "create:shaft");
        BlockEntity bearing = requireBlockEntity(level, bearingPos, "PropellerBearingBlockEntity", "aeronautics:propeller_bearing");

        publicMethod(enginePort, "setCurrentBurnTime", int.class).invoke(enginePort, ENGINE_BURN_TICKS);
        publicMethod(engineStarboard, "setCurrentBurnTime", int.class).invoke(engineStarboard, ENGINE_BURN_TICKS);

        tick(enginePort);
        tick(engineStarboard);
        tick(enginePort);
        tick(engineStarboard);

        float enginePortRpm = number(publicMethod(enginePort, "getGeneratedSpeed").invoke(enginePort));
        float engineStarboardRpm = number(publicMethod(engineStarboard, "getGeneratedSpeed").invoke(engineStarboard));
        assertAbsNear("port Portable Engine generated RPM", EXPECTED_ENGINE_RPM, enginePortRpm, 0.001f);
        assertAbsNear("starboard Portable Engine generated RPM", EXPECTED_ENGINE_RPM, engineStarboardRpm, 0.001f);

        Object enginePortNetwork = publicField(enginePort, "network").get(enginePort);
        Object engineStarboardNetwork = publicField(engineStarboard, "network").get(engineStarboard);
        assertTrue("port engine has kinetic network", enginePortNetwork != null);
        assertTrue("starboard engine has kinetic network", engineStarboardNetwork != null);
        assertTrue("both Portable Engines share the same source kinetic network", enginePortNetwork.equals(engineStarboardNetwork));

        Object targetSpeed = publicField(governor, "targetSpeed").get(governor);
        assertTrue("Rotation Speed Controller targetSpeed behaviour initialized", targetSpeed != null);
        publicMethod(targetSpeed, "setValue", int.class).invoke(targetSpeed, GOVERNOR_TARGET_RPM);
        int realizedTarget = ((Number) publicMethod(targetSpeed, "getValue").invoke(targetSpeed)).intValue();
        assertInt("Rotation Speed Controller target", GOVERNOR_TARGET_RPM, realizedTarget);

        for (int i = 0; i < NETWORK_SETTLE_TICKS; i++) {
            tick(enginePort);
            tick(engineStarboard);
            tick(governor);
            tick(governorCog);
            tick(propShaft);
            tick(bearing);
        }

        enginePortRpm = number(publicMethod(enginePort, "getGeneratedSpeed").invoke(enginePort));
        engineStarboardRpm = number(publicMethod(engineStarboard, "getGeneratedSpeed").invoke(engineStarboard));
        assertAbsNear("settled port Portable Engine generated RPM", EXPECTED_ENGINE_RPM, enginePortRpm, 0.001f);
        assertAbsNear("settled starboard Portable Engine generated RPM", EXPECTED_ENGINE_RPM, engineStarboardRpm, 0.001f);

        enginePortNetwork = publicField(enginePort, "network").get(enginePort);
        engineStarboardNetwork = publicField(engineStarboard, "network").get(engineStarboard);
        assertTrue("settled engines retain one shared source network",
                enginePortNetwork != null && enginePortNetwork.equals(engineStarboardNetwork));

        assertTrue("Propeller Bearing has governed kinetic network",
                (Boolean) publicMethod(bearing, "hasNetwork").invoke(bearing));
        float bearingRpm = number(publicMethod(bearing, "getSpeed").invoke(bearing));
        float bearingTheoreticalRpm = number(publicMethod(bearing, "getTheoreticalSpeed").invoke(bearing));
        assertAbsNear("Propeller Bearing live RPM", GOVERNOR_TARGET_RPM, bearingRpm, 0.01f);
        assertAbsNear("Propeller Bearing theoretical RPM", GOVERNOR_TARGET_RPM, bearingTheoreticalRpm, 0.01f);

        Object bearingNetwork = publicMethod(bearing, "getOrCreateNetwork").invoke(bearing);
        assertTrue("Propeller Bearing kinetic network object exists", bearingNetwork != null);
        float capacity = number(publicMethod(bearingNetwork, "calculateCapacity").invoke(bearingNetwork));
        float stress = number(publicMethod(bearingNetwork, "calculateStress").invoke(bearingNetwork));
        assertFinite("kinetic capacity", capacity);
        assertFinite("kinetic stress", stress);
        assertTrue("kinetic capacity positive", capacity > 0.0f);
        assertTrue("kinetic stress nonnegative", stress >= 0.0f);
        float stressMargin = capacity - stress;
        assertTrue("kinetic stress capacity is sufficient", stressMargin > 0.0f);

        for (int i = 0; i < PROPELLER_SMOOTHING_TICKS; i++) {
            tick(enginePort);
            tick(engineStarboard);
            tick(bearing);
        }

        float directionIndependentSpeed = number(publicMethod(bearing, "getDirectionIndependentSpeed").invoke(bearing));
        double rawThrust = doubleNumber(publicMethod(bearing, "getThrust").invoke(bearing));
        double scaledThrust = doubleNumber(publicMethod(bearing, "getScaledThrust").invoke(bearing));
        Object blockDirection = publicMethod(bearing, "getBlockDirection").invoke(bearing);
        String facing = blockDirection.toString();
        int facingStepX = ((Number) publicMethod(blockDirection, "getStepX").invoke(blockDirection)).intValue();
        double appliedForceX = facingStepX * scaledThrust;

        assertFinite("direction-independent propeller speed", directionIndependentSpeed);
        assertFinite("raw propeller thrust", rawThrust);
        assertFinite("Sable scaled propeller thrust", scaledThrust);
        assertTrue("direction-independent propeller speed is nonzero", Math.abs(directionIndependentSpeed) > 0.01f);
        assertTrue("raw propeller thrust magnitude is nonzero", Math.abs(rawThrust) > 1.0e-9);
        assertTrue("compiled Propeller Bearing remains WEST-facing", "west".equalsIgnoreCase(facing));
        assertTrue("WEST-facing tractor has positive Sable scaled-thrust scalar", scaledThrust > 0.0);
        assertTrue("Sable propulsion point force acts toward aircraft nose (-X)", appliedForceX < 0.0);

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_POWERTRAIN PASS"
                        + " enginePortRpm=" + enginePortRpm
                        + " engineStarboardRpm=" + engineStarboardRpm
                        + " sharedSourceNetwork=" + enginePortNetwork
                        + " governorTargetRpm=" + realizedTarget
                        + " bearingRpm=" + bearingRpm
                        + " bearingTheoreticalRpm=" + bearingTheoreticalRpm
                        + " kineticCapacity=" + capacity
                        + " kineticStress=" + stress
                        + " stressMargin=" + stressMargin
                        + " directionIndependentSpeed=" + directionIndependentSpeed
                        + " rawThrust=" + rawThrust
                        + " scaledThrust=" + scaledThrust
                        + " facing=" + facing
                        + " appliedForceX=" + appliedForceX
                        + " thrustMeasured=true"
                        + " thrustDirectionQualified=true");
    }

    private static BlockEntity requireBlockEntity(
            ServerLevel level, BlockPos pos, String expectedClassSuffix, String expectedBlockId) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            fail("missing block entity at " + pos + " expectedClassSuffix=" + expectedClassSuffix);
        }
        if (!be.getClass().getName().endsWith(expectedClassSuffix)
                && !expectedClassSuffix.equals("KineticBlockEntity")) {
            fail("wrong block entity type at " + pos + ": expectedSuffix=" + expectedClassSuffix
                    + " actual=" + be.getClass().getName());
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!expectedBlockId.equals(id.toString())) {
            fail("wrong block resource at " + pos + ": expected=" + expectedBlockId + " actual=" + id);
        }
        return be;
    }

    private static void tick(Object target) throws ReflectiveOperationException {
        publicMethod(target, "tick").invoke(target);
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Field publicField(Object target, String name) throws NoSuchFieldException {
        return target.getClass().getField(name);
    }

    private static float number(Object value) {
        if (!(value instanceof Number)) {
            fail("expected numeric value, got " + value);
        }
        return ((Number) value).floatValue();
    }

    private static double doubleNumber(Object value) {
        if (!(value instanceof Number)) {
            fail("expected numeric value, got " + value);
        }
        return ((Number) value).doubleValue();
    }

    private static void assertInt(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertAbsNear(String label, float expectedMagnitude, float actual, float tolerance) {
        if (!Float.isFinite(actual) || Math.abs(Math.abs(actual) - expectedMagnitude) > tolerance) {
            fail(label + ": expectedAbs=" + expectedMagnitude + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertFinite(String label, float value) {
        if (!Float.isFinite(value)) {
            fail(label + ": expected finite actual=" + value);
        }
    }

    private static void assertFinite(String label, double value) {
        if (!Double.isFinite(value)) {
            fail(label + ": expected finite actual=" + value);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_POWERTRAIN FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 powertrain runtime acceptance failed: " + reason);
    }
}
