package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** AIRCRAFT-001 v0.21: natural Sable player tracking and inherited parent translation proof. */
final class SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftCompilerPilotTracking";

    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance.class.getName());

    private static volatile Object assembledParentSubLevel;
    private static volatile boolean configured;
    private static volatile boolean listenerInstalled;
    private static volatile boolean clientMeasurementArmed;
    private static volatile boolean measurementStarted;
    private static volatile boolean passLogged;
    private static volatile int trackingAcquireTicks;
    private static volatile int motionTicks;
    private static volatile double translationVelocityMetersPerSecond;
    private static volatile double minimumParentTranslationBlocks;
    private static volatile double serverPlayerDeltaToleranceBlocks;
    private static volatile int trackingAcquireSettleTicks;
    private static volatile int translationSettleTicks;
    private static volatile double startParentX;
    private static volatile double startPlayerX;
    private static volatile double measuredParentDeltaX;
    private static volatile double measuredPlayerDeltaX;
    private static volatile PhysicsContext physicsContext;

    private SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance() {}

    static void bindAssembledParentSubLevel(Object parentSubLevel) {
        assembledParentSubLevel = Objects.requireNonNull(parentSubLevel, "parentSubLevel");
    }

    static void configure(
            double translationVelocity,
            double minimumTranslation,
            double serverDeltaTolerance,
            int acquireSettleTicks,
            int motionSettleTicks) {
        if (!Double.isFinite(translationVelocity) || Math.abs(translationVelocity) <= 0.0) {
            throw new IllegalArgumentException("v0.21 translation velocity must be finite and nonzero");
        }
        if (!Double.isFinite(minimumTranslation) || minimumTranslation <= 0.0) {
            throw new IllegalArgumentException("v0.21 minimum translation must be finite and positive");
        }
        if (!Double.isFinite(serverDeltaTolerance) || serverDeltaTolerance <= 0.0) {
            throw new IllegalArgumentException("v0.21 server/player delta tolerance must be finite and positive");
        }
        if (acquireSettleTicks < 1 || motionSettleTicks < 1) {
            throw new IllegalArgumentException("v0.21 settle windows must be positive");
        }
        translationVelocityMetersPerSecond = translationVelocity;
        minimumParentTranslationBlocks = minimumTranslation;
        serverPlayerDeltaToleranceBlocks = serverDeltaTolerance;
        trackingAcquireSettleTicks = acquireSettleTicks;
        translationSettleTicks = motionSettleTicks;
        configured = true;
    }

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || listenerInstalled) {
            return;
        }
        listenerInstalled = true;
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftCompilerPilotTrackingRuntimeAcceptance::onServerTick);
    }

    static void armClientMeasurement() {
        clientMeasurementArmed = true;
    }

    static boolean passed() {
        return passLogged;
    }

    static int clientSettleTicks() {
        return trackingAcquireSettleTicks + translationSettleTicks + 20;
    }

    static double measuredParentDeltaX() {
        return measuredParentDeltaX;
    }

    static double measuredPlayerDeltaX() {
        return measuredPlayerDeltaX;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || !configured
                || assembledParentSubLevel == null
                || !clientMeasurementArmed
                || passLogged
                || !SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.seatDismountObserved()) {
            return;
        }

        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        if (players.size() != 1) {
            return;
        }
        ServerPlayer player = players.getFirst();

        try {
            Object trackingSubLevel = trackingSubLevel(player);
            if (!measurementStarted) {
                trackingAcquireTicks++;
                if (trackingSubLevel != assembledParentSubLevel) {
                    if (trackingAcquireTicks > trackingAcquireSettleTicks) {
                        fail("natural Sable tracking did not resolve to the assembled parent within "
                                + trackingAcquireSettleTicks + " ticks; observed=" + className(trackingSubLevel));
                    }
                    return;
                }
                if (player.isPassenger()) {
                    fail("v0.21 measurement must begin after ordinary seat dismount");
                }

                startParentX = parentPositionX(assembledParentSubLevel);
                startPlayerX = player.getX();
                physicsContext = preparePhysics(player.serverLevel(), assembledParentSubLevel);
                setLinearVelocityX(physicsContext.handle(), translationVelocityMetersPerSecond);
                measurementStarted = true;
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "AIRCRAFT_001_V021_TRACKING_ACQUIRED"
                                + " naturalSeatVehicleAcquisition=true"
                                + " trackingParentIdentity=true"
                                + " afterDismount=true"
                                + " harnessTrackingSetterInvoked=false"
                                + " harnessPlayerMutationDuringMeasurement=false"
                                + " startParentX=" + startParentX
                                + " startPlayerX=" + startPlayerX
                                + " commandedParentVelocityX=" + translationVelocityMetersPerSecond);
                return;
            }

            motionTicks++;
            if (trackingSubLevel != assembledParentSubLevel) {
                cleanupPhysics();
                fail("Sable player tracking left the assembled parent during the measured translation");
            }
            if (player.isPassenger()) {
                cleanupPhysics();
                fail("player became a passenger during the post-dismount tracking measurement");
            }

            measuredParentDeltaX = parentPositionX(assembledParentSubLevel) - startParentX;
            measuredPlayerDeltaX = player.getX() - startPlayerX;
            if (Math.abs(measuredParentDeltaX) >= minimumParentTranslationBlocks) {
                cleanupPhysics();
                double deltaError = Math.abs(measuredPlayerDeltaX - measuredParentDeltaX);
                if (Math.signum(measuredPlayerDeltaX) != Math.signum(measuredParentDeltaX)) {
                    fail("player inherited translation sign differs from parent: parentDeltaX="
                            + measuredParentDeltaX + " playerDeltaX=" + measuredPlayerDeltaX);
                }
                if (deltaError > serverPlayerDeltaToleranceBlocks) {
                    fail("player did not inherit parent translation within tolerance: parentDeltaX="
                            + measuredParentDeltaX + " playerDeltaX=" + measuredPlayerDeltaX
                            + " error=" + deltaError
                            + " tolerance=" + serverPlayerDeltaToleranceBlocks);
                }
                passLogged = true;
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "AIRCRAFT_001_RUNTIME_PILOT_TRACKING PASS"
                                + " naturalTrackingAcquired=true"
                                + " trackingParentIdentity=true"
                                + " postDismountMeasurement=true"
                                + " parentTranslationApplied=true"
                                + " parentDeltaX=" + measuredParentDeltaX
                                + " playerDeltaX=" + measuredPlayerDeltaX
                                + " deltaError=" + deltaError
                                + " tolerance=" + serverPlayerDeltaToleranceBlocks
                                + " harnessTrackingSetterInvoked=false"
                                + " harnessPlayerMutationDuringMeasurement=false"
                                + " playerSableTrackingQualified=true"
                                + " inheritedParentTranslationQualified=true"
                                + " completedControlsPersistenceQualified=false"
                                + " pitchRollQualified=false"
                                + " flightQualified=false");
                return;
            }

            if (motionTicks > translationSettleTicks) {
                cleanupPhysics();
                fail("assembled parent did not reach the bounded translation threshold within "
                        + translationSettleTicks + " ticks; parentDeltaX=" + measuredParentDeltaX);
            }
        } catch (ReflectiveOperationException failure) {
            cleanupPhysicsQuietly();
            fail("v0.21 exact-stack reflection failed: " + failure);
        }
    }

    private static Object trackingSubLevel(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> sableClass = Class.forName("dev.ryanhcode.sable.Sable");
        Object helper = sableClass.getField("HELPER").get(null);
        Method method = helper.getClass().getMethod("getTrackingSubLevel", Entity.class);
        return method.invoke(helper, player);
    }

    private static double parentPositionX(Object parentSubLevel) throws ReflectiveOperationException {
        Object pose = parentSubLevel.getClass().getMethod("logicalPose").invoke(parentSubLevel);
        Object value = pose.getClass().getMethod("position").invoke(pose);
        if (!(value instanceof Vector3dc position)) {
            throw new IllegalStateException("Sable logical pose position is not Vector3dc: " + className(value));
        }
        return position.x();
    }

    private static PhysicsContext preparePhysics(ServerLevel level, Object parentSubLevel)
            throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder");
        Object container = holderClass.getMethod("sable$getPlotContainer").invoke(level);
        if (container == null) {
            throw new IllegalStateException("Sable ServerSubLevelContainer unavailable for v0.21");
        }
        Object physicsSystem = container.getClass().getMethod("physicsSystem").invoke(container);
        boolean wasPaused = (boolean) physicsSystem.getClass().getMethod("getPaused").invoke(physicsSystem);
        if (wasPaused) {
            physicsSystem.getClass().getMethod("setPaused", boolean.class).invoke(physicsSystem, false);
        }
        Object pipeline = physicsSystem.getClass().getMethod("getPipeline").invoke(physicsSystem);
        oneArgMethod(pipeline, "wakeUp", parentSubLevel).invoke(pipeline, parentSubLevel);
        Object handle = oneArgMethod(physicsSystem, "getPhysicsHandle", parentSubLevel)
                .invoke(physicsSystem, parentSubLevel);
        if (handle == null) {
            throw new IllegalStateException("Sable physics handle unavailable for assembled parent");
        }
        return new PhysicsContext(physicsSystem, handle, wasPaused);
    }

    private static void setLinearVelocityX(Object handle, double targetX) throws ReflectiveOperationException {
        Vector3d currentLinear = new Vector3d();
        Vector3d currentAngular = new Vector3d();
        oneArgMethod(handle, "getLinearVelocity", currentLinear).invoke(handle, currentLinear);
        oneArgMethod(handle, "getAngularVelocity", currentAngular).invoke(handle, currentAngular);
        Vector3d linearDelta = new Vector3d(
                targetX - currentLinear.x(),
                -currentLinear.y(),
                -currentLinear.z());
        Vector3d angularDelta = new Vector3d(currentAngular).negate();
        twoArgMethod(handle, "addLinearAndAngularVelocity", linearDelta, angularDelta)
                .invoke(handle, linearDelta, angularDelta);
    }

    private static void cleanupPhysics() throws ReflectiveOperationException {
        PhysicsContext context = physicsContext;
        if (context == null) {
            return;
        }
        setLinearVelocityX(context.handle(), 0.0);
        if (context.wasPaused()) {
            context.physicsSystem().getClass().getMethod("setPaused", boolean.class)
                    .invoke(context.physicsSystem(), true);
        }
        physicsContext = null;
    }

    private static void cleanupPhysicsQuietly() {
        try {
            cleanupPhysics();
        } catch (ReflectiveOperationException ignored) {
            // The original acceptance failure remains the authoritative diagnostic.
        }
    }

    private static Method oneArgMethod(Object target, String name, Object argument) throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name)
                    && method.getParameterCount() == 1
                    && (argument == null || method.getParameterTypes()[0].isAssignableFrom(argument.getClass()))) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(1 arg)");
    }

    private static Method twoArgMethod(Object target, String name, Object first, Object second)
            throws NoSuchMethodException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if ((first == null || types[0].isAssignableFrom(first.getClass()))
                    && (second == null || types[1].isAssignableFrom(second.getClass()))) {
                return method;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name + "(2 args)");
    }

    private static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "AIRCRAFT_001_RUNTIME_PILOT_TRACKING FAIL " + reason);
        throw new IllegalStateException("AIRCRAFT-001 v0.21 pilot tracking acceptance failed: " + reason);
    }

    private record PhysicsContext(Object physicsSystem, Object handle, boolean wasPaused) {}
}
