package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * One incident confluence leg from the exact shared F3 node state to a finite ordinary boundary.
 *
 * <p>The retreat length is evidence geometry only; it grants no hydraulic-head or terrain authority.
 */
public record SkyIslandHydraulicTransitionLegGeometry(
        SkyIslandHydraulicTransitionBoundaryState nodeBoundary,
        SkyIslandHydraulicTransitionBoundaryState finiteBoundary,
        double retreatLength) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandHydraulicTransitionLegGeometry {
        nodeBoundary = Objects.requireNonNull(nodeBoundary, "nodeBoundary");
        finiteBoundary = Objects.requireNonNull(finiteBoundary, "finiteBoundary");
        if (nodeBoundary.role() != finiteBoundary.role()
                || nodeBoundary.reachStartCellIndex() != finiteBoundary.reachStartCellIndex()
                || nodeBoundary.reachEndCellIndex() != finiteBoundary.reachEndCellIndex()) {
            throw new IllegalArgumentException("transition leg boundaries must belong to one incident reach");
        }
        if (!Double.isFinite(retreatLength) || retreatLength <= 0.0) {
            throw new IllegalArgumentException("retreatLength must be finite and positive");
        }
        double arcRetreat =
                Math.abs(nodeBoundary.arcLength() - finiteBoundary.arcLength());
        if (Math.abs(arcRetreat - retreatLength) > EPSILON) {
            throw new IllegalArgumentException(
                    "transition leg retreat must equal F2B arc-length separation");
        }
        if (nodeBoundary.role() == SkyIslandHydraulicTransitionBoundaryRole.INCOMING
                && !(finiteBoundary.stationFraction() < nodeBoundary.stationFraction())) {
            throw new IllegalArgumentException("incoming finite boundary must retreat upstream");
        }
        if (nodeBoundary.role() == SkyIslandHydraulicTransitionBoundaryRole.OUTGOING
                && !(finiteBoundary.stationFraction() > nodeBoundary.stationFraction())) {
            throw new IllegalArgumentException("outgoing finite boundary must retreat downstream");
        }
    }
}
