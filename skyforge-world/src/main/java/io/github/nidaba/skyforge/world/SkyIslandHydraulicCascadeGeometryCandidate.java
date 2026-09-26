package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Exact finite C2 centerline interval owned by one authored contiguous CASCADE run.
 *
 * <p>The interval itself is already semantically authored; this record adds no drop-head or terrain
 * authority.
 */
public record SkyIslandHydraulicCascadeGeometryCandidate(
        SkyIslandHydraulicCascadeTransitionSite transitionSite,
        List<SkyIslandLocalPosition> centerlinePoints,
        double pathLength) {

    public SkyIslandHydraulicCascadeGeometryCandidate {
        transitionSite = Objects.requireNonNull(transitionSite, "transitionSite");
        centerlinePoints = List.copyOf(centerlinePoints);
        if (centerlinePoints.size() < 2) {
            throw new IllegalArgumentException("cascade geometry requires at least two points");
        }
        centerlinePoints.forEach(point -> Objects.requireNonNull(point, "centerline point"));
        if (!Double.isFinite(pathLength) || pathLength <= 0.0) {
            throw new IllegalArgumentException("cascade pathLength must be finite and positive");
        }
        double expected =
                transitionSite.downstreamBoundary().arcLength()
                        - transitionSite.upstreamBoundary().arcLength();
        if (Math.abs(pathLength - expected) > 1.0e-9) {
            throw new IllegalArgumentException(
                    "cascade geometry path length must match transition boundary arc lengths");
        }
    }
}
