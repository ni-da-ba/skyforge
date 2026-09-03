package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeStructureProjectionHeightBridge;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps vanilla/modded terrain-matching structure processors inside their owning vertical world
 * domain without changing the processor list, template-pool projection semantics, or structure IDs.
 */
@Mixin(GravityProcessor.class)
abstract class SkyforgeGravityProcessorDomainMixin {
    @Redirect(
            method = "processBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int skyforge$readProjectionHeight(
            LevelReader level,
            Heightmap.Types heightmapType,
            int worldX,
            int worldZ) {
        return SkyforgeStructureProjectionHeightBridge.resolve(
                level,
                heightmapType,
                worldX,
                worldZ);
    }
}
