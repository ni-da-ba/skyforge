package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkyIslandJunctionEnergyLossTest {
    private static final double GRAVITY = 9.81;

    @Test
    void usesAverageConveyanceAndExpansionCoefficientWhenVelocityHeadFalls() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);

        SkyIslandStandardStepSolver.JunctionLosses losses =
                SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        upstream,
                        1.0,
                        8.0,
                        0.035,
                        downstream,
                        1.0,
                        8.0,
                        0.035,
                        0.3,
                        0.1);

        double expectedFriction = 10.0 * Math.pow((8.0 + 8.0) / (100.0 + 200.0), 2.0);
        double expectedTransition = 0.1 * Math.abs(
                Math.pow(8.0 / 4.0, 2.0) / (2.0 * GRAVITY)
                        - Math.pow(8.0 / 8.0, 2.0) / (2.0 * GRAVITY));
        assertEquals(expectedFriction, losses.frictionLossMetres(), 1.0e-12);
        assertEquals(expectedTransition, losses.transitionLossMetres(), 1.0e-12);
        assertEquals(expectedFriction + expectedTransition, losses.totalLossMetres(), 1.0e-12);
    }

    @Test
    void usesContractionCoefficientWhenVelocityHeadRisesDownstream() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);

        SkyIslandStandardStepSolver.JunctionLosses losses =
                SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        upstream,
                        1.0,
                        4.0,
                        0.035,
                        downstream,
                        1.0,
                        16.0,
                        0.035,
                        0.3,
                        0.1);

        double expectedTransition = 0.3 * Math.abs(
                Math.pow(16.0 / 8.0, 2.0) / (2.0 * GRAVITY)
                        - Math.pow(4.0 / 4.0, 2.0) / (2.0 * GRAVITY));
        assertEquals(expectedTransition, losses.transitionLossMetres(), 1.0e-12);
    }

    @Test
    void rejectsNegativeJunctionLossCoefficients() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        rectangularSection(4.0, 100.0),
                        1.0,
                        4.0,
                        0.035,
                        rectangularSection(8.0, 200.0),
                        1.0,
                        8.0,
                        0.035,
                        -0.3,
                        0.1));
    }

    @Test
    void rejectsCombiningJunctionWithDownstreamFlowBelowItsBranchFlow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.combiningJunctionLosses(
                        10.0,
                        rectangularSection(4.0, 100.0),
                        1.0,
                        9.0,
                        0.035,
                        rectangularSection(8.0, 200.0),
                        1.0,
                        8.0,
                        0.035,
                        0.3,
                        0.1));
    }

    @Test
    void solvesSubcriticalBranchDepthByClosingEnergyWithJunctionLosses() {
        var upstream = rectangularSection(4.0, 100.0);
        var downstream = rectangularSection(8.0, 200.0);
        double downstreamDepth = 2.0;
        double downstreamEnergyGrade = downstreamDepth
                + Math.pow(8.0 / (8.0 * downstreamDepth), 2.0) / (2.0 * GRAVITY);

        SkyIslandStandardStepSolver.JunctionStepResult result =
                SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                        0.0,
                        0.0,
                        downstreamDepth,
                        upstream,
                        downstream,
                        8.0,
                        8.0,
                        0.035,
                        0.035,
                        10.0,
                        0.3,
                        0.1);

        assertEquals(
                downstreamEnergyGrade + result.losses().totalLossMetres(),
                result.upstreamEnergyGradeMetres(),
                1.0e-8);
        assertEquals(
                result.upstreamEnergyGradeMetres(),
                result.upstreamDepthMetres()
                        + Math.pow(8.0 / (4.0 * result.upstreamDepthMetres()), 2.0)
                                / (2.0 * GRAVITY),
                1.0e-12);
    }

    @Test
    void failsClosedWhenTheDownstreamBoundaryIsNotSubcritical() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandStandardStepSolver.solveSubcriticalCombiningJunction(
                        0.0,
                        0.0,
                        0.1,
                        rectangularSection(4.0, 100.0),
                        rectangularSection(8.0, 200.0),
                        4.0,
                        8.0,
                        0.035,
                        0.035,
                        10.0,
                        0.3,
                        0.1));
    }

    private static SkyIslandStandardStepSolver.CrossSection rectangularSection(
            double widthMetres, double conveyance) {
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

            @Override
            public double conveyance(double depthMetres, double manningRoughness) {
                return conveyance;
            }
        };
    }
}
