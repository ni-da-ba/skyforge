package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeGeneratedFluidPropagationStage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reinstates persisted exact-volume provenance around asynchronous generated-fluid ticks. */
@Mixin(FlowingFluid.class)
abstract class SkyforgeFlowingFluidDomainMixin {
    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At("HEAD"))
    private void skyforge$openGeneratedFluidDomain(
            Level level,
            BlockPos position,
            FluidState state,
            CallbackInfo callback) {
        SkyforgeGeneratedFluidPropagationStage.beginFluidTick(level, position, state);
    }

    @Inject(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/FluidState;)V",
            at = @At("RETURN"))
    private void skyforge$closeGeneratedFluidDomain(
            Level level,
            BlockPos position,
            FluidState state,
            CallbackInfo callback) {
        SkyforgeGeneratedFluidPropagationStage.endFluidTick();
    }
}
