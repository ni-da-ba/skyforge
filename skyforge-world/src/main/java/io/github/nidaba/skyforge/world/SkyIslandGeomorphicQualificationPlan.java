package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Network-level hard plausibility qualification before any fluvial terrain mutation. */
public record SkyIslandGeomorphicQualificationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicChannelNetworkPlan hydraulicPlan,
        List<SkyIslandGeomorphicReachQualification> reaches) {

    public SkyIslandGeomorphicQualificationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        hydraulicPlan = Objects.requireNonNull(hydraulicPlan, "hydraulicPlan");
        reaches = List.copyOf(reaches);
        reaches.forEach(reach -> Objects.requireNonNull(reach, "reach"));
    }

    public boolean accepted() {
        return reaches.stream().allMatch(SkyIslandGeomorphicReachQualification::accepted);
    }

    public long rejectedReachCount() {
        return reaches.stream().filter(reach -> !reach.accepted()).count();
    }

    public long failureCount(SkyIslandGeomorphicQualificationFailureKind kind) {
        return reaches.stream()
                .flatMap(reach -> reach.failures().stream())
                .filter(kind::equals)
                .count();
    }
}
