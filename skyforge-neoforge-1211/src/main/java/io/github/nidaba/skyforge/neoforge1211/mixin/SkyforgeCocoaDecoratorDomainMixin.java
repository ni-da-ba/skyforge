package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.world.level.levelgen.feature.treedecorators.CocoaDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla cocoa decoration fail-closed when an exact-volume tree establishes no trunk.
 *
 * <p>Skyforge population intentionally rejects writes that escape the exact owner/attachment
 * envelope. A native tree can therefore reach its decorator phase with an empty log set even
 * though vanilla normally assumes at least one trunk block survived placement. CocoaDecorator
 * indexes the first log unconditionally. During an active Skyforge population operation, an empty
 * log set means there is no owner-local trunk to decorate, so the correct bounded behavior is a
 * no-op. Ordinary Minecraft generation is untouched.
 */
@Mixin(CocoaDecorator.class)
abstract class SkyforgeCocoaDecoratorDomainMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void skyforge$skipDecorationWithoutOwnedTrunk(
            TreeDecorator.Context context,
            CallbackInfo callback) {
        if (SkyforgeWorldGenRegionDomainBridge.populationActive() && context.logs().isEmpty()) {
            callback.cancel();
        }
    }
}
