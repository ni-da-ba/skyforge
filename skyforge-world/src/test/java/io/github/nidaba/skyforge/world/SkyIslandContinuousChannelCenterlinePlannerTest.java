package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandContinuousChannelCenterlinePlannerTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void refinementIsDeterministicPreservesEndpointsAndStaysInSearchTube() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        SkyIslandSemanticField terrain = position -> 0.55 + 0.002 * Math.abs(position.z());
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline first =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 4.0);
        SkyIslandContinuousChannelCenterline second =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 4.0);

        assertEquals(first, second);
        assertEquals(route.points().getFirst(), first.points().getFirst());
        assertEquals(route.points().getLast(), first.points().getLast());
        assertTrue(
                first.maximumSearchPathDeviation()
                        <= 4.0
                                * SkyIslandContinuousChannelCenterlinePlanner
                                        .MAXIMUM_DEVIATION_SPACING_FRACTION
                                + EPSILON);
    }

    @Test
    void refinementReducesGridTurnSeverityOnStairStepRoute() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        SkyIslandSemanticField terrain = ignored -> 0.55;
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline refined =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 4.0);

        assertTrue(refined.points().size() > route.points().size());
        assertTrue(refined.maximumTurnAngleRadians() < Math.PI / 2.0);
    }

    @Test
    void smoothingDoesNotCanonizeAvoidableHighCorner() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        SkyIslandSemanticField terrain = position -> {
            double dx = position.x() - 4.0;
            double dz = position.z() - 1.0;
            return Math.min(
                    1.0,
                    0.50 + 0.30 * Math.exp(-(dx * dx + dz * dz) / 0.35));
        };
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline refined =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 4.0);

        for (SkyIslandLocalPosition point : refined.points()) {
            assertTrue(
                    terrain.sample(point) <= 0.82,
                    "bounded smoothing must not canonize an avoidable high corner");
        }
    }

    private static SkyIslandGeomorphicCandidateRoute stairRoute() {
        List<SkyIslandLocalPosition> points =
                List.of(
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
