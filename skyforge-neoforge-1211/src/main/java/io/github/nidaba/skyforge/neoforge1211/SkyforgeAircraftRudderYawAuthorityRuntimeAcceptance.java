package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** AIRCRAFT-RUNTIME-004: aircraft-local sign/nonzero qualification over PLATFORM-014 force observation. */
final class SkyforgeAircraftRudderYawAuthorityRuntimeAcceptance {
    private static final System.Logger LOGGER = System.getLogger(SkyforgeAircraftRudderYawAuthorityRuntimeAcceptance.class.getName());
    private static final String PREFIX = "AIRCRAFT_V0131_RUDDER_YAW_AUTHORITY_RUNTIME";
    static final double FORWARD_SPEED_MPS = 10.0;
    static final int PHYSICAL_SETTLE_TICKS = 80;
    static final double MINIMUM_PHYSICAL_DEFLECTION_DEGREES = 5.0;
    static final double MINIMUM_LATERAL_FORCE_MAGNITUDE = 1.0e-4;
    static final double MINIMUM_YAW_MOMENT_MAGNITUDE = 1.0e-4;

    private SkyforgeAircraftRudderYawAuthorityRuntimeAcceptance() {}

    static Result verify(ServerLevel level, Object parentSubLevel, BlockEntity bearing)
            throws ReflectiveOperationException {
        Object child = SkyforgeAircraftRudderActuationRuntimeAcceptance.currentChild(bearing);
        UUID parentId = uuid(parentSubLevel);
        UUID childId = uuid(child);
        settlePhysics(level, PHYSICAL_SETTLE_TICKS);
        Object currentChild = SkyforgeAircraftRudderActuationRuntimeAcceptance.currentChild(bearing);
        UUID currentChildId = uuid(currentChild);
        require(childId.equals(currentChildId), "FAIL_CHILD_ASSEMBLY rudder child identity changed during physical settle");

        double targetDegrees = number(publicMethod(bearing, "getTargetAngleDegrees").invoke(bearing));
        double physicalYawDegrees = SkyforgeAircraftRudderActuationRuntimeAcceptance.relativeYawDegrees(parentSubLevel, currentChild);
        require(Double.isFinite(physicalYawDegrees), "FAIL_PHYSICS physical rudder yaw is non-finite");
        require(Math.abs(physicalYawDegrees) >= MINIMUM_PHYSICAL_DEFLECTION_DEGREES,
                "FAIL_PHYSICS physical rudder deflection below meaningful floor: " + physicalYawDegrees);

        SkyforgeAerodynamicForceObservationAcceptance.Observation observation =
                SkyforgeAerodynamicForceObservationAcceptance.observe(level, parentSubLevel, currentChild, FORWARD_SPEED_MPS);
        Object parentPose = publicMethod(parentSubLevel, "logicalPose").invoke(parentSubLevel);
        Quaterniondc parentOrientation = quaternion(publicMethod(parentPose, "orientation").invoke(parentPose));
        Quaterniond worldToAircraft = new Quaterniond(parentOrientation).conjugate();
        Vector3d forceAircraft = worldToAircraft.transform(new Vector3d(observation.totalForceWorld()));
        Vector3d momentAircraft = worldToAircraft.transform(new Vector3d(observation.totalMomentWorld()));
        Vector3d flowAircraft = worldToAircraft.transform(new Vector3d(observation.flowVelocityWorld()));
        double lateralForceZ = forceAircraft.z;
        double yawMomentY = momentAircraft.y;

        require(finite(forceAircraft) && Double.isFinite(lateralForceZ), "FAIL_PHYSICS rudder lateral force is non-finite");
        require(Math.abs(lateralForceZ) >= MINIMUM_LATERAL_FORCE_MAGNITUDE,
                "FAIL_PHYSICS rudder lateral force below numerical floor: " + lateralForceZ);
        require(finite(momentAircraft) && Double.isFinite(yawMomentY), "FAIL_PHYSICS rudder yaw moment is non-finite");
        require(Math.abs(yawMomentY) >= MINIMUM_YAW_MOMENT_MAGNITUDE,
                "FAIL_PHYSICS rudder yaw moment below numerical floor: " + yawMomentY);
        require(Math.signum(yawMomentY) == -Math.signum(physicalYawDegrees),
                "FAIL_PHYSICS aft-rudder yaw moment must oppose measured rudder-body yaw sign: physical="
                        + physicalYawDegrees + " yawMomentY=" + yawMomentY);

        Object massTracker = publicMethod(parentSubLevel, "getMassTracker").invoke(parentSubLevel);
        Object comLocal = publicMethod(massTracker, "getCenterOfMass").invoke(massTracker);
        LOGGER.log(System.Logger.Level.INFO, PREFIX + " PASS capability=AIRCRAFT_V0131_RUDDER_YAW_AUTHORITY_RUNTIME"
                + " parentId=" + parentId + " childId=" + childId
                + " targetDegrees=" + targetDegrees + " physicalYawDegrees=" + physicalYawDegrees
                + " forwardSpeedMps=" + FORWARD_SPEED_MPS + " flowAircraftLocal=" + flowAircraft
                + " parentComLocal=" + comLocal + " parentComWorld=" + observation.referenceWorld()
                + " dragPointCount=" + observation.pointForces().size()
                + " dragPointsWorld=" + observation.pointForces()
                + " totalForceWorld=" + observation.totalForceWorld() + " totalMomentWorld=" + observation.totalMomentWorld()
                + " totalForceAircraft=" + forceAircraft + " totalMomentAircraft=" + momentAircraft
                + " lateralForceZ=" + lateralForceZ + " yawMomentY=" + yawMomentY
                + " lateralFloor=" + MINIMUM_LATERAL_FORCE_MAGNITUDE + " yawMomentFloor=" + MINIMUM_YAW_MOMENT_MAGNITUDE
                + " signRule=yawMomentY_opposes_physicalYaw"
                + " aerodynamicModelFitted=false analyticalAuthorityIndependent=true"
                + " atmosphereSweepQualified=false handlingQualified=false flightQualified=false");
        return new Result(targetDegrees, physicalYawDegrees, lateralForceZ, yawMomentY, childId);
    }

