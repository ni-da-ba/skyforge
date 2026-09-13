package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** AIRCRAFT-001 v0.18: exact-stack production cockpit-to-rudder Create kinetic route acceptance. */
final class SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance.class.getName());
    private static final double RPM_TOLERANCE = 0.01;
    private static final int RELEASE_HOLD_TICKS = 4;

    private SkyforgeAircraftCompilerCockpitYawRouteRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            Object parentSubLevel,
            BlockPos movedSwivelBearingPos,
            BlockPos movedDriveCogPos,
            BlockPos movedSteeringWheelPos,
            double wheelCommandDegrees,
            int expectedWheelSign,
            int expectedDriveCogSign,
            int expectedExtraCogSign,
            int expectedRpmMagnitude,
            int maximumCommandTicks,
            int physicalSettlePhysicsTicks,
            double minimumSwivelTargetDeflectionDegrees,
            double minimumPhysicalRudderDeflectionDegrees,
            double targetNeutralToleranceDegrees,
            double physicalNeutralToleranceDegrees)
            throws ReflectiveOperationException {
        assertSign("expected Steering Wheel sign", expectedWheelSign);
        assertSign("expected tail drive-cog sign", expectedDriveCogSign);
        assertSign("expected Swivel extra-cog sign", expectedExtraCogSign);

        BlockEntity bearing = requireBlockEntity(level, movedSwivelBearingPos, "SwivelBearingBlockEntity");
        BlockEntity driveCog = requireBlockEntity(level, movedDriveCogPos, null);
        BlockEntity wheel = requireBlockEntity(level, movedSteeringWheelPos, "SteeringWheelBlockEntity");
        Object extraCog = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        assertTrue("Swivel extra cog available", extraCog != null);
        Object childSubLevel = attachedChild(bearing);

        assertNear(
                "v0.18 begins from neutral Swivel target",
                0.0,
                normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing))),
                targetNeutralToleranceDegrees);
        assertNear(
                "v0.18 begins from neutral Steering Wheel angle",
                0.0,
                number(publicMethod(wheel, "getAngle").invoke(wheel)),
                targetNeutralToleranceDegrees);

        commandWheel(wheel, (float) wheelCommandDegrees);
        DriveObservation outbound = runFiniteWheelCommand(
                wheel,
                driveCog,
                bearing,
                extraCog,
                maximumCommandTicks,
                expectedRpmMagnitude);
        assertSignedPeak("cockpit Steering Wheel outbound sign", outbound.maxWheelRpm, outbound.minWheelRpm,
                expectedWheelSign, expectedRpmMagnitude);
        assertSignedPeak("tail drive-cog outbound sign", outbound.maxDriveCogRpm, outbound.minDriveCogRpm,
                expectedDriveCogSign, expectedRpmMagnitude);
        assertSignedPeak("Swivel extra-cog outbound sign", outbound.maxExtraCogRpm, outbound.minExtraCogRpm,
                expectedExtraCogSign, expectedRpmMagnitude);

        double targetDeflected = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        assertTrue("cockpit route creates substantial Swivel target deflection",
                Math.abs(targetDeflected) >= minimumSwivelTargetDeflectionDegrees);
        assertTrue("Swivel target sign follows compiled extra-cog sign",
                Math.signum(targetDeflected) == expectedExtraCogSign);
        assertNear("Steering Wheel itself reaches requested cockpit angle", wheelCommandDegrees,
                number(publicMethod(wheel, "getAngle").invoke(wheel)), targetNeutralToleranceDegrees);

        publicMethod(wheel, "stopHolding").invoke(wheel);
        double releasedWheelAngle = number(publicMethod(wheel, "getAngle").invoke(wheel));
        double releasedSwivelTarget = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        for (int i = 0; i < RELEASE_HOLD_TICKS; i++) {
            tick(wheel);
            tick(driveCog);
            tick(bearing);
            assertNear("released cockpit Steering Wheel remains stopped", 0.0,
                    number(publicMethod(wheel, "getGeneratedSpeed").invoke(wheel)), RPM_TOLERANCE);
            assertNear("released cockpit route Swivel extra cog remains stopped", 0.0,
                    number(publicMethod(extraCog, "getSpeed").invoke(extraCog)), RPM_TOLERANCE);
        }
        assertNear("release preserves cockpit Steering Wheel angle", releasedWheelAngle,
                number(publicMethod(wheel, "getAngle").invoke(wheel)), targetNeutralToleranceDegrees);
        assertNear("release preserves cockpit-routed Swivel target", 0.0,
                signedDeltaDegrees(
                        releasedSwivelTarget,
                        normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)))),
                targetNeutralToleranceDegrees);
        assertNear("release preserves packet-style requested cockpit target", wheelCommandDegrees,
                fieldFloat(wheel, "targetAngleToUpdate"), targetNeutralToleranceDegrees);

        Object container = requireServerSubLevelContainer(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Object physicsSystem = serverContainerClass.getDeclaredMethod("physicsSystem").invoke(container);
        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder follows cockpit-routed target",
                Math.abs(physicalDeflected) >= minimumPhysicalRudderDeflectionDegrees);
        assertTrue("physical rudder sign follows compiled extra-cog sign",
                Math.signum(physicalDeflected) == expectedExtraCogSign);

        commandWheel(wheel, 0.0f);
        DriveObservation inbound = runFiniteWheelCommand(
                wheel,
                driveCog,
                bearing,
                extraCog,
                maximumCommandTicks,
                expectedRpmMagnitude);
        assertSignedPeak("cockpit Steering Wheel neutral-return sign", inbound.maxWheelRpm, inbound.minWheelRpm,
                -expectedWheelSign, expectedRpmMagnitude);
        assertSignedPeak("tail drive-cog neutral-return sign", inbound.maxDriveCogRpm, inbound.minDriveCogRpm,
                -expectedDriveCogSign, expectedRpmMagnitude);
        assertSignedPeak("Swivel extra-cog neutral-return sign", inbound.maxExtraCogRpm, inbound.minExtraCogRpm,
                -expectedExtraCogSign, expectedRpmMagnitude);
        publicMethod(wheel, "stopHolding").invoke(wheel);

        double targetReturned = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        assertTrue("cockpit route command returns Swivel target near neutral",
                Math.abs(targetReturned) <= targetNeutralToleranceDegrees);
        assertNear("cockpit Steering Wheel internal angle returns neutral", 0.0,
                number(publicMethod(wheel, "getAngle").invoke(wheel)), targetNeutralToleranceDegrees);
        assertNear("neutral command persists packet-style requested cockpit target", 0.0,
                fieldFloat(wheel, "targetAngleToUpdate"), targetNeutralToleranceDegrees);

        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder returns near neutral through cockpit route",
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);

        zeroVelocity(oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel).invoke(physicsSystem, parentSubLevel));
        zeroVelocity(oneArgMethod(physicsSystem, "getPhysicsHandle", childSubLevel).invoke(physicsSystem, childSubLevel));

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_COCKPIT_YAW_ROUTE PASS"
                        + " wheelCommandDegrees=" + wheelCommandDegrees
                        + " outboundTicks=" + outbound.ticks
                        + " outboundWheelRpm=" + signedPeak(outbound.maxWheelRpm, outbound.minWheelRpm, expectedWheelSign)
                        + " outboundDriveCogRpm=" + signedPeak(outbound.maxDriveCogRpm, outbound.minDriveCogRpm, expectedDriveCogSign)
                        + " outboundExtraCogRpm=" + signedPeak(outbound.maxExtraCogRpm, outbound.minExtraCogRpm, expectedExtraCogSign)
                        + " targetDeflectedDegrees=" + targetDeflected
                        + " physicalDeflectedDegrees=" + physicalDeflected
                        + " releaseHoldVerified=true"
                        + " inboundTicks=" + inbound.ticks
                        + " targetReturnedDegrees=" + targetReturned
                        + " physicalReturnedDegrees=" + physicalReturned
                        + " productionCockpitRoutingVerified=true"
                        + " compiledNetworkSignVerified=true"
                        + " commandedNeutralReturnVerified=true"
                        + " pilotInteractionBindingVerified=false"
                        + " stableFlightVerified=false");
    }

    private static void commandWheel(Object wheel, float targetDegrees) throws ReflectiveOperationException {
        Field targetField = wheel.getClass().getField("targetAngleToUpdate");
        targetField.setFloat(wheel, targetDegrees);
        publicMethod(wheel, "startHolding").invoke(wheel);
    }

    private static DriveObservation runFiniteWheelCommand(
            Object wheel,
            Object driveCog,
            Object bearing,
            Object extraCog,
            int maximumTicks,
            int expectedRpm)
            throws ReflectiveOperationException {
        double maxWheel = 0.0;
        double minWheel = 0.0;
        double maxDrive = 0.0;
        double minDrive = 0.0;
        double maxExtra = 0.0;
        double minExtra = 0.0;
        int stoppedTicks = 0;
        int ticks = 0;
        for (; ticks < maximumTicks; ticks++) {
            tick(wheel);
            tick(driveCog);
            tick(bearing);
            double wheelRpm = number(publicMethod(wheel, "getGeneratedSpeed").invoke(wheel));
            double driveRpm = number(publicMethod(driveCog, "getSpeed").invoke(driveCog));
            double extraRpm = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
            maxWheel = Math.max(maxWheel, wheelRpm);
            minWheel = Math.min(minWheel, wheelRpm);
            maxDrive = Math.max(maxDrive, driveRpm);
            minDrive = Math.min(minDrive, driveRpm);
            maxExtra = Math.max(maxExtra, extraRpm);
            minExtra = Math.min(minExtra, extraRpm);
            if (Math.abs(wheelRpm) <= RPM_TOLERANCE && Math.abs(extraRpm) <= RPM_TOLERANCE) {
                stoppedTicks++;
            } else {
                stoppedTicks = 0;
            }
            if (stoppedTicks >= 2) {
                break;
            }
        }
        if (ticks >= maximumTicks - 1 && stoppedTicks < 2) {
            fail("cockpit Steering Wheel source did not stop within bounded command window");
        }
        assertTrue(
                "cockpit Steering Wheel command reached expected RPM magnitude",
                Math.max(Math.abs(maxWheel), Math.abs(minWheel)) >= expectedRpm - RPM_TOLERANCE);
        return new DriveObservation(ticks + 1, maxWheel, minWheel, maxDrive, minDrive, maxExtra, minExtra);
    }

    private static void assertSignedPeak(String label, double max, double min, int expectedSign, int expectedMagnitude) {
        double observed = signedPeak(max, min, expectedSign);
        assertTrue(label + " expected sign " + expectedSign + " observed=" + observed,
                expectedSign > 0 ? observed > 0.0 : observed < 0.0);
        assertNear(label + " magnitude", expectedMagnitude, Math.abs(observed), RPM_TOLERANCE);
    }

    private static double signedPeak(double max, double min, int sign) {
        return sign > 0 ? max : min;
    }

    private static void assertSign(String label, int value) {
        assertTrue(label + " must be +/-1", value == -1 || value == 1);
    }

    private static float fieldFloat(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).getFloat(target);
    }

    private static Object attachedChild(BlockEntity bearing) throws ReflectiveOperationException {
        Object raw = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(raw instanceof Iterable<?>)) {
            throw new IllegalStateException("Swivel did not expose child dependencies");
        }
        Iterable<?> values = (Iterable<?>) raw;
        Object child = null;
        int count = 0;
        for (Object value : values) {
            if (value != null) {
                child = value;
                count++;
            }
        }
        if (count != 1 || child == null) {
            fail("expected exactly one rudder child dependency, got " + count);
        }
        return child;
    }

    private static double relativeYawDegrees(Object parentSubLevel, Object childSubLevel)
            throws ReflectiveOperationException {
        Object parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Object childPose = publicMethod(childSubLevel, "logicalPose").invoke(childSubLevel);
        Quaterniondc parent = quaternion(publicMethod(parentPose, "orientation").invoke(parentPose));
        Quaterniondc child = quaternion(publicMethod(childPose, "orientation").invoke(childPose));
        Quaterniond relative = new Quaterniond(parent).conjugate().mul(new Quaterniond(child));
        Vector3d forward = relative.transform(new Vector3d(1.0, 0.0, 0.0));
        return normalizeDegrees(Math.toDegrees(Math.atan2(-forward.z, forward.x)));
    }

    private static Quaterniondc quaternion(Object value) {
        if (value instanceof Quaterniondc quaternion) {
            return quaternion;
        }
        throw new IllegalStateException(
                "expected Quaterniondc, got " + (value == null ? "null" : value.getClass().getName()));
    }

    private static Object requireServerSubLevelContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object container = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Class<?> serverClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (container == null || !serverClass.isInstance(container)) {
            fail("Sable ServerSubLevelContainer unavailable");
        }
        return container;
    }

    private static void runPhysics(Object physicsSystem, Object container, int ticks)
            throws ReflectiveOperationException {
        for (int i = 0; i < ticks; i++) {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
        }
    }

    private static void zeroVelocity(Object handle) throws ReflectiveOperationException {
        if (handle == null) {
            fail("physics handle missing during cockpit-route cleanup");
        }
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", linear).invoke(handle, linear);
        oneArgMethod(handle, "getAngularVelocity", angular).invoke(handle, angular);
        linear.negate();
        angular.negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linear, angular).invoke(handle, linear, angular);
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String suffix) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            fail("missing block entity at " + pos);
        }
        if (suffix != null && !blockEntity.getClass().getName().endsWith(suffix)) {
            fail("wrong block entity at " + pos + ": " + blockEntity.getClass().getName());
        }
        return blockEntity;
    }

    private static void tick(Object target) throws ReflectiveOperationException {
        publicMethod(target, "tick").invoke(target);
    }

    private static Method publicMethod(Object target, String name) throws NoSuchMethodException {
        return target.getClass().getMethod(name);
    }

    private static Method oneArgMethod(Object target, String name, Object argument) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            if (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "(1 arg)");
    }

    private static Method twoArgMethod(Object target, String name, Object first, Object second) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean firstMatches = first == null || parameterTypes[0].isAssignableFrom(first.getClass());
            boolean secondMatches = second == null || parameterTypes[1].isAssignableFrom(second.getClass());
            if (firstMatches && secondMatches) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + "(2 args)");
    }

    private static double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException(
                "expected Number, got " + (value == null ? "null" : value.getClass().getName()));
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

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized > 180.0) {
            normalized -= 360.0;
        } else if (normalized <= -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private static void assertNear(String label, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            fail(label + ": expected=" + expected + " actual=" + actual + " tolerance=" + tolerance);
        }
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label);
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }

    private record DriveObservation(
            int ticks,
            double maxWheelRpm,
            double minWheelRpm,
            double maxDriveCogRpm,
            double minDriveCogRpm,
            double maxExtraCogRpm,
            double minExtraCogRpm) {}
}
