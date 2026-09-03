package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeDeferredPopulationPostProcessingBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects only explicitly deferred Skyforge LevelChunk post-processing into the native queue. */
@Mixin(ChunkAccess.class)
abstract class SkyforgeChunkPostProcessingMixin {
    @Inject(
            method = "markPosForPostprocessing(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$captureDeferredPostProcessing(BlockPos position, CallbackInfo callback) {
        if (SkyforgeDeferredPopulationPostProcessingBridge.capture((ChunkAccess) (Object) this, position)) {
            callback.cancel();
        }
    }
}
