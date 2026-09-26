package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicBoundedQpSolverTest {
    private static final double EPSILON = 1.0e-7;

    @Test
    void unconstrainedInteriorProjectionReturnsAnalyticTarget() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {1.0, 2.0, -3.0},
                new double[] {1.0, 2.0, 4.0},
                fill(3, -100.0),
                fill(3, 100.0),
                List.of()));

        assertSolved(result);
        assertArrayEquals(new double[] {1.0, 2.0, -3.0}, result.solution(), EPSILON);
    }

    @Test
    void activeBoxProjectsToBoundary() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {5.0},
                new double[] {1.0},
                new double[] {0.0},
                new double[] {2.0},
                List.of()));

        assertSolved(result);
        assertEquals(2.0, result.solution()[0], EPSILON);
    }

    @Test
    void simultaneousUpperAndLowerDifferenceBoundsReachAnalyticOptimum() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {10.0, 0.0, 10.0},
                fill(3, 1.0),
                fill(3, -100.0),
                fill(3, 100.0),
                List.of(
                        new SkyIslandHydraulicDifferenceConstraint("upper-active", 0, 1, 2.0, 4.0),
                        new SkyIslandHydraulicDifferenceConstraint("lower-active", 1, 2, 1.0, 3.0))));

        assertSolved(result);
        assertArrayEquals(
                new double[] {29.0 / 3.0, 17.0 / 3.0, 14.0 / 3.0},
                result.solution(),
                EPSILON);
        assertEquals(4.0, result.solution()[0] - result.solution()[1], EPSILON);
        assertEquals(1.0, result.solution()[1] - result.solution()[2], EPSILON);
    }

    @Test
    void constraintInputPermutationDoesNotChangeSymmetricSolution() {
        List<SkyIslandHydraulicDifferenceConstraint> constraints = new ArrayList<>(List.of(
                new SkyIslandHydraulicDifferenceConstraint("tributary-a", 0, 2, 1.0, 3.0),
                new SkyIslandHydraulicDifferenceConstraint("tributary-b", 1, 2, 1.0, 3.0),
                new SkyIslandHydraulicDifferenceConstraint("outgoing", 2, 3, 1.0, 3.0)));
        SkyIslandHydraulicBoundedQpProblem first = problem(
                new double[] {8.0, 8.0, 5.0, 2.0},
                fill(4, 1.0),
                fill(4, -100.0),
                fill(4, 100.0),
                constraints);

        Collections.reverse(constraints);
        SkyIslandHydraulicBoundedQpProblem second = problem(
                new double[] {8.0, 8.0, 5.0, 2.0},
                fill(4, 1.0),
                fill(4, -100.0),
                fill(4, 100.0),
                constraints);

        SkyIslandHydraulicQpResult a = SkyIslandHydraulicBoundedQpSolver.solve(first);
        SkyIslandHydraulicQpResult b = SkyIslandHydraulicBoundedQpSolver.solve(second);

        assertSolved(a);
        assertSolved(b);
        assertArrayEquals(a.solution(), b.solution(), EPSILON);
    }

    @Test
    void exactBasinDatumIsAnEqualityNotATunablePenalty() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {20.0},
                new double[] {1.0},
                new double[] {7.0},
                new double[] {7.0},
                List.of()));

        assertSolved(result);
        assertEquals(7.0, result.solution()[0], EPSILON);
    }

    @Test
    void explicitDropPartitionDoesNotInventCrossDropContinuity() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {10.0, 8.0, 3.0, 1.0},
                fill(4, 1.0),
                fill(4, -100.0),
                fill(4, 100.0),
                List.of(
                        new SkyIslandHydraulicDifferenceConstraint("upper-subreach", 0, 1, 1.0, 3.0),
                        new SkyIslandHydraulicDifferenceConstraint("lower-subreach", 2, 3, 1.0, 3.0))));

        assertSolved(result);
        assertArrayEquals(new double[] {10.0, 8.0, 3.0, 1.0}, result.solution(), EPSILON);
        assertEquals(5.0, result.solution()[1] - result.solution()[2], EPSILON);
    }

    @Test
    void contradictoryExactBoundariesFailAsInfeasible() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {0.0, 10.0},
                fill(2, 1.0),
                new double[] {0.0, 10.0},
                new double[] {0.0, 10.0},
                List.of(new SkyIslandHydraulicDifferenceConstraint(
                        "contradiction", 0, 1, -2.0, -1.0))));

        assertEquals(SkyIslandHydraulicQpStatus.INFEASIBLE, result.status());
        assertTrue(result.diagnostic().isPresent());
    }

    @Test
    void redundantConstraintDoesNotDestabilizeActiveSet() {
        SkyIslandHydraulicQpResult result = SkyIslandHydraulicBoundedQpSolver.solve(problem(
                new double[] {10.0, 0.0},
                fill(2, 1.0),
                fill(2, -100.0),
                fill(2, 100.0),
                List.of(
                        new SkyIslandHydraulicDifferenceConstraint("duplicate-a", 0, 1, 2.0, 4.0),
                        new SkyIslandHydraulicDifferenceConstraint("duplicate-b", 0, 1, 2.0, 4.0))));

        assertSolved(result);
        assertArrayEquals(new double[] {7.0, 3.0}, result.solution(), EPSILON);
    }

    @Test
    void variablePermutationMapsBackToSamePhysicalSolution() {
        SkyIslandHydraulicBoundedQpProblem original = problem(
                new double[] {10.0, 0.0, 10.0},
                new double[] {1.0, 2.0, 1.5},
                fill(3, -100.0),
                fill(3, 100.0),
                List.of(
                        new SkyIslandHydraulicDifferenceConstraint("a", 0, 1, 2.0, 4.0),
                        new SkyIslandHydraulicDifferenceConstraint("b", 1, 2, 1.0, 3.0)));
        int[] permutation = {2, 1, 0};
        SkyIslandHydraulicBoundedQpProblem permuted = permute(original, permutation);

        SkyIslandHydraulicQpResult a = SkyIslandHydraulicBoundedQpSolver.solve(original);
        SkyIslandHydraulicQpResult b = SkyIslandHydraulicBoundedQpSolver.solve(permuted);

        assertSolved(a);
        assertSolved(b);
        double[] mapped = new double[3];
        for (int newIndex = 0; newIndex < permutation.length; newIndex++) {
            mapped[permutation[newIndex]] = b.solution()[newIndex];
        }
        assertArrayEquals(a.solution(), mapped, EPSILON);
    }

    private static SkyIslandHydraulicBoundedQpProblem permute(
            SkyIslandHydraulicBoundedQpProblem problem,
            int[] oldIndexByNewIndex) {
        int n = oldIndexByNewIndex.length;
        int[] newIndexByOldIndex = new int[n];
        double[] target = new double[n];
        double[] weight = new double[n];
        double[] lower = new double[n];
        double[] upper = new double[n];
        double[] oldTarget = problem.target();
        double[] oldWeight = problem.weight();
        double[] oldLower = problem.lowerBound();
        double[] oldUpper = problem.upperBound();
        for (int newIndex = 0; newIndex < n; newIndex++) {
            int oldIndex = oldIndexByNewIndex[newIndex];
            newIndexByOldIndex[oldIndex] = newIndex;
            target[newIndex] = oldTarget[oldIndex];
            weight[newIndex] = oldWeight[oldIndex];
            lower[newIndex] = oldLower[oldIndex];
            upper[newIndex] = oldUpper[oldIndex];
        }
        List<SkyIslandHydraulicDifferenceConstraint> constraints = problem.differenceConstraints().stream()
                .map(constraint -> new SkyIslandHydraulicDifferenceConstraint(
                        constraint.id(),
                        newIndexByOldIndex[constraint.leftIndex()],
                        newIndexByOldIndex[constraint.rightIndex()],
                        constraint.lower(),
                        constraint.upper()))
                .toList();
        return problem(target, weight, lower, upper, constraints);
    }

    private static void assertSolved(SkyIslandHydraulicQpResult result) {
        assertEquals(SkyIslandHydraulicQpStatus.SOLVED, result.status(), result.diagnostic().orElse(""));
        assertTrue(result.primalResidual() < 1.0e-6);
        assertTrue(result.stationarityResidual() < 1.0e-6);
        assertTrue(result.dualFeasibilityResidual() < 1.0e-6);
        assertTrue(result.complementarityResidual() < 1.0e-6);
    }

    private static SkyIslandHydraulicBoundedQpProblem problem(
            double[] target,
            double[] weight,
            double[] lower,
            double[] upper,
            List<SkyIslandHydraulicDifferenceConstraint> constraints) {
        return new SkyIslandHydraulicBoundedQpProblem(
                target, weight, lower, upper, constraints);
    }

    private static double[] fill(int size, double value) {
        double[] result = new double[size];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
