package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** AUTH-0103 raw isolation evidence over an explicit AUTH-0046 association catalog. */
public final class SkyIslandAuthoredRealizationIsolationProfiler {
    public SkyIslandAuthoredRealizationIsolationProfile profile(
            SkyIslandAuthoredRealizationCatalog catalog) {
        return new SkyIslandAuthoredRealizationIsolationProfile(
                Objects.requireNonNull(catalog, "catalog"));
    }
}
