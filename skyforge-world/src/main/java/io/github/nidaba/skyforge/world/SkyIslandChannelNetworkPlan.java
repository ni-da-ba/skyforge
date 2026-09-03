package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral semantic hierarchy for the selected channel network of one island. */
public record SkyIslandChannelNetworkPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandChannelSegment> segments,
        int maxStreamOrder) {

    public SkyIslandChannelNetworkPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        segments = List.copyOf(segments);
        if (maxStreamOrder < 0 || (!segments.isEmpty() && maxStreamOrder < 1)) {
            throw new IllegalArgumentException("invalid maxStreamOrder");
        }
    }

    public long count(SkyIslandChannelRole role) {
        return segments.stream().filter(segment -> segment.role() == role).count();
    }
}
