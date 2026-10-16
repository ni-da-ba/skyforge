package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandSubcriticalJunctionNetworkSolverTest {
    private static final double GRAVITY = 9.81;

    @Test
    void solvesReceivingReachBeforeIndependentTributariesAndClosesEachJunctionEnergyStep() {
        var receivingSection = rectangularSection(10.0);
        var upperSection = rectangularSection(5.0);
        var spruceSection = rectangularSection(7.0);
        var receiving = new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0.0, 100.0},
                new double[] {0.1, 0.0},
                new SkyIslandStandardStepSolver.CrossSection[] {receivingSection, receivingSection},
                new double[] {4.0, 4.0},
                0.03);
        var upper = new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0.0, 50.0},
                new double[] {0.4, 0.2},
                new SkyIslandStandardStepSolver.CrossSection[] {upperSection, upperSection},
                new double[] {2.5, 2.5},
                0.03);
        var spruce = new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0.0, 45.0},
                new double[] {0.3, 0.15},
                new SkyIslandStandardStepSolver.CrossSection[] {spruceSection, spruceSection},
                new double[] {1.5, 1.5},
                0.03);

        var result = SkyIslandSubcriticalJunctionNetworkSolver.solve(
                receiving,
                1.0,
                List.of(
                        new SkyIslandSubcriticalJunctionNetworkSolver.Tributary(upper, 20.0, 0.1, 0.3),
                        new SkyIslandSubcriticalJunctionNetworkSolver.Tributary(spruce, 30.0, 0.1, 0.3)));

        assertEquals(2, result.tributaries().size());
        double receivingJunctionDepth = result.receivingDepthsMetres()[0];
        double receivingJunctionStage = receiving.bedElevationsMetres()[0] + receivingJunctionDepth;
        double upperStage = upper.bedElevationsMetres()[1]
                + result.tributaries().get(0).depthsMetres()[1];
        double spruceStage = spruce.bedElevationsMetres()[1]
                + result.tributaries().get(1).depthsMetres()[1];

        assertNotEquals(upperStage, spruceStage, 1.0e-5,
                "tributary stages are branch-specific and must not be forced to match");
        assertEquals(4.0, 2.5 + 1.5, 1.0e-12);
        assertJunctionEnergyCloses(result.tributaries().get(0), receivingJunctionStage,
                receivingJunctionDepth, receivingSection, 4.0);
        assertJunctionEnergyCloses(result.tributaries().get(1), receivingJunctionStage,
                receivingJunctionDepth, receivingSection, 4.0);
    }

    @Test
    void rejectsJunctionWithUnbalancedDischarge() {
        var section = rectangularSection(10.0);
        var receiving = new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0.0, 100.0},
                new double[] {0.1, 0.0},
                new SkyIslandStandardStepSolver.CrossSection[] {section, section},
                new double[] {4.0, 4.0},
                0.03);
        var branch = new SkyIslandSubcriticalJunctionNetworkSolver.Reach(
                new double[] {0.0, 50.0},
                new double[] {0.3, 0.2},
                new SkyIslandStandardStepSolver.CrossSection[] {section, section},
                new double[] {1.9, 1.9},
                0.03);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandSubcriticalJunctionNetworkSolver.solve(
                        receiving,
                        1.0,
                        List.of(
                                new SkyIslandSubcriticalJunctionNetworkSolver.Tributary(
                                        branch, 20.0, 0.1, 0.3),
                                new SkyIslandSubcriticalJunctionNetworkSolver.Tributary(
                                        branch, 20.0, 0.1, 0.3))));
    }

    private static void assertJunctionEnergyCloses(
            SkyIslandSubcriticalJunctionNetworkSolver.TributaryProfile profile,
            double receivingJunctionStage,
            double receivingJunctionDepth,
            SkyIslandStandardStepSolver.CrossSection receivingSection,
            double receivingDischarge) {
        var branch = profile.tributary().reach();
        int last = branch.stationsMetres().length - 1;
        double branchStage = branch.bedElevationsMetres()[last] + profile.depthsMetres()[last];
        double branchArea = branch.sections()[last].area(profile.depthsMetres()[last]);
        double receivingArea = receivingSection.area(receivingJunctionDepth);
        double branchVelocityHead = square(branch.dischargeAtStationsCubicMetresPerSecond()[last] / branchArea)
                / (2.0 * GRAVITY);
        double receivingVelocityHead = square(receivingDischarge / receivingArea) / (2.0 * GRAVITY);
        assertEquals(
                receivingJunctionStage + receivingVelocityHead
                        + profile.junctionStep().losses().totalLossMetres(),
                branchStage + branchVelocityHead,
                1.0e-8);
    }

    private static double square(double value) {
        return value * value;
    }

    private static SkyIslandStandardStepSolver.CrossSection rectangularSection(double widthMetres) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return widthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return widthMetres + 2.0 * depthMetres;
            }

            @Override
            public double topWidth(double depthMetres) {
                return widthMetres;
            }
        };
    }
}
