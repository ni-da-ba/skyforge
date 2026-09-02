package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationExecutionStageTest {
    @Test
    void executionRequiresMatchingTerrainDomainAndScopesVisibility() {
        var volumeId = new SkyIslandWorldVolumeId(31L, "population-execution", 0, 0, 41L);
        var operation = SkyforgePopulationOperation.create(
                volumeId,
                new ChunkPos(0, 0),
                ResourceLocation.fromNamespaceAndPath("minecraft", "trees_plains"),
                9,
                0);
        BlockPos owner = new BlockPos(0, 100, 0);

        assertThrows(
                IllegalStateException.class,
                () -> SkyforgePopulationExecutionStage.openForTest(operation, owner::equals, 2));

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var executionScope = SkyforgePopulationExecutionStage.openForTest(operation, owner::equals, 2)) {
            domain.requireActive();
            executionScope.requireActive();
            var execution = executionScope.execution();

            assertTrue(execution.isVisible(owner));
            assertFalse(execution.isVisible(new BlockPos(0, 101, 0)));
            assertTrue(execution.acceptWrite(new BlockPos(0, 101, 0)));
            assertTrue(execution.isVisible(new BlockPos(0, 101, 0)));
            assertFalse(execution.isVisible(new BlockPos(10, 101, 0)));

            assertThrows(
                    IllegalStateException.class,
                    () -> SkyforgePopulationExecutionStage.openForTest(operation, owner::equals, 2));
        }

        assertTrue(SkyforgePopulationExecutionStage.activeExecution().isEmpty());
        assertTrue(SkyforgeGenerationDomainStage.isBaseWorld());
    }
}
