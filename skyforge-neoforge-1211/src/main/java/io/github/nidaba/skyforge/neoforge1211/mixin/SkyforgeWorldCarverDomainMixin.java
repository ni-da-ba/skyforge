package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeCarverVerticalFrame;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Maps the configured standalone carver lava anchor without altering HeightProvider RNG. */
@Mixin(WorldCarver.class)
abstract class SkyforgeWorldCarverDomainMixin {
    @Redirect(
            method = "getCarveState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/VerticalAnchor;resolveY(Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int skyforge$mapCarverLavaAnchor(
            VerticalAnchor anchor,
            WorldGenerationContext context) {
        int nativeY = anchor.resolveY(context);
        return SkyforgeCarverVerticalFrame.active()
                ? SkyforgeCarverVerticalFrame.mapStandaloneAnchorY(nativeY)
                : nativeY;
    }
}
