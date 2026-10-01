package io.github.nidaba.skyforge.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One connected semantic cave-system graph. */
public record SkyIslandCaveSystem(
        int systemId,
        List<SkyIslandCaveNode> nodes,
        List<SkyIslandCaveLink> links) {

    public SkyIslandCaveSystem {
        if (systemId < 0) {
            throw new IllegalArgumentException("systemId must be non-negative");
        }
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("cave system must contain at least one node");
        }
        nodes.forEach(node -> Objects.requireNonNull(node, "cave node"));
        links.forEach(link -> Objects.requireNonNull(link, "cave link"));

        Set<Integer> nodeIds = new HashSet<>();
        for (SkyIslandCaveNode node : nodes) {
            if (!nodeIds.add(node.nodeId())) {
                throw new IllegalArgumentException("duplicate cave node identifier");
            }
        }
        for (SkyIslandCaveLink link : links) {
            if (!nodeIds.contains(link.firstNodeId()) || !nodeIds.contains(link.secondNodeId())) {
                throw new IllegalArgumentException("cave link endpoint is outside its cave system");
            }
        }
    }

    public double meanGroundwaterPotential() {
        return nodes.stream()
                .mapToDouble(SkyIslandCaveNode::groundwaterPotential)
                .average()
                .orElse(0.0);
    }

    public boolean waterInfluenced() {
        return meanGroundwaterPotential() >= 0.48
                || links.stream().anyMatch(link -> link.aquiferSupport() >= 0.42);
    }

    public int sourceVoidRegionCount() {
        return (int) nodes.stream().map(SkyIslandCaveNode::sourceVoidRegionId).distinct().count();
    }
}
