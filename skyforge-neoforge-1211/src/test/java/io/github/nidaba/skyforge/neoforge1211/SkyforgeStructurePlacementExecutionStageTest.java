package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class SkyforgeStructurePlacementExecutionStageTest {
    @Test
    void exactPlacementFenceRejectsExteriorAndForeignStackedSolid() {
        var volumeId = new SkyIslandWorldVolumeId(11L, "structure-placement-test", 0, 0, 12L);
        var bounds = new BoundingBox(0, 100, 0, 15, 140, 15);
        var allowed = new BlockPos(8, 120, 8);
        var foreign = new BlockPos(8, 121, 8);
        var exterior = new BlockPos(16, 120, 8);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var placement = SkyforgeStructurePlacementExecutionStage.openForTest(
                        volumeId, bounds, foreign::equals)) {
            assertTrue(SkyforgeStructurePlacementExecutionStage.active());
            assertTrue(SkyforgeStructurePlacementExecutionStage.canWrite(allowed));
            assertTrue(SkyforgeStructurePlacementExecutionStage.isVisible(allowed));
            assertFalse(SkyforgeStructurePlacementExecutionStage.canWrite(foreign));
            assertFalse(SkyforgeStructurePlacementExecutionStage.isVisible(foreign));
            assertFalse(SkyforgeStructurePlacementExecutionStage.canWrite(exterior));
        }

        assertFalse(SkyforgeStructurePlacementExecutionStage.active());
        assertTrue(SkyforgeStructurePlacementExecutionStage.canWrite(exterior));
    }
}
