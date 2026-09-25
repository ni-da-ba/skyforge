package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Network-level hard geomorphic qualification performed before any hydrologic terrain mutation. */
public record SkyIslandGeomorphicQualificationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicChannelNetworkPlan hydraulicPlan,
        SkyIslandGeomorphicQualificationPolicy policy,
        List<SkyIslandGeomorphicReachQualification> reaches) {

    public SkyIslandGeomorphicQualificationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        hydraulicPlan = Objects.requireNonNull(hydraulicPlan, "hydraulicPlan");
        policy = Objects.requireNonNull(policy, "policy");
        reaches = List.copyOf(reaches);
        reaches.forEach(reach -> Objects.requireNonNull(reach, "reach"));
    }

    public boolean accepted() {
        return reaches.stream().allMatch(SkyIslandGeomorphicReachQualification::accepted);
    }

    public long rejectedReachCount() {
        return reaches.stream().filter(reach -> !reach.accepted()).count();
    }

    public long violationCount(SkyIslandGeomorphicQualificationViolation violation) {
        return reaches.stream()
                .filter(reach -> reach.violations().contains(violation))
                .count();
    }
}
