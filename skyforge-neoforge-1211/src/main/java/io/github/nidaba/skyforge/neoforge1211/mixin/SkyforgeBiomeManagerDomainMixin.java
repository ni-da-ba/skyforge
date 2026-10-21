package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Minecraft's ordinary block-position biome lookup exact-volume-aware only while a Skyforge
 * population execution is active.
 *
 * <p>{@code LevelReader#getBiome(BlockPos)} delegates through {@link BiomeManager}; intercepting the
 * manager read is therefore the narrow seam needed by native {@code BiomeFilter} placement
 * modifiers and other biome-aware feature predicates. With no active population execution the hook
 * is inert and vanilla/base-world biome behavior is unchanged.
 */
@Mixin(BiomeManager.class)
abstract class SkyforgeBiomeManagerDomainMixin {
    @Inject(
            method = "getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readOwnedBiome(
            BlockPos position,
            CallbackInfoReturnable<Holder<Biome>> callback) {
        SkyforgeWorldGenRegionDomainBridge.exactBiome(position)
                .ifPresent(callback::setReturnValue);
    }
}
