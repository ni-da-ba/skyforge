package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends exact-volume population read virtualization to direct stable-chunk reads.
 *
 * <p>Deferred native population runs against {@link LevelChunk}s. Some vanilla feature paths can
 * obtain the chunk directly and bypass the higher {@code Level}/{@code WorldGenRegion} read seams.
 * Those reads must see the same deterministic virtual neighbor state as every other population
 * access, otherwise feature shape can depend on which neighboring chunk happened to populate first.
 */
@Mixin(LevelChunk.class)
abstract class SkyforgeLevelChunkPopulationReadMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readPopulationVirtualBlock(
            BlockPos position,
            CallbackInfoReturnable<BlockState> callback) {
        if (!SkyforgeWorldGenRegionDomainBridge.populationActive()) {
            return;
        }
        var authoredHydrology = SkyforgeWorldGenRegionDomainBridge.authoredHydrologyPopulationState(position);
        if (authoredHydrology.isPresent()) {
            callback.setReturnValue(authoredHydrology.orElseThrow());
            return;
        }
        if (!SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(SkyforgeWorldGenRegionDomainBridge.hiddenBlockState(position));
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readPopulationVirtualFluid(
            BlockPos position,
            CallbackInfoReturnable<FluidState> callback) {
        if (!SkyforgeWorldGenRegionDomainBridge.populationActive()) {
            return;
        }
        var authoredHydrology = SkyforgeWorldGenRegionDomainBridge.authoredHydrologyPopulationState(position);
        if (authoredHydrology.isPresent()) {
            callback.setReturnValue(authoredHydrology.orElseThrow().getFluidState());
            return;
        }
        if (!SkyforgeWorldGenRegionDomainBridge.isVisible(position)) {
            callback.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }
}
