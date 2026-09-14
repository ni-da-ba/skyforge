package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side observer for AIRCRAFT-001 v0.20 actual-client evidence.
 *
 * <p>The observer never calls SteeringWheelBlockEntity start/stop methods and never mounts the
 * player. It only watches the state produced by the connected client and performs one bounded
 * test-setup teleport so the real player is within reach of the compiled cockpit.
 */
final class SkyforgeAircraftCompilerPilotClientRuntimeAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftCompilerPilotClient";
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeAircraftCompilerPilotClientRuntimeAcceptance.class.getName());

    private static volatile boolean playerPositioned;
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
            var seat = snapshot.pilotSeatPos();
            Vec3 plotStandPosition = new Vec3(seat.getX() + 0.5, seat.getY() + 1.0, seat.getZ() + 0.5);
            Vec3 globalStandPosition = projectOutOfSubLevel(player.level(), plotStandPosition);
            player.teleportTo(globalStandPosition.x, globalStandPosition.y, globalStandPosition.z);
            playerPositioned = true;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "AIRCRAFT_001_V020_PLAYER_POSITIONED"
                            + " pilotSeatPlot=" + seat
                            + " globalStand=" + globalStandPosition);
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

    private static Vec3 projectOutOfSubLevel(Level level, Vec3 plotPosition) {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            Object helper = sable.getField("HELPER").get(null);
            Method method = helper.getClass().getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            Object projected = method.invoke(helper, level, plotPosition);
            if (!(projected instanceof Vec3 globalPosition)) {
                throw new IllegalStateException("Sable projectOutOfSubLevel returned " + projected);
            }
            return globalPosition;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not project AIRCRAFT-001 plot position into Sable global space", failure);
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
