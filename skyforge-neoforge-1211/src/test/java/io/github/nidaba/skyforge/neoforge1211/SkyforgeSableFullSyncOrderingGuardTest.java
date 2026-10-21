package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class SkyforgeSableFullSyncOrderingGuardTest {
    private static final UUID PLAYER = UUID.fromString("fdc36f50-43c8-4f8f-bd9f-e96c5f171086");

    @AfterEach
    void clear() {
        SkyforgeSableFullSyncOrderingGuard.clearForTests();
    }

    @Test
    void freshlyFullSyncedPlayerUsesOrderedWindowThenReturnsToUdp() {
        SkyforgeSableFullSyncOrderingGuard.recordFullSync(PLAYER, 100L);

        assertTrue(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(PLAYER, 100L));
        assertTrue(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(PLAYER, 110L));
        assertFalse(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(PLAYER, 111L));
    }

    @Test
    void staleFutureTickFromPriorServerCannotPinNewServer() {
        SkyforgeSableFullSyncOrderingGuard.recordFullSync(PLAYER, 500L);

        assertFalse(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(PLAYER, 10L));
    }

    @Test
    void recordsAreIndependentPerPlayer() {
        UUID other = UUID.fromString("30e848fb-b65e-435f-ac18-9304f1ee28a9");
        SkyforgeSableFullSyncOrderingGuard.recordFullSync(PLAYER, 200L);

        assertTrue(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(PLAYER, 205L));
        assertFalse(SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(other, 205L));
    }
}
