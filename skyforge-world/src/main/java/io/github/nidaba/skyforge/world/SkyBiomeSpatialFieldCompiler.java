package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeId;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Compiles stable SkyBiome habitat zones from province and island geometry. */
public final class SkyBiomeSpatialFieldCompiler {
    /**
     * Compiles an open-sky domain with explicit cloud-shelf regions and island-shelter halos.
     *
     * <p>Island shelter is a static geometric envelope around island mass. It does not claim a
     * prevailing wind direction or simulate atmosphere. Cloud-shelf bounds are supplied by world
     * composition and classify habitat only; no clouds or weather are generated here.
     *
     * @param worldSeed stable world identity used by persisted zone assignments
     * @param provinceBounds closed finite assignment domain
     * @param islands deterministic Skyforge island world catalog
     * @param cloudShelfBounds authored cloud-associated airspace regions inside the province
     * @param leeRadius horizontal distance from island bounds considered terrain-sheltered
     */
    public SkyBiomeSpatialField compile(
            long worldSeed,
            WorldBounds provinceBounds,
            SkyIslandWorldCatalog islands,
            List<WorldBounds> cloudShelfBounds,
            double leeRadius) {
        Objects.requireNonNull(provinceBounds, "provinceBounds");
        Objects.requireNonNull(islands, "islands");
        Objects.requireNonNull(cloudShelfBounds, "cloudShelfBounds");
        if (!Double.isFinite(leeRadius) || leeRadius <= 0.0) {
            throw new IllegalArgumentException("leeRadius must be finite and positive");
        }

        Map<Long, String> keyOwners = new HashMap<>();
        ArrayList<SkyBiomeSpatialZone> zones = new ArrayList<>();
        zones.add(zone(worldSeed, provinceBounds, SkyBiomeCatalog.OPEN_SKY_ID,
                "world/open-sky", keyOwners));

        TreeMap<String, WorldBounds> stableCloudShelves = new TreeMap<>();
        for (WorldBounds shelf : cloudShelfBounds) {
            Objects.requireNonNull(shelf, "cloud shelf bounds");
            if (!provinceBounds.contains(shelf)) {
                throw new IllegalArgumentException("cloud shelf region lies outside province bounds");
            }
            stableCloudShelves.putIfAbsent("cloud-shelf/" + boundsKey(shelf), shelf);
        }
        for (Map.Entry<String, WorldBounds> shelf : stableCloudShelves.entrySet()) {
            zones.add(zone(worldSeed, shelf.getValue(), SkyBiomeCatalog.CLOUD_SHELF_ID,
                    shelf.getKey(), keyOwners));
        }

        List<SkyIslandWorldVolume> orderedIslands = islands.volumes().stream()
                .sorted(Comparator.comparing(volume -> islandIdKey(volume.id())))
                .toList();
        for (SkyIslandWorldVolume island : orderedIslands) {
            WorldBounds shelter = clip(expandHorizontally(island.bounds(), leeRadius), provinceBounds);
            if (shelter != null) {
                zones.add(zone(worldSeed, shelter, SkyBiomeCatalog.ISLAND_LEE_ID,
                        "island-lee/" + islandIdKey(island.id()), keyOwners));
            }
        }
        return new SkyBiomeSpatialField(worldSeed, provinceBounds, zones);
    }

    private static SkyBiomeSpatialZone zone(
            long worldSeed,
            WorldBounds bounds,
            SkyBiomeId id,
            String stableKey,
            Map<Long, String> keyOwners) {
        long zoneKey = stableHash64(stableKey);
        String previousOwner = keyOwners.putIfAbsent(zoneKey, stableKey);
        if (previousOwner != null && !previousOwner.equals(stableKey)) {
            throw new IllegalStateException("SkyBiome zone-key hash collision");
        }
        SkyBiomeZoneIdentity identity = SkyBiomeZoneIdentity.of(worldSeed, zoneKey);
        return new SkyBiomeSpatialZone(bounds, SkyBiomeZoneAssignment.of(identity, id));
    }

    private static WorldBounds expandHorizontally(WorldBounds bounds, double radius) {
        return new WorldBounds(
                bounds.minimumX() - radius,
                bounds.maximumX() + radius,
                bounds.minimumY(),
                bounds.maximumY(),
                bounds.minimumZ() - radius,
                bounds.maximumZ() + radius);
    }

    private static WorldBounds clip(WorldBounds bounds, WorldBounds domain) {
        double minimumX = Math.max(bounds.minimumX(), domain.minimumX());
        double maximumX = Math.min(bounds.maximumX(), domain.maximumX());
        double minimumY = Math.max(bounds.minimumY(), domain.minimumY());
        double maximumY = Math.min(bounds.maximumY(), domain.maximumY());
        double minimumZ = Math.max(bounds.minimumZ(), domain.minimumZ());
        double maximumZ = Math.min(bounds.maximumZ(), domain.maximumZ());
        if (minimumX > maximumX || minimumY > maximumY || minimumZ > maximumZ) {
            return null;
        }
        return new WorldBounds(minimumX, maximumX, minimumY, maximumY, minimumZ, maximumZ);
    }

    private static String islandIdKey(SkyIslandWorldVolumeId id) {
        String group = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(id.groupIdentifier().getBytes(StandardCharsets.UTF_8));
        return Long.toUnsignedString(id.archipelagoRootSeed()) + "/" + group
                + "/" + id.groupOrdinal() + "/" + id.memberOrdinal()
                + "/" + Long.toUnsignedString(id.geometrySeed());
    }

    private static String boundsKey(WorldBounds bounds) {
        return Double.toHexString(bounds.minimumX()) + "/"
                + Double.toHexString(bounds.maximumX()) + "/"
                + Double.toHexString(bounds.minimumY()) + "/"
                + Double.toHexString(bounds.maximumY()) + "/"
                + Double.toHexString(bounds.minimumZ()) + "/"
                + Double.toHexString(bounds.maximumZ());
    }

    private static long stableHash64(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
