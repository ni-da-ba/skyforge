package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.List;
import java.util.Objects;

/** One early Minecraft height answer together with the Skyforge volume provenance at that top. */
record MinecraftSkyforgeHeightClaim(int height, List<SkyIslandWorldVolumeId> volumeIds) {
    MinecraftSkyforgeHeightClaim {
        if (volumeIds.isEmpty()) {
            throw new IllegalArgumentException("a Skyforge height claim requires at least one supporting volume");
        }
        volumeIds = List.copyOf(volumeIds);
        volumeIds.forEach(id -> Objects.requireNonNull(id, "volumeIds contains null"));
    }
}
