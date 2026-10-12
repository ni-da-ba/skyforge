package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * A fixed, pre-hydraulic channel-bed and valley candidate sampled along one semantic reach.
 *
 * <p>The candidate is geometry, not a water-surface solution or realization authorization. Its
 * longitudinal bed and lateral section anchors are constructed before any open-channel calculation;
 * a later solver must evaluate this fixed candidate without moving it. D2 remains an independent
 * post-solve qualification step.
 */
public record SkyIslandHydraulicCandidateBed(
        SkyIslandGeomorphicReachRoute geomorphicRoute,
        SkyIslandContinuousChannelCenterline centerline,
        List<Station> stations,
        double pathLengthWorldUnits) {

    public SkyIslandHydraulicCandidateBed {
        geomorphicRoute = Objects.requireNonNull(geomorphicRoute, "geomorphicRoute");
        centerline = Objects.requireNonNull(centerline, "centerline");
        stations = List.copyOf(stations);
        if (!centerline.searchRoute().equals(geomorphicRoute.route())) {
            throw new IllegalArgumentException("candidate bed centerline must match its geomorphic route");
        }
        if (stations.size() < 2 || stations.size() != centerline.points().size()) {
            throw new IllegalArgumentException("candidate bed needs one station per centerline point");
        }
        for (int i = 0; i < stations.size(); i++) {
            Station station = Objects.requireNonNull(stations.get(i), "station");
            if (i > 0 && !(station.stationWorldUnits() > stations.get(i - 1).stationWorldUnits())) {
                throw new IllegalArgumentException("candidate bed stations must increase strictly");
            }
        }
        if (!Double.isFinite(pathLengthWorldUnits)
                || pathLengthWorldUnits <= 0.0
                || Math.abs(pathLengthWorldUnits - centerline.pathLength()) > 1.0e-9) {
            throw new IllegalArgumentException("candidate bed path length must match its centerline");
        }
    }

    /** One candidate channel cross-section at a longitudinal station. */
    public record Station(
            SkyIslandLocalPosition position,
            double stationWorldUnits,
            double relativeDischarge,
            double bankfullHalfWidthWorldUnits,
            double targetFlowDepthWorldUnits,
            double originalTerrainElevationWorldUnits,
            double candidateBedElevationWorldUnits,
            List<CrossSectionPoint> crossSection) {

        public Station {
            position = Objects.requireNonNull(position, "position");
            crossSection = List.copyOf(crossSection);
            requireFiniteNonNegative(stationWorldUnits, "stationWorldUnits");
            requireFraction(relativeDischarge, "relativeDischarge");
            requireFinitePositive(bankfullHalfWidthWorldUnits, "bankfullHalfWidthWorldUnits");
            requireFinitePositive(targetFlowDepthWorldUnits, "targetFlowDepthWorldUnits");
            requireFiniteNonNegative(originalTerrainElevationWorldUnits, "originalTerrainElevationWorldUnits");
            requireFiniteNonNegative(candidateBedElevationWorldUnits, "candidateBedElevationWorldUnits");
            if (!(candidateBedElevationWorldUnits < originalTerrainElevationWorldUnits)) {
                throw new IllegalArgumentException("candidate bed must be below original terrain");
            }
            if (crossSection.size() < 3) {
                throw new IllegalArgumentException("candidate valley section needs center and shoulder anchors");
            }
            boolean hasBedAnchor = false;
            for (int i = 0; i < crossSection.size(); i++) {
                CrossSectionPoint point = Objects.requireNonNull(crossSection.get(i), "cross-section point");
                if (i > 0 && !(point.lateralOffsetWorldUnits()
                        > crossSection.get(i - 1).lateralOffsetWorldUnits())) {
                    throw new IllegalArgumentException("cross-section offsets must increase strictly");
                }
                if (point.lateralOffsetWorldUnits() == 0.0) {
                    hasBedAnchor = Math.abs(point.candidateElevationWorldUnits()
                            - candidateBedElevationWorldUnits) <= 1.0e-9;
                }
            }
            if (!hasBedAnchor) {
                throw new IllegalArgumentException("cross-section must include the candidate bed at offset zero");
            }
        }
    }

    /** Fixed terrain elevation at one signed lateral offset; candidates may cut but never fill. */
    public record CrossSectionPoint(
            double lateralOffsetWorldUnits,
            double originalElevationWorldUnits,
            double candidateElevationWorldUnits) {
        public CrossSectionPoint {
            if (!Double.isFinite(lateralOffsetWorldUnits)) {
                throw new IllegalArgumentException("lateral offset must be finite");
            }
            requireFiniteNonNegative(originalElevationWorldUnits, "originalElevationWorldUnits");
            requireFiniteNonNegative(candidateElevationWorldUnits, "candidateElevationWorldUnits");
            if (candidateElevationWorldUnits > originalElevationWorldUnits + 1.0e-9) {
                throw new IllegalArgumentException("candidate valley terrain cannot exceed original terrain");
            }
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
