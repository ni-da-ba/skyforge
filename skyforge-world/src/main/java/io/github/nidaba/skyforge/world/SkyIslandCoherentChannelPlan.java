package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Backend-neutral coherent visible-channel skeleton selected from accepted channel components. */
public record SkyIslandCoherentChannelPlan(
        SkyIslandDescriptor descriptor,
        double planningSpacing,
        int sourceComponentCount,
        List<SkyIslandCoherentChannelComponent> retainedComponents) {

    public SkyIslandCoherentChannelPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        if (sourceComponentCount < 0) {
            throw new IllegalArgumentException("sourceComponentCount must be non-negative");
        }
        retainedComponents = List.copyOf(retainedComponents);
        retainedComponents.forEach(component -> Objects.requireNonNull(component, "component"));
        if (retainedComponents.size() > sourceComponentCount) {
            throw new IllegalArgumentException("cannot retain more components than the source network contains");
        }
    }

    public int retainedComponentCount() {
        return retainedComponents.size();
    }

    public int prunedComponentCount() {
        return sourceComponentCount - retainedComponents.size();
    }

    public int retainedReachCount() {
        return retainedComponents.stream().mapToInt(SkyIslandCoherentChannelComponent::reachCount).sum();
    }

    public List<SkyIslandChannelProfile> profiles() {
        return retainedComponents.stream()
                .flatMap(component -> component.profiles().stream())
                .sorted(Comparator.comparingInt(profile -> profile.segment().sourceCellIndex()))
                .toList();
    }
}
