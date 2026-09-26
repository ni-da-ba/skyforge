package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Exact confluence ownership site before a finite transition envelope is sized. */
public record SkyIslandHydraulicConfluenceTransitionSite(
        int nodeCellIndex,
        SkyIslandLocalPosition nodePosition,
        List<SkyIslandHydraulicTransitionBoundaryState> boundaries) {

    private static final double EPSILON = 1.0e-10;

    public SkyIslandHydraulicConfluenceTransitionSite {
        if (nodeCellIndex < 0) {
            throw new IllegalArgumentException("nodeCellIndex must be non-negative");
        }
        nodePosition = Objects.requireNonNull(nodePosition, "nodePosition");
        boundaries = List.copyOf(boundaries);
        if (boundaries.size() < 3) {
            throw new IllegalArgumentException("confluence transition requires at least three incident boundaries");
        }
        long incoming = boundaries.stream()
                .filter(boundary -> boundary.role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING)
                .count();
        long outgoing = boundaries.stream()
                .filter(boundary -> boundary.role() == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING)
                .count();
        if (incoming < 2 || outgoing != 1) {
            throw new IllegalArgumentException(
                    "confluence transition requires at least two incoming and exactly one outgoing boundary");
        }
        for (SkyIslandHydraulicTransitionBoundaryState boundary : boundaries) {
            Objects.requireNonNull(boundary, "boundary");
            double expectedStation =
                    boundary.role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING ? 1.0 : 0.0;
            if (Math.abs(boundary.stationFraction() - expectedStation) > EPSILON) {
                throw new IllegalArgumentException(
                        "confluence boundary must coincide with its semantic reach endpoint");
            }
            if (Math.hypot(
                            boundary.position().x() - nodePosition.x(),
                            boundary.position().z() - nodePosition.z())
                    > EPSILON) {
                throw new IllegalArgumentException(
                        "confluence boundary must coincide with the shared C2 node position");
            }
        }
    }
}
