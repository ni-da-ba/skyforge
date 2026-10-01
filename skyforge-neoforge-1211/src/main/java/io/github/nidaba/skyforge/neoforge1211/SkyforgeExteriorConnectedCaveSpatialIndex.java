package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandCavePassagePoint;
import io.github.nidaba.skyforge.world.SkyIslandExteriorConnectedCaveVolumeField;
import io.github.nidaba.skyforge.world.SkyIslandSubsurfacePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conservative backend-local broad phase for canonical AUTH-0030 cave sampling.
 *
 * <p>This class does not evaluate cave occupancy and does not replace AUTH-0030. It only proves
 * when a semantic point cannot possibly lie inside any accepted chamber, passage, or exposure
 * corridor. Every point that survives this broad phase is still evaluated by the unchanged
 * {@link SkyIslandExteriorConnectedCaveVolumeField} sampler.
 */
final class SkyforgeExteriorConnectedCaveSpatialIndex {
    private static final double EPSILON = 1.0e-12;

    private final List<SemanticBounds> bounds;

    private SkyforgeExteriorConnectedCaveSpatialIndex(List<SemanticBounds> bounds) {
        this.bounds = List.copyOf(bounds);
    }

    static SkyforgeExteriorConnectedCaveSpatialIndex create(
            SkyIslandExteriorConnectedCaveVolumeField field) {
        Objects.requireNonNull(field, "field");
        List<SemanticBounds> result = new ArrayList<>();

        for (var system : field.baseField().geometry().systems()) {
            for (var chamber : system.chambers()) {
                var center = chamber.center();
                result.add(new SemanticBounds(
                        center.x() - chamber.horizontalRadius(),
                        center.x() + chamber.horizontalRadius(),
                        center.z() - chamber.horizontalRadius(),
                        center.z() + chamber.horizontalRadius(),
                        center.depthFraction() - chamber.depthRadius(),
                        center.depthFraction() + chamber.depthRadius()));
            }
            for (var passage : system.passages()) {
                result.add(boundsForPoints(passage.points()));
            }
        }

        for (var connection : field.exposureGeometry().connections()) {
            result.add(boundsForPoints(connection.points()));
        }

        return new SkyforgeExteriorConnectedCaveSpatialIndex(result);
    }

    Slice slice(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ) {
        if (!Double.isFinite(minimumX)
                || !Double.isFinite(maximumX)
                || !Double.isFinite(minimumZ)
                || !Double.isFinite(maximumZ)
                || maximumX < minimumX
                || maximumZ < minimumZ) {
            throw new IllegalArgumentException("invalid AUTH-0030 spatial-index slice bounds");
        }
        List<SemanticBounds> selected = bounds.stream()
                .filter(candidate -> candidate.overlapsHorizontal(
                        minimumX,
                        maximumX,
                        minimumZ,
                        maximumZ))
                .toList();
        return new Slice(selected);
    }

    private static SemanticBounds boundsForPoints(List<SkyIslandCavePassagePoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("AUTH-0030 passage bounds require points");
        }

        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        double minimumDepth = Double.POSITIVE_INFINITY;
        double maximumDepth = Double.NEGATIVE_INFINITY;

        for (SkyIslandCavePassagePoint point : points) {
            double horizontal = point.horizontalRadius();
            double depth = point.depthRadius();
            minimumX = Math.min(minimumX, point.position().x() - horizontal);
            maximumX = Math.max(maximumX, point.position().x() + horizontal);
            minimumZ = Math.min(minimumZ, point.position().z() - horizontal);
            maximumZ = Math.max(maximumZ, point.position().z() + horizontal);
            minimumDepth = Math.min(minimumDepth, point.position().depthFraction() - depth);
            maximumDepth = Math.max(maximumDepth, point.position().depthFraction() + depth);
        }

        return new SemanticBounds(
                minimumX,
                maximumX,
                minimumZ,
                maximumZ,
                minimumDepth,
                maximumDepth);
    }

    record Slice(List<SemanticBounds> bounds) {
        Slice {
            bounds = List.copyOf(bounds);
        }

        boolean mayContainPositive(SkyIslandSubsurfacePosition position) {
            Objects.requireNonNull(position, "position");
            for (SemanticBounds candidate : bounds) {
                if (candidate.contains(position)) {
                    return true;
                }
            }
            return false;
        }

        int candidatePrimitiveBounds() {
            return bounds.size();
        }
    }

    private record SemanticBounds(
            double minimumX,
            double maximumX,
            double minimumZ,
            double maximumZ,
            double minimumDepth,
            double maximumDepth) {
        private SemanticBounds {
            if (!Double.isFinite(minimumX)
                    || !Double.isFinite(maximumX)
                    || !Double.isFinite(minimumZ)
                    || !Double.isFinite(maximumZ)
                    || !Double.isFinite(minimumDepth)
                    || !Double.isFinite(maximumDepth)
                    || maximumX < minimumX
                    || maximumZ < minimumZ
                    || maximumDepth < minimumDepth) {
                throw new IllegalArgumentException("invalid AUTH-0030 primitive bounds");
            }
        }

        boolean overlapsHorizontal(
                double queryMinimumX,
                double queryMaximumX,
                double queryMinimumZ,
                double queryMaximumZ) {
            return maximumX + EPSILON >= queryMinimumX
                    && minimumX - EPSILON <= queryMaximumX
                    && maximumZ + EPSILON >= queryMinimumZ
                    && minimumZ - EPSILON <= queryMaximumZ;
        }

        boolean contains(SkyIslandSubsurfacePosition position) {
            return position.x() >= minimumX - EPSILON
                    && position.x() <= maximumX + EPSILON
                    && position.z() >= minimumZ - EPSILON
                    && position.z() <= maximumZ + EPSILON
                    && position.depthFraction() >= minimumDepth - EPSILON
                    && position.depthFraction() <= maximumDepth + EPSILON;
        }
    }
}
