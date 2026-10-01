package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeGeneratedFluidPropagationStage;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures descendant scheduled fluid ticks while generated-fluid provenance is active. */
@Mixin(LevelTicks.class)
abstract class SkyforgeLevelTicksFluidProvenanceMixin {
    @Inject(method = "schedule", at = @At("HEAD"))
    private void skyforge$captureGeneratedFluidTick(
            ScheduledTick<?> tick,
            CallbackInfo callback) {
        SkyforgeGeneratedFluidPropagationStage.observeScheduledTick(tick.pos(), tick.type());
    }
}
