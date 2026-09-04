package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeNativeLakeAdmissionStage;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels an unsafe native LakeFeature before its first mutation.
 *
 * <p>Outside an explicit SF-IMP-0064 LAKES execution the admission stage is inactive and ordinary
 * Minecraft LakeFeature behavior is unchanged.
 */
@SuppressWarnings("deprecation")
@Mixin(LakeFeature.class)
abstract class SkyforgeLakeFeatureAdmissionMixin {
    @Inject(
            method = "place(Lnet/minecraft/world/level/levelgen/feature/FeaturePlaceContext;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$admitWholeLake(
            FeaturePlaceContext<LakeFeature.Configuration> context,
            CallbackInfoReturnable<Boolean> callback) {
        if (!SkyforgeNativeLakeAdmissionStage.admit(context.origin())) {
            callback.setReturnValue(false);
        }
    }
}
