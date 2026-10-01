package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral candidate channel network with one shared physical location per semantic graph node.
 *
 * <p>This plan is pre-hydraulic and pre-carving. It is not yet publishable as production hydrology.
 */
public record SkyIslandGeomorphicChannelNetworkPlan(
        SkyIslandDescriptor descriptor,
        double planningSpacing,
        List<SkyIslandGeomorphicNetworkNode> nodes,
        List<SkyIslandGeomorphicReachRoute> routes) {

    public SkyIslandGeomorphicChannelNetworkPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        nodes = List.copyOf(nodes);
        routes = List.copyOf(routes);
        nodes.forEach(node -> Objects.requireNonNull(node, "node"));
        routes.forEach(route -> Objects.requireNonNull(route, "route"));
    }

    public SkyIslandGeomorphicNetworkNode requireNode(int cellIndex) {
        return nodes.stream()
                .filter(node -> node.cellIndex() == cellIndex)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing geomorphic network node " + cellIndex));
    }

    public long count(SkyIslandGeomorphicNetworkNodeKind kind) {
        return nodes.stream().filter(node -> node.kind() == kind).count();
    }
}
