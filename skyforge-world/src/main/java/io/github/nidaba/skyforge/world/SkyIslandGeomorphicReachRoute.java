package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One semantic macro reach paired with its shared-node-consistent fine candidate route. */
public record SkyIslandGeomorphicReachRoute(
        SkyIslandSemanticChannelReach semanticReach,
        SkyIslandGeomorphicCandidateRoute route) {

    public SkyIslandGeomorphicReachRoute {
        semanticReach = Objects.requireNonNull(semanticReach, "semanticReach");
        route = Objects.requireNonNull(route, "route");
    }
}
