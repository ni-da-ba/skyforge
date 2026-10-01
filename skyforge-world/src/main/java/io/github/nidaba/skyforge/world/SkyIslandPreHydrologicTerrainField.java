package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Explicit backend-neutral terrain substrate before any hydrologic terrain response is applied.
 *
 * <p>The post-H6 hydrology reset must not route against AUTH-0014/AUTH-0015 incision, deposition,
 * floodplain, drop, or retained-waterbody shaping. This field therefore exposes the current authored
 * elevation tendency directly and is the authority boundary at which a future compiled-surface
 * projection may be substituted without reintroducing legacy hydrologic adjustments.
 */
public final class SkyIslandPreHydrologicTerrainField implements SkyIslandSemanticField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticField elevation;

    private SkyIslandPreHydrologicTerrainField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.elevation = SkyIslandSemanticFieldSet.create(descriptor).elevationTendency();
    }

    public static SkyIslandPreHydrologicTerrainField create(SkyIslandDescriptor descriptor) {
        return new SkyIslandPreHydrologicTerrainField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public double sample(SkyIslandLocalPosition position) {
        return elevation.sample(Objects.requireNonNull(position, "position"));
    }
}
