package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short ordering window used by the Sable 2.0.5 compatibility shim.
 *
 * <p>Sable sends a newly tracked sub-level's full-sync bundle over the ordered Minecraft
 * connection, but can send its same-tick movement snapshot over UDP. Keeping snapshots on the
 * ordered connection briefly after full sync prevents movement from overtaking creation/finalize
 * without disabling Sable's steady-state UDP transport.
 */
public final class SkyforgeSableFullSyncOrdering {
    public static final long ORDERED_SNAPSHOT_WINDOW_TICKS = 10L;

    private static final Map<UUID, Long> LAST_FULL_SYNC_TICK = new ConcurrentHashMap<>();

    private SkyforgeSableFullSyncOrdering() {}

    public static void recordFullSync(UUID playerId, long serverTick) {
        LAST_FULL_SYNC_TICK.entrySet().removeIf(
                entry -> !withinWindow(serverTick, entry.getValue(), ORDERED_SNAPSHOT_WINDOW_TICKS));
        LAST_FULL_SYNC_TICK.put(playerId, serverTick);
    }

    public static boolean needsOrderedSnapshots(UUID playerId, long serverTick) {
        Long syncTick = LAST_FULL_SYNC_TICK.get(playerId);
        return syncTick != null
                && withinWindow(serverTick, syncTick, ORDERED_SNAPSHOT_WINDOW_TICKS);
    }

    static boolean withinWindow(long nowTick, long syncTick, long windowTicks) {
        long elapsed = nowTick - syncTick;
        return elapsed >= 0L && elapsed <= windowTicks;
    }
}
