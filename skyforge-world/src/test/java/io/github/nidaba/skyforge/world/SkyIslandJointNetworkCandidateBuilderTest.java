package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandJointNetworkCandidateBuilderTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void constructsSharedBedAndOrderIndependentValleyUnionAtConfluence() {
        List<SkyIslandJointNetworkCandidateBuilder.ReachInput> reaches = component();
        var forward = SkyIslandJointNetworkCandidateBuilder.build(reaches, "outlet", ignored -> 0.8);
        var reverseInputs = List.of(reaches.get(2), reaches.get(1), reaches.get(0));
        var reverse = SkyIslandJointNetworkCandidateBuilder.build(reverseInputs, "outlet", ignored -> 0.8);

        assertEquals(List.of("branch-a", "branch-b", "main"), forward.reachesById().keySet().stream().toList());
        assertEquals(
                forward.bedProfile().bedElevationsByNodeMetres().get("join"),
                forward.reachesById().get("branch-a").bedElevationsMetres()[1],
                EPSILON);
        assertEquals(
                forward.reachesById().get("branch-a").bedElevationsMetres()[1],
                forward.reachesById().get("branch-b").bedElevationsMetres()[1],
                EPSILON);
        assertEquals(
                forward.reachesById().get("branch-a").bedElevationsMetres()[1],
                forward.reachesById().get("main").bedElevationsMetres()[0],
                EPSILON);
        assertEquals(
                forward.reachesById().get("main").bedElevationsMetres()[1],
                forward.bedProfile().bedElevationsByNodeMetres().get("outlet"),
                EPSILON);

        SkyIslandLocalPosition join = reaches.getFirst().route().points().getLast();
        assertEquals(
                forward.reachesById().get("main").bedElevationsMetres()[0],
                forward.sampleTerrainMetres(join),
                EPSILON);
        assertEquals(
                forward.sampleTerrainMetres(join),
                reverse.sampleTerrainMetres(join),
                EPSILON);
        assertTrue(forward.sampleTerrainMetres(new SkyIslandLocalPosition(100.0, 100.0)) <= 8.0);
    }

    @Test
    void rejectsMismatchedPhysicalJunctionPositions() {
        var reaches = component();
        var mismatched = input(
                "branch-b", "source-b", "join",
                List.of(new SkyIslandLocalPosition(10.0, 10.0), new SkyIslandLocalPosition(10.0, 0.01)));
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJointNetworkCandidateBuilder.build(
                        List.of(reaches.get(0), mismatched, reaches.get(2)),
                        "outlet",
                        ignored -> 0.8));
    }

    @Test
    void rejectsValleyOverlapThatWouldCreateAnUnmodeledConfluence() {
        var main = input(
                "main", "join", "outlet",
                List.of(
                        new SkyIslandLocalPosition(0.0, 0.0),
                        new SkyIslandLocalPosition(10.0, 0.0),
                        new SkyIslandLocalPosition(20.0, 0.0)));
        var crossingTributary = input(
                "crossing-branch", "source", "join",
                List.of(
                        new SkyIslandLocalPosition(20.0, 10.0),
                        new SkyIslandLocalPosition(0.0, -10.0),
                        new SkyIslandLocalPosition(0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJointNetworkCandidateBuilder.build(
                        List.of(main, crossingTributary), "outlet", ignored -> 0.8));
    }

    @Test
    void rejectsFlowSplitsCyclesAndNonterminalSinks() {
        var branch = input(
                "branch", "source", "join",
                List.of(new SkyIslandLocalPosition(0.0, 0.0), new SkyIslandLocalPosition(10.0, 0.0)));
        var splitA = input(
                "split-a", "join", "out-a",
                List.of(new SkyIslandLocalPosition(10.0, 0.0), new SkyIslandLocalPosition(20.0, 0.0)));
        var splitB = input(
                "split-b", "join", "out-b",
                List.of(new SkyIslandLocalPosition(10.0, 0.0), new SkyIslandLocalPosition(20.0, 10.0)));
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJointNetworkCandidateBuilder.build(
                        List.of(branch, splitA, splitB), "out-a", ignored -> 0.8));

        var cycleA = input(
                "cycle-a", "a", "b",
                List.of(new SkyIslandLocalPosition(0.0, 0.0), new SkyIslandLocalPosition(10.0, 0.0)));
        var cycleB = input(
                "cycle-b", "b", "a",
                List.of(new SkyIslandLocalPosition(10.0, 0.0), new SkyIslandLocalPosition(0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJointNetworkCandidateBuilder.build(
                        List.of(cycleA, cycleB), "a", ignored -> 0.8));
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJointNetworkCandidateBuilder.build(
                        List.of(branch, splitB), "out-a", ignored -> 0.8));
    }

    private static List<SkyIslandJointNetworkCandidateBuilder.ReachInput> component() {
        return List.of(
                input(
                        "branch-a", "source-a", "join",
                        List.of(new SkyIslandLocalPosition(0.0, 0.0), new SkyIslandLocalPosition(10.0, 0.0))),
                input(
                        "branch-b", "source-b", "join",
                        List.of(new SkyIslandLocalPosition(10.0, 10.0), new SkyIslandLocalPosition(10.0, 0.0))),
                input(
                        "main", "join", "outlet",
                        List.of(new SkyIslandLocalPosition(10.0, 0.0), new SkyIslandLocalPosition(20.0, 0.0))));
    }

    private static SkyIslandJointNetworkCandidateBuilder.ReachInput input(
            String id,
            String upstream,
            String downstream,
            List<SkyIslandLocalPosition> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z());
        }
        var route = new SkyIslandGeomorphicCandidateRoute(
                points, length, length, 0.0, 0.0, 0.0, 0.0, 0.0);
        var configuration = new SkyIslandHydraulicCandidateBuilder.Configuration(
                1.0, 10.0, 0.0, 0.01, 0.0,
                2.0, 2.0, 1.0, 8.0, 3.0);
        return new SkyIslandJointNetworkCandidateBuilder.ReachInput(
                id, upstream, downstream, route, configuration);
    }
}
