package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One contiguous authored CASCADE profile run with exact upstream/downstream boundary states. */
public record SkyIslandHydraulicCascadeTransitionSite(
        int reachStartCellIndex,
        int reachEndCellIndex,
        int firstProfileIndex,
        int lastProfileIndexExclusive,
        int cascadeStartCellIndex,
        int cascadeEndCellIndex,
        SkyIslandHydraulicTransitionBoundaryState upstreamBoundary,
        SkyIslandHydraulicTransitionBoundaryState downstreamBoundary) {

    public SkyIslandHydraulicCascadeTransitionSite {
        if (reachStartCellIndex < 0
                || reachEndCellIndex < 0
                || reachStartCellIndex == reachEndCellIndex) {
            throw new IllegalArgumentException("cascade transition requires a valid reach identity");
        }
        if (firstProfileIndex < 0 || lastProfileIndexExclusive <= firstProfileIndex) {
            throw new IllegalArgumentException("cascade profile interval must be non-empty");
        }
        if (cascadeStartCellIndex < 0
                || cascadeEndCellIndex < 0
                || cascadeStartCellIndex == cascadeEndCellIndex) {
            throw new IllegalArgumentException("cascade transition requires distinct coarse anchors");
        }
        upstreamBoundary = Objects.requireNonNull(upstreamBoundary, "upstreamBoundary");
        downstreamBoundary = Objects.requireNonNull(downstreamBoundary, "downstreamBoundary");
        if (upstreamBoundary.role() != SkyIslandHydraulicTransitionBoundaryRole.INCOMING
                || downstreamBoundary.role() != SkyIslandHydraulicTransitionBoundaryRole.OUTGOING) {
            throw new IllegalArgumentException("cascade boundary roles must be incoming then outgoing");
        }
        if (upstreamBoundary.reachStartCellIndex() != reachStartCellIndex
                || upstreamBoundary.reachEndCellIndex() != reachEndCellIndex
                || downstreamBoundary.reachStartCellIndex() != reachStartCellIndex
                || downstreamBoundary.reachEndCellIndex() != reachEndCellIndex) {
            throw new IllegalArgumentException("cascade boundaries must belong to the owning reach");
        }
        if (!(downstreamBoundary.stationFraction() > upstreamBoundary.stationFraction())) {
            throw new IllegalArgumentException("cascade transition must advance downstream");
        }
    }

    public int profileCount() {
        return lastProfileIndexExclusive - firstProfileIndex;
    }
}
