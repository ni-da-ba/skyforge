package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeCarverVerticalFrame;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Maps built-in leaf HeightProvider results only while an exact-volume native carver frame is
 * active. Injection occurs at RETURN, after the provider has consumed its ordinary RNG.
 */
@Mixin(targets = {
    "net.minecraft.world.level.levelgen.heightproviders.UniformHeight",
    "net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight",
    "net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight",
    "net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight",
    "net.minecraft.world.level.levelgen.heightproviders.ConstantHeight"
})
abstract class SkyforgeCarverHeightProviderDomainMixin {
    @Inject(
            method = "sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I",
            at = @At("RETURN"),
            cancellable = true)
    private void skyforge$mapCarverHeight(
            RandomSource random,
            WorldGenerationContext context,
            CallbackInfoReturnable<Integer> callback) {
        if (!SkyforgeCarverVerticalFrame.active()) {
            return;
        }
        callback.setReturnValue(SkyforgeCarverVerticalFrame.mapSampledY(callback.getReturnValue()));
    }
}
