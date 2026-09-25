package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Pre-carving hydraulic geometry for the candidate channel network.
 *
 * <p>This is diagnostic/qualification state, not yet terrain-authoring authority.
 */
public record SkyIslandHydraulicChannelNetworkPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandGeomorphicChannelNetworkPlan geomorphicNetwork,
        List<SkyIslandHydraulicReachGeometry> reaches,
        double maximumRequiredLowering,
        double meanRequiredLowering,
        double maximumWaterSurfaceSlope) {

    public SkyIslandHydraulicChannelNetworkPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        geomorphicNetwork = Objects.requireNonNull(geomorphicNetwork, "geomorphicNetwork");
        reaches = List.copyOf(reaches);
        reaches.forEach(reach -> Objects.requireNonNull(reach, "reach"));
        requireFiniteNonNegative(maximumRequiredLowering, "maximumRequiredLowering");
        requireFiniteNonNegative(meanRequiredLowering, "meanRequiredLowering");
        requireFiniteNonNegative(maximumWaterSurfaceSlope, "maximumWaterSurfaceSlope");
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
