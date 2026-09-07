package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgePortableEngineCutoffAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minimal opt-in UX for the Portable Engine cutoff.
 *
 * <p>Sneak-use a Redstone Torch on a Portable Engine to toggle whether neighboring redstone acts as
 * an ignition cutoff. Redstone Torch is deliberately used as a configuration token rather than
 * adding a bespoke Skyforge block/item; nothing is consumed.
 */
@Pseudo
@Mixin(
        targets = "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock",
        remap = false)
abstract class SkyforgePortableEngineBlockMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void skyforge$configureRedstoneCutoff(
            ItemStack heldItem,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!player.isShiftKeyDown() || !heldItem.is(Items.REDSTONE_TORCH)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SkyforgePortableEngineCutoffAccess cutoff)) {
            return;
        }

        if (!level.isClientSide) {
            boolean enabled = !cutoff.skyforge$isRedstoneCutoffEnabled();
            cutoff.skyforge$setRedstoneCutoffEnabled(enabled);
            player.displayClientMessage(
                    Component.translatable(
                            enabled
                                    ? "message.skyforge.portable_engine_cutoff.enabled"
                                    : "message.skyforge.portable_engine_cutoff.disabled"),
                    true);
        }

        cir.setReturnValue(ItemInteractionResult.sidedSuccess(level.isClientSide));
    }
}
