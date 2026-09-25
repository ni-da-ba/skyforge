package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** A smoothed, uniformly sampled centerline derived from one accepted fine search path. */
public record SkyIslandContinuousChannelCenterline(
        SkyIslandGeomorphicCandidateRoute searchRoute,
        List<SkyIslandLocalPosition> points,
        double pathLength,
        double maximumSearchPathDeviation,
        double maximumTurnAngleRadians) {

    public SkyIslandContinuousChannelCenterline {
        searchRoute = Objects.requireNonNull(searchRoute, "searchRoute");
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new IllegalArgumentException("continuous centerline requires at least two points");
        }
        points.forEach(point -> Objects.requireNonNull(point, "point"));
        if (!points.getFirst().equals(searchRoute.points().getFirst())
                || !points.getLast().equals(searchRoute.points().getLast())) {
            throw new IllegalArgumentException("continuous centerline must preserve search-route endpoints");
        }
        if (!Double.isFinite(pathLength) || pathLength <= 0.0) {
            throw new IllegalArgumentException("pathLength must be finite and positive");
        }
        if (!Double.isFinite(maximumSearchPathDeviation) || maximumSearchPathDeviation < 0.0) {
            throw new IllegalArgumentException("maximumSearchPathDeviation must be finite and non-negative");
        }
        if (!Double.isFinite(maximumTurnAngleRadians)
                || maximumTurnAngleRadians < 0.0
                || maximumTurnAngleRadians > Math.PI) {
            throw new IllegalArgumentException("maximumTurnAngleRadians must be finite and in [0, pi]");
        }
    }
}
