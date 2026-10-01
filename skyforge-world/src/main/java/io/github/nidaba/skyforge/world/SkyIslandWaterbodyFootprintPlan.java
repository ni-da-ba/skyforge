package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic retained-waterbody footprint plan for one authored island. */
public record SkyIslandWaterbodyFootprintPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandWaterbodyFootprint> footprints) {

    public SkyIslandWaterbodyFootprintPlan {
        Objects.requireNonNull(descriptor, "descriptor");
        footprints = List.copyOf(footprints);
    }
}
