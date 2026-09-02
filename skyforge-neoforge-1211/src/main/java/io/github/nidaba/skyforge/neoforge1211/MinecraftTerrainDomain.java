package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandWorldVolumeId;
import java.util.Objects;

/**
 * One independent terrain domain used by Minecraft terrain-sensitive placement.
 *
 * <p>Vanilla terrain and every independently compiled Skyforge volume are deliberately separate
 * domains. A caller resolves exactly one domain before asking for a surface; unrelated domains are
 * therefore invisible to that query instead of competing through one global heightmap.
 */
sealed interface MinecraftTerrainDomain
        permits MinecraftTerrainDomain.BaseWorld, MinecraftTerrainDomain.SkyforgeVolume {

    /** The native Minecraft terrain produced before Skyforge writes any suspended-volume overlay. */
    enum BaseWorld implements MinecraftTerrainDomain {
        INSTANCE
    }

    /** One exact deterministic Skyforge world volume. */
    record SkyforgeVolume(SkyIslandWorldVolumeId volumeId) implements MinecraftTerrainDomain {
        public SkyforgeVolume {
            Objects.requireNonNull(volumeId, "volumeId");
        }
    }
}
