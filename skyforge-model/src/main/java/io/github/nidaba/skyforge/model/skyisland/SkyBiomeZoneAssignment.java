package io.github.nidaba.skyforge.model.skyisland;

import java.util.Objects;

/**
 * Persistable assignment of one authored SkyBiome class to one coherent sky zone.
 *
 * <p>This records authored identity only. Geographic membership, local island ecology, live
 * atmosphere, spawn policy, and backend biome projection are owned by their respective systems.
 */
public record SkyBiomeZoneAssignment(
        int schemaVersion,
        SkyBiomeZoneIdentity zoneIdentity,
        SkyBiomeId skyBiomeId) {
    public static final int SCHEMA_VERSION = 1;

    public SkyBiomeZoneAssignment {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported SkyBiome zone assignment schema: " + schemaVersion);
        }
        zoneIdentity = Objects.requireNonNull(zoneIdentity, "zoneIdentity");
        skyBiomeId = Objects.requireNonNull(skyBiomeId, "skyBiomeId");
    }

    public static SkyBiomeZoneAssignment of(SkyBiomeZoneIdentity zoneIdentity, SkyBiomeId skyBiomeId) {
        return new SkyBiomeZoneAssignment(SCHEMA_VERSION, zoneIdentity, skyBiomeId);
    }
}
