package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandSubcriticalDrainageTreeSolverTest {
    private static final double GRAVITY = 9.81;
    private static final double ROUGHNESS = 0.03;

    @Test
    void solvesNestedConfluencesDownstreamFirstWithIndependentBranches() {
        var root = reach("root", 100.0, new double[] {0.1, 0.0}, 18.0, 5.0);
        var middle = reach("middle", 60.0, new double[] {0.4, 0.2}, 12.0, 3.0);
        var tributaryC = reach("tributary-c", 50.0, new double[] {0.45, 0.2}, 10.0, 2.0);
        var tributaryA = reach("tributary-a", 40.0, new double[] {0.8, 0.55}, 7.0, 1.0);
        var tributaryB = reach("tributary-b", 35.0, new double[] {0.7, 0.5}, 8.0, 2.0);

        var result = SkyIslandSubcriticalDrainageTreeSolver.solve(
                List.of(root, middle, tributaryC, tributaryA, tributaryB),
                List.of(
                        junction("root", tributary("middle", 20.0), tributary("tributary-c", 25.0)),
                        junction("middle", tributary("tributary-a", 15.0), tributary("tributary-b", 18.0))),
                "root",
                1.2);

        assertEquals(
                List.of("middle", "root", "tributary-a", "tributary-b", "tributary-c"),
                List.copyOf(result.reachesById().keySet()));
        assertTrue(result.reachesById().get("root").downstreamJunctionStep().isEmpty());
        for (String id : List.of("middle", "tributary-a", "tributary-b", "tributary-c")) {
            assertTrue(result.reachesById().get(id).downstreamJunctionStep().isPresent(), id);
        }

        assertJunctionCloses(result, "root", "middle");
        assertJunctionCloses(result, "root", "tributary-c");
        assertJunctionCloses(result, "middle", "tributary-a");
        assertJunctionCloses(result, "middle", "tributary-b");

        double aStage = downstreamStage(result, "tributary-a");
        double bStage = downstreamStage(result, "tributary-b");
        assertTrue(Math.abs(aStage - bStage) > 1.0e-4,
                "energy-method branches retain independent stages");
    }

    @Test
    void rejectsUnbalancedNestedJunctionBeforeHydraulicMarch() {
        var root = reach("root", 100.0, new double[] {0.1, 0.0}, 18.0, 5.0);
        var branchA = reach("a", 60.0, new double[] {0.4, 0.2}, 12.0, 3.0);
        var branchB = reach("b", 50.0, new double[] {0.45, 0.2}, 10.0, 1.8);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandSubcriticalDrainageTreeSolver.solve(
                        List.of(root, branchA, branchB),
                        List.of(junction("root", tributary("a", 20.0), tributary("b", 25.0))),
                        "root",
                        1.2));
    }

    @Test
    void rejectsDisconnectedCyclicDrainageComponent() {
        var root = reach("root", 100.0, new double[] {0.1, 0.0}, 18.0, 5.0);
        var a = reach("a", 60.0, new double[] {0.4, 0.2}, 12.0, 3.0);
        var b = reach("b", 50.0, new double[] {0.45, 0.2}, 10.0, 2.0);
        var c = reach("c", 50.0, new double[] {0.45, 0.2}, 10.0, 2.0);
        var d = reach("d", 50.0, new double[] {0.45, 0.2}, 10.0, 2.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandSubcriticalDrainageTreeSolver.solve(
                        List.of(root, a, b, c, d),
                        List.of(
                                junction("root", tributary("a", 20.0), tributary("b", 25.0)),
                                junction("c", tributary("d", 15.0), tributary("d", 18.0)),
                                junction("d", tributary("c", 15.0), tributary("c", 18.0))),
                        "root",
                        1.2));
    }

    private static SkyIslandSubcriticalDrainageTreeSolver.Reach reach(
            String id, double length, double[] beds, double width, double discharge) {
        var section = rectangularSection(width);
        return new SkyIslandSubcriticalDrainageTreeSolver.Reach(
                id,
                new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                        new double[] {0.0, length},
                        beds,
                        new SkyIslandStandardStepSolver.CrossSection[] {section, section},
                        new double[] {discharge, discharge},
                        ROUGHNESS));
    }

    private static SkyIslandSubcriticalDrainageTreeSolver.Junction junction(
            String receiving,
            SkyIslandSubcriticalDrainageTreeSolver.Tributary... tributaries) {
        return new SkyIslandSubcriticalDrainageTreeSolver.Junction(
                receiving, List.of(tributaries));
    }

    private static SkyIslandSubcriticalDrainageTreeSolver.Tributary tributary(
            String id, double length) {
        return new SkyIslandSubcriticalDrainageTreeSolver.Tributary(id, length, 0.1, 0.3);
    }

    private static void assertJunctionCloses(
            SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile network,
            String receivingId,
            String tributaryId) {
        var receiving = network.reachesById().get(receivingId).reach().hydraulicReach();
        var branchProfile = network.reachesById().get(tributaryId);
        var branch = branchProfile.reach().hydraulicReach();
        var step = branchProfile.downstreamJunctionStep().orElseThrow();
        int branchEnd = branch.stationsMetres().length - 1;
        double[] receiverDepths = network.reachesById().get(receivingId).depthsMetres();
        double receiverDepth = receiverDepths[0];
        double branchDepth = branchProfile.depthsMetres()[branchEnd];
        double receiverArea = receiving.sections()[0].area(receiverDepth);
        double branchArea = branch.sections()[branchEnd].area(branchDepth);
        double receiverQ = receiving.dischargeAtStationsCubicMetresPerSecond()[0];
        double branchQ = branch.dischargeAtStationsCubicMetresPerSecond()[branchEnd];
        double receiverHead = receiving.bedElevationsMetres()[0]
                + receiverDepth
                + square(receiverQ / receiverArea) / (2.0 * GRAVITY);
        double branchHead = branch.bedElevationsMetres()[branchEnd]
                + branchDepth
                + square(branchQ / branchArea) / (2.0 * GRAVITY);
        assertEquals(
                receiverHead + step.losses().totalLossMetres(),
                branchHead,
                1.0e-8);
    }

    private static double downstreamStage(
            SkyIslandSubcriticalDrainageTreeSolver.NetworkProfile network,
            String id) {
        var profile = network.reachesById().get(id);
        var reach = profile.reach().hydraulicReach();
        int last = reach.stationsMetres().length - 1;
        return reach.bedElevationsMetres()[last] + profile.depthsMetres()[last];
    }

    private static double square(double value) {
        return value * value;
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
}
