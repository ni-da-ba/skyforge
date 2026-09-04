package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeCarverVerticalFrameTest {
    @Test
    void sampledNativeHeightsMapMonotonicallyIntoExplicitInteriorFrame() {
        int previous = Integer.MIN_VALUE;
        for (int y = -120; y <= 380; y++) {
            int mapped = SkyforgeCarverVerticalFrame.mapYForTest(y, -64, 319, 222, 241);
            assertTrue(mapped >= 222 && mapped <= 241);
            assertTrue(mapped >= previous);
            previous = mapped;
        }
        assertEquals(222, SkyforgeCarverVerticalFrame.mapYForTest(-64, -64, 319, 222, 241));
        assertEquals(241, SkyforgeCarverVerticalFrame.mapYForTest(319, -64, 319, 222, 241));
    }

    @Test
    void exactCarverFenceAcceptsOnlyOwnerAndHardVetoesForeignSolid() throws Exception {
        var volumeId = new SkyIslandWorldVolumeId(61L, "carver-test", 0, 0, 6101L);
        var targetChunk = new ChunkPos(0, 0);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var execution = SkyforgeCarverExecutionStage.openForTest(
                        volumeId,
                        targetChunk,
                        position -> position.getY() >= 220 && position.getY() <= 240,
                        position -> position.getY() == 235)) {
            assertTrue(execution.authorizeForTest(new BlockPos(8, 225, 8)));
            assertFalse(execution.authorizeForTest(new BlockPos(8, 235, 8)));
            assertFalse(execution.authorizeForTest(new BlockPos(8, 100, 8)));

            var snapshot = execution.snapshot();
            assertEquals(3, snapshot.writeAttempts());
            assertEquals(1, snapshot.acceptedWriteAttempts());
            assertEquals(2, snapshot.rejectedWriteAttempts());
            assertEquals(0, snapshot.changedBlocks());
            domain.requireActive();
        }
    }
}
