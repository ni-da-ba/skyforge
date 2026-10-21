package io.github.nidaba.skyforge.neoforge1211.mixin;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes Create: Diesel Generators' independent per-chunk oil authority.
 *
 * <p>Skyforge pumpjacks bypass this method through the dedicated exact-source redirect. Other CDG
 * consumers, including scanners and commands that read ordinary chunk oil, observe zero rather than
 * synthesizing a second petroleum geography beside Skyforge's authored sources.
 */
@Pseudo
@Mixin(
        targets = "com.jesz.createdieselgenerators.world.OilChunksSavedData",
        remap = false)
abstract class SkyforgeDieselNativeOilSuppressionMixin {
    @Inject(
            method = "getChunkOilAmount(Lnet/minecraft/world/level/ChunkPos;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private void skyforge$suppressNativeChunkOil(
            ChunkPos ignoredChunk,
            CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }
}
