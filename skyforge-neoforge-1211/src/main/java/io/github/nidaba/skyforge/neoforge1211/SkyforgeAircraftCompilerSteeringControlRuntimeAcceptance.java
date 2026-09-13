package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** AIRCRAFT-001 v0.17: real Simulated Steering Wheel source substitution for the accepted yaw mechanism. */
final class SkyforgeAircraftCompilerSteeringControlRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerSteeringControlRuntimeAcceptance.class.getName());
    private static final ResourceLocation STEERING_WHEEL_ID = id("simulated:steering_wheel");
    private static final ResourceLocation COGWHEEL_ID = id("create:cogwheel");
    private static final double RPM_TOLERANCE = 0.01;
    private static final int RELEASE_HOLD_TICKS = 4;

    private SkyforgeAircraftCompilerSteeringControlRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            Object parentSubLevel,
            BlockPos movedSwivelBearingPos,
            BlockPos movedDriveCogPos,
            BlockPos movedSteeringWheelPos,
            double wheelCommandDegrees,
            int expectedWheelRpmMagnitude,
            int maximumCommandTicks,
            int physicalSettlePhysicsTicks,
            double minimumSwivelTargetDeflectionDegrees,
            double minimumPhysicalRudderDeflectionDegrees,
            double targetNeutralToleranceDegrees,
            double physicalNeutralToleranceDegrees)
            throws ReflectiveOperationException {
        assertTrue("drive cog fixture position begins air", level.getBlockState(movedDriveCogPos).isAir());
        assertTrue("steering source fixture position begins air", level.getBlockState(movedSteeringWheelPos).isAir());

        BlockEntity bearing = requireBlockEntity(level, movedSwivelBearingPos, "SwivelBearingBlockEntity");
        assertNear("v0.17 begins from neutral Swivel target", 0.0,
                normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing))),
                targetNeutralToleranceDegrees);
        Object childSubLevel = attachedChild(bearing);

        BlockState cogState = requireBlock(COGWHEEL_ID).defaultBlockState();
        cogState = setProperty(cogState, "axis", "y");
        BlockState wheelState = requireBlock(STEERING_WHEEL_ID).defaultBlockState();
        wheelState = setProperty(wheelState, "facing", "north");
        wheelState = setProperty(wheelState, "on_floor", "false");
        wheelState = setProperty(wheelState, "waterlogged", "false");
        level.setBlock(movedDriveCogPos, cogState, 3);
        level.setBlock(movedSteeringWheelPos, wheelState, 3);

        BlockEntity driveCog = requireBlockEntity(level, movedDriveCogPos, null);
        BlockEntity wheel = requireBlockEntity(level, movedSteeringWheelPos, "SteeringWheelBlockEntity");
        Object extraCog = publicMethod(bearing, "getExtraKinetics").invoke(bearing);
        assertTrue("Swivel extra cog available", extraCog != null);

        // Mirror SteeringWheelPacket.handle(): persist the requested angle on targetAngleToUpdate,
        // then mark the wheel held. The wheel's own server tick is responsible for starting the
        // bounded 16-RPM sequence; do not call updateTargetAngle() directly.
        commandWheel(wheel, (float) wheelCommandDegrees);
        DriveObservation outbound = runFiniteWheelCommand(
                wheel, driveCog, bearing, extraCog, maximumCommandTicks, expectedWheelRpmMagnitude);
        assertTrue("Steering Wheel generated +16-RPM-class outbound source", outbound.maxWheelRpm > 0.0);
        assertTrue("drive cog follows Steering Wheel outbound sign", outbound.maxDriveCogRpm > 0.0);
        assertTrue("small-cog mesh reverses outbound sign into Swivel", outbound.minExtraCogRpm < 0.0);
        assertNear("outbound Steering Wheel RPM magnitude", expectedWheelRpmMagnitude,
                Math.abs(outbound.maxWheelRpm), RPM_TOLERANCE);

        double targetDeflected = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        assertTrue("Steering Wheel creates substantial signed Swivel target deflection",
                targetDeflected <= -minimumSwivelTargetDeflectionDegrees);
        double wheelAngleDeflected = number(publicMethod(wheel, "getAngle").invoke(wheel));
        assertTrue("Steering Wheel itself reaches positive commanded angle", wheelAngleDeflected > 0.0);

        // Mirror a real release packet: release changes only held state and preserves the final
        // targetAngleToUpdate. Verify that release holds the commanded angle instead of silently
        // self-centering, which is an important production-control semantic.
        publicMethod(wheel, "stopHolding").invoke(wheel);
        double releasedWheelAngle = number(publicMethod(wheel, "getAngle").invoke(wheel));
        double releasedSwivelTarget = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        for (int i = 0; i < RELEASE_HOLD_TICKS; i++) {
            tick(wheel);
            tick(driveCog);
            tick(bearing);
            assertNear("released Steering Wheel remains stopped", 0.0,
                    number(publicMethod(wheel, "getGeneratedSpeed").invoke(wheel)), RPM_TOLERANCE);
            assertNear("released Swivel extra cog remains stopped", 0.0,
                    number(publicMethod(extraCog, "getSpeed").invoke(extraCog)), RPM_TOLERANCE);
        }
        assertNear("release preserves Steering Wheel angle", releasedWheelAngle,
                number(publicMethod(wheel, "getAngle").invoke(wheel)), targetNeutralToleranceDegrees);
        assertNear("release preserves Swivel target", 0.0,
                signedDeltaDegrees(releasedSwivelTarget,
                        normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)))),
                targetNeutralToleranceDegrees);
        assertNear("release preserves packet-style requested target", wheelCommandDegrees,
                fieldFloat(wheel, "targetAngleToUpdate"), targetNeutralToleranceDegrees);

        Object container = requireServerSubLevelContainer(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Object physicsSystem = serverContainerClass.getDeclaredMethod("physicsSystem").invoke(container);
        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalDeflected = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder follows Steering Wheel-generated target",
                physicalDeflected <= -minimumPhysicalRudderDeflectionDegrees);

        // Neutral is another real steering command, not passive centering: persist target 0,
        // mark held, let the wheel generate its finite reverse sequence, then release at neutral.
        commandWheel(wheel, 0.0f);
        DriveObservation inbound = runFiniteWheelCommand(
                wheel, driveCog, bearing, extraCog, maximumCommandTicks, expectedWheelRpmMagnitude);
        assertTrue("Steering Wheel return source reverses sign", inbound.minWheelRpm < 0.0);
        assertTrue("drive cog return source reverses sign", inbound.minDriveCogRpm < 0.0);
        assertTrue("Swivel extra cog return sign reverses positive", inbound.maxExtraCogRpm > 0.0);
        publicMethod(wheel, "stopHolding").invoke(wheel);

        double targetReturned = normalizeDegrees(number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing)));
        assertTrue("Steering Wheel command returns Swivel target near neutral",
                Math.abs(targetReturned) <= targetNeutralToleranceDegrees);
        assertNear("Steering Wheel internal angle returns neutral", 0.0,
                number(publicMethod(wheel, "getAngle").invoke(wheel)), targetNeutralToleranceDegrees);
        assertNear("neutral command persists packet-style requested target", 0.0,
                fieldFloat(wheel, "targetAngleToUpdate"), targetNeutralToleranceDegrees);

        runPhysics(physicsSystem, container, physicalSettlePhysicsTicks);
        double physicalReturned = relativeYawDegrees(parentSubLevel, childSubLevel);
        assertTrue("physical rudder returns near neutral under Steering Wheel command",
                Math.abs(physicalReturned) <= physicalNeutralToleranceDegrees);

        level.setBlock(movedSteeringWheelPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(movedDriveCogPos, Blocks.AIR.defaultBlockState(), 3);
        zeroVelocity(oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel).invoke(physicsSystem, parentSubLevel));
        zeroVelocity(oneArgMethod(physicsSystem, "getPhysicsHandle", childSubLevel).invoke(physicsSystem, childSubLevel));

        LOGGER.log(System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_STEERING_CONTROL PASS"
                        + " wheelCommandDegrees=" + wheelCommandDegrees
                        + " outboundTicks=" + outbound.ticks
                        + " outboundWheelRpm=" + outbound.maxWheelRpm
                        + " outboundDriveCogRpm=" + outbound.maxDriveCogRpm
                        + " outboundExtraCogRpm=" + outbound.minExtraCogRpm
                        + " targetDeflectedDegrees=" + targetDeflected
                        + " physicalDeflectedDegrees=" + physicalDeflected
                        + " releaseHoldVerified=true"
                        + " passiveSelfCenteringVerified=false"
                        + " inboundTicks=" + inbound.ticks
                        + " inboundWheelRpm=" + inbound.minWheelRpm
                        + " inboundDriveCogRpm=" + inbound.minDriveCogRpm
                        + " inboundExtraCogRpm=" + inbound.maxExtraCogRpm
                        + " targetReturnedDegrees=" + targetReturned
                        + " physicalReturnedDegrees=" + physicalReturned
                        + " realSteeringWheelSourceVerified=true"
                        + " serverPacketStateContractEmulated=true"
                        + " cockpitRoutingVerified=false"
                        + " pilotInteractionBindingVerified=false"
                        + " stableFlightVerified=false");
    }

    private static void commandWheel(Object wheel, float targetDegrees) throws ReflectiveOperationException {
        Field targetField = wheel.getClass().getField("targetAngleToUpdate");
        targetField.setFloat(wheel, targetDegrees);
        publicMethod(wheel, "startHolding").invoke(wheel);
    }

    private static float fieldFloat(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getField(name).getFloat(target);
    }

    private static DriveObservation runFiniteWheelCommand(
            Object wheel, Object driveCog, Object bearing, Object extraCog,
            int maximumTicks, int expectedRpm) throws ReflectiveOperationException {
        double maxWheel = 0.0, minWheel = 0.0;
        double maxCog = 0.0, minCog = 0.0;
        double maxExtra = 0.0, minExtra = 0.0;
        int stoppedTicks = 0;
        int ticks = 0;
        for (; ticks < maximumTicks; ticks++) {
            tick(wheel);
            tick(driveCog);
            tick(bearing);
            double wr = number(publicMethod(wheel, "getGeneratedSpeed").invoke(wheel));
            double cr = number(publicMethod(driveCog, "getSpeed").invoke(driveCog));
            double er = number(publicMethod(extraCog, "getSpeed").invoke(extraCog));
            maxWheel = Math.max(maxWheel, wr); minWheel = Math.min(minWheel, wr);
            maxCog = Math.max(maxCog, cr); minCog = Math.min(minCog, cr);
            maxExtra = Math.max(maxExtra, er); minExtra = Math.min(minExtra, er);
            if (Math.abs(wr) <= RPM_TOLERANCE && Math.abs(er) <= RPM_TOLERANCE) stoppedTicks++;
            else stoppedTicks = 0;
            if (stoppedTicks >= 2) break;
        }
        if (ticks >= maximumTicks - 1 && stoppedTicks < 2) fail("Steering Wheel source did not stop within bounded command window");
        assertTrue("Steering Wheel command reached expected RPM magnitude",
                Math.max(Math.abs(maxWheel), Math.abs(minWheel)) >= expectedRpm - RPM_TOLERANCE);
        return new DriveObservation(ticks + 1, maxWheel, minWheel, maxCog, minCog, maxExtra, minExtra);
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

    private static void runPhysics(Object physicsSystem, Object container, int ticks)
            throws ReflectiveOperationException {
        for (int i = 0; i < ticks; i++) oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
    }

    private static void zeroVelocity(Object handle) throws ReflectiveOperationException {
        if (handle == null) fail("physics handle missing during steering-control cleanup");
        Vector3d linear = new Vector3d();
        Vector3d angular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", linear).invoke(handle, linear);
        oneArgMethod(handle, "getAngularVelocity", angular).invoke(handle, angular);
        linear.negate(); angular.negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linear, angular).invoke(handle, linear, angular);
    }

    private static Object attachedChild(BlockEntity bearing) throws ReflectiveOperationException {
        Object raw = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(raw instanceof Iterable<?>)) fail("Swivel did not expose child dependencies");
        Object child = null; int count = 0;
        for (Object value : (Iterable<?>) raw) if (value != null) { child = value; count++; }
        if (count != 1 || child == null) fail("expected exactly one rudder child dependency, got " + count);
        return child;
    }

    private static Object requireServerSubLevelContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object container = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Class<?> serverClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (container == null || !serverClass.isInstance(container)) fail("Sable ServerSubLevelContainer unavailable");
        return container;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, String propertyName, String value) {
        for (Property property : state.getProperties()) {
            if (!property.getName().equals(propertyName)) continue;
            Optional parsed = property.getValue(value);
            if (parsed.isEmpty()) fail("invalid " + propertyName + "=" + value + " for " + state);
            return state.setValue(property, (Comparable) parsed.get());
        }
        fail("missing blockstate property " + propertyName + " on " + state);
        return state;
    }

    private static Block requireBlock(ResourceLocation id) {
        if (!BuiltInRegistries.BLOCK.containsKey(id)) fail("required block not registered: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static BlockEntity requireBlockEntity(ServerLevel level, BlockPos pos, String suffix) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) fail("missing block entity at " + pos);
        if (suffix != null && !be.getClass().getName().endsWith(suffix)) fail("wrong block entity at " + pos + ": " + be.getClass().getName());
        return be;
    }

    private static void tick(Object target) throws ReflectiveOperationException { publicMethod(target, "tick").invoke(target); }
    private static double signedDeltaDegrees(double from, double to) { double d=(to-from)%360.0; if(d>180)d-=360; else if(d<=-180)d+=360; return d; }
    private static double normalizeDegrees(double v) { double d=v%360.0; if(d>180)d-=360; else if(d<=-180)d+=360; return d; }
    private static ResourceLocation id(String s) { ResourceLocation r=ResourceLocation.tryParse(s); if(r==null) throw new IllegalArgumentException(s); return r; }
    private static Quaterniondc quaternion(Object v) { if(!(v instanceof Quaterniondc)) fail("expected quaternion, got "+v); return (Quaterniondc)v; }
    private static double number(Object v) { if(!(v instanceof Number)) fail("expected number, got "+v); return ((Number)v).doubleValue(); }
    private static Method publicMethod(Object target, String name, Class<?>... types) throws NoSuchMethodException { return target.getClass().getMethod(name, types); }
    private static Method oneArgMethod(Object target, String name, Object arg) throws NoSuchMethodException {
        for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==1&&m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) return m;
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    private static Method twoArgMethod(Object target, String name, Object a, Object b) throws NoSuchMethodException {
        for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==2&&m.getParameterTypes()[0].isAssignableFrom(a.getClass())&&m.getParameterTypes()[1].isAssignableFrom(b.getClass())) return m;
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    private static void assertNear(String label,double expected,double actual,double tol){if(!Double.isFinite(actual)||Math.abs(actual-expected)>tol)fail(label+": expected="+expected+" actual="+actual+" tolerance="+tol);}
    private static void assertTrue(String label,boolean ok){if(!ok)fail(label+": expected true");}
    private static void fail(String reason){LOGGER.log(System.Logger.Level.ERROR,"AIRCRAFT_001_RUNTIME_STEERING_CONTROL FAIL "+reason);throw new IllegalStateException("AIRCRAFT-001 steering-control runtime acceptance failed: "+reason);}

    private record DriveObservation(int ticks, double maxWheelRpm, double minWheelRpm,
                                    double maxDriveCogRpm, double minDriveCogRpm,
                                    double maxExtraCogRpm, double minExtraCogRpm) {}
}
