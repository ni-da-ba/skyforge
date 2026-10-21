package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeGeneratedFluidPropagationStage;
import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Supplies an exact-volume override for the WorldGenLevel default write preflight on ServerLevel.
 *
 * <p>Deferred Skyforge population runs on stable ServerLevel/LevelChunk state. ServerLevel normally
 * inherits WorldGenLevel.ensureCanWrite directly, so there is no concrete target method for a
 * callback injector. This mixin contributes the class override: Skyforge's non-mutating volume gate
 * runs first, then the untouched Minecraft default enforces its normal build-height/world rules.
 */
@Mixin(ServerLevel.class)
abstract class SkyforgeServerLevelWorldGenDomainMixin implements WorldGenLevel {
    @Override
    public boolean ensureCanWrite(BlockPos position) {
        if (!SkyforgeGeneratedFluidPropagationStage.acceptWrite(position)
                || !SkyforgeWorldGenRegionDomainBridge.canWrite(position)) {
            return false;
        }
        return WorldGenLevel.super.ensureCanWrite(position);
    }
}
