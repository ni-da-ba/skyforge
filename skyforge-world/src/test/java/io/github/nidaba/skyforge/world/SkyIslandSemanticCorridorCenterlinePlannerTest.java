package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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

    @Test
    void d2HeadGapObjectiveFindsLateralCandidateInsideSemanticCorridor() {
        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 1.0),
                new SkyIslandLocalPosition(2.0, 1.0),
                new SkyIslandLocalPosition(4.0, 1.0),
                new SkyIslandLocalPosition(6.0, 1.0),
                new SkyIslandLocalPosition(8.0, 1.0));
        SkyIslandGeomorphicCandidateRoute route =
                new SkyIslandGeomorphicCandidateRoute(
                        points, 1.0, 8.0, 2.0, 0.0, 0.0, 0.0, 0.0);
        List<SkyIslandLocalPosition> guidance = List.of(points.getFirst(), points.getLast());
        SkyIslandSemanticField terrain = ignored -> 0.5;
        SkyIslandSemanticField interiority = ignored -> 1.0;
        SkyIslandContinuousChannelCenterline seed =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        route, terrain, interiority, 2.0);

        SkyIslandContinuousChannelCenterline first =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route,
                        guidance,
                        terrain,
                        interiority,
                        2.0,
                        2.0,
                        0.0,
                        ignored -> 1.0,
                        (position, station, tangentX, tangentZ, halfWidth) ->
                                Math.abs(position.z()));
        SkyIslandContinuousChannelCenterline second =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route,
                        guidance,
                        terrain,
                        interiority,
                        2.0,
                        2.0,
                        0.0,
                        ignored -> 1.0,
                        (position, station, tangentX, tangentZ, halfWidth) ->
                                Math.abs(position.z()));

        assertEquals(first, second);
        assertTrue(meanInteriorAbsoluteZ(first.points()) < meanInteriorAbsoluteZ(seed.points()));
        assertEquals(route.points().getFirst(), first.points().getFirst());
        assertEquals(route.points().getLast(), first.points().getLast());
        for (SkyIslandLocalPosition point : first.points()) {
            assertTrue(Math.abs(point.z() - 1.0) <= 2.0 + EPSILON);
        }
    }

    @Test
    void d2HeadGapObjectiveRetainsBaselineCurvatureWidthQualification() {
        SkyIslandGeomorphicCandidateRoute route = stairRoute();
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(8.0, 0.0));
        SkyIslandSemanticField terrain = ignored -> 0.55;
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandContinuousChannelCenterline baseline =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority, 4.0, 3.0, 2.0);
        SkyIslandContinuousChannelCenterline d2Directed =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route,
                        guidance,
                        terrain,
                        interiority,
                        4.0,
                        3.0,
                        2.0,
                        ignored -> 1.0,
                        (position, station, tangentX, tangentZ, halfWidth) ->
                                Math.abs(position.z()));

        assertTrue(
                maximumCurvature(baseline.points()) * 2.0 <= 1.0 + EPSILON,
                "geometry-only C2 baseline must satisfy the D1 curvature-width bound");
        assertTrue(
                maximumCurvature(d2Directed.points()) * 2.0 <= 1.0 + EPSILON,
                "D2-directed search must not regress the accepted C2 curvature-width bound");
        assertEquals(route.points().getFirst(), d2Directed.points().getFirst());
        assertEquals(route.points().getLast(), d2Directed.points().getLast());
    }

    @Test
    void coupledWindowSearchFindsSmoothMoveBlockedByPointwiseCurvatureLimit() {
        List<SkyIslandLocalPosition> points = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            points.add(new SkyIslandLocalPosition(i, 0.0));
        }
        SkyIslandGeomorphicCandidateRoute route =
                new SkyIslandGeomorphicCandidateRoute(
                        points, 1.0, 20.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        List<SkyIslandLocalPosition> guidance =
                List.of(points.getFirst(), points.getLast());
        SkyIslandSemanticField terrain = ignored -> 0.5;
        SkyIslandSemanticField interiority = ignored -> 1.0;
        SkyIslandCenterlineHeadEnvelopeGap gap =
                (position, station, tangentX, tangentZ, halfWidth) ->
                        Math.abs(position.z()) < 0.5 ? 1.0 : 0.0;

        SkyIslandContinuousChannelCenterline first =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority,
                        1.0, 1.0, 20.0, ignored -> 1.0, gap);
        SkyIslandContinuousChannelCenterline second =
                SkyIslandSemanticCorridorCenterlinePlanner.refine(
                        route, guidance, terrain, interiority,
                        1.0, 1.0, 20.0, ignored -> 1.0, gap);

        assertEquals(first, second);
        assertEquals(points.getFirst(), first.points().getFirst());
        assertEquals(points.getLast(), first.points().getLast());
        assertTrue(
                meanInteriorAbsoluteZ(first.points()) >= 0.25,
                "a smooth coordinated move should improve the integrated head-gap objective");
        assertTrue(
                maximumCurvature(first.points()) * 20.0 <= 1.0 + 1.0e-6,
                "the coupled move must preserve the hard curvature-width bound");
        for (SkyIslandLocalPosition point : first.points()) {
            assertTrue(Math.abs(point.z()) <= 1.0 + EPSILON);
        }
    }


    @Test
    void globalModeSearchImprovesWholeRouteEnvelopeWithoutBreakingHardGeometry() {
        List<SkyIslandLocalPosition> points = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            points.add(new SkyIslandLocalPosition(i, 0.0));
        }
        SkyIslandGeomorphicCandidateRoute route =
                new SkyIslandGeomorphicCandidateRoute(
                        points, 1.0, 40.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        List<SkyIslandLocalPosition> guidance =
                List.of(points.getFirst(), points.getLast());
        SkyIslandSemanticField terrain = ignored -> 0.5;
        SkyIslandSemanticField interiority = ignored -> 1.0;
        SkyIslandCenterlineHeadEnvelopeGap gap =
                (position, station, tangentX, tangentZ, halfWidth) ->
                        Math.abs(position.z() - 1.2 * Math.sin(Math.PI * station));

        SkyIslandSemanticCorridorCenterlinePlanner.RefinementOutcome outcome =
                SkyIslandSemanticCorridorCenterlinePlanner.refineWithDiagnostics(
                        route, guidance, terrain, interiority,
                        1.0, 2.0, 20.0, ignored -> 1.0, gap);
        SkyIslandContinuousChannelCenterline repeated =
                SkyIslandSemanticCorridorCenterlinePlanner.refineWithDiagnostics(
                        route, guidance, terrain, interiority,
                        1.0, 2.0, 20.0, ignored -> 1.0, gap).centerline();

        assertEquals(outcome.centerline(), repeated);
        assertEquals(points.getFirst(), outcome.centerline().points().getFirst());
        assertEquals(points.getLast(), outcome.centerline().points().getLast());
        assertTrue(outcome.diagnostics().globalModeSearchProposals() > 0);
        assertTrue(outcome.diagnostics().globalModeSearchAdmissible() > 0);
        assertTrue(outcome.diagnostics().globalModeSearchAcceptedMoves() > 0);
        assertTrue(
                outcome.diagnostics().finalMaximumHeadEnvelopeGap()
                        < outcome.diagnostics().initialMaximumHeadEnvelopeGap());
        assertTrue(maximumCurvature(outcome.centerline().points()) * 20.0 <= 1.0 + 1.0e-6);
        for (SkyIslandLocalPosition point : outcome.centerline().points()) {
            assertTrue(Math.abs(point.z()) <= 2.0 + EPSILON);
        }
    }

    @Test
    void globalModeSearchUsesAuthoredCorridorBeyondLocalSampleScale() {
        List<SkyIslandLocalPosition> points = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            points.add(new SkyIslandLocalPosition(i, 0.0));
        }
        SkyIslandGeomorphicCandidateRoute route =
                new SkyIslandGeomorphicCandidateRoute(
                        points, 1.0, 40.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        List<SkyIslandLocalPosition> guidance =
                List.of(points.getFirst(), points.getLast());
        SkyIslandSemanticField terrain = ignored -> 0.5;
        SkyIslandSemanticField interiority = ignored -> 1.0;
        SkyIslandCenterlineHeadEnvelopeGap gap =
                (position, station, tangentX, tangentZ, halfWidth) ->
                        Math.abs(position.z() - 3.0 * Math.sin(Math.PI * station));

        SkyIslandSemanticCorridorCenterlinePlanner.RefinementOutcome outcome =
                SkyIslandSemanticCorridorCenterlinePlanner.refineWithDiagnostics(
                        route, guidance, terrain, interiority,
                        1.0, 4.0, 10.0, ignored -> 1.0, gap);

        double maximumDisplacement = outcome.centerline().points().stream()
                .mapToDouble(point -> Math.abs(point.z()))
                .max()
                .orElseThrow();
        assertTrue(
                maximumDisplacement > 1.0 + EPSILON,
                "the authored corridor permits useful whole-route moves beyond one sample interval");
        assertTrue(maximumDisplacement <= 4.0 + EPSILON);
        assertTrue(
                outcome.diagnostics().finalMaximumHeadEnvelopeGap()
                        < outcome.diagnostics().initialMaximumHeadEnvelopeGap());
        assertTrue(
                maximumCurvature(outcome.centerline().points()) * 10.0
                        <= 1.0 + 1.0e-6,
                "full-corridor search must still honor the hard curvature-width constraint");
    }

    private static double meanInteriorAbsoluteZ(List<SkyIslandLocalPosition> points) {
        return points.subList(1, points.size() - 1).stream()
                .mapToDouble(point -> Math.abs(point.z()))
                .average()
                .orElseThrow();
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
