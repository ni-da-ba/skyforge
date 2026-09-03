package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeUndergroundPlacementProbe;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Development-observation seam for Minecraft's registry-native vertical placement output.
 *
 * <p>The returned stream is only decorated with {@code peek}; coordinates, ordering and random
 * consumption are unchanged. Outside an explicit SF-IMP-0059 probe scope the exact vanilla stream
 * is returned untouched.
 */
@Mixin(HeightRangePlacement.class)
abstract class SkyforgeHeightRangePlacementProbeMixin {
    @Inject(
            method = "getPositions(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;"
                    + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Ljava/util/stream/Stream;",
            at = @At("RETURN"),
            cancellable = true)
    private void skyforge$observeHeightRangePositions(
            PlacementContext context,
            RandomSource random,
            BlockPos position,
            CallbackInfoReturnable<Stream<BlockPos>> callback) {
        if (!SkyforgeUndergroundPlacementProbe.active()) {
            return;
        }
        callback.setReturnValue(
                SkyforgeUndergroundPlacementProbe.observeHeightRangePositions(callback.getReturnValue()));
    }
}
