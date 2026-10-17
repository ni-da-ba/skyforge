package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandJunctionHydraulicInputAdapterTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void slicesAtPlannedSectionsAndPreservesFlowAndIntervalLossInputs() {
        var plan = SkyIslandJunctionCrossSectionPlanner.plan(
                "join",
                List.of(
                        sectionInput(
                                "main", "join", "outlet",
                                List.of(point(0, 0), point(2, 0), point(4, 0), point(6, 0)),
                                new double[] {0, 2, 4, 6}),
                        sectionInput(
                                "branch-a", "source-a", "join",
                                List.of(point(0, 6), point(0, 4), point(0, 2), point(0, 0)),
                                new double[] {0, 2, 4, 6}),
                        sectionInput(
                                "branch-b", "source-b", "join",
                                List.of(point(6, 6), point(4, 4), point(2, 2), point(0, 0)),
                                new double[] {0, Math.sqrt(8), 2 * Math.sqrt(8), 3 * Math.sqrt(8)})));

        var receiver = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "main", fullReach(new double[] {3, 3, 3, 3}), 2, 3);
        var branchA = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "branch-a", fullReach(new double[] {1, 1, 1, 1}), 0, 1);
        var branchB = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "branch-b", fullReach(new double[] {2, 2, 2, 2}, new double[] {0, Math.sqrt(8), 2 * Math.sqrt(8), 3 * Math.sqrt(8)}), 0, 1);
        var junction = SkyIslandJunctionHydraulicInputAdapter.junction(
                plan,
                receiver,
                List.of(branchB, branchA),
                Map.of(
                        "branch-a", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3),
                        "branch-b", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3)));

        assertEquals("main", junction.receivingReachId());
        assertEquals(List.of("branch-a", "branch-b"),
                junction.tributaries().stream().map(
                        SkyIslandSubcriticalDrainageTreeSolver.Tributary::reachId).toList());
        assertEquals(8.0, junction.tributaries().get(0).junctionLengthMetres(), EPSILON);
        assertEquals(4.0 + 2 * Math.sqrt(8), junction.tributaries().get(1).junctionLengthMetres(), EPSILON);

        var sliced = receiver.toHydraulicReach();
        assertArrayEquals(new double[] {0, 2}, sliced.stationsMetres(), EPSILON);
        assertArrayEquals(new double[] {8, 7}, sliced.bedElevationsMetres(), EPSILON);
        assertArrayEquals(new double[] {3, 3}, sliced.dischargeAtStationsCubicMetresPerSecond(), EPSILON);
        assertArrayEquals(new double[] {0.1}, sliced.contractionCoefficientsByInterval(), EPSILON);
        assertArrayEquals(new double[] {0.3}, sliced.expansionCoefficientsByInterval(), EPSILON);

        var branchSlice = branchB.toHydraulicReach();
        assertEquals(Math.sqrt(8), branchSlice.stationsMetres()[1], EPSILON);
        assertArrayEquals(new double[] {2, 2},
                branchSlice.dischargeAtStationsCubicMetresPerSecond(), EPSILON);

        var profile = SkyIslandJunctionHydraulicInputAdapter.solveNetwork(
                List.of(receiver, branchA, branchB),
                List.of(new SkyIslandJunctionHydraulicInputAdapter.JunctionInput(
                        plan,
                        Map.of(
                                "branch-a", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3),
                                "branch-b", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0.1, 0.3)))),
                "main",
                1.0);
        assertEquals(3, profile.reachesById().size());
    }

    @Test
    void rejectsSlicesThatDoNotEndAtTheirPlannedJunctionSections() {
        var plan = SkyIslandJunctionCrossSectionPlanner.plan(
                "join",
                List.of(
                        sectionInput("main", "join", "outlet",
                                List.of(point(0, 0), point(2, 0), point(4, 0), point(6, 0)),
                                new double[] {0, 2, 4, 6}),
                        sectionInput("branch-a", "source-a", "join",
                                List.of(point(0, 6), point(0, 4), point(0, 2), point(0, 0)),
                                new double[] {0, 2, 4, 6}),
                        sectionInput("branch-b", "source-b", "join",
                                List.of(point(6, 6), point(4, 4), point(2, 2), point(0, 0)),
                                new double[] {0, Math.sqrt(8), 2 * Math.sqrt(8), 3 * Math.sqrt(8)})));
        var receiver = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "main", fullReach(new double[] {2, 2, 2, 2}), 2, 3);
        var wrongBranch = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "branch-a", fullReach(new double[] {1, 1, 1, 1}), 0, 2);
        var otherBranch = new SkyIslandJunctionHydraulicInputAdapter.ReachSlice(
                "branch-b", fullReach(new double[] {1, 1, 1, 1}), 0, 1);
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandJunctionHydraulicInputAdapter.junction(
                        plan, receiver, List.of(wrongBranch, otherBranch),
                        Map.of(
                                "branch-a", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0, 0),
                                "branch-b", new SkyIslandJunctionHydraulicInputAdapter.LossCoefficients(0, 0))));
    }

    private static SkyIslandJunctionCrossSectionPlanner.ReachInput sectionInput(
            String id, String upstream, String downstream,
            List<SkyIslandLocalPosition> points, double[] stations) {
        return new SkyIslandJunctionCrossSectionPlanner.ReachInput(
                id, upstream, downstream, points, stations, 1.0, 0.5, 3.0);
    }

    private static SkyIslandSubcriticalJunctionNetworkSolver.Reach fullReach(double[] flows) {\n        return fullReach(flows, new double[] {0, 2, 4, 6});\n    }\n\n    private static SkyIslandSubcriticalJunctionNetworkSolver.Reach fullReach(\n            double[] flows, double[] stations) {
        var section = new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depth) { return 2.0 * depth; }
            @Override
            public double wettedPerimeter(double depth) { return 2.0 + 2.0 * depth; }
            @Override
            public double topWidth(double depth) { return 2.0; }
        };
        return new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0, 2, 4, 6},
                new double[] {10, 9, 8, 7},
                new SkyIslandStandardStepSolver.CrossSection[] {section, section, section, section},
                flows, 0.035,
                new double[] {0.1, 0.2, 0.3},
                new double[] {0.3, 0.2, 0.1});
    }

    private static SkyIslandLocalPosition point(double x, double z) {
        return new SkyIslandLocalPosition(x, z);
    }
}
