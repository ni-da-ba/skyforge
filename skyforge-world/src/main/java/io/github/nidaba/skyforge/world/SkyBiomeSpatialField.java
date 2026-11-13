package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable deterministic point resolver for SkyBiome habitat regions in one province domain. */
public final class SkyBiomeSpatialField {
    private final long worldSeed;
    private final WorldBounds domain;
    private final List<SkyBiomeSpatialZone> zones;

    public SkyBiomeSpatialField(long worldSeed, WorldBounds domain, List<SkyBiomeSpatialZone> zones) {
        this.worldSeed = worldSeed;
        this.domain = Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(zones, "zones");
        ArrayList<SkyBiomeSpatialZone> ordered = new ArrayList<>(zones);
        ordered.forEach(zone -> Objects.requireNonNull(zone, "zone"));
        ordered.sort(Comparator.comparingInt(SkyBiomeSpatialZone::priority).reversed()
                .thenComparing((first, second) -> Long.compareUnsigned(
                        first.assignment().zoneIdentity().zoneKey(),
                        second.assignment().zoneIdentity().zoneKey())));
        Set<Long> keys = new HashSet<>();
        boolean hasOpenSkyDomain = false;
        for (SkyBiomeSpatialZone zone : ordered) {
            if (zone.assignment().zoneIdentity().worldSeed() != worldSeed) {
                throw new IllegalArgumentException("zone world seed differs from field world seed");
            }
            if (!keys.add(zone.assignment().zoneIdentity().zoneKey())) {
                throw new IllegalArgumentException("duplicate SkyBiome zone key");
            }
            if (!domain.contains(zone.bounds())) {
                throw new IllegalArgumentException("SkyBiome zone lies outside the field domain");
            }
            if (zone.assignment().skyBiomeId().equals(SkyBiomeCatalog.OPEN_SKY_ID)
                    && zone.bounds().equals(domain)) {
                hasOpenSkyDomain = true;
            }
        }
        if (!hasOpenSkyDomain) {
            throw new IllegalArgumentException("field requires an open-sky zone covering its domain");
        }
        this.zones = List.copyOf(ordered);
    }

    public long worldSeed() {
        return worldSeed;
    }

    public WorldBounds domain() {
        return domain;
    }

    /** Returns zones in stable resolution order: sheltered, cloud-associated, then open sky. */
    public List<SkyBiomeSpatialZone> zones() {
        return zones;
    }

    /**
     * Resolves every in-domain point to exactly one authored assignment.
     *
     * <p>Overlapping regions are intentional: terrain shelter takes precedence over cloud-shelf
     * membership, which takes precedence over the open-sky domain assignment.
     */
    public SkyBiomeZoneAssignment resolve(double x, double y, double z) {
        if (!domain.contains(x, y, z)) {
            throw new IllegalArgumentException("point lies outside the SkyBiome field domain");
        }
        for (SkyBiomeSpatialZone zone : zones) {
            if (zone.bounds().contains(x, y, z)) {
                return zone.assignment();
            }
        }
        throw new IllegalStateException("field has no assignment for an in-domain point");
    }
}
