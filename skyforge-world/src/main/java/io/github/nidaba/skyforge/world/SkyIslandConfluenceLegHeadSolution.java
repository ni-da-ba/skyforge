package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Solved finite-boundary head for one F3A confluence leg. */
public record SkyIslandConfluenceLegHeadSolution(
        SkyIslandHydraulicTransitionLegGeometry leg,
        double finiteBoundaryHeadWorldUnits) {

    public SkyIslandConfluenceLegHeadSolution {
        leg = Objects.requireNonNull(leg, "leg");
        if (!Double.isFinite(finiteBoundaryHeadWorldUnits)) {
            throw new IllegalArgumentException(
                    "finiteBoundaryHeadWorldUnits must be finite");
        }
    }
}
