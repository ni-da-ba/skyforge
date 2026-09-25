package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral continuous retained-open-water candidate plan. */
public record SkyIslandContinuousWaterbodyPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandContinuousWaterbodyBasin> basins,
        List<SkyIslandWaterbodyCandidate> deferredWetlands) {

    public SkyIslandContinuousWaterbodyPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        basins = List.copyOf(basins);
        basins.forEach(basin -> Objects.requireNonNull(basin, "basin"));
        deferredWetlands = List.copyOf(deferredWetlands);
        deferredWetlands.forEach(candidate -> Objects.requireNonNull(candidate, "deferred wetland"));
    }
}
