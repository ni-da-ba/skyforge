package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandHydraulicJumpSolverTest {
    private static final double GRAVITY = 9.81;
    private static final double TOLERANCE = 1.0e-10;
    private static final int ITERATIONS = 160;

    @Test
    void rectangularJumpMatchesClassicalConjugateDepthAndLosesEnergy() {
        double upstreamDepth = 0.5;
        double width = 10.0;
        double upstreamFroude = 2.0;
        double area = width * upstreamDepth;
        double discharge = upstreamFroude * Math.sqrt(GRAVITY * upstreamDepth) * area;
        var section = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                0.0, 0.0, discharge, width, 0.0);

        var jump = SkyIslandHydraulicJumpSolver.solveConjugateDepth(
                section, upstreamDepth, GRAVITY, TOLERANCE, ITERATIONS);
        double expectedDownstreamDepth = 0.5 * upstreamDepth
                * (Math.sqrt(1.0 + 8.0 * upstreamFroude * upstreamFroude) - 1.0);

        assertEquals(expectedDownstreamDepth, jump.downstreamDepthMeters(), 1.0e-8);
        assertTrue(jump.upstreamFroudeNumber() > 1.0);
        assertTrue(jump.downstreamFroudeNumber() < 1.0);
        assertTrue(jump.energyLossMeters() > 0.0);
        assertEquals(0.0, jump.specificForceResidualCubicMeters(), 1.0e-8);
    }

    @Test
    void trapezoidalJumpConservesHydrostaticSpecificForce() {
        var section = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                0.0, 12.0, 18.0, 6.0, 1.5);
        var jump = SkyIslandHydraulicJumpSolver.solveConjugateDepth(
                section, 0.4, GRAVITY, TOLERANCE, ITERATIONS);

        assertTrue(jump.downstreamDepthMeters() > jump.upstreamDepthMeters());
        assertTrue(jump.downstreamFroudeNumber() < 1.0);
        assertTrue(jump.energyLossMeters() > 0.0);
        assertEquals(
                SkyIslandHydraulicJumpSolver.specificForce(section, jump.upstreamDepthMeters(), GRAVITY),
                SkyIslandHydraulicJumpSolver.specificForce(section, jump.downstreamDepthMeters(), GRAVITY),
                1.0e-8);
    }

    @Test
    void refusesSubcriticalUpstreamStateInsteadOfInventingAJump() {
        var section = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                0.0, 0.0, 1.0, 10.0, 0.0);

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicJumpSolver.solveConjugateDepth(
                        section, 1.0, GRAVITY, TOLERANCE, ITERATIONS));
    }

    @Test
    void specificForceRejectsNonphysicalInputs() {
        var section = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                0.0, 0.0, 1.0, 2.0, 1.0);

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicJumpSolver.specificForce(section, 0.0, GRAVITY));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicJumpSolver.specificForce(section, 1.0, 0.0));
    }
}
