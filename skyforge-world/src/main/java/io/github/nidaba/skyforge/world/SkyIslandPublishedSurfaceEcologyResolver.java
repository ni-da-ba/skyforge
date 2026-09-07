package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0088 world-space surface-ecology projection for an exact AUTH-0087 published authored
 * realization binding.
 *
 * <p>The caller supplies the exact published volume identity. This resolver never discovers or
 * ranks islands from position, bounds, seed, or encounter order. World X/Z are translated through
 * the explicit AUTH-0046 association; physical horizontal support and the current authored domain
 * gate the unchanged AUTH-0003 ecology field.
 */
public final class SkyIslandPublishedSurfaceEcologyResolver {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final Map<SkyIslandWorldVolumeId, Entry> entries;

    public SkyIslandPublishedSurfaceEcologyResolver(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
        Map<SkyIslandWorldVolumeId, Entry> built = new HashMap<>();
        for (SkyIslandAuthoredRealizationAssociation association :
                binding.associationCatalog().associations()) {
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
                        "published authored-realization binding contains duplicate volume identity");
            }
        }
        this.entries = Map.copyOf(built);
    }

    public SkyIslandPublishedAuthoredRealizationBinding binding() {
        return binding;
    }

    /**
     * Samples one exact published volume at a world-space horizontal position.
     *
     * <p>No physical Y is accepted here. The backend owns the vertical/quart-cell envelope in which
     * this surface ecology is presented.
     */
    public SkyIslandPublishedSurfaceEcologySample sample(
            SkyIslandWorldVolumeId volumeId,
            Coordinate2 worldPosition) {
        Objects.requireNonNull(volumeId, "volumeId");
        Objects.requireNonNull(worldPosition, "worldPosition");
        Entry entry = entries.get(volumeId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "surface-ecology query references an unbound published volume: " + volumeId.path());
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

        return new SkyIslandPublishedSurfaceEcologySample(
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
