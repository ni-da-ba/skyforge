package io.github.nidaba.skyforge.world;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bounded, deterministic nonlinear least-squares feedback for hydraulic candidate geometry.
 *
 * <p>The caller maps a low-dimensional geometry-control vector to one candidate, evaluates that
 * candidate with the physical hydraulic solvers, and returns dimensionless or otherwise scaled
 * energy/momentum residuals. This class never changes topology or relaxes geometry bounds. A
 * candidate is converged only when the residual norm meets the supplied tolerance.
 */
public final class SkyIslandHydraulicResidualFeedbackSolver {
    private static final double INITIAL_DAMPING = 1.0e-3;
    private static final double MIN_DAMPING = 1.0e-12;
    private static final double MAX_DAMPING = 1.0e12;
    private static final double DIFFERENCE_SCALE = 1.0e-5;
    private static final int MAX_LINE_SEARCH_STEPS = 12;

    private SkyIslandHydraulicResidualFeedbackSolver() {}

    public static Result solve(
            double[] initialControls,
            double[] lowerBounds,
            double[] upperBounds,
            Function<double[], double[]> hydraulicResiduals,
            double residualTolerance,
            int maximumIterations) {
        double[] controls = copyAndValidate(initialControls, "initialControls");
        double[] lower = copyAndValidate(lowerBounds, "lowerBounds");
        double[] upper = copyAndValidate(upperBounds, "upperBounds");
        Objects.requireNonNull(hydraulicResiduals, "hydraulicResiduals");
        if (controls.length == 0 || lower.length != controls.length || upper.length != controls.length) {
            throw new IllegalArgumentException("controls and bounds must have the same positive length");
        }
        if (!Double.isFinite(residualTolerance) || residualTolerance <= 0.0 || maximumIterations < 1) {
            throw new IllegalArgumentException("tolerance and iteration limit must be positive");
        }
        for (int i = 0; i < controls.length; i++) {
            if (lower[i] > upper[i]) {
                throw new IllegalArgumentException("lower bound exceeds upper bound at control " + i);
            }
            controls[i] = clamp(controls[i], lower[i], upper[i]);
        }

        double[] residuals = evaluate(hydraulicResiduals, controls, -1);
        double objective = squaredNorm(residuals);
        double damping = INITIAL_DAMPING;
        int iterations = 0;
        boolean converged = norm(residuals) <= residualTolerance;

        while (!converged && iterations < maximumIterations) {
            iterations++;
            double[][] jacobian = finiteDifferenceJacobian(
                    controls, residuals, lower, upper, hydraulicResiduals);
            double[][] normal = new double[controls.length][controls.length];
            double[] gradient = new double[controls.length];
            for (int row = 0; row < residuals.length; row++) {
                for (int i = 0; i < controls.length; i++) {
                    gradient[i] += jacobian[row][i] * residuals[row];
                    for (int j = 0; j < controls.length; j++) {
                        normal[i][j] += jacobian[row][i] * jacobian[row][j];
                    }
                }
            }
            for (int i = 0; i < controls.length; i++) {
                normal[i][i] += damping * Math.max(1.0, normal[i][i]);
                gradient[i] = -gradient[i];
            }
            double[] step = solveLinearSystem(normal, gradient);
            if (step == null) {
                damping = Math.min(MAX_DAMPING, damping * 10.0);
                continue;
            }

            boolean improved = false;
            for (int line = 0; line < MAX_LINE_SEARCH_STEPS; line++) {
                double alpha = Math.scalb(1.0, -line);
                double[] trial = controls.clone();
                for (int i = 0; i < trial.length; i++) {
                    trial[i] = clamp(controls[i] + alpha * step[i], lower[i], upper[i]);
                }
                if (Arrays.equals(trial, controls)) {
                    continue;
                }
                double[] trialResiduals = evaluate(hydraulicResiduals, trial, residuals.length);
                double trialObjective = squaredNorm(trialResiduals);
                if (trialObjective < objective) {
                    controls = trial;
                    residuals = trialResiduals;
                    objective = trialObjective;
                    damping = Math.max(MIN_DAMPING, damping * 0.3);
                    improved = true;
                    converged = norm(residuals) <= residualTolerance;
                    break;
                }
            }
            if (!improved) {
                damping = Math.min(MAX_DAMPING, damping * 10.0);
                if (damping >= MAX_DAMPING) {
                    break;
                }
            }
        }
        return new Result(controls, residuals, iterations, converged);
    }

