package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeGeneratedFluidPropagationStage;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.WorldGenTickAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures generated spring ticks scheduled through a WorldGenRegion tick access. */
@Mixin(WorldGenTickAccess.class)
abstract class SkyforgeWorldGenTickAccessFluidProvenanceMixin {
    @Inject(method = "schedule", at = @At("HEAD"))
    private void skyforge$captureGeneratedFluidTick(
            ScheduledTick<?> tick,
            CallbackInfo callback) {
        SkyforgeGeneratedFluidPropagationStage.observeScheduledTick(tick.pos(), tick.type());
    }
}
