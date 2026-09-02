package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
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
 * Makes ordinary WorldGenRegion terrain and biome access domain-local only while an explicit
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
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * TreeFeature and several configured-feature predicates use TestableWorld directly instead of
     * calling getBlockState. Keep that alternate read seam under the same exact-volume visibility
     * rules so base-world or foreign-volume blocks cannot participate in an island-owned feature.
     */
    @Inject(
            method = "isStateAtPosition(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$testOwnedBlock(
            BlockPos position,
            Predicate<BlockState> predicate,
            CallbackInfoReturnable<Boolean> callback) {
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(predicate.test(Blocks.AIR.defaultBlockState()));
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedFluid(
            BlockPos position,
            CallbackInfoReturnable<FluidState> callback) {
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(
            method = "isFluidAtPosition(Lnet/minecraft/core/BlockPos;Ljava/util/function/Predicate;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$testOwnedFluid(
            BlockPos position,
            Predicate<FluidState> predicate,
            CallbackInfoReturnable<Boolean> callback) {
        if (SkyforgeWorldGenRegionDomainBridge.active()
                && !SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(predicate.test(Fluids.EMPTY.defaultFluidState()));
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
        var height = SkyforgeWorldGenRegionDomainBridge.exactHeight(
                heightmapType,
                worldX,
                worldZ,
                getMinBuildHeight(),
                getHeight());
        if (height.isPresent()) {
            callback.setReturnValue(height.getAsInt());
        }
    }

    @Inject(
            method = "getUncachedNoiseBiome(III)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedBiome(
            int quartX,
            int quartY,
            int quartZ,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        SkyforgeWorldGenRegionDomainBridge.exactBiome(quartX, quartY, quartZ)
                .ifPresent(callback::setReturnValue);
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
        if (!SkyforgeWorldGenRegionDomainBridge.acceptWrite(position)) {
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
        if (!SkyforgeWorldGenRegionDomainBridge.acceptWrite(position)) {
            callback.setReturnValue(false);
        }
    }
}
