package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral geomorphic profile plan for the accepted channel network of one island. */
public record SkyIslandChannelProfilePlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandChannelProfile> profiles) {

    public SkyIslandChannelProfilePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        profiles = List.copyOf(profiles);
        profiles.forEach(profile -> Objects.requireNonNull(profile, "profile"));
    }

    public long count(SkyIslandChannelProfileKind kind) {
        return profiles.stream().filter(profile -> profile.kind() == kind).count();
    }

    public double maxWidthPotential() {
        return profiles.stream().mapToDouble(SkyIslandChannelProfile::bankfullWidthPotential).max().orElse(0.0);
    }

    public double maxDepthPotential() {
        return profiles.stream().mapToDouble(SkyIslandChannelProfile::depthPotential).max().orElse(0.0);
    }

    public double maxIncisionPotential() {
        return profiles.stream().mapToDouble(SkyIslandChannelProfile::incisionPotential).max().orElse(0.0);
    }

    public double maxGradientPotential() {
        return profiles.stream().mapToDouble(SkyIslandChannelProfile::gradientPotential).max().orElse(0.0);
    }
}
