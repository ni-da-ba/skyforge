package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.core.BlockPos;

/**
 * Development-only in-process handoff between the exact-stack server assembly proof and the
 * integrated actual-client acceptance. The bridge carries test coordinates/configuration only;
 * it never mutates Simulated control state or player occupancy.
 */
final class SkyforgeAircraftCompilerPilotClientBridge {
    private static volatile Snapshot snapshot;

    private SkyforgeAircraftCompilerPilotClientBridge() {}

    static void publish(
            BlockPos pilotSeatPos,
            BlockPos steeringWheelPos,
            double mouseYawDelta,
            double minimumAbsoluteTargetDegrees,
            double maximumAbsoluteTargetDegrees,
            int activePacketSettleTicks,
            int releasePacketSettleTicks,
            int seatMountSettleTicks,
            int seatDismountSettleTicks) {
        snapshot = new Snapshot(
                Objects.requireNonNull(pilotSeatPos, "pilotSeatPos").immutable(),
                Objects.requireNonNull(steeringWheelPos, "steeringWheelPos").immutable(),
                mouseYawDelta,
                minimumAbsoluteTargetDegrees,
                maximumAbsoluteTargetDegrees,
                activePacketSettleTicks,
                releasePacketSettleTicks,
                seatMountSettleTicks,
                seatDismountSettleTicks);
    }

    static Snapshot snapshot() {
        return snapshot;
    }

    record Snapshot(
            BlockPos pilotSeatPos,
            BlockPos steeringWheelPos,
            double mouseYawDelta,
            double minimumAbsoluteTargetDegrees,
            double maximumAbsoluteTargetDegrees,
            int activePacketSettleTicks,
            int releasePacketSettleTicks,
            int seatMountSettleTicks,
            int seatDismountSettleTicks) {}
}
