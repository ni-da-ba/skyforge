package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeAdmissionLedgerTest {
    private static final SkyIslandWorldVolumeId VOLUME =
            new SkyIslandWorldVolumeId(56L, "physical-admission-test", 0, 0, 57L);
    private static final long CHUNK_A = new ChunkPos(0, 0).toLong();
    private static final long CHUNK_B = new ChunkPos(1, 0).toLong();

    @Test
    void clearVolumeCannotCommitUntilEveryRequiredChunkIsSurveyed() {
        var ledger = ledger(CHUNK_A, CHUNK_B);

        var first = ledger.observe(clear(CHUNK_A));
        assertEquals(SkyforgePhysicalVolumeAdmissionState.PLANNED, first.state());
        assertEquals(1, first.observedChunks());
        assertEquals(2, first.requiredChunks());
        assertFalse(first.transitionedNow());
        assertFalse(ledger.admitted(VOLUME));

        var second = ledger.observe(clear(CHUNK_B));
        assertEquals(SkyforgePhysicalVolumeAdmissionState.ADMITTED, second.state());
        assertEquals(2, second.observedChunks());
        assertTrue(second.transitionedNow());
        assertTrue(ledger.admitted(VOLUME));
    }

    @Test
    void requiredFootprintExposureIsImmutableAndIndependentOfAdmissionProgress() {
        var ledger = ledger(CHUNK_A, CHUNK_B);

        Set<Long> footprint = ledger.requiredChunkKeys(VOLUME);
        assertEquals(Set.of(CHUNK_A, CHUNK_B), footprint);
        assertThrows(UnsupportedOperationException.class, () -> footprint.remove(CHUNK_A));

        ledger.observe(clear(CHUNK_A));
        assertEquals(Set.of(CHUNK_A, CHUNK_B), ledger.requiredChunkKeys(VOLUME));
        ledger.observe(clear(CHUNK_B));
        assertEquals(Set.of(CHUNK_A, CHUNK_B), ledger.requiredChunkKeys(VOLUME));
    }

    @Test
    void onePhysicalConflictRejectsWholeVolumeImmediately() {
        var ledger = ledger(CHUNK_A, CHUNK_B);

        var rejected = ledger.observe(conflict(CHUNK_A));

        assertEquals(SkyforgePhysicalVolumeAdmissionState.REJECTED, rejected.state());
        assertEquals(1, rejected.observedChunks());
        assertEquals(2, rejected.requiredChunks());
        assertTrue(rejected.transitionedNow());
        assertTrue(rejected.firstConflict().isPresent());
        assertFalse(ledger.admitted(VOLUME));

        // Later clear evidence cannot reopen a terminal rejection.
        var stable = ledger.observe(clear(CHUNK_B));
        assertEquals(SkyforgePhysicalVolumeAdmissionState.REJECTED, stable.state());
        assertEquals(1, stable.observedChunks());
        assertFalse(stable.transitionedNow());
    }

    @Test
    void duplicateEvidenceIsIdempotentButChangedEvidenceIsRejected() {
        var ledger = ledger(CHUNK_A, CHUNK_B);
        var clear = clear(CHUNK_A);

        ledger.observe(clear);
        var replay = ledger.observe(clear);
        assertEquals(SkyforgePhysicalVolumeAdmissionState.PLANNED, replay.state());
        assertEquals(1, replay.observedChunks());
        assertFalse(replay.transitionedNow());

        assertThrows(IllegalStateException.class, () -> ledger.observe(conflict(CHUNK_A)));
    }

    @Test
    void evidenceOutsideDeclaredWholeVolumeFootprintFailsClosed() {
        var ledger = ledger(CHUNK_A);

        assertThrows(IllegalArgumentException.class, () -> ledger.observe(clear(CHUNK_B)));
    }

    private static SkyforgePhysicalVolumeAdmissionLedger ledger(long... requiredChunks) {
        Long[] boxed = new Long[requiredChunks.length];
        for (int i = 0; i < requiredChunks.length; i++) {
            boxed[i] = requiredChunks[i];
        }
        return new SkyforgePhysicalVolumeAdmissionLedger(Map.of(VOLUME, Set.of(boxed)));
    }

    private static SkyforgeNativeChunkOccupancySurvey.Result clear(long chunkKey) {
        return new SkyforgeNativeChunkOccupancySurvey.Result(
                VOLUME,
                chunkKey,
                64,
                0,
                Optional.empty());
    }

    private static SkyforgeNativeChunkOccupancySurvey.Result conflict(long chunkKey) {
        return new SkyforgeNativeChunkOccupancySurvey.Result(
                VOLUME,
                chunkKey,
                64,
                1,
                Optional.of(new SkyforgeNativeChunkOccupancySurvey.Conflict(
                        new BlockPos(1, 80, 1),
                        Blocks.CHEST.defaultBlockState(),
                        true)));
    }
}
