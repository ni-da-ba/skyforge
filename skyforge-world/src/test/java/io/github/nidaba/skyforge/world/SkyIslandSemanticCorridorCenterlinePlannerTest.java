package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandSemanticCorridorCenterlinePlannerTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void semanticCorridorRelaxationIsDeterministicAndReducesSeedCurvature() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(8.0, 0.0));
        SkyIslandSemanticField terrain = ignored -> 0.55;
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline seed =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 4.0);
        SkyIslandContinuousChannelCenterline first =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority, 4.0, 3.0, 4.0);
        SkyIslandContinuousChannelCenterline second =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority, 4.0, 3.0, 4.0);

        assertEquals(first, second);
        assertEquals(route.points().getFirst(), first.points().getFirst());
        assertEquals(route.points().getLast(), first.points().getLast());
        assertTrue(maximumCurvature(first.points()) < maximumCurvature(seed.points()));
        for (SkyIslandLocalPosition point : first.points()) {
            assertTrue(Math.abs(point.z()) <= 3.0 + EPSILON);
        }
    }

    @Test
    void relaxationDoesNotCrossHighTerrainToBuyCurvature() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(8.0, 0.0));
        SkyIslandSemanticField terrain = position -> {
            double dx = position.x() - 4.0;
            double dz = position.z() - 0.5;
            return 0.50 + 0.25 * Math.exp(-(dx * dx + dz * dz) / 0.30);
        };
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline result =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority, 4.0, 3.0, 4.0);

        for (SkyIslandLocalPosition point : result.points()) {
            SkyIslandLocalPosition seedProjection = projectToPolyline(point, route.points());
            assertTrue(
                    terrain.sample(point) - terrain.sample(seedProjection)
                            <= SkyIslandSemanticCorridorCenterlinePlanner
                                            .MAXIMUM_TERRAIN_RISE_FROM_SEED
                                    + 0.01,
                    "semantic relaxation must not buy smoothness by crossing a high obstacle");
        }
    }

    private static SkyIslandLocalPosition projectToPolyline(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        SkyIslandLocalPosition best = polyline.getFirst();
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            SkyIslandLocalPosition a = polyline.get(i - 1);
            SkyIslandLocalPosition b = polyline.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared <= EPSILON
                    ? 0.0
                    : Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                                            / lengthSquared));
            SkyIslandLocalPosition projected =
                    new SkyIslandLocalPosition(a.x() + t * dx, a.z() + t * dz);
            double distance =
                    Math.hypot(point.x() - projected.x(), point.z() - projected.z());
            if (distance < bestDistance) {
                best = projected;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double maximumCurvature(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double al = Math.hypot(ax, az);
            double bl = Math.hypot(bx, bz);
            if (al <= EPSILON || bl <= EPSILON) {
                continue;
            }
            double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
            maximum = Math.max(maximum, Math.acos(cosine) / (0.5 * (al + bl)));
        }
        return maximum;
    }

    private static SkyIslandGeomorphicCandidateRoute stairRoute() {
        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(2.0, 0.0),
                new SkyIslandLocalPosition(2.0, 2.0),
                new SkyIslandLocalPosition(4.0, 2.0),
                new SkyIslandLocalPosition(4.0, 0.0),
                new SkyIslandLocalPosition(6.0, 0.0),
                new SkyIslandLocalPosition(8.0, 0.0));
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z());
        }
        return new SkyIslandGeomorphicCandidateRoute(
                points, 1.0, length, 2.0, 0.0, 0.0, 0.0, 0.0);
    }
}
