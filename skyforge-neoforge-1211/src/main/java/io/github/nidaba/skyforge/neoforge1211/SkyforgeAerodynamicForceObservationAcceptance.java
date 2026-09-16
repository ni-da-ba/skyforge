package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** PLATFORM-014: bounded exact-stack observation of real Sable aerodynamic DRAG point forces. */
final class SkyforgeAerodynamicForceObservationAcceptance {
    private static final System.Logger LOGGER = System.getLogger(SkyforgeAerodynamicForceObservationAcceptance.class.getName());
    private static final String PREFIX = "COMPILER_PLATFORM_SABLE_AERODYNAMIC_FORCE_OBSERVATION_LIFECYCLE";

    private SkyforgeAerodynamicForceObservationAcceptance() {}

    static void verify(ServerLevel level, Object parentSubLevel, Object childSubLevel, double forwardSpeedMps)
            throws ReflectiveOperationException {
        Object container = requireContainer(level);
        Object physicsSystem = publicMethod(container, "physicsSystem").invoke(container);
        Object parentHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel).invoke(physicsSystem, parentSubLevel);
        Object childHandle = oneArgMethod(physicsSystem, "getPhysicsHandle", childSubLevel).invoke(physicsSystem, childSubLevel);
        require(parentHandle != null && childHandle != null, "FAIL_PHYSICS current parent/child physics handle unavailable");

        Object parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Vector3d flowVelocity = transformNormal(parentPose, new Vector3d(-forwardSpeedMps, 0.0, 0.0));
        setVelocity(parentHandle, flowVelocity);
        setVelocity(childHandle, flowVelocity);

        publicMethod(childSubLevel, "enableIndividualQueuedForcesTracking", boolean.class).invoke(childSubLevel, true);
        List<?> recorded;
        try {
            oneArgMethod(physicsSystem, "tick", container).invoke(physicsSystem, container);
            Class<?> forceGroups = Class.forName("dev.ryanhcode.sable.api.physics.force.ForceGroups");
            Field dragField = forceGroups.getField("DRAG");
            Object dragRegistryObject = dragField.get(null);
            Object dragGroup = dragField.getType().getMethod("get").invoke(dragRegistryObject);
            Object queuedDrag = oneArgMethod(childSubLevel, "getOrCreateQueuedForceGroup", dragGroup).invoke(childSubLevel, dragGroup);
            Object raw = publicMethod(queuedDrag, "getRecordedPointForces").invoke(queuedDrag);
            require(raw instanceof List<?>, "FAIL_PHYSICS DRAG point-force record has unexpected type=" + raw);
            recorded = List.copyOf((List<?>) raw);
        } finally {
            publicMethod(childSubLevel, "enableIndividualQueuedForcesTracking", boolean.class).invoke(childSubLevel, false);
        }
        require(!recorded.isEmpty(), "FAIL_PHYSICS no Sable DRAG point forces under controlled flow");

        Object childPose = publicMethod(childSubLevel, "logicalPose").invoke(childSubLevel);
        Object massTracker = publicMethod(parentSubLevel, "getMassTracker").invoke(parentSubLevel);
        Vector3dc comLocal = vector(publicMethod(massTracker, "getCenterOfMass").invoke(massTracker));
        Vector3d referenceWorld = transformPosition(parentPose, new Vector3d(comLocal));
        Vector3d totalForce = new Vector3d();
        Vector3d totalMoment = new Vector3d();
        List<String> points = new ArrayList<>();
        for (Object pointForce : recorded) {
            Vector3d point = transformPosition(childPose, new Vector3d(vector(publicMethod(pointForce, "point").invoke(pointForce))));
            Vector3d force = transformNormal(childPose, new Vector3d(vector(publicMethod(pointForce, "force").invoke(pointForce))));
            require(finite(point) && finite(force), "FAIL_PHYSICS non-finite DRAG point force");
            totalForce.add(force);
            totalMoment.add(new Vector3d(point).sub(referenceWorld).cross(force, new Vector3d()));
            points.add("p=" + point + ",f=" + force);
        }
        require(finite(totalForce) && totalForce.lengthSquared() > 0.0, "FAIL_PHYSICS aggregate DRAG force is zero/non-finite");
        require(finite(totalMoment), "FAIL_PHYSICS aggregate DRAG moment is non-finite");
        require(totalMoment.lengthSquared() > 0.0, "FAIL_PHYSICS aggregate DRAG moment is zero");
        LOGGER.log(System.Logger.Level.INFO, PREFIX + " PASS forwardSpeedMps=" + forwardSpeedMps
                + " pointCount=" + recorded.size() + " referenceWorld=" + referenceWorld
                + " totalForceWorld=" + totalForce + " totalMomentWorld=" + totalMoment
                + " forceTrackingReleased=true aircraftSignConventionQualified=false points=" + points);
    }

    private static Object requireContainer(ServerLevel level) throws ReflectiveOperationException {
        Class<?> holder = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        require(holder.isInstance(level), "FAIL_PHYSICS ServerLevel lacks Sable container holder");
        Object container = holder.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        require(container != null, "FAIL_PHYSICS Sable container unavailable");
        return container;
    }
    private static void setVelocity(Object handle, Vector3d desired) throws ReflectiveOperationException {
        Vector3d linear = new Vector3d(), angular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", linear).invoke(handle, linear);
        oneArgMethod(handle, "getAngularVelocity", angular).invoke(handle, angular);
        twoArgMethod(handle, "addLinearAndAngularVelocity", desired, angular)
                .invoke(handle, new Vector3d(desired).sub(linear), angular.negate());
    }
    private static Vector3d transformPosition(Object pose, Vector3d v) throws ReflectiveOperationException { oneArgMethod(pose,"transformPosition",v).invoke(pose,v); return v; }
    private static Vector3d transformNormal(Object pose, Vector3d v) throws ReflectiveOperationException { oneArgMethod(pose,"transformNormal",v).invoke(pose,v); return v; }
    private static Vector3dc vector(Object value) { require(value instanceof Vector3dc,"FAIL_PHYSICS expected Vector3dc="+value); return (Vector3dc)value; }
    private static boolean finite(Vector3dc v) { return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z()); }
    private static Method publicMethod(Object target,String name,Class<?>... types) throws NoSuchMethodException { return target.getClass().getMethod(name,types); }
    private static Method oneArgMethod(Object target,String name,Object arg) throws NoSuchMethodException { for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==1&&m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) return m; throw new NoSuchMethodException(target.getClass().getName()+"."+name); }
    private static Method twoArgMethod(Object target,String name,Object a,Object b) throws NoSuchMethodException { for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==2&&m.getParameterTypes()[0].isAssignableFrom(a.getClass())&&m.getParameterTypes()[1].isAssignableFrom(b.getClass())) return m; throw new NoSuchMethodException(target.getClass().getName()+"."+name); }
    private static void require(boolean ok,String reason) { if(!ok){ LOGGER.log(System.Logger.Level.ERROR,PREFIX+" FAIL "+reason); throw new IllegalStateException(PREFIX+" "+reason); } }
}
