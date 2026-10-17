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
        var receiving = reach("main", "join", "outlet", new double[] {0.0, 12.0},
                new double[] {0.70, 0.95});

        var result = SkyIslandMassBalancedDischargePlanner.plan(
                List.of(receiving, tributaryB, tributaryA), "outlet", 1.10, 100.0);

        assertEquals(100.0, result.cubicMetresPerSecondPerRelativeUnit(), 0.0);
        assertEquals(110.0, result.terminalDischargeCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {20.0, 35.0},
                result.reachesById().get("a").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {15.0, 30.0},
                result.reachesById().get("b").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {65.0, 95.0},
                result.reachesById().get("main").dischargeAtStationsCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {30.0},
                result.reachesById().get("main").lateralInflowByIntervalCubicMetresPerSecond(), 1.0e-12);
        assertArrayEquals(new double[] {15.0},
                result.reachesById().get("main").lateralInflowByIntervalCubicMetresPerSecond(), 1.0e-12);
        assertEquals(
                result.reachesById().get("a").dischargeAtStationsCubicMetresPerSecond()[1]
                        + result.reachesById().get("b").dischargeAtStationsCubicMetresPerSecond()[1],
                result.reachesById().get("main").dischargeAtStationsCubicMetresPerSecond()[0],
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

    private static SkyIslandMassBalancedDischargePlanner.ReachInput reach(
            String id, String upstream, String downstream, double[] stations, double[] relative) {
        return new SkyIslandMassBalancedDischargePlanner.ReachInput(
                id, upstream, downstream, stations, relative);
    }
}