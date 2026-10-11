package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicResidualFeedbackSolverTest {
    @Test
    void solvesCoupledEnergyAndMomentumResidualsWithinBounds() {
        var result = SkyIslandHydraulicResidualFeedbackSolver.solve(
                new double[] {-0.8, 0.9},
                new double[] {-2.0, -2.0},
                new double[] {2.0, 2.0},
                controls -> new double[] {
                    controls[0] + 2.0 * controls[1] - 1.0,
                    3.0 * controls[0] - controls[1] + 0.5
                },
                1.0e-9,
                80);

        assertTrue(result.converged());
        assertTrue(result.residualNorm() <= 1.0e-9);
        assertArrayEquals(new double[] {0.0, 0.5}, result.controls(), 1.0e-7);
    }

    @Test
    void respectsGeometryBoundsAndReportsUnclosedHydraulicsInsteadOfClaimingSuccess() {
        var result = SkyIslandHydraulicResidualFeedbackSolver.solve(
                new double[] {0.0},
                new double[] {-0.25},
                new double[] {0.25},
                controls -> new double[] {controls[0] - 1.0},
                1.0e-8,
                30);

        assertFalse(result.converged());
        assertArrayEquals(new double[] {0.25}, result.controls(), 1.0e-7);
        assertTrue(result.residualNorm() > 0.7);
    }

    @Test
    void residualEvaluatorMustReturnFiniteStableShape() {
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandHydraulicResidualFeedbackSolver.solve(
                        new double[] {0.0},
                        new double[] {-1.0},
                        new double[] {1.0},
                        controls -> new double[] {Double.NaN},
                        1.0e-8,
                        4));

        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () ->
                SkyIslandHydraulicResidualFeedbackSolver.solve(
                        new double[] {0.0},
                        new double[] {-1.0},
                        new double[] {1.0},
                        controls -> calls.incrementAndGet() == 1
                                ? new double[] {controls[0]}
                                : new double[] {controls[0], 1.0},
                        1.0e-8,
                        4));
    }
}