package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Finite non-CASCADE portion of one parent F2B reach after explicit transition intervals are removed. */
public record SkyIslandOrdinaryHydraulicSpan(
        int parentReachStartCellIndex,
        int parentReachEndCellIndex,
        double parentStartStationFraction,
        double parentEndStationFraction,
        double parentStartArcLength,
        double parentEndArcLength,
        List<SkyIslandHydraulicGeometrySkeletonSample> samples,
        List<SkyIslandChannelProfileKind> sampleProfileKinds,
        SkyIslandGeomorphicQualificationClass qualificationClass,
        SkyIslandOrdinarySpanBoundary upstreamBoundary,
        SkyIslandOrdinarySpanBoundary downstreamBoundary) {

    private static final double EPSILON = 1.0e-9;

    public SkyIslandOrdinaryHydraulicSpan {
        if (parentReachStartCellIndex < 0
                || parentReachEndCellIndex < 0
                || parentReachStartCellIndex == parentReachEndCellIndex) {
            throw new IllegalArgumentException("ordinary span requires a valid parent reach identity");
        }
        requireFraction(parentStartStationFraction, "parentStartStationFraction");
        requireFraction(parentEndStationFraction, "parentEndStationFraction");
        if (!(parentEndStationFraction > parentStartStationFraction)) {
            throw new IllegalArgumentException("ordinary span must advance downstream");
        }
        if (!Double.isFinite(parentStartArcLength)
                || !Double.isFinite(parentEndArcLength)
                || !(parentEndArcLength > parentStartArcLength)) {
            throw new IllegalArgumentException("ordinary span arc interval must advance downstream");
        }
        samples = List.copyOf(samples);
        sampleProfileKinds = List.copyOf(sampleProfileKinds);
        qualificationClass = Objects.requireNonNull(qualificationClass, "qualificationClass");
        upstreamBoundary = Objects.requireNonNull(upstreamBoundary, "upstreamBoundary");
        downstreamBoundary = Objects.requireNonNull(downstreamBoundary, "downstreamBoundary");
        if (samples.size() < 2 || samples.size() != sampleProfileKinds.size()) {
            throw new IllegalArgumentException("ordinary span requires aligned profile/sample evidence");
        }
        samples.forEach(sample -> Objects.requireNonNull(sample, "sample"));
        sampleProfileKinds.forEach(kind -> {
            Objects.requireNonNull(kind, "profile kind");
            if (kind == SkyIslandChannelProfileKind.CASCADE) {
                throw new IllegalArgumentException("ordinary span cannot contain CASCADE samples");
            }
        });
        if (qualificationClass == SkyIslandGeomorphicQualificationClass.CASCADE) {
            throw new IllegalArgumentException("ordinary span cannot use CASCADE qualification class");
        }
        if (upstreamBoundary.state().reachStartCellIndex() != parentReachStartCellIndex
                || upstreamBoundary.state().reachEndCellIndex() != parentReachEndCellIndex
                || downstreamBoundary.state().reachStartCellIndex() != parentReachStartCellIndex
                || downstreamBoundary.state().reachEndCellIndex() != parentReachEndCellIndex) {
            throw new IllegalArgumentException("ordinary span boundaries must belong to parent reach");
        }
        if (Math.abs(upstreamBoundary.state().arcLength() - parentStartArcLength) > EPSILON
                || Math.abs(downstreamBoundary.state().arcLength() - parentEndArcLength) > EPSILON) {
            throw new IllegalArgumentException("ordinary span boundary arcs must match span interval");
        }
        if (Math.abs(samples.getFirst().arcLength()) > EPSILON
                || Math.abs(samples.getLast().arcLength() - pathLength()) > EPSILON
                || Math.abs(samples.getFirst().stationFraction()) > EPSILON
                || Math.abs(samples.getLast().stationFraction() - 1.0) > EPSILON) {
            throw new IllegalArgumentException("ordinary span samples must use local arc/station coordinates");
        }
    }

    public double pathLength() {
        return parentEndArcLength - parentStartArcLength;
    }

    public boolean boundaryDeferred() {
        return upstreamBoundary.status() == SkyIslandOrdinarySpanBoundaryStatus.DEFERRED
                || downstreamBoundary.status() == SkyIslandOrdinarySpanBoundaryStatus.DEFERRED;
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
