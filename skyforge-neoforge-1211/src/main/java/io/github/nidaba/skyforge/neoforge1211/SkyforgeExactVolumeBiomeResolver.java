package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Minecraft-adapter seam mapping one exact Skyforge world volume/environment sample to a registered
 * Minecraft biome identity.
 *
 * <p>The backend-neutral Skyforge layers remain unaware of Minecraft biome keys. Production
 * implementations may derive the key from Skyforge climate/environment fields; development proofs
 * may use a fixed mapping. The returned key is resolved through the live final biome registry so
 * datapack and NeoForge biome modifications remain visible to population.
 */
@FunctionalInterface
interface SkyforgeExactVolumeBiomeResolver {
    ResourceKey<Biome> resolve(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ);
}
