package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** AIRCRAFT-001 v0.15: genuine Sable physical-rudder pose and aerodynamic yaw-authority probe. */
final class SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance.class.getName());

    private SkyforgeAircraftCompilerYawAuthorityRuntimeAcceptance() {}

    static void verify(
            ServerLevel level,
            Object parentSubLevel,
            BlockPos movedSwivelBearingPos,
            double forwardSpeedMps,
            int servoSettlePhysicsTicks,
            double minimumPhysicalDeflectionDegrees,
            double minimumLateralImpulseMagnitude,
            double minimumYawImpulseMagnitude)
            throws ReflectiveOperationException {
        BlockEntity bearing = level.getBlockEntity(movedSwivelBearingPos);
        if (bearing == null || !bearing.getClass().getName().endsWith("SwivelBearingBlockEntity")) {
            fail("missing Swivel bearing at " + movedSwivelBearingPos);
        }
        Object childSubLevel = attachedChild(bearing);
        assertTrue("rudder child is ServerSubLevel", childSubLevel.getClass().getName().endsWith("ServerSubLevel"));

        Object container = requireServerSubLevelContainer(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        Object physicsSystem = serverContainerClass.getDeclaredMethod("physicsSystem").invoke(container);
        assertTrue("Sable physics system available", physicsSystem != null);

        double commandedTargetDegrees = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        for (int i = 0; i < servoSettlePhysicsTicks; i++) {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
        }

        Object parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Object childPose = publicMethod(childSubLevel, "logicalPose").invoke(childSubLevel);
        Quaterniondc parentOrientation = quaternion(publicMethod(parentPose, "orientation").invoke(parentPose));
        Quaterniondc childOrientation = quaternion(publicMethod(childPose, "orientation").invoke(childPose));
        Quaterniond relative = new Quaterniond(parentOrientation).conjugate().mul(new Quaterniond(childOrientation));
        Vector3d childForwardInParent = relative.transform(new Vector3d(1.0, 0.0, 0.0));
        double physicalYawDegrees = Math.toDegrees(Math.atan2(-childForwardInParent.z, childForwardInParent.x));
        assertTrue("physical rudder yaw is finite", Double.isFinite(physicalYawDegrees));
        assertTrue("physical rudder body responds to Swivel target",
                Math.abs(physicalYawDegrees) >= minimumPhysicalDeflectionDegrees);

        Object parentHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel)
                .invoke(physicsSystem, parentSubLevel);
        Object childHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", childSubLevel)
                .invoke(physicsSystem, childSubLevel);
        assertTrue("parent physics handle available", parentHandle != null);
        assertTrue("child physics handle available", childHandle != null);

        Vector3d desiredVelocity = new Vector3d(-forwardSpeedMps, 0.0, 0.0);
        new Quaterniond(parentOrientation).transform(desiredVelocity);
        setVelocity(parentHandle, desiredVelocity);
        setVelocity(childHandle, desiredVelocity);

        publicMethod(childSubLevel, "enableIndividualQueuedForcesTracking", boolean.class)
                .invoke(childSubLevel, true);
        oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);

        Object dragRegistryObject = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroups")
                .getField("DRAG")
                .get(null);
        Object dragGroup = publicMethod(dragRegistryObject, "get").invoke(dragRegistryObject);
        Object queuedDrag = oneArgMethod(childSubLevel, "getOrCreateQueuedForceGroup", dragGroup)
                .invoke(childSubLevel, dragGroup);
        Object rawForces = publicMethod(queuedDrag, "getRecordedPointForces").invoke(queuedDrag);
        if (!(rawForces instanceof List<?>)) {
            fail("rudder child drag-force record has unexpected type: " + rawForces);
        }
        List<?> recorded = (List<?>) rawForces;
        if (recorded.isEmpty()) {
            fail("rudder child produced no recorded Sable DRAG point forces under controlled forward flow");
        }

        childPose = publicMethod(childSubLevel, "logicalPose").invoke(childSubLevel);
        parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Object parentMassTracker = publicMethod(parentSubLevel, "getMassTracker").invoke(parentSubLevel);
        Vector3dc parentComLocal = vector(publicMethod(parentMassTracker, "getCenterOfMass").invoke(parentMassTracker));
        Vector3d parentComWorld = transformPosition(parentPose, new Vector3d(parentComLocal));

        Vector3d totalForceWorld = new Vector3d();
        Vector3d totalMomentWorld = new Vector3d();
        List<String> pointSummaries = new ArrayList<>();
        for (Object pointForce : recorded) {
            Vector3dc pointLocal = vector(publicMethod(pointForce, "point").invoke(pointForce));
            Vector3dc forceLocal = vector(publicMethod(pointForce, "force").invoke(pointForce));
            Vector3d pointWorld = transformPosition(childPose, new Vector3d(pointLocal));
            Vector3d forceWorld = transformNormal(childPose, new Vector3d(forceLocal));
            Vector3d armWorld = new Vector3d(pointWorld).sub(parentComWorld);
            Vector3d momentWorld = armWorld.cross(forceWorld, new Vector3d());
            totalForceWorld.add(forceWorld);
            totalMomentWorld.add(momentWorld);
            pointSummaries.add("p=" + pointWorld + ",f=" + forceWorld);
        }

        publicMethod(childSubLevel, "enableIndividualQueuedForcesTracking", boolean.class)
                .invoke(childSubLevel, false);

        assertTrue("rudder lateral drag impulse is finite", Double.isFinite(totalForceWorld.z));
        assertTrue("rudder lateral drag impulse is nonzero",
                Math.abs(totalForceWorld.z) >= minimumLateralImpulseMagnitude);
        assertTrue("rudder yaw impulse is finite", Double.isFinite(totalMomentWorld.y));
        assertTrue("rudder produces nonzero yaw impulse about parent COM",
                Math.abs(totalMomentWorld.y) >= minimumYawImpulseMagnitude);
        assertTrue("aft rudder yaw impulse opposes measured rudder-body yaw sign",
                Math.signum(totalMomentWorld.y) == -Math.signum(physicalYawDegrees));

        LOGGER.log(
                System.Logger.Level.INFO,
                "AIRCRAFT_001_RUNTIME_YAW_AUTHORITY PASS"
                        + " targetDegrees=" + commandedTargetDegrees
                        + " physicalYawDegrees=" + physicalYawDegrees
                        + " forwardSpeedMps=" + forwardSpeedMps
                        + " dragPointCount=" + recorded.size()
                        + " lateralImpulseZ=" + totalForceWorld.z
                        + " yawImpulseY=" + totalMomentWorld.y
                        + " totalForceWorld=" + totalForceWorld
                        + " totalMomentWorld=" + totalMomentWorld
                        + " neutralReturnVerified=false"
                        + " productionControlBindingVerified=false"
                        + " stableFlightVerified=false"
                        + " points=" + pointSummaries);
    }

    private static Object requireServerSubLevelContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        if (!holderClass.isInstance(level)) {
            fail("ServerLevel does not expose Sable SubLevelContainerHolder");
        }
        Object container = holderClass.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Class<?> serverContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer");
        if (container == null || !serverContainerClass.isInstance(container)) {
            fail("Sable ServerSubLevelContainer unavailable");
        }
        return container;
    }

    private static Object attachedChild(BlockEntity bearing) throws ReflectiveOperationException {
        Object rawDependencies = publicMethod(bearing, "sable$getConnectionDependencies").invoke(bearing);
        if (!(rawDependencies instanceof Iterable<?>)) {
            fail("Swivel did not expose child dependencies");
        }
        Iterable<?> dependencies = (Iterable<?>) rawDependencies;
        Object child = null;
        int count = 0;
        for (Object dependency : dependencies) {
            if (dependency != null) {
                child = dependency;
                count++;
            }
        }
        if (count != 1 || child == null) {
            fail("expected exactly one rudder child dependency, got " + count);
        }
        return child;
    }

    private static void setVelocity(Object handle, Vector3d desired) throws ReflectiveOperationException {
        Vector3d currentLinear = new Vector3d();
        Vector3d currentAngular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", currentLinear).invoke(handle, currentLinear);
        oneArgMethod(handle, "getAngularVelocity", currentAngular).invoke(handle, currentAngular);
        Vector3d deltaLinear = new Vector3d(desired).sub(currentLinear);
        Vector3d deltaAngular = new Vector3d(currentAngular).negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", deltaLinear, deltaAngular)
                .invoke(handle, deltaLinear, deltaAngular);
    }

    private static Vector3d transformPosition(Object pose, Vector3d value) throws ReflectiveOperationException {
        oneArgMethod(pose, "transformPosition", value).invoke(pose, value);
        return value;
    }

    private static Vector3d transformNormal(Object pose, Vector3d value) throws ReflectiveOperationException {
        oneArgMethod(pose, "transformNormal", value).invoke(pose, value);
        return value;
    }

    private static Quaterniondc quaternion(Object value) {
        if (!(value instanceof Quaterniondc)) {
            fail("expected Quaterniondc, got " + value);
        }
        return (Quaterniondc) value;
    }

    private static Vector3dc vector(Object value) {
        if (!(value instanceof Vector3dc)) {
            fail("expected Vector3dc, got " + value);
        }
        return (Vector3dc) value;
    }

    private static double number(Object value) {
        if (!(value instanceof Number)) {
            fail("expected numeric value, got " + value);
        }
        return ((Number) value).doubleValue();
    }

    private static Method publicMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return target.getClass().getMethod(name, parameterTypes);
    }

    private static Method oneArgMethod(Object target, String name, Object arg) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + " compatible with " + arg.getClass());
    }

    private static Method twoArgMethod(Object target, String name, Object a, Object b) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(a.getClass())
                    && method.getParameterTypes()[1].isAssignableFrom(b.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name + " compatible args");
    }

    private static void assertTrue(String label, boolean value) {
        if (!value) {
            fail(label + ": expected true");
        }
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_YAW_AUTHORITY FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 yaw-authority runtime acceptance failed: " + reason);
    }
}
