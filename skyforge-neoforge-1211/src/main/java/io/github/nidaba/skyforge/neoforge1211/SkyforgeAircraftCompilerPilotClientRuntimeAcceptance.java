package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side observer for AIRCRAFT-001 v0.20 actual-client evidence.
 *
 * <p>The observer never calls SteeringWheelBlockEntity start/stop methods and never mounts the
 * player. Because v0.20 explicitly does not qualify Sable player tracking, it performs bounded
 * test-setup positioning: one initial teleport to establish aircraft tracking, then a temporary
 * server-authoritative seat re-anchor after the wheel release packet and before the real Create
 * seat interaction. The pre-release setup also preserves/enables player invulnerability so vanilla
 * fall damage cannot kill the deliberately-untracked test player before the wheel packet proof;
 * the original invulnerability state is restored before the seat interaction. None of these setup
 * controls are player-tracking evidence.
 */
final class SkyforgeAircraftCompilerPilotClientRuntimeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftCompilerPilotClient";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.class.getName());

    private static volatile Object assembledParentSubLevel;
    private static volatile boolean playerPositioned;
    private static volatile boolean playerInvulnerabilityCaptured;
    private static volatile boolean originalPlayerInvulnerable;
    private static volatile boolean playerInvulnerabilityRestored;
    private static volatile boolean seatSetupReanchorLogged;
    private static volatile int seatDiagnosticTicks;
    private static volatile boolean activePacketObserved;
    private static volatile boolean releasePacketObserved;
    private static volatile boolean seatMountObserved;
    private static volatile boolean seatDismountObserved;
    private static volatile boolean passLogged;
    private static volatile float activeTargetDegrees;

    private SkyforgeAircraftCompilerPilotClientRuntimeAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftCompilerPilotClientRuntimeAcceptance::onServerTick);
    }

    static void bindAssembledParentSubLevel(Object parentSubLevel) {
        assembledParentSubLevel = Objects.requireNonNull(parentSubLevel, "parentSubLevel");
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot =
                SkyforgeAircraftCompilerPilotClientBridge.snapshot();
        if (snapshot == null) {
            return;
        }

        List<ServerPlayer> players = event.getServer().getPlayerList().getPlayers();
        if (players.size() != 1) {
            return;
        }
        ServerPlayer player = players.getFirst();

        if (!playerPositioned) {
            originalPlayerInvulnerable = player.isInvulnerable();
            playerInvulnerabilityCaptured = true;
            player.setInvulnerable(true);
            Vec3 globalStandPosition = positionServerPlayerAtCurrentSeat(player, snapshot);
            playerPositioned = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "AIRCRAFT_001_V020_PLAYER_POSITIONED"
                            + " pilotSeatPlot=" + snapshot.pilotSeatPos()
                            + " globalStand=" + globalStandPosition
                            + " directParentPoseProjection=true"
                            + " testSetupInvulnerable=true"
                            + " originalPlayerInvulnerable=" + originalPlayerInvulnerable
                            + " playerSableTrackingQualified=false");
            return;
        }

        BlockEntity wheel = event.getServer().overworld().getBlockEntity(snapshot.steeringWheelPos());
        if (wheel != null && wheel.getClass().getName().endsWith("SteeringWheelBlockEntity")) {
            boolean held = readBooleanField(wheel, "held");
            float target = readFloatField(wheel, "targetAngleToUpdate");
            double absoluteTarget = Math.abs(target);
            if (!activePacketObserved
                    && held
                    && absoluteTarget >= snapshot.minimumAbsoluteTargetDegrees()
                    && absoluteTarget <= snapshot.maximumAbsoluteTargetDegrees()) {
                activeTargetDegrees = target;
                activePacketObserved = true;
                LOGGER.log(System.Logger.Level.INFO,
                        "AIRCRAFT_001_V020_ACTIVE_PACKET OBSERVED targetDegrees=" + target);
            }
            if (activePacketObserved
                    && !releasePacketObserved
                    && !held
                    && Math.abs(target - activeTargetDegrees) <= 0.01f) {
                releasePacketObserved = true;
                LOGGER.log(System.Logger.Level.INFO,
                        "AIRCRAFT_001_V020_RELEASE_PACKET OBSERVED retainedTargetDegrees=" + target);
            }
        }

        // Fall immunity is test setup only. Restore the player's exact pre-test state as soon as
        // the real Simulated release packet completes the wheel proof, before probing Create seat use.
        if (releasePacketObserved && playerInvulnerabilityCaptured && !playerInvulnerabilityRestored) {
            player.setInvulnerable(originalPlayerInvulnerable);
            playerInvulnerabilityRestored = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "AIRCRAFT_001_V020_PLAYER_INVULNERABILITY_RESTORED"
                            + " invulnerable=" + originalPlayerInvulnerable
                            + " beforeSeatInteraction=true");
        }

        // The SteeringWheelPacket itself has no player-distance gate, but Create's ordinary seat
        // interaction does. Once the real release packet has completed the wheel proof, keep the
        // ServerPlayer at the server-authoritative current seat pose until the genuine seat use
        // mounts it. This is bounded test setup and must not be reported as Sable tracking.
        if (releasePacketObserved && !seatMountObserved && !player.isPassenger()) {
            Vec3 globalStandPosition = positionServerPlayerAtCurrentSeat(player, snapshot);
            if (!seatSetupReanchorLogged) {
                seatSetupReanchorLogged = true;
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "AIRCRAFT_001_V020_SEAT_SETUP_REANCHOR"
                                + " globalStand=" + globalStandPosition
                                + " playerSableTrackingQualified=false");
            }
        }

        // Diagnostic-only observation of the ordinary Create seat boundary. This deliberately does
        // not invoke SeatBlock, create SeatEntity, rewrite packet coordinates, or mount the player.
        // Sampling after the real wheel release distinguishes a rejected/misrouted vanilla use
        // packet from a Create seat mount that occurred but failed to synchronize back to the client.
        if (releasePacketObserved && !seatMountObserved) {
            seatDiagnosticTicks++;
            if (seatDiagnosticTicks == 5 || seatDiagnosticTicks == 20) {
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "AIRCRAFT_001_V020_SEAT_BOUNDARY_DIAGNOSTIC"
                                + " ticksAfterRelease=" + seatDiagnosticTicks
                                + " " + seatBoundaryDiagnostics(player, snapshot));
            }
        }

        Entity vehicle = player.getVehicle();
        if (!seatMountObserved
                && player.isPassenger()
                && vehicle != null
                && vehicle.getClass().getName().endsWith("SeatEntity")) {
            seatMountObserved = true;
            LOGGER.log(System.Logger.Level.INFO,
                    "AIRCRAFT_001_V020_SEAT_MOUNT OBSERVED vehicle=" + vehicle.getClass().getName());
        }
        if (seatMountObserved && !seatDismountObserved && !player.isPassenger()) {
            seatDismountObserved = true;
            LOGGER.log(System.Logger.Level.INFO, "AIRCRAFT_001_V020_SEAT_DISMOUNT OBSERVED");
        }

        if (!passLogged && activePacketObserved && releasePacketObserved && seatMountObserved && seatDismountObserved) {
            passLogged = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "AIRCRAFT_001_RUNTIME_PILOT_CLIENT_SERVER_OBSERVER PASS"
                            + " activePacketRoundTrip=true"
                            + " releasePacketRoundTrip=true"
                            + " pilotSeatMount=true"
                            + " pilotSeatDismount=true"
                            + " activeTargetDegrees=" + activeTargetDegrees
                            + " testSetupSeatReanchor=true"
                            + " testSetupInvulnerabilityRestored=" + playerInvulnerabilityRestored
                            + " playerSableTrackingQualified=false"
                            + " flightQualified=false");
        }
    }

    static boolean playerPositioned() {
        return playerPositioned;
    }

    static boolean activePacketObserved() {
        return activePacketObserved;
    }

    static boolean releasePacketObserved() {
        return releasePacketObserved;
    }

    static boolean seatMountObserved() {
        return seatMountObserved;
    }

    static boolean seatDismountObserved() {
        return seatDismountObserved;
    }

    static float activeTargetDegrees() {
        return activeTargetDegrees;
    }

    private static Vec3 positionServerPlayerAtCurrentSeat(
            ServerPlayer player,
            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot) {
        var seat = snapshot.pilotSeatPos();
        Vec3 plotStandPosition = new Vec3(seat.getX() + 0.5, seat.getY() + 1.0, seat.getZ() + 0.5);
        Vec3 globalStandPosition = projectThroughBoundParent(plotStandPosition);
        player.teleportTo(globalStandPosition.x, globalStandPosition.y, globalStandPosition.z);
        player.setDeltaMovement(Vec3.ZERO);
        return globalStandPosition;
    }

    private static String seatBoundaryDiagnostics(
            ServerPlayer player,
            SkyforgeAircraftCompilerPilotClientBridge.Snapshot snapshot) {
        BlockPos seat = snapshot.pilotSeatPos();
        Vec3 plotCenter = Vec3.atCenterOf(seat);
        Vec3 globalCenter = projectThroughBoundParent(plotCenter);
        Object parentSubLevel = assembledParentSubLevel;
        if (parentSubLevel == null) {
            return "parentSubLevel=<null>";
        }

        try {
            Method getBlockState = parentSubLevel.getClass().getMethod("getBlockState", BlockPos.class);
            Object parentSeatState = getBlockState.invoke(parentSubLevel, seat);

            Method getEntities = parentSubLevel.getClass().getMethod("getEntities", Entity.class, AABB.class);
            Object entitiesValue = getEntities.invoke(parentSubLevel, new Object[] {null, new AABB(seat)});
            StringBuilder entityClasses = new StringBuilder();
            int seatEntityCount = 0;
            int totalEntityCount = 0;
            if (entitiesValue instanceof List<?> entities) {
                totalEntityCount = entities.size();
                for (Object entity : entities) {
                    if (entity == null) {
                        continue;
                    }
                    if (!entityClasses.isEmpty()) {
                        entityClasses.append(',');
                    }
                    String className = entity.getClass().getName();
                    entityClasses.append(className);
                    if (className.endsWith("SeatEntity")) {
                        seatEntityCount++;
                    }
                }
            }

            return "parentClass=" + parentSubLevel.getClass().getName()
                    + " parentSeatState=" + parentSeatState
                    + " overworldSeatState=" + player.serverLevel().getBlockState(seat)
                    + " parentSeatEntityCount=" + seatEntityCount
                    + " parentCellEntityCount=" + totalEntityCount
                    + " parentCellEntityClasses=[" + entityClasses + "]"
                    + " playerPassenger=" + player.isPassenger()
                    + " playerVehicle=" + (player.getVehicle() == null ? "<null>" : player.getVehicle().getClass().getName())
                    + " playerPosition=" + player.position()
                    + " plotSeatCenter=" + plotCenter
                    + " globalSeatCenter=" + globalCenter
                    + " plotDistanceSquared=" + player.distanceToSqr(plotCenter)
                    + " globalDistanceSquared=" + player.distanceToSqr(globalCenter);
        } catch (ReflectiveOperationException failure) {
            return "diagnosticReflectionFailure=" + failure
                    + " parentClass=" + parentSubLevel.getClass().getName()
                    + " playerPosition=" + player.position()
                    + " plotSeatCenter=" + plotCenter
                    + " globalSeatCenter=" + globalCenter
                    + " plotDistanceSquared=" + player.distanceToSqr(plotCenter)
                    + " globalDistanceSquared=" + player.distanceToSqr(globalCenter);
        }
    }

    private static Vec3 projectThroughBoundParent(Vec3 plotPosition) {
        Object parentSubLevel = assembledParentSubLevel;
        if (parentSubLevel == null) {
            throw new IllegalStateException("AIRCRAFT-001 assembled parent sub-level was not bound before player positioning");
        }
        try {
            Object pose = parentSubLevel.getClass().getMethod("logicalPose").invoke(parentSubLevel);
            Method transform = pose.getClass().getMethod("transformPosition", Vec3.class);
            Object projected = transform.invoke(pose, plotPosition);
            if (!(projected instanceof Vec3 globalPosition)) {
                throw new IllegalStateException("Sable parent logicalPose transformPosition returned " + projected);
            }
            return globalPosition;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not project AIRCRAFT-001 plot position through bound parent pose", failure);
        }
    }

    private static boolean readBooleanField(Object target, String name) {
        try {
            Field field = target.getClass().getField(name);
            return field.getBoolean(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not read public boolean field " + name + " from " + target.getClass(), failure);
        }
    }

    private static float readFloatField(Object target, String name) {
        try {
            Field field = target.getClass().getField(name);
            return field.getFloat(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not read public float field " + name + " from " + target.getClass(), failure);
        }
    }
}