    record Result(double targetDegrees, double physicalYawDegrees, double lateralForceZ, double yawMomentY, UUID childId) {}

    private static void settlePhysics(ServerLevel level, int ticks) throws ReflectiveOperationException {
        Class<?> holder = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object container = holder.getDeclaredMethod("sable$getPlotContainer").invoke(level);
        Object physics = container.getClass().getDeclaredMethod("physicsSystem").invoke(container);
        Method tick = oneArgMethod(physics, "tick", container);
        for (int i = 0; i < ticks; i++) tick.invoke(physics, container);
    }
    private static UUID uuid(Object subLevel) throws ReflectiveOperationException {
        Object value = publicMethod(subLevel, "getUniqueId").invoke(subLevel);
        require(value instanceof UUID, "FAIL_PHYSICS Sable sub-level ID is not UUID: " + value);
        return (UUID) value;
    }
    private static Quaterniondc quaternion(Object value) { require(value instanceof Quaterniondc, "FAIL_PHYSICS expected Quaterniondc=" + value); return (Quaterniondc)value; }
    private static double number(Object value) { require(value instanceof Number, "FAIL_PHYSICS expected number=" + value); return ((Number)value).doubleValue(); }
    private static boolean finite(Vector3d v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Method publicMethod(Object target,String name,Class<?>... types) throws NoSuchMethodException { return target.getClass().getMethod(name,types); }
    private static Method oneArgMethod(Object target,String name,Object arg) throws NoSuchMethodException { for(Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==1&&m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) return m; throw new NoSuchMethodException(target.getClass().getName()+"."+name); }
    private static void require(boolean ok, String reason) { if (!ok) { LOGGER.log(System.Logger.Level.ERROR, PREFIX + " FAIL " + reason); throw new IllegalStateException(PREFIX + " " + reason); } }
}
