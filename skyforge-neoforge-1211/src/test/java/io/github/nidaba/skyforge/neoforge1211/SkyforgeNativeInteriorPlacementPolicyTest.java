package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.junit.jupiter.api.Test;

final class SkyforgeNativeInteriorPlacementPolicyTest {
    private static final SkyIslandWorldVolumeId VOLUME =
            new SkyIslandWorldVolumeId(71L, "plausibility-test", 0, 0, 73L);
    private static final ChunkPos CHUNK = new ChunkPos(0, 0);
    private static final ResourceLocation FEATURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "plausibility_test");

    @Test
    void undergroundDecorationUsesSolidExteriorReadBarrierWithoutBlanketShellWriteBan() {
        Predicate<BlockPos> solidCube = cubeOwner(0, 2);
        var operation = operation(GenerationStep.Decoration.UNDERGROUND_DECORATION);

        assertTrue(SkyforgeNativeInteriorPlacementPolicy.canWrite(
                operation, new BlockPos(1, 1, 1), solidCube));
        assertTrue(SkyforgeNativeInteriorPlacementPolicy.canWrite(
                operation, new BlockPos(0, 1, 1), solidCube));
        assertTrue(SkyforgeNativeInteriorPlacementPolicy
                .hiddenExteriorBlockState(operation)
                .is(Blocks.BEDROCK));
    }

    @Test
    void fluidSpringsRejectOuterOwnerShellButAllowBuriedCell() {
        Predicate<BlockPos> solidCube = cubeOwner(0, 2);
        var operation = operation(GenerationStep.Decoration.FLUID_SPRINGS);

        assertTrue(SkyforgeNativeInteriorPlacementPolicy.canWrite(
                operation, new BlockPos(1, 1, 1), solidCube));
        assertFalse(SkyforgeNativeInteriorPlacementPolicy.canWrite(
                operation, new BlockPos(2, 1, 1), solidCube));
    }

    @Test
    void orePlacementKeepsHistoricalExactOwnerAdmissionAtShell() {
        Predicate<BlockPos> solidCube = cubeOwner(0, 2);
        var operation = operation(GenerationStep.Decoration.UNDERGROUND_ORES);

        assertTrue(SkyforgeNativeInteriorPlacementPolicy.canWrite(
                operation, new BlockPos(0, 1, 1), solidCube));
        assertTrue(SkyforgeNativeInteriorPlacementPolicy
                .hiddenExteriorBlockState(operation)
                .is(Blocks.AIR));
    }

    @Test
    void interiorOwnerCellRequiresAllSixCardinalNeighbors() {
        Predicate<BlockPos> solidCube = cubeOwner(0, 2);

        assertTrue(SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(
                new BlockPos(1, 1, 1), solidCube));
        assertFalse(SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(
                new BlockPos(1, 1, 0), solidCube));
        assertFalse(SkyforgeNativeInteriorPlacementPolicy.isInteriorOwnerCell(
                new BlockPos(3, 1, 1), solidCube));
    }

    private static SkyforgePopulationOperation operation(GenerationStep.Decoration phase) {
        return SkyforgePopulationOperation.create(
                VOLUME,
                CHUNK,
                FEATURE,
                phase.ordinal(),
                0);
    }

    private static Predicate<BlockPos> cubeOwner(int minimum, int maximum) {
        return position -> position.getX() >= minimum
                && position.getX() <= maximum
                && position.getY() >= minimum
                && position.getY() <= maximum
                && position.getZ() >= minimum
                && position.getZ() <= maximum;
    }
}
