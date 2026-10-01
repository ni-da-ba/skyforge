package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable deterministic world catalog queried by backend spatial regions. */
public final class SkyIslandWorldCatalog {
    private final long rootSeed;
    private final List<SkyIslandWorldVolume> volumes;

    /** Freezes plan-order volumes and rejects duplicate world identities. */
    public SkyIslandWorldCatalog(long rootSeed, List<SkyIslandWorldVolume> volumes) {
        this.rootSeed = rootSeed;
        Objects.requireNonNull(volumes, "volumes");
        this.volumes = List.copyOf(volumes);
        Set<SkyIslandWorldVolumeId> identities = new HashSet<>();
        for (SkyIslandWorldVolume volume : this.volumes) {
            Objects.requireNonNull(volume, "world volume");
            if (volume.id().archipelagoRootSeed() != rootSeed) {
                throw new IllegalArgumentException(
                        "world volume root seed differs from catalog root seed");
            }
            if (!identities.add(volume.id())) {
                throw new IllegalArgumentException("duplicate world volume identity: " + volume.id().path());
            }
        }
    }

    /** Root identity of the planned regional hierarchy. */
    public long rootSeed() {
        return rootSeed;
    }

    /** Number of independently compiled island volumes available to backend queries. */
    public int volumeCount() {
        return volumes.size();
    }

    /** Immutable deterministic plan-order view of every world volume. */
    public List<SkyIslandWorldVolume> volumes() {
        return volumes;
    }

    /**
     * Returns all conservatively bounded volumes that overlap or touch a backend query region.
     *
     * <p>Result order is the stable archipelago/group/member plan order. The first implementation
     * uses a linear scan intentionally; later spatial acceleration can remain an internal detail.
     */
    public List<SkyIslandWorldVolume> query(WorldBounds region) {
        Objects.requireNonNull(region, "region");
        ArrayList<SkyIslandWorldVolume> result = new ArrayList<>();
        for (SkyIslandWorldVolume volume : volumes) {
            if (volume.bounds().intersects(region)) {
                result.add(volume);
            }
        }
        return List.copyOf(result);
    }
}
