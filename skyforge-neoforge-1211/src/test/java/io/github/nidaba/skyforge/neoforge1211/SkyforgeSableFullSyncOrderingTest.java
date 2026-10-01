package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SkyforgeSableFullSyncOrderingTest {
    @Test
    void orderedWindowIncludesSyncTickAndTenFollowingTicks() {
        assertTrue(SkyforgeSableFullSyncOrdering.withinWindow(100L, 100L, 10L));
        assertTrue(SkyforgeSableFullSyncOrdering.withinWindow(110L, 100L, 10L));
        assertFalse(SkyforgeSableFullSyncOrdering.withinWindow(111L, 100L, 10L));
        assertFalse(SkyforgeSableFullSyncOrdering.withinWindow(99L, 100L, 10L));
    }

    @Test
    void recordFullSyncScopesOrderingByPlayer() {
        UUID synced = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        SkyforgeSableFullSyncOrdering.recordFullSync(synced, 250L);

        assertTrue(SkyforgeSableFullSyncOrdering.needsOrderedSnapshots(synced, 250L));
        assertTrue(SkyforgeSableFullSyncOrdering.needsOrderedSnapshots(synced, 260L));
        assertFalse(SkyforgeSableFullSyncOrdering.needsOrderedSnapshots(synced, 261L));
        assertFalse(SkyforgeSableFullSyncOrdering.needsOrderedSnapshots(other, 250L));
    }
}
