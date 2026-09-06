package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * Phase-aware plausibility policy for native post-cave population in floating exact volumes.
 *
 * <p>Minecraft's underground features normally run inside a world with effectively unbounded
 * surrounding terrain. A floating island has an exterior instead. UNDERGROUND_DECORATION and
 * FLUID_SPRINGS therefore see hidden exterior positions as an inert solid read barrier instead of
 * AIR. Native springs additionally may not occupy the outermost exact-owner shell. Surface ecology,
 * ores, local modifications and admitted lakes retain their existing policies.
 */
final class SkyforgeNativeInteriorPlacementPolicy {
    private SkyforgeNativeInteriorPlacementPolicy() {}

    static boolean canWrite(
            SkyforgePopulationOperation operation,
            BlockPos position,
            Predicate<BlockPos> ownerSolid) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(ownerSolid, "ownerSolid");
        return !requiresInteriorCell(operation)
                || isInteriorOwnerCell(position, ownerSolid);
    }

    static boolean canWriteState(
            WorldGenLevel level,
            SkyforgePopulationOperation operation,
            BlockPos position,
            BlockState state,
            Predicate<BlockPos> ownerSolid) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        if (!canWrite(operation, position, ownerSolid)) {
            return false;
        }

        if (state.is(Blocks.GLOW_LICHEN)) {
            // Native multiface decoration is useful inside caves, but it must have real support in
            // the final post-cave Minecraft topology at the moment it is written.
            return state.canSurvive(level, position);
        }

        if (operation.generationStep() == GenerationStep.Decoration.FLUID_SPRINGS.ordinal()
                && !state.getFluidState().isEmpty()) {
            // FLUID_SPRINGS executes after UNDERGROUND_DECORATION. Do not replace a solid that is
            // currently supporting glow lichen; otherwise a valid native decoration can become
            // detached immediately after its own phase completed.
            for (Direction direction : Direction.values()) {
                if (level.getBlockState(position.relative(direction)).is(Blocks.GLOW_LICHEN)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isInteriorOwnerCell(
            SkyIslandWorldVolumeId volumeId,
            BlockPos position) {
        Objects.requireNonNull(volumeId, "volumeId");
        return isInteriorOwnerCell(
                position,
                candidate -> SkyforgeNeoForge1211SurfaceStage.isSolidOwnedBy(
                                volumeId,
                                candidate.getX(),
                                candidate.getY(),
                                candidate.getZ())
                        .orElseThrow(() -> new IllegalStateException(
                                "Skyforge terrain binding disappeared during interior plausibility check")));
    }

    static boolean isInteriorOwnerCell(
            BlockPos position,
            Predicate<BlockPos> ownerSolid) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(ownerSolid, "ownerSolid");
        if (!ownerSolid.test(position)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!ownerSolid.test(position.relative(direction))) {
                return false;
            }
        }
        return true;
    }

    static BlockState hiddenExteriorBlockState(SkyforgePopulationOperation operation) {
        Objects.requireNonNull(operation, "operation");
        int generationStep = operation.generationStep();
        if (generationStep == GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                || generationStep == GenerationStep.Decoration.FLUID_SPRINGS.ordinal()) {
            // Underground-native features assume their search domain is embedded in surrounding
            // terrain. Present the floating-island exterior as an inert solid read barrier rather
            // than AIR so the sky is not mistaken for cave space. This is read-only virtualization.
            return Blocks.BEDROCK.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static boolean requiresInteriorCell(SkyforgePopulationOperation operation) {
        return operation.generationStep() == GenerationStep.Decoration.FLUID_SPRINGS.ordinal();
    }
}
