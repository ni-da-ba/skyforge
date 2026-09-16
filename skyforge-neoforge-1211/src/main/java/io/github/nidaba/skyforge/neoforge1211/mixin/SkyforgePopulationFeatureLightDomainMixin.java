package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Preserves native FEATURES-stage light semantics during deferred exact-volume population.
 *
 * <p>Minecraft decorates a ProtoChunk before INITIALIZE_LIGHT/LIGHT. Skyforge may replay that same
 * native feature phase later against a fully lit stable LevelChunk after deferred terrain catch-up.
 * Reading the live light engine there makes light-sensitive feature predicates depend on asynchronous
 * stable-world lighting state instead of the deterministic generation-stage state they were authored
 * for. While one exact-volume native population operation is active, expose the pre-light FEATURES
 * value (zero) through the common raw-brightness seam. Ordinary Minecraft runtime and all reads
 * outside native population are untouched.
 */
@Mixin(LevelLightEngine.class)
abstract class SkyforgePopulationFeatureLightDomainMixin {
    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true)
    private void skyforge$useFeaturesStageRawBrightness(
            BlockPos position,
            int ambientDarkness,
            CallbackInfoReturnable<Integer> callback) {
        if (SkyforgeWorldGenRegionDomainBridge.populationActive()) {
            callback.setReturnValue(0);
        }
    }
}
