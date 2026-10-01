package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Geometric realization of one semantic cave-system graph, still backend-neutral. */
public record SkyIslandCaveSystemGeometry(
        int systemId,
        List<SkyIslandCaveChamberGeometry> chambers,
        List<SkyIslandCavePassageGeometry> passages) {

    public SkyIslandCaveSystemGeometry {
        if (systemId < 0) {
            throw new IllegalArgumentException("systemId must be non-negative");
        }
        chambers = List.copyOf(chambers);
        passages = List.copyOf(passages);
        if (chambers.isEmpty()) {
            throw new IllegalArgumentException("cave system geometry requires at least one chamber");
        }
        chambers.forEach(chamber -> Objects.requireNonNull(chamber, "chamber"));
        passages.forEach(passage -> Objects.requireNonNull(passage, "passage"));
    }
}
