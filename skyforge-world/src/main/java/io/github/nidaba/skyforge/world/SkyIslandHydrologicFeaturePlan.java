package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Semantic hydrologic feature candidates for one authored island. */
public record SkyIslandHydrologicFeaturePlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandHydrologicFeature> features) {

    public SkyIslandHydrologicFeaturePlan {
        Objects.requireNonNull(descriptor, "descriptor");
        features = List.copyOf(features);
    }

    public long count(SkyIslandHydrologicFeatureKind kind) {
        return features.stream().filter(f -> f.kind() == kind).count();
    }
}
