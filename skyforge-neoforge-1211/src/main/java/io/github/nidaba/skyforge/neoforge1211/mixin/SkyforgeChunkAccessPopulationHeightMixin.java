package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeWorldGenRegionDomainBridge;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps direct chunk heightmap reads deterministic during deferred exact-volume population.
 *
 * <p>{@code Level#getHeight} and {@code WorldGenRegion#getHeight} already project the compiled
 * Skyforge terrain surface while a population operation is active. Native features can also acquire
 * a {@link ChunkAccess} and sample its live heightmap directly. A stable LevelChunk heightmap
 * includes population written by earlier scheduling, so exposing it here can make an otherwise
 * deterministic placed-feature seed depend on A/B chunk realization order.
 *
 * <p>ChunkAccess height queries return the highest occupied Y, while the level-facing bridge returns
 * the first free Y. Subtract one so this direct seam observes exactly the same compiled terrain
 * surface without exposing previously populated attachments.
 */
@Mixin(ChunkAccess.class)
abstract class SkyforgeChunkAccessPopulationHeightMixin {
    @Shadow
    public abstract ChunkPos getPos();

    @Shadow
    public abstract int getMinBuildHeight();

    @Shadow
    public abstract int getHeight();

    @Inject(
            method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",
            at = @At("HEAD"),
            cancellable = true)
    private void skyforge$readPopulationVirtualHeight(
            Heightmap.Types heightmapType,
            int localX,
            int localZ,
            CallbackInfoReturnable<Integer> callback) {
        if (!SkyforgeWorldGenRegionDomainBridge.populationActive()) {
            return;
        }
        ChunkPos chunk = getPos();
        int worldX = chunk.getMinBlockX() + Math.floorMod(localX, 16);
        int worldZ = chunk.getMinBlockZ() + Math.floorMod(localZ, 16);
        var firstFreeHeight = SkyforgeWorldGenRegionDomainBridge.exactHeight(
                heightmapType,
                worldX,
                worldZ,
                getMinBuildHeight(),
                getHeight());
        if (firstFreeHeight.isPresent()) {
            callback.setReturnValue(firstFreeHeight.getAsInt() - 1);
        }
    }
}
