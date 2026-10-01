package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One sub-grid naturalized geometric realization of an accepted semantic channel segment. */
public record SkyIslandNaturalizedChannelPath(
        SkyIslandChannelProfile profile,
        List<SkyIslandLocalPosition> points,
        double chordLength,
        double pathLength,
        double maxChordDeviation) {

    private static final double EPSILON = 1.0e-10;

    public SkyIslandNaturalizedChannelPath {
        profile = Objects.requireNonNull(profile, "profile");
        points = List.copyOf(points);
        points.forEach(point -> Objects.requireNonNull(point, "path point"));
        if (points.size() < 2) {
            throw new IllegalArgumentException("naturalized path requires at least two points");
        }
        if (!points.getFirst().equals(profile.segment().start())
                || !points.getLast().equals(profile.segment().end())) {
            throw new IllegalArgumentException("naturalized path must preserve accepted segment endpoints");
        }
        if (!Double.isFinite(chordLength) || chordLength <= 0.0) {
            throw new IllegalArgumentException("chordLength must be finite and positive");
        }
        if (!Double.isFinite(pathLength) || pathLength + EPSILON < chordLength) {
            throw new IllegalArgumentException("pathLength cannot be shorter than its endpoint chord");
        }
        if (!Double.isFinite(maxChordDeviation) || maxChordDeviation < 0.0) {
            throw new IllegalArgumentException("maxChordDeviation must be finite and non-negative");
        }
    }

    public double lengthRatio() {
        return pathLength / chordLength;
    }
}
