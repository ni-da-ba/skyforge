package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeCarverExecutionStage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exact-volume fence and stable-client notification for direct native carver LevelChunk writes. */
@Mixin(LevelChunk.class)
abstract class SkyforgeLevelChunkCarverDomainMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readCarverVirtualBlock(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        SkyforgeCarverExecutionStage.virtualRead((LevelChunk) (Object) this, position)
                .ifPresent(callback::setReturnValue);
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readCarverVirtualFluid(
            BlockPos position,
            CallbackInfoReturnable<FluidState> callback) {
        SkyforgeCarverExecutionStage.virtualRead((LevelChunk) (Object) this, position)
                .map(BlockState::getFluidState)
                .ifPresent(callback::setReturnValue);
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$authorizeCarverWrite(
            BlockPos position,
            BlockState state,
            boolean moved,
            CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (SkyforgeCarverExecutionStage.active()
                && !SkyforgeCarverExecutionStage.authorizeWrite(chunk, position, state)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void skyforge$publishCarverWrite(
            BlockPos position,
            BlockState state,
            boolean moved,
            CallbackInfoReturnable<BlockState> callback) {
        if (!SkyforgeCarverExecutionStage.active() || callback.getReturnValue() == null) {
            return;
        }
        SkyforgeCarverExecutionStage.afterChangedWrite((LevelChunk) (Object) this, position, state);
    }
}
