package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGameScaleDrainageNetworkModelTest {
    private static final double EPSILON = 1.0e-8;

    @Test
    void buildsOneTerrainAndFlowComponentBeforeSolvingItsSubcriticalJunctionTree() {
        var inputs = List.of(
                reach("branch-a", "source-a", "join", line(0, 0, 10, 0),
                        new double[] {1, 1, 1}),
                reach("branch-b", "source-b", "join", line(20, 10, 10, 0),
                        new double[] {1, 1, 1}),
                reach("main", "join", "outlet", line(10, 0, 20, 0),
                        new double[] {2, 2.5, 3}));
        SkyIslandSemanticField terrain = point -> 0.9 - 0.00001 * point.x() - 0.00001 * point.z();

        var solution = SkyIslandGameScaleDrainageNetworkModel.solve(
                inputs, "outlet", 3.0, terrain);

        var flows = solution.massBalancedFlows().reachesById();
        double incoming = flows.get("branch-a")
                        .dischargeAtStationsCubicMetresPerSecond()[2]
                + flows.get("branch-b")
                        .dischargeAtStationsCubicMetresPerSecond()[2];
        assertEquals(
                incoming,
                flows.get("main").dischargeAtStationsCubicMetresPerSecond()[0],
                EPSILON);
        assertTrue(flows.values().stream()
                .flatMapToDouble(flow -> java.util.Arrays.stream(
                        flow.lateralInflowByIntervalCubicMetresPerSecond()))
                .allMatch(value -> value >= 0.0));
        assertTrue(solution.massBalancedFlows().cubicMetresPerSecondPerRelativeUnit() > 0.0);
        assertTrue(solution.terminalNormalDepthMetres() > 0.0);
        assertEquals(1, solution.junctionSectionsByNodeId().size());
        assertEquals(3, solution.hydraulicProfile().reachesById().size());

        double branchBed = solution.terrainCandidate().reachesById()
                .get("branch-a").bedElevationsMetres()[2];
        double receiverBed = solution.terrainCandidate().reachesById()
                .get("main").bedElevationsMetres()[0];
        assertEquals(branchBed, receiverBed, EPSILON);
        assertEquals(
                branchBed,
                solution.terrainCandidate().bedProfile()
                        .bedElevationsByNodeMetres().get("join"),
                EPSILON);
    }

    private static SkyIslandGameScaleDrainageNetworkModel.ReachInput reach(
            String id, String upstream, String downstream,
            List<SkyIslandLocalPosition> points, double[] relativeDischarge) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z());
        }
        var route = new SkyIslandGeomorphicCandidateRoute(
                points, length, length, 0.0, 0.0, 0.0, 0.0, 0.0);
        var configuration = new SkyIslandHydraulicCandidateBuilder.Configuration(
                1.0, 100.0, 0.0, 0.001, 0.0,
                1.0, 1.0, 1.0, 1.6, 20.0);
        var candidate = new SkyIslandJointNetworkCandidateBuilder.ReachInput(
                id, upstream, downstream, route, configuration);
        return new SkyIslandGameScaleDrainageNetworkModel.ReachInput(
                candidate,
                relativeDischarge,
                new double[points.size() - 1],
                new double[points.size() - 1],
                5.0,
                new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3));
    }

    private static List<SkyIslandLocalPosition> line(double startX, double startZ, double endX, double endZ) {
        List<SkyIslandLocalPosition> points = new ArrayList<>();
        for (int i = 0; i <= 2; i++) {
            double fraction = i / 2.0;
            points.add(new SkyIslandLocalPosition(
                    startX + (endX - startX) * fraction,
                    startZ + (endZ - startZ) * fraction));
        }
        return List.copyOf(points);
    }
}
