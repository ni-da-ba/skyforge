package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;

/** Reads the active generation world's biome field at the exact admitted surface sample. */
final class SkyforgeProviderBiomeResolver {
    private SkyforgeProviderBiomeResolver() {}

    static SkyforgeExactVolumeBiomeResolver create() {
        return new SkyforgeExactVolumeBiomeResolver() {
            @Override
            public ResourceKey<Biome> resolve(
                    SkyIslandWorldVolumeId volumeId,
                    int worldX,
                    int worldY,
                    int worldZ) {
                Objects.requireNonNull(volumeId, "volumeId");
                throw new IllegalStateException(
                        "provider biome resolution requires the active generation level");
            }

            @Override
            public ResourceKey<Biome> resolve(
                    WorldGenLevel level,
                    SkyIslandWorldVolumeId volumeId,
                    int worldX,
                    int worldY,
                    int worldZ) {
                Objects.requireNonNull(level, "level");
                Objects.requireNonNull(volumeId, "volumeId");
                return level.getBiome(new BlockPos(worldX, worldY, worldZ))
                        .unwrapKey()
                        .orElseThrow(() -> new IllegalStateException(
                                "provider returned an unregistered biome at exact-volume sample "
                                        + volumeId.path() + " (" + worldX + ", " + worldY + ", " + worldZ + ")"));
            }
        };
    }
}
