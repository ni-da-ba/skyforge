package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
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
            assertEquals(Blocks.STONE.defaultBlockState(),
                    execution.virtualBlockStateForTest(new BlockPos(8, 225, 8)));
            assertEquals(Blocks.BEDROCK.defaultBlockState(),
                    execution.virtualBlockStateForTest(new BlockPos(8, 235, 8)));
            assertEquals(Blocks.AIR.defaultBlockState(),
                    execution.virtualBlockStateForTest(new BlockPos(8, 100, 8)));

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
    @Test
    void nativeCarverFluidsRequireInteriorOwnerShellWhileAirCarvingMayReachOwnerBoundary() throws Exception {
        var volumeId = new SkyIslandWorldVolumeId(62L, "carver-fluid-test", 0, 0, 6201L);
        var targetChunk = new ChunkPos(0, 0);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var execution = SkyforgeCarverExecutionStage.openForTest(
                        volumeId,
                        targetChunk,
                        position -> position.getX() >= 0 && position.getX() <= 4
                                && position.getY() >= 220 && position.getY() <= 224
                                && position.getZ() >= 0 && position.getZ() <= 4,
                        position -> false)) {
            BlockPos boundary = new BlockPos(0, 222, 2);
            BlockPos interior = new BlockPos(2, 222, 2);

            assertTrue(execution.authorizeForTest(boundary),
                    "ordinary cave carving may preserve an accepted owner-boundary opening");
            assertFalse(execution.authorizeFluidForTest(boundary),
                    "carver fluid must not be placed on the exact-volume shell");
            assertTrue(execution.authorizeFluidForTest(interior),
                    "interior cave fluid remains accepted and is fenced during later propagation");
            var snapshot = execution.snapshot();
            assertTrue(snapshot.rejectedWriteAttempts() == 1);
            assertTrue(snapshot.rejectedFluidWriteAttempts() == 1,
                    "intentional shell-fluid veto must be distinguishable from unsafe write rejection");
            domain.requireActive();
        }
    }

}
