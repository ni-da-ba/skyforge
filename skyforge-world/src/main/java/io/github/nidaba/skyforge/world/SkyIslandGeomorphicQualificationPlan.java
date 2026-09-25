package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Network-level geomorphic qualification completed before any hydrologic terrain mutation. */
public record SkyIslandGeomorphicQualificationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandGeomorphicQualificationPolicy policy,
        List<SkyIslandGeomorphicReachQualification> reaches) {

    public SkyIslandGeomorphicQualificationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        policy = Objects.requireNonNull(policy, "policy");
        reaches = List.copyOf(reaches);
        reaches.forEach(r -> Objects.requireNonNull(r, "reach"));
    }

    public boolean accepted() {
        return reaches.stream().allMatch(SkyIslandGeomorphicReachQualification::accepted);
    }

    public long rejectedReachCount() {
        return reaches.stream().filter(r -> !r.accepted()).count();
    }

    public long violationCount(SkyIslandGeomorphicQualificationViolation violation) {
        Objects.requireNonNull(violation, "violation");
        return reaches.stream().filter(r -> r.violations().contains(violation)).count();
    }
}
