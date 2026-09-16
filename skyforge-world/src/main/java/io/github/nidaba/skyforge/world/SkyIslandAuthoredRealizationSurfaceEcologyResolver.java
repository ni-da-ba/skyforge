package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Surface-ecology projection over one explicit AUTH-0046 authored-realization catalog.
 *
 * <p>Unlike AUTH-0088, this resolver makes no AUTH-0058 publication claim. It exists for exact
 * production/runtime fixtures whose authored descriptor and realized volume are already explicitly
 * associated but whose volume is not part of an AUTH-0087 published binding.
 */
public final class SkyIslandAuthoredRealizationSurfaceEcologyResolver {
    private final SkyIslandAuthoredRealizationCatalog catalog;
    private final Map<SkyIslandWorldVolumeId, Entry> entries;

    public SkyIslandAuthoredRealizationSurfaceEcologyResolver(
            SkyIslandAuthoredRealizationCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Map<SkyIslandWorldVolumeId, Entry> built = new HashMap<>();
        for (SkyIslandAuthoredRealizationAssociation association : catalog.associations()) {
            SkyIslandWorldVolumeId volumeId = association.realizedVolumeId();
            Entry previous = built.put(
                    volumeId,
                    new Entry(
                            association,
                            new SkyIslandCompiledVolumeColumnField(
                                    association.realizedVolume().compiledVolume()),
                            SkyIslandSemanticFieldSet.create(
                                            association.authoredDescriptor())
                                    .interiority(),
                            SkyIslandEcologyField.create(
                                    association.authoredDescriptor())));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "authored-realization catalog contains duplicate volume identity");
            }
        }
        this.entries = Map.copyOf(built);
    }

    public SkyIslandAuthoredRealizationCatalog catalog() {
        return catalog;
    }

    /**
     * Samples one exact explicitly associated volume at a world-space horizontal position.
     *
     * <p>No physical Y is accepted. Unknown volume identity fails closed rather than triggering
     * spatial, seed, morphology, or encounter-order inference.
     */
    public SkyIslandAuthoredRealizationSurfaceEcologySample sample(
            SkyIslandWorldVolumeId volumeId,
            Coordinate2 worldPosition) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(worldPosition, "worldPosition");
        Entry entry = entries.get(volumeId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "surface-ecology query references an unbound realized volume: " + volumeId.path());
        }

        var realized = entry.association().realizedVolume().compiledVolume().descriptor();
        SkyIslandLocalPosition local = new SkyIslandLocalPosition(
                worldPosition.x() - realized.centerX(),
                worldPosition.z() - realized.centerZ());
        boolean physicalColumnPresent = entry.columns().columnAt(local).isPresent();
        double authoredInteriority = entry.interiority().sample(local);
        SkyIslandEcologySample ecology = physicalColumnPresent && authoredInteriority > 0.0
                ? entry.ecology().sample(local)
                : null;

        return new SkyIslandAuthoredRealizationSurfaceEcologySample(
                entry.association(),
                worldPosition,
                local,
                physicalColumnPresent,
                authoredInteriority,
                ecology);
    }

    private record Entry(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandCompiledVolumeColumnField columns,
            SkyIslandSemanticField interiority,
            SkyIslandEcologyField ecology) {}
}