    private static double[][] finiteDifferenceJacobian(
            double[] controls,
            double[] baseResiduals,
            double[] lower,
            double[] upper,
            Function<double[], double[]> evaluator) {
        double[][] jacobian = new double[baseResiduals.length][controls.length];
        for (int column = 0; column < controls.length; column++) {
            double scale = Math.max(1.0, Math.abs(controls[column]));
            double delta = DIFFERENCE_SCALE * scale;
            double plusValue = Math.min(upper[column], controls[column] + delta);
            double minusValue = Math.max(lower[column], controls[column] - delta);
            if (plusValue == minusValue) {
                continue;
            }
            double[] plusControls = controls.clone();
            double[] minusControls = controls.clone();
            plusControls[column] = plusValue;
            minusControls[column] = minusValue;
            double[] plus = plusValue == controls[column]
                    ? baseResiduals
                    : evaluate(evaluator, plusControls, baseResiduals.length);
            double[] minus = minusValue == controls[column]
                    ? baseResiduals
                    : evaluate(evaluator, minusControls, baseResiduals.length);
            double denominator = plusValue - minusValue;
            for (int row = 0; row < baseResiduals.length; row++) {
                jacobian[row][column] = (plus[row] - minus[row]) / denominator;
            }
        }
        return jacobian;
    }

    private static double[] evaluate(
            Function<double[], double[]> evaluator,
            double[] controls,
            int expectedLength) {
        double[] residuals = copyAndValidate(
                evaluator.apply(controls.clone()), "hydraulicResiduals result");
        if (residuals.length == 0 || (expectedLength >= 0 && residuals.length != expectedLength)) {
            throw new IllegalArgumentException("hydraulic residual vector must have a stable positive length");
        }
        return residuals;
    }

    private static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
        int size = rhs.length;
        double[][] a = new double[size][size + 1];
        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, a[row], 0, size);
            a[row][size] = rhs[row];
        }
        for (int pivot = 0; pivot < size; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < size; row++) {
                if (Math.abs(a[row][pivot]) > Math.abs(a[best][pivot])) {
                    best = row;
                }
            }
            if (!(Math.abs(a[best][pivot]) > 1.0e-18)) {
                return null;
            }
            double[] swap = a[pivot];
            a[pivot] = a[best];
            a[best] = swap;
            double divisor = a[pivot][pivot];
            for (int column = pivot; column <= size; column++) {
                a[pivot][column] /= divisor;
            }
            for (int row = 0; row < size; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = a[row][pivot];
                for (int column = pivot; column <= size; column++) {
                    a[row][column] -= factor * a[pivot][column];
                }
            }
        }
        double[] solution = new double[size];
        for (int row = 0; row < size; row++) {
            solution[row] = a[row][size];
        }
        return solution;
    }

    private static double[] copyAndValidate(double[] values, String name) {
        Objects.requireNonNull(values, name);
        double[] copy = values.clone();
        for (double value : copy) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(name + " must contain only finite values");
            }
        }
        return copy;
    }

    private static double squaredNorm(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value * value;
        }
        return sum;
    }

    private static double norm(double[] values) {
        return Math.sqrt(squaredNorm(values));
    }

    private static double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    public record Result(double[] controls, double[] residuals, int iterations, boolean converged) {
        public Result {
            controls = copyAndValidate(controls, "controls");
            residuals = copyAndValidate(residuals, "residuals");
            if (controls.length == 0 || residuals.length == 0 || iterations < 0) {
                throw new IllegalArgumentException("result vectors and iteration count must be valid");
            }
        }

        @Override
        public double[] controls() {
            return controls.clone();
        }

        @Override
        public double[] residuals() {
            return residuals.clone();
        }

        public double residualNorm() {
            return norm(residuals);
        }
    }
}