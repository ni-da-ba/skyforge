package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeUndergroundPlacementProbe;
import io.github.nidaba.skyforge.neoforge1211.SkyforgeVerticalPlacementFrame;
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
 * Maps native HeightRangePlacement output into an explicitly active Skyforge vertical frame.
 *
 * <p>Minecraft still evaluates the registered HeightProvider with its ordinary random source first.
 * This return-boundary transform therefore consumes no additional random values and preserves X/Z,
 * stream ordering, feature identity and all non-height placement modifiers. Outside an exact-volume
 * underground frame the vanilla stream is returned unchanged.
 */
@Mixin(HeightRangePlacement.class)
abstract class SkyforgeHeightRangePlacementDomainMixin {
    @Inject(
            method = "getPositions(Lnet/minecraft/world/level/levelgen/placement/PlacementContext;"
                    + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Ljava/util/stream/Stream;",
            at = @At("RETURN"),
            cancellable = true)
    private void skyforge$mapHeightRangePositions(
            PlacementContext context,
            RandomSource random,
            BlockPos position,
            CallbackInfoReturnable<Stream<BlockPos>> callback) {
        if (!SkyforgeVerticalPlacementFrame.active() && !SkyforgeUndergroundPlacementProbe.active()) {
            return;
        }
        callback.setReturnValue(callback.getReturnValue().map(nativePosition -> {
            BlockPos mappedPosition = SkyforgeVerticalPlacementFrame.mapHeightRangePosition(nativePosition);
            SkyforgeUndergroundPlacementProbe.observeHeightRangeTransform(nativePosition, mappedPosition);
            return mappedPosition;
        }));
    }
}
