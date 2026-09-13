package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side observer for AIRCRAFT-001 v0.20 actual-client evidence.
 *
 * <p>The observer never calls SteeringWheelBlockEntity start/stop methods and never mounts the
 * player. It only watches the state produced by the connected client and performs one bounded
 * test-setup teleport so the real player is within reach of the compiled cockpit.</p>
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
            player.teleportTo(seat.getX() + 0.5, seat.getY() + 1.0, seat.getZ() + 0.5);
            playerPositioned = true;
            LOGGER.log(System.Logger.Level.INFO,
                    "AIRCRAFT_001_V020_PLAYER_POSITIONED pilotSeat=" + seat);
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
