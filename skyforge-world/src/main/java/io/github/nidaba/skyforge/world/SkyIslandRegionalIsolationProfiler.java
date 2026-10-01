package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Public AUTH-0092 producer for raw regional island-isolation evidence. */
public final class SkyIslandRegionalIsolationProfiler {

    public SkyIslandRegionalIsolationProfile profile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        return new SkyIslandRegionalIsolationProfile(
                Objects.requireNonNull(binding, "binding"));
    }
}
