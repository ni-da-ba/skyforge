package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgePetroleumPumpjackBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces CDG's chunk-oil persistence calls only at the retained pumpjack extraction point.
 *
 * <p>The pinned CDG pumpjack still owns pipe validation, crank throughput, tank fill and output
 * fluid selection. Skyforge substitutes only petroleum quantity/depletion authority, resolving the
 * exact admitted source from the physical Skyforge termination beneath this pumpjack.
 */
@Pseudo
@Mixin(
        targets = "com.jesz.createdieselgenerators.content.pumpjack.PumpjackHoleBlockEntity",
        remap = false)
abstract class SkyforgeDieselPumpjackOilAuthorityMixin {
    @Redirect(
            method = "pumpjackRotation",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/jesz/createdieselgenerators/world/OilChunksSavedData;"
                            + "getChunkOilAmount(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/level/ChunkPos;)I",
                    remap = false),
            require = 1,
            remap = false)
    private int skyforge$readExactSourceAmount(ServerLevel level, ChunkPos ignoredChunk) {
        BlockPos pumpjackPosition = ((BlockEntity) (Object) this).getBlockPos();
        return SkyforgePetroleumPumpjackBridge.oilAmountForPumpjack(level, pumpjackPosition);
    }

    @Redirect(
            method = "pumpjackRotation",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/jesz/createdieselgenerators/world/OilChunksSavedData;"
                            + "setChunkOilAmount(Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/level/ChunkPos;I)V",
                    remap = false),
            require = 1,
            remap = false)
    private void skyforge$persistExactSourceAmount(
            ServerLevel level,
            ChunkPos ignoredChunk,
            int remainingMillibuckets) {
        BlockPos pumpjackPosition = ((BlockEntity) (Object) this).getBlockPos();
        SkyforgePetroleumPumpjackBridge.setOilAmountForPumpjack(
                level, pumpjackPosition, remainingMillibuckets);
    }
}
