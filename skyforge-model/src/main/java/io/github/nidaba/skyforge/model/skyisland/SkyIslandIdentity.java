package io.github.nidaba.skyforge.model.skyisland;

/**
 * Stable hierarchical identity for one authored Skyforge island.
 *
 * <p>Identity is deliberately independent of any backend coordinate placement. Province, cluster,
 * and island keys are opaque stable values whose only contract is persistence within one authored
 * world. Later Province- and Cluster-level semantic descriptors can influence authorship without
 * changing this identity shape.
 */
public record SkyIslandIdentity(
        int schemaVersion,
        long worldSeed,
        long provinceKey,
        long clusterKey,
        long islandKey) {
    /** The only identity schema supported by AUTH-0001. */
    public static final int SCHEMA_VERSION = 1;

    /** Validates one stable island identity. Every signed 64-bit key value is valid. */
    public SkyIslandIdentity {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported sky-island identity schema: " + schemaVersion);
        }
    }

    /** Creates one schema-1 island identity. */
    public static SkyIslandIdentity of(
            long worldSeed,
            long provinceKey,
            long clusterKey,
            long islandKey) {
        return new SkyIslandIdentity(
                SCHEMA_VERSION,
                worldSeed,
                provinceKey,
                clusterKey,
                islandKey);
    }
}
