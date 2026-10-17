package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandMassBalancedDischargePlannerTest {
    @Test
    void usesOneScaleAndClosesAConfluenceWithDistributedRunoff() {
        var tributaryA = reach("a", "source-a", "join", new double[] {0.0, 10.0},
                new double[] {0.20, 0.35});
        var tributaryB = reach("b", "source-b", "join", new double[] {0.0, 8.0},
                new double[] {0.15, 0.30});
        var receiving = reach("main", "join", "outlet", new double[] {0.0, 6.0, 12.0},
                new double[] {0.70, 0.85, 0.95});

        var result = SkyIslandMassBalancedDischargePlanner.plan(
                List.of(receiving, tributaryB, tributaryA), "outlet", 1.10, 100.0);

        assertEquals(100.0, result.cubicMetresPerSecondPerRelativeUnit(), 0.0);
        assertEquals(110.0, result.terminalDischargeCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {20.0, 35.0},
                result.reachesById().get("a").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {15.0, 30.0},
                result.reachesById().get("b").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {65.0, 85.0, 110.0},
                result.reachesById().get("main").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {20.0, 25.0},
                result.reachesById().get("main").lateralInflowByIntervalCubicMetresPerSecond(), 1.0e-12);
        assertEquals(
                result.reachesById().get("a").dischargeAtStationsCubicMetresPerSecond()[1]
                        + result.reachesById().get("b").dischargeAtStationsCubicMetresPerSecond()[1],
                result.reachesById().get("main").dischargeAtStationsCubicMetresPerSecond()[0],
                0.0);
    }

    @Test
    void mappedFlowsSatisfyTheSubcriticalNetworkSolverMassContract() {
        var tributaryA = reach("a", "source-a", "join", new double[] {0.0, 10.0},
                new double[] {0.20, 0.35});
        var tributaryB = reach("b", "source-b", "join", new double[] {0.0, 8.0},
                new double[] {0.15, 0.30});
        var receiving = reach("main", "join", "outlet", new double[] {0.0, 6.0, 12.0},
                new double[] {0.70, 0.85, 0.95});
        var mapped = SkyIslandMassBalancedDischargePlanner.plan(
                List.of(tributaryA, tributaryB, receiving), "outlet", 1.10, 100.0);

        var sectionA = rectangularSection(25.0);
        var sectionB = rectangularSection(22.0);
        var sectionMain = rectangularSection(50.0);
        var hydraulicA = hydraulicReach(
                new double[] {0.0, 10.0}, new double[] {2.10, 2.00}, sectionA,
                mapped.reachesById().get("a").dischargeAtStationsCubicMetresPerSecond());
        var hydraulicB = hydraulicReach(
                new double[] {0.0, 8.0}, new double[] {2.08, 2.00}, sectionB,
                mapped.reachesById().get("b").dischargeAtStationsCubicMetresPerSecond());
        var hydraulicMain = hydraulicReach(
                new double[] {0.0, 6.0, 12.0}, new double[] {1.95, 1.90, 1.85}, sectionMain,
                mapped.reachesById().get("main").dischargeAtStationsCubicMetresPerSecond());
        double downstreamDepth = SkyIslandManningNormalDepthSolver.solve(
                110.0, 0.035, 0.005,
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depthMetres) {
                        return sectionMain.area(depthMetres);
                    }

                    @Override
                    public double wettedPerimeter(double depthMetres) {
                        return sectionMain.wettedPerimeter(depthMetres);
                    }
                });

        var profile = SkyIslandSubcriticalDrainageTreeSolver.solve(
                List.of(
                        new SkyIslandSubcriticalDrainageTreeSolver.Reach("a", hydraulicA),
                        new SkyIslandSubcriticalDrainageTreeSolver.Reach("b", hydraulicB),
                        new SkyIslandSubcriticalDrainageTreeSolver.Reach("main", hydraulicMain)),
                List.of(new SkyIslandSubcriticalDrainageTreeSolver.Junction(
                        "main",
                        List.of(
                                new SkyIslandSubcriticalDrainageTreeSolver.Tributary(
                                        "a", 0.5, 0.1, 0.3),
                                new SkyIslandSubcriticalDrainageTreeSolver.Tributary(
                                        "b", 0.5, 0.1, 0.3)))),
                "main",
                downstreamDepth);

        assertEquals(3, profile.reachesById().size());
        assertEquals(110.0, mapped.terminalDischargeCubicMetresPerSecond(), 1.0e-12);
        for (var reachProfile : profile.reachesById().values()) {
            for (double depth : reachProfile.depthsMetres()) {
                org.junit.jupiter.api.Assertions.assertTrue(
                        Double.isFinite(depth) && depth > 0.0);
            }
        }
    }

    @Test
    void preservesContinuityAcrossNestedConfluences() {
        var a = reach("a", "source-a", "join-1", new double[] {0.0, 5.0},
                new double[] {0.10, 0.20});
        var b = reach("b", "source-b", "join-1", new double[] {0.0, 5.0},
                new double[] {0.10, 0.15});
        var middle = reach("middle", "join-1", "join-2", new double[] {0.0, 8.0},
                new double[] {0.40, 0.50});
        var c = reach("c", "source-c", "join-2", new double[] {0.0, 6.0},
                new double[] {0.15, 0.20});
        var outlet = reach("outlet-reach", "join-2", "terminal", new double[] {0.0, 10.0},
                new double[] {0.75, 0.80});

        var result = SkyIslandMassBalancedDischargePlanner.plan(
                List.of(outlet, c, middle, b, a), "terminal", 0.90, 10.0);

        assertEquals(3.5,
                result.reachesById().get("middle")
                        .dischargeAtStationsCubicMetresPerSecond()[0],
                1.0e-12);
        assertEquals(7.0,
                result.reachesById().get("outlet-reach")
                        .dischargeAtStationsCubicMetresPerSecond()[0],
                1.0e-12);
        assertEquals(9.0, result.terminalDischargeCubicMetresPerSecond(), 1.0e-12);
        assertEquals(
                result.reachesById().get("middle")
                                .dischargeAtStationsCubicMetresPerSecond()[1]
                        + result.reachesById().get("c")
                                .dischargeAtStationsCubicMetresPerSecond()[1],
                result.reachesById().get("outlet-reach")
                                .dischargeAtStationsCubicMetresPerSecond()[0],
                0.0);
    }

    @Test
    void rejectsAuthoredConfluenceAccumulationBelowTributaryFlow() {
        var tributaryA = reach("a", "source-a", "join", new double[] {0.0, 10.0},
                new double[] {0.40, 0.55});
        var tributaryB = reach("b", "source-b", "join", new double[] {0.0, 10.0},
                new double[] {0.40, 0.55});
        var receiving = reach("main", "join", "outlet", new double[] {0.0, 10.0},
                new double[] {0.70, 1.00});

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandMassBalancedDischargePlanner.plan(
                        List.of(tributaryA, tributaryB, receiving), "outlet", 1.10, 100.0));
    }

    @Test
    void rejectsNegativeRunoffNeededToPreserveAuthoredStationFlow() {
        var tributaryA = reach("a", "source-a", "join", new double[] {0.0, 10.0},
                new double[] {0.40, 0.55});
        var tributaryB = reach("b", "source-b", "join", new double[] {0.0, 10.0},
                new double[] {0.35, 0.50});
        var receiving = reach("main", "join", "outlet", new double[] {0.0, 10.0},
                new double[] {0.80, 0.70});

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandMassBalancedDischargePlanner.plan(
                        List.of(tributaryA, tributaryB, receiving), "outlet", 0.90, 100.0));
    }

    @Test
    void rejectsDisconnectedCyclesAndMultipleTerminalBranches() {
        var cycleA = reach("a", "node-a", "node-b", new double[] {0.0, 1.0},
                new double[] {0.2, 0.3});
        var cycleB = reach("b", "node-b", "node-a", new double[] {0.0, 1.0},
                new double[] {0.2, 0.3});
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandMassBalancedDischargePlanner.plan(
                        List.of(cycleA, cycleB), "node-a", 0.4, 10.0));

        var branchA = reach("a", "source-a", "outlet", new double[] {0.0, 1.0},
                new double[] {0.2, 0.3});
        var branchB = reach("b", "source-b", "outlet", new double[] {0.0, 1.0},
                new double[] {0.2, 0.3});
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandMassBalancedDischargePlanner.plan(
                        List.of(branchA, branchB), "outlet", 0.8, 10.0));
    }

    private static SkyIslandSubcriticalJunctionNetworkSolver.Reach hydraulicReach(
            double[] stations,
            double[] beds,
            SkyIslandStandardStepSolver.CrossSection section,
            double[] discharges) {
        SkyIslandStandardStepSolver.CrossSection[] sections =
                new SkyIslandStandardStepSolver.CrossSection[stations.length];
        java.util.Arrays.fill(sections, section);
        return new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                stations, beds, sections, discharges, 0.035);
    }

    private static SkyIslandStandardStepSolver.CrossSection rectangularSection(double width) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return width * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return width + 2.0 * depthMetres;
            }

            @Override
            public double topWidth(double depthMetres) {
                return width;
            }
        };
    }

    private static SkyIslandMassBalancedDischargePlanner.ReachInput reach(
            String id, String upstream, String downstream, double[] stations, double[] relative) {
        return new SkyIslandMassBalancedDischargePlanner.ReachInput(
                id, upstream, downstream, stations, relative);
    }
}