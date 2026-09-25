package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral semantic macro reaches used by the continuous hydrology solver. */
public record SkyIslandSemanticChannelReachPlan(
        SkyIslandDescriptor descriptor,
        double planningSpacing,
        List<SkyIslandSemanticChannelReach> reaches) {

    public SkyIslandSemanticChannelReachPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        reaches = List.copyOf(reaches);
        reaches.forEach(reach -> Objects.requireNonNull(reach, "reach"));
    }

    public int coarseSegmentCount() {
        return reaches.stream().mapToInt(SkyIslandSemanticChannelReach::coarseSegmentCount).sum();
    }
}
