package io.github.nidaba.skyforge.model.skyisland;

/**
 * Stable identity for one Skyforge-authored sky zone within a world.
 *
 * <p>A zone may span multiple provinces, clusters, and islands. Its geographic membership is
 * authored separately; this identity and its assigned SkyBiome remain independent of backend
 * coordinates and Minecraft biome keys.
 */
public record SkyBiomeZoneIdentity(int schemaVersion, long worldSeed, long zoneKey) {
    public static final int SCHEMA_VERSION = 1;

    public SkyBiomeZoneIdentity {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported SkyBiome zone identity schema: " + schemaVersion);
        }
    }

    public static SkyBiomeZoneIdentity of(long worldSeed, long zoneKey) {
        return new SkyBiomeZoneIdentity(SCHEMA_VERSION, worldSeed, zoneKey);
    }
}
