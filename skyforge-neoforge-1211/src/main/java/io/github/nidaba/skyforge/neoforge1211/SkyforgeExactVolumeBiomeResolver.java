package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
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

    /**
     * Resolves against the active generation level when an integration needs the provider's live
     * biome field. Fixed-key fixtures keep using the coordinate-only functional method above.
     */
    default ResourceKey<Biome> resolve(
            WorldGenLevel level,
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        Objects.requireNonNull(level, "level");
        return resolve(volumeId, worldX, worldY, worldZ);
    }

    /**
     * Whether this exact surface column is authorized to consume/persist this resolver's biome as
     * surface ecology. Native cave/interior context may still resolve a technical biome when false.
     * Fixed/native resolvers default to true; authored production ecology overrides this fail-closed.
     */
    default boolean supportsSurface(
            SkyIslandWorldVolumeId volumeId,
            int worldX,
            int worldY,
            int worldZ) {
        return true;
    }
}
