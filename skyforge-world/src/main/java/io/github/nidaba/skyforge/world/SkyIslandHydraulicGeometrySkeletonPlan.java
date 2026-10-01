package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Head-independent C2 hydraulic geometry for one authored island. */
public record SkyIslandHydraulicGeometrySkeletonPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandGeomorphicChannelNetworkPlan geomorphicNetwork,
        List<SkyIslandHydraulicReachSkeleton> reaches) {

    public SkyIslandHydraulicGeometrySkeletonPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        geomorphicNetwork = Objects.requireNonNull(geomorphicNetwork, "geomorphicNetwork");
        reaches = List.copyOf(reaches);
        reaches.forEach(reach -> Objects.requireNonNull(reach, "reach"));
        if (!descriptor.equals(geomorphicNetwork.descriptor())) {
            throw new IllegalArgumentException("skeleton network descriptor must match descriptor");
        }
    }
}
