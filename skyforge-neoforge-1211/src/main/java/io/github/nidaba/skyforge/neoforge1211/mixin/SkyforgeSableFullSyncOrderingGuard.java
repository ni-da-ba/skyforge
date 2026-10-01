package io.github.nidaba.skyforge.neoforge1211.mixin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps Sable movement snapshots on its ordered connection briefly after a full sublevel sync.
 *
 * <p>Sable 2.0.5 sends a new sublevel's full StartTracking/chunk/Finalize bundle over the ordered
 * Minecraft connection, then may send that same tick's first movement snapshot over its UDP path.
 * The UDP snapshot can arrive before the client has registered the sublevel. This bounded guard is
 * keyed by player and server tick so only freshly full-synced players take Sable's existing TCP
 * fallback; ordinary steady-state movement remains on UDP.
 */
final class SkyforgeSableFullSyncOrderingGuard {
    static final long ORDERED_SNAPSHOT_WINDOW_TICKS = 10L;

    private static final Map<UUID, Long> LAST_FULL_SYNC_TICK = new ConcurrentHashMap<>();

    private SkyforgeSableFullSyncOrderingGuard() {}

    static void recordFullSync(UUID playerId, long serverTick) {
        LAST_FULL_SYNC_TICK.entrySet().removeIf(
                entry -> !withinWindow(serverTick, entry.getValue(), ORDERED_SNAPSHOT_WINDOW_TICKS));
        LAST_FULL_SYNC_TICK.put(playerId, serverTick);
    }

    static boolean needsOrderedSnapshots(UUID playerId, long serverTick) {
        Long fullSyncTick = LAST_FULL_SYNC_TICK.get(playerId);
        return fullSyncTick != null
                && withinWindow(serverTick, fullSyncTick, ORDERED_SNAPSHOT_WINDOW_TICKS);
    }

    static boolean withinWindow(long currentTick, long fullSyncTick, long windowTicks) {
        long elapsed = currentTick - fullSyncTick;
        return elapsed >= 0L && elapsed <= windowTicks;
    }

    static void clearForTests() {
        LAST_FULL_SYNC_TICK.clear();
    }
}
