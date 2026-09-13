package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * AIRCRAFT-001 v0.12 exact-stack propulsion-performance boundary.
 *
 * <p>This runs only after the 127-block primary Sable recapture and the nine-block Propeller
 * Bearing child capture have succeeded. It proves the first 128-RPM governed point using the real
 * Create kinetic network and records Aeronautics thrust as runtime evidence. The measured thrust
 * sign is deliberately not yet interpreted as a flight-direction qualification.
 */
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

        Method setBurnPort = publicMethod(enginePort, "setCurrentBurnTime", int.class);
        Method setBurnStarboard = publicMethod(engineStarboard, "setCurrentBurnTime", int.class);
        setBurnPort.invoke(enginePort, ENGINE_BURN_TICKS);
        setBurnStarboard.invoke(engineStarboard, ENGINE_BURN_TICKS);

        // Let both real Simulated sources publish their 32-RPM output and attach kinetics.
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

        // Use Create's public ScrollValueBehaviour API. setValue() executes the controller's own
        // updateTargetRotation callback; there is no direct mutation of its internal target field.
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

        // Re-read the sources after the controller has rebuilt the network.
        enginePortRpm = number(publicMethod(enginePort, "getGeneratedSpeed").invoke(enginePort));
        engineStarboardRpm = number(publicMethod(engineStarboard, "getGeneratedSpeed").invoke(engineStarboard));
        assertAbsNear("settled port Portable Engine generated RPM", EXPECTED_ENGINE_RPM, enginePortRpm, 0.001f);
        assertAbsNear("settled starboard Portable Engine generated RPM", EXPECTED_ENGINE_RPM, engineStarboardRpm, 0.001f);

        enginePortNetwork = publicField(enginePort, "network").get(enginePort);
        engineStarboardNetwork = publicField(engineStarboard, "network").get(engineStarboard);
        assertTrue("settled engines retain one shared source network",
                enginePortNetwork != null && enginePortNetwork.equals(engineStarboardNetwork));

        boolean bearingHasNetwork = (Boolean) publicMethod(bearing, "hasNetwork").invoke(bearing);
        assertTrue("Propeller Bearing has governed kinetic network", bearingHasNetwork);
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

        // Aeronautics intentionally smooths propeller angular speed as a function of sail power.
        // Advance the real bearing until that state has converged before measuring propulsive output.
        for (int i = 0; i < PROPELLER_SMOOTHING_TICKS; i++) {
            tick(enginePort);
            tick(engineStarboard);
            tick(bearing);
        }

        float directionIndependentSpeed = number(publicMethod(bearing, "getDirectionIndependentSpeed").invoke(bearing));
        double thrust = doubleNumber(publicMethod(bearing, "getThrust").invoke(bearing));
        assertFinite("direction-independent propeller speed", directionIndependentSpeed);
        assertFinite("propeller thrust", thrust);
        assertTrue("direction-independent propeller speed is nonzero", Math.abs(directionIndependentSpeed) > 0.01f);
        assertTrue("propeller thrust magnitude is nonzero", Math.abs(thrust) > 1.0e-9);

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
                        + " thrust=" + thrust
                        + " thrustMeasured=true"
                        + " thrustDirectionQualified=false");
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
