package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** AIRCRAFT-001 v0.12 exact-stack governed propulsion-performance acceptance. */
final class SkyforgeAircraftCompilerPowertrainRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPowertrainRuntimeAcceptance.class.getName());

    private static final int ENGINE_BURN_TICKS = 1200;
    private static final float EXPECTED_ENGINE_RPM = 32.0f;
    private static final int FIRST_ACCEPTED_GOVERNOR_RPM = 128;
    private static final int EXPECTED_BOUNDARY_RPM = 256;
    private static final int NETWORK_SETTLE_TICKS = 24;
    private static final int PROPELLER_SMOOTHING_TICKS = 48;
    private static final float RPM_TOLERANCE = 0.01f;
    private static final float STRESS_TOLERANCE = 0.01f;

    private SkyforgeAircraftCompilerPowertrainRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            BlockPos enginePortPos,
            BlockPos engineStarboardPos,
            BlockPos governorPos,
            BlockPos governorCogPos,
            BlockPos propShaftPos,
            BlockPos bearingPos,
            int[] governorRpmPoints)
            throws ReflectiveOperationException {
        assertTrue("governor ladder contains at least first accepted point", governorRpmPoints.length >= 1);
        assertInt("governor ladder starts at accepted 128 RPM point", FIRST_ACCEPTED_GOVERNOR_RPM, governorRpmPoints[0]);
        assertInt("governor ladder ends at declared 256 RPM boundary", EXPECTED_BOUNDARY_RPM, governorRpmPoints[governorRpmPoints.length - 1]);
        for (int i = 1; i < governorRpmPoints.length; i++) {
            assertTrue("governor ladder strictly increasing", governorRpmPoints[i] > governorRpmPoints[i - 1]);
        }

        BlockEntity enginePort = requireBlockEntity(level, enginePortPos, "PortableEngineBlockEntity", "simulated:red_portable_engine");
        BlockEntity engineStarboard = requireBlockEntity(level, engineStarboardPos, "PortableEngineBlockEntity", "simulated:red_portable_engine");
        BlockEntity governor = requireBlockEntity(level, governorPos, "SpeedControllerBlockEntity", "create:rotation_speed_controller");
        BlockEntity governorCog = requireBlockEntity(level, governorCogPos, "KineticBlockEntity", "create:large_cogwheel");
        BlockEntity propShaft = requireBlockEntity(level, propShaftPos, "KineticBlockEntity", "create:shaft");
        BlockEntity bearing = requireBlockEntity(level, bearingPos, "PropellerBearingBlockEntity", "aeronautics:propeller_bearing");

        Direction bearingDirection = (Direction) publicMethod(bearing, "getBlockDirection").invoke(bearing);
        assertTrue("compiled Propeller Bearing remains WEST-facing", bearingDirection == Direction.WEST);

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

        List<PointResult> results = new ArrayList<>();
        for (int targetRpm : governorRpmPoints) {
            publicMethod(targetSpeed, "setValue", int.class).invoke(targetSpeed, targetRpm);
            int realizedTarget = ((Number) publicMethod(targetSpeed, "getValue").invoke(targetSpeed)).intValue();
            assertInt("Rotation Speed Controller target", targetRpm, realizedTarget);

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
            assertAbsNear("Propeller Bearing live RPM", targetRpm, bearingRpm, RPM_TOLERANCE);
            assertAbsNear("Propeller Bearing theoretical RPM", targetRpm, bearingTheoreticalRpm, RPM_TOLERANCE);

            Object bearingNetwork = publicMethod(bearing, "getOrCreateNetwork").invoke(bearing);
            assertTrue("Propeller Bearing kinetic network object exists", bearingNetwork != null);
            float capacity = number(publicMethod(bearingNetwork, "calculateCapacity").invoke(bearingNetwork));
            float stress = number(publicMethod(bearingNetwork, "calculateStress").invoke(bearingNetwork));
            assertFinite("kinetic capacity", capacity);
            assertFinite("kinetic stress", stress);
            assertTrue("kinetic capacity positive", capacity > 0.0f);
            assertTrue("kinetic stress nonnegative", stress >= 0.0f);
            float stressMargin = capacity - stress;
            if (targetRpm < EXPECTED_BOUNDARY_RPM) {
                assertTrue("operating RPM retains positive kinetic stress margin", stressMargin > STRESS_TOLERANCE);
            } else {
                assertTrue("256 RPM boundary does not exceed kinetic capacity", stressMargin >= -STRESS_TOLERANCE);
            }

            for (int i = 0; i < PROPELLER_SMOOTHING_TICKS; i++) {
                tick(enginePort);
                tick(engineStarboard);
                tick(bearing);
            }

            float directionIndependentSpeed = number(publicMethod(bearing, "getDirectionIndependentSpeed").invoke(bearing));
            double rawThrust = doubleNumber(publicMethod(bearing, "getThrust").invoke(bearing));
            double scaledThrust = doubleNumber(publicMethod(bearing, "getScaledThrust").invoke(bearing));
            double airflowScaling = doubleNumber(publicMethod(bearing, "getAirflowScaling").invoke(bearing));
            double airPressure = doubleNumber(publicMethod(bearing, "getCurrentAirPressure").invoke(bearing));
            assertFinite("direction-independent propeller speed", directionIndependentSpeed);
            assertFinite("raw propeller thrust", rawThrust);
            assertFinite("Sable scaled propeller thrust", scaledThrust);
            assertFinite("airflow scaling", airflowScaling);
            assertFinite("air pressure", airPressure);
            assertTrue("direction-independent propeller speed is nonzero", Math.abs(directionIndependentSpeed) > 0.01f);
            assertTrue("raw propeller thrust magnitude is nonzero", Math.abs(rawThrust) > 1.0e-9);
            assertTrue("WEST-facing tractor raw thrust scalar is negative", rawThrust < 0.0);
            assertTrue("Sable scaled-thrust scalar is positive", scaledThrust > 0.0);
            assertTrue("airflow scaling remains nonnegative", airflowScaling >= 0.0);
            assertTrue("air pressure remains positive", airPressure > 0.0);

            double appliedForceX = bearingDirection.getStepX() * scaledThrust;
            double appliedForceY = bearingDirection.getStepY() * scaledThrust;
            double appliedForceZ = bearingDirection.getStepZ() * scaledThrust;
            assertTrue("Sable propulsion point force acts toward aircraft nose/local -X", appliedForceX < 0.0);
            assertNearZero("Sable propulsion point force local Y", appliedForceY, 1.0e-9);
            assertNearZero("Sable propulsion point force local Z", appliedForceZ, 1.0e-9);

            PointResult result = new PointResult(
                    targetRpm,
                    bearingRpm,
                    bearingTheoreticalRpm,
                    capacity,
                    stress,
                    stressMargin,
                    directionIndependentSpeed,
                    rawThrust,
                    scaledThrust,
                    airflowScaling,
                    airPressure,
                    appliedForceX);
            results.add(result);
            LOGGER.log(System.Logger.Level.INFO, "AIRCRAFT_001_RUNTIME_POWERTRAIN_POINT PASS " + result);
        }

        PointResult first = results.getFirst();
        PointResult boundary = results.getLast();
        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_POWERTRAIN PASS"
                        + " enginePortRpm=" + enginePortRpm
                        + " engineStarboardRpm=" + engineStarboardRpm
                        + " sharedSourceNetwork=" + enginePortNetwork
                        + " pointCount=" + results.size()
                        + " firstTargetRpm=" + first.targetRpm()
                        + " firstStressMargin=" + first.stressMargin()
                        + " firstRawThrust=" + first.rawThrust()
                        + " firstScaledThrust=" + first.scaledThrust()
                        + " firstAppliedForceX=" + first.appliedForceX()
                        + " boundaryTargetRpm=" + boundary.targetRpm()
                        + " boundaryStressMargin=" + boundary.stressMargin()
                        + " boundaryRawThrust=" + boundary.rawThrust()
                        + " thrustDirectionQualified=true"
                        + " operatingLadderVerified=true"
                        + " boundaryPointVerified=true");
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
        assertTrue("tick target exists", target != null);
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

    private static void assertNearZero(String label, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual) > tolerance) {
            fail(label + ": expected near zero actual=" + actual + " tolerance=" + tolerance);
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

    private record PointResult(
            int targetRpm,
            float bearingRpm,
            float bearingTheoreticalRpm,
            float kineticCapacity,
            float kineticStress,
            float stressMargin,
            float directionIndependentSpeed,
            double rawThrust,
            double scaledThrust,
            double airflowScaling,
            double airPressure,
            double appliedForceX) {}
}
