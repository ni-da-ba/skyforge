package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeGeneratedFluidPropagationStage;
import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the exact-volume population view to stable post-generation {@link Level} access.
 *
 * <p>SF-IMP-0056 may defer an admitted island until its chunk is a real loaded LevelChunk. Native
 * population executed at that point uses ServerLevel rather than WorldGenRegion, so the same
 * thread-local visibility and write rules must be applied at Level's read/write boundary. These
 * hooks are inert whenever no Skyforge population execution is active.
 */
@Mixin(Level.class)
abstract class SkyforgeLevelDomainMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedBlock(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        if (SkyforgeGeneratedFluidPropagationStage.propagationActive()
                && !SkyforgeGeneratedFluidPropagationStage.isVisible(position)) {
            callback.setReturnValue(Blocks.BEDROCK.defaultBlockState());
            return;
        }
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedFluid(
            BlockPos position,
            CallbackInfoReturnable<FluidState> callback) {
        if (SkyforgeGeneratedFluidPropagationStage.propagationActive()
                && !SkyforgeGeneratedFluidPropagationStage.isVisible(position)) {
            callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(
            method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedHeight(
            Heightmap.Types heightmapType,
            int worldX,
            int worldZ,
            CallbackInfoReturnable<Integer> callback) {
        LevelHeightAccessor heightAccessor = (LevelHeightAccessor) (Object) this;
        var height = SkyforgeWorldGenRegionDomainBridge.exactHeight(
                heightmapType,
                worldX,
                worldZ,
                heightAccessor.getMinBuildHeight(),
                heightAccessor.getHeight());
        if (height.isPresent()) {
            callback.setReturnValue(height.getAsInt());
        }
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$writeOwnedBlock(
            BlockPos position,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback) {
        if (!SkyforgeGeneratedFluidPropagationStage.acceptWrite(position)
                || !SkyforgeWorldGenRegionDomainBridge.acceptWrite(position, state)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"))
    private void skyforge$observeGeneratedFluidWrite(
            BlockPos position,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> callback) {
        SkyforgeGeneratedFluidPropagationStage.observeCommittedBlockWrite(
                (Level) (Object) this,
                position,
                state,
                Boolean.TRUE.equals(callback.getReturnValue()));
    }

    @Inject(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$removeOwnedBlock(
            BlockPos position,
            boolean move,
            CallbackInfoReturnable<Boolean> callback) {
        if (!SkyforgeGeneratedFluidPropagationStage.acceptWrite(position)
                || !SkyforgeWorldGenRegionDomainBridge.acceptWrite(position)) {
            callback.setReturnValue(false);
        }
    }
}
