package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** In-process handoff between the PLATFORM-011 integrated server and actual client. */
final class SkyforgeSeatPassengerOnSableBridge {
    private static volatile Snapshot snapshot;

    private SkyforgeSeatPassengerOnSableBridge() {}

    static void publish(
            UUID bodyId,
            BlockPos seatPos,
            Vec3 standPlotPosition,
            Vec3 expectedGlobalSeatCenter) {
        snapshot = new Snapshot(
                Objects.requireNonNull(bodyId, "bodyId"),
                Objects.requireNonNull(seatPos, "seatPos").immutable(),
                Objects.requireNonNull(standPlotPosition, "standPlotPosition"),
                Objects.requireNonNull(expectedGlobalSeatCenter, "expectedGlobalSeatCenter"));
    }

    static Snapshot snapshot() {
        return snapshot;
    }

    record Snapshot(
            UUID bodyId,
            BlockPos seatPos,
            Vec3 standPlotPosition,
            Vec3 expectedGlobalSeatCenter) {}
}
