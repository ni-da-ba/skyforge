package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import java.util.Objects;

/**
 * AUTH-0088 world-space surface-ecology projection for an exact AUTH-0087 published authored
 * realization binding.
 *
 * <p>The publication gate remains mandatory here. Projection math delegates to the exact AUTH-0046
 * association-catalog resolver so published and non-published explicit-association consumers cannot
 * drift onto different world/local or ecology semantics.
 */
public final class SkyIslandPublishedSurfaceEcologyResolver {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final SkyIslandAuthoredRealizationSurfaceEcologyResolver delegate;

    public SkyIslandPublishedSurfaceEcologyResolver(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.delegate = new SkyIslandAuthoredRealizationSurfaceEcologyResolver(
                binding.associationCatalog());
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
        SkyIslandAuthoredRealizationSurfaceEcologySample projected =
                delegate.sample(volumeId, worldPosition);
        return new SkyIslandPublishedSurfaceEcologySample(
                projected.association(),
                projected.worldPosition(),
                projected.localPosition(),
                projected.physicalColumnPresent(),
                projected.authoredInteriority(),
                projected.ecology());
    }
}
