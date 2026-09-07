package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgePopulationExecutionStageTest {
    @Test
    void executionAppliesInteriorShellPolicyOnlyToSensitiveNativePhases() {
        var volumeId = new SkyIslandWorldVolumeId(37L, "interior-shell", 0, 0, 43L);
        var chunk = new ChunkPos(0, 0);
        var key = ResourceLocation.fromNamespaceAndPath("minecraft", "interior_shell_test");
        var spring = SkyforgePopulationOperation.create(
                volumeId,
                chunk,
                key,
                GenerationStep.Decoration.FLUID_SPRINGS.ordinal(),
                0);
        var ore = SkyforgePopulationOperation.create(
                volumeId,
                chunk,
                key,
                GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(),
                0);
        java.util.function.Predicate<BlockPos> owner = position ->
                position.getX() >= 0 && position.getX() <= 2
                        && position.getY() >= 0 && position.getY() <= 2
                        && position.getZ() >= 0 && position.getZ() <= 2;
        BlockPos interior = new BlockPos(1, 1, 1);
        BlockPos shell = new BlockPos(0, 1, 1);

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var executionScope = SkyforgePopulationExecutionStage.openForTest(
                        spring, owner, 0)) {
            domain.requireActive();
            executionScope.requireActive();
            assertTrue(executionScope.execution().canWrite(interior));
            assertFalse(executionScope.execution().canWrite(shell));
        }

        try (var domain = SkyforgeGenerationDomainStage.openIsland(volumeId);
                var executionScope = SkyforgePopulationExecutionStage.openForTest(
                        ore, owner, 0)) {
            domain.requireActive();
            executionScope.requireActive();
            assertTrue(executionScope.execution().canWrite(shell));
        }
    }

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
