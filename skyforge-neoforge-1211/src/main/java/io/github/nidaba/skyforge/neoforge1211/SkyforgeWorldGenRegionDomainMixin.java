package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes ordinary WorldGenRegion block/height access terrain-domain-local only while an explicit
 * Skyforge population execution is active. Base-world generation never opens that scope, so these
 * hooks are observationally inert for vanilla and unknown modded generation.
 */
@Mixin(WorldGenRegion.class)
abstract class SkyforgeWorldGenRegionDomainMixin {
    @Shadow
    public abstract int getMinBuildHeight();

    @Shadow
    public abstract int getHeight();

    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedBlock(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isPresent() && !execution.orElseThrow().isVisible(position)) {
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
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isPresent() && !execution.orElseThrow().isVisible(position)) {
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
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isEmpty()) {
            return;
        }
        var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                execution.orElseThrow().operation().volumeId(),
                worldX,
                worldZ,
                heightmapType,
                getMinBuildHeight(),
                getHeight());
        callback.setReturnValue(claim.map(MinecraftSkyforgeHeightClaim::height).orElse(getMinBuildHeight()));
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
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isPresent() && !execution.orElseThrow().acceptWrite(position)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$removeOwnedBlock(
            BlockPos position,
            boolean move,
            CallbackInfoReturnable<Boolean> callback) {
        var execution = SkyforgePopulationExecutionStage.activeExecution();
        if (execution.isPresent() && !execution.orElseThrow().acceptWrite(position)) {
            callback.setReturnValue(false);
        }
    }
}
