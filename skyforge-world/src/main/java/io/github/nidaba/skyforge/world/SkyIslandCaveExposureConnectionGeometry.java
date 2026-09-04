package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * One authored boundary connection from an accepted AUTH-0028 cave exposure intent.
 *
 * <p>The connection is backend-neutral sampled corridor geometry. It may move the final mouth
 * horizontally away from the projected AUTH-0028 boundary anchor while preserving the accepted
 * exposure side and source cave anchor.
 */
public record SkyIslandCaveExposureConnectionGeometry(
        int systemId,
        SkyIslandCaveExposureSide side,
        SkyIslandCaveExposureIntent intent,
        List<SkyIslandCavePassagePoint> points,
        double steeringSupport,
        double straightSupport,
        double normalizedMouthOffset,
        double normalizedMaxDeviation) {

    public SkyIslandCaveExposureConnectionGeometry {
        if (systemId < 0) {
            throw new IllegalArgumentException("systemId must be non-negative");
        }
        side = Objects.requireNonNull(side, "side");
        intent = Objects.requireNonNull(intent, "intent");
        if (intent.systemId() != systemId || intent.side() != side) {
            throw new IllegalArgumentException("exposure connection must preserve intent system and side");
        }
        points = List.copyOf(points);
        if (points.size() < 5) {
            throw new IllegalArgumentException("exposure connection requires at least five sampled points");
        }
        points.forEach(point -> Objects.requireNonNull(point, "exposure connection point"));
        if (!points.getFirst().position().equals(intent.caveAnchor())) {
            throw new IllegalArgumentException("exposure connection must begin at the accepted cave anchor");
        }
        double expectedBoundary = side == SkyIslandCaveExposureSide.UPPER_SURFACE ? 0.0 : 1.0;
        if (Double.doubleToLongBits(points.getLast().position().depthFraction())
                != Double.doubleToLongBits(expectedBoundary)) {
            throw new IllegalArgumentException("exposure connection mouth must lie on accepted semantic boundary");
        }
        requireNormalized("steeringSupport", steeringSupport);
        requireNormalized("straightSupport", straightSupport);
        requireNormalized("normalizedMouthOffset", normalizedMouthOffset);
        requireNormalized("normalizedMaxDeviation", normalizedMaxDeviation);
    }

    public SkyIslandCavePassagePoint caveSidePoint() {
        return points.getFirst();
    }

    public SkyIslandCavePassagePoint mouthPoint() {
        return points.getLast();
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
