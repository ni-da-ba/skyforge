package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Curved backend-neutral corridor geometry for one AUTH-0024 topological cave link. */
public record SkyIslandCavePassageGeometry(
        int linkId,
        SkyIslandCaveConnectionKind kind,
        List<SkyIslandCavePassagePoint> points,
        double steeringSupport) {

    public SkyIslandCavePassageGeometry {
        if (linkId < 0) {
            throw new IllegalArgumentException("linkId must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        points = List.copyOf(points);
        if (points.size() < 3) {
            throw new IllegalArgumentException("cave passage requires at least three sampled points");
        }
        points.forEach(point -> Objects.requireNonNull(point, "passage point"));
        if (!Double.isFinite(steeringSupport) || steeringSupport < 0.0 || steeringSupport > 1.0) {
            throw new IllegalArgumentException("steeringSupport must be finite and in [0, 1]");
        }
    }

    public double meanHorizontalRadius() {
        return points.stream().mapToDouble(SkyIslandCavePassagePoint::horizontalRadius).average().orElse(0.0);
    }

    public double meanDepthRadius() {
        return points.stream().mapToDouble(SkyIslandCavePassagePoint::depthRadius).average().orElse(0.0);
    }
}
