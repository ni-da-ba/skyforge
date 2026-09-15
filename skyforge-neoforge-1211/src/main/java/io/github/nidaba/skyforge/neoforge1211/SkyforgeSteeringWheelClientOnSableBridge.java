package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** In-process test handoff between PLATFORM-006 integrated server and actual client. */
final class SkyforgeSteeringWheelClientOnSableBridge {
    private static volatile Snapshot snapshot;

    private SkyforgeSteeringWheelClientOnSableBridge() {}

    static void publish(
            UUID bodyId,
            BlockPos steeringWheelPos,
            BlockPos endpointPos,
            Vec3 standPlotPosition,
            Vec3 expectedGlobalWheelCenter,
            double mouseYawDelta,
            float minimumAbsoluteTargetDegrees,
            float maximumAbsoluteTargetDegrees) {
        snapshot = new Snapshot(
                Objects.requireNonNull(bodyId, "bodyId"),
                Objects.requireNonNull(steeringWheelPos, "steeringWheelPos").immutable(),
                Objects.requireNonNull(endpointPos, "endpointPos").immutable(),
                Objects.requireNonNull(standPlotPosition, "standPlotPosition"),
                Objects.requireNonNull(expectedGlobalWheelCenter, "expectedGlobalWheelCenter"),
                mouseYawDelta,
                minimumAbsoluteTargetDegrees,
                maximumAbsoluteTargetDegrees);
    }
    static Snapshot snapshot() {
        return snapshot;
    }

    record Snapshot(
            UUID bodyId,
            BlockPos steeringWheelPos,
            BlockPos endpointPos,
            Vec3 standPlotPosition,
            Vec3 expectedGlobalWheelCenter,
            double mouseYawDelta,
            float minimumAbsoluteTargetDegrees,
            float maximumAbsoluteTargetDegrees) {}
}
