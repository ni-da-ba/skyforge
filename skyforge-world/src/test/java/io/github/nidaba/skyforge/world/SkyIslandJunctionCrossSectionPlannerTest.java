package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandJunctionCrossSectionPlannerTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void placesSectionsOutsideClearanceAndMeasuresJunctionLengths() {
        var receiver = reach(
                "main", "join", "outlet",
                List.of(
                        point(0.0, 0.0),
                        point(2.0, 0.0),
                        point(4.0, 0.0),
                        point(6.0, 0.0)),
                new double[] {0.0, 2.0, 4.0, 6.0},
                3.0);
        var tributaryA = reach(
                "branch-a", "source-a", "join",
                List.of(
                        point(0.0, 6.0),
                        point(0.0, 4.0),
                        point(0.0, 2.0),
                        point(0.0, 0.0)),
                new double[] {0.0, 2.0, 4.0, 6.0},
                3.0);
        var tributaryB = reach(
                "branch-b", "source-b", "join",
                List.of(
                        point(6.0, 6.0),
                        point(4.0, 4.0),
                        point(2.0, 2.0),
                        point(0.0, 0.0)),
                new double[] {0.0, Math.sqrt(8.0), 2.0 * Math.sqrt(8.0), 3.0 * Math.sqrt(8.0)},
                3.0);

        var plan = SkyIslandJunctionCrossSectionPlanner.plan(
                "join", List.of(receiver, tributaryA, tributaryB));

        assertEquals("main", plan.receivingReachId());
        assertEquals(2, plan.receivingStationIndex());
        assertEquals(4.0, plan.receivingDistanceFromJunctionMetres(), EPSILON);
        assertEquals(2, plan.tributaries().size());
        var branchA = plan.tributaries().get(0);
        var branchB = plan.tributaries().get(1);
        assertEquals("branch-a", branchA.reachId());
        assertEquals(1, branchA.stationIndex());
        assertEquals(4.0, branchA.distanceFromJunctionMetres(), EPSILON);
        assertEquals(8.0, branchA.junctionLengthMetres(), EPSILON);
        assertEquals("branch-b", branchB.reachId());
        assertEquals(1, branchB.stationIndex());
        assertEquals(3.0 * Math.sqrt(8.0), branchB.distanceFromJunctionMetres(), EPSILON);
        assertEquals(4.0 + 3.0 * Math.sqrt(8.0), branchB.junctionLengthMetres(), EPSILON);
        assertTrue(plan.totalSectionDistanceMetres() >= 3.0 * 3.0);
    }

    @Test
    void failsClosedWhenNoSampleIsOutsideMixingClearance() {
        var receiver = reach(
                "main", "join", "outlet",
                List.of(point(0.0, 0.0), point(2.0, 0.0), point(4.0, 0.0)),
                new double[] {0.0, 2.0, 4.0},
                100.0);
        var tributaryA = reach(
                "branch-a", "source-a", "join",
                List.of(point(0.0, 4.0), point(0.0, 2.0), point(0.0, 0.0)),
                new double[] {0.0, 2.0, 4.0},
                100.0);
        var tributaryB = reach(
                "branch-b", "source-b", "join",
                List.of(point(4.0, 4.0), point(2.0, 2.0), point(0.0, 0.0)),
                new double[] {0.0, Math.sqrt(8.0), 2.0 * Math.sqrt(8.0)},
                100.0);

        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJunctionCrossSectionPlanner.plan(
                        "join", List.of(receiver, tributaryA, tributaryB)));
    }

    @Test
    void rejectsBranchesThatDoNotShareOneExactJunctionPosition() {
        var receiver = reach(
                "main", "join", "outlet",
                List.of(point(0.0, 0.0), point(2.0, 0.0), point(4.0, 0.0)),
                new double[] {0.0, 2.0, 4.0},
                0.0);
        var tributaryA = reach(
                "branch-a", "source-a", "join",
                List.of(point(0.0, 4.0), point(0.0, 2.0), point(0.0, 0.0)),
                new double[] {0.0, 2.0, 4.0},
                0.0);
        var tributaryB = reach(
                "branch-b", "source-b", "join",
                List.of(point(4.0, 4.0), point(2.0, 2.0), point(0.01, 0.0)),
                new double[] {0.0, Math.sqrt(8.0), Math.sqrt(8.0) + Math.sqrt(7.9601)},
                0.0);

        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJunctionCrossSectionPlanner.plan(
                        "join", List.of(receiver, tributaryA, tributaryB)));
    }

    private static SkyIslandJunctionCrossSectionPlanner.ReachInput reach(
            String id,
            String upstream,
            String downstream,
            List<SkyIslandLocalPosition> points,
            double[] stations,
            double clearance) {
        return new SkyIslandJunctionCrossSectionPlanner.ReachInput(
                id, upstream, downstream, points, stations, 1.0, 0.5, clearance);
    }

    private static SkyIslandLocalPosition point(double x, double z) {
        return new SkyIslandLocalPosition(x, z);
    }
}
