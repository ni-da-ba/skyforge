package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic feasible primal active-set solver for the F2 bounded hydraulic-head problem.
 *
 * <p>The supported problem is intentionally narrower than a general-purpose QP: diagonal strictly
 * positive quadratic weights, finite box bounds, and affine pair-difference bounds. This is exactly
 * the sparse convex structure required by the hydraulic profile contract. Feasibility is established
 * independently as a difference-constraints graph before optimization; optimization never widens a
 * bound to manufacture a solution.
 */
public final class SkyIslandHydraulicBoundedQpSolver {
    private static final double ABSOLUTE_TOLERANCE = 1.0e-10;
    private static final double RELATIVE_TOLERANCE = 1.0e-9;
    private static final double RANK_TOLERANCE = 1.0e-11;
    private static final double BELLMAN_FORD_TOLERANCE = 1.0e-13;
    private static final int MAX_ITERATIONS = 10_000;

    private SkyIslandHydraulicBoundedQpSolver() {}

    public static SkyIslandHydraulicQpResult solve(SkyIslandHydraulicBoundedQpProblem problem) {
        Objects.requireNonNull(problem, "problem");
        ScaledProblem scaled = scale(problem);
        ConstraintSet constraints = constraints(scaled);
        Optional<double[]> feasible = feasiblePoint(scaled);
        if (feasible.isEmpty()) {
            return terminalFailure(
                    SkyIslandHydraulicQpStatus.INFEASIBLE,
                    "difference/box constraint graph is infeasible",
                    0);
        }

        double[] x = feasible.get();
        double tolerance = tolerance(1.0);
        if (primalResidual(x, constraints) > 32.0 * tolerance) {
            return terminalFailure(
                    SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                    "feasibility construction exceeded scaled residual budget",
                    0);
        }

        Set<String> working = new HashSet<>();
        for (Row row : constraints.inequalities()) {
            if (row.rhs() - row.dot(x) <= 8.0 * tolerance) {
                working.add(row.id());
            }
        }

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            List<Row> active = independentActiveRows(
                    constraints.equalities(),
                    constraints.inequalities(),
                    working,
                    scaled.weight());
            Set<String> retainedWorking = new HashSet<>();
            for (Row row : active) {
                if (!row.equality()) {
                    retainedWorking.add(row.id());
                }
            }
            working = retainedWorking;

            double[] gradient = gradient(x, scaled.target(), scaled.weight());
            KktStep step = kktStep(gradient, scaled.weight(), active);
            if (step == null) {
                return terminalFailure(
                        SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                        "active-set KKT system lost positive-definite rank",
                        iteration);
            }

            double directionNorm = infinityNorm(step.direction());
            if (directionNorm <= tolerance) {
                Row removal = null;
                double mostNegative = -tolerance;
                for (int i = 0; i < active.size(); i++) {
                    Row row = active.get(i);
                    if (row.equality()) {
                        continue;
                    }
                    double multiplier = step.multipliers()[i];
                    if (multiplier < mostNegative
                            || (Math.abs(multiplier - mostNegative) <= tolerance
                                    && removal != null
                                    && row.id().compareTo(removal.id()) < 0)) {
                        mostNegative = multiplier;
                        removal = row;
                    }
                }
                if (removal != null) {
                    working.remove(removal.id());
                    continue;
                }

                Evidence evidence = evidence(x, scaled, constraints, active, step.multipliers());
                double acceptance = tolerance(Math.max(1.0, evidence.problemScale()));
                if (evidence.primalResidual() > 32.0 * acceptance
                        || evidence.stationarityResidual() > 32.0 * acceptance
                        || evidence.dualFeasibilityResidual() > 32.0 * acceptance
                        || evidence.complementarityResidual() > 64.0 * acceptance) {
                    return terminalFailure(
                            SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                            "KKT residuals exceeded deterministic acceptance budget",
                            iteration);
                }
                return solved(problem, scaled, x, iteration, active, evidence);
            }

            double alpha = 1.0;
            Row blocker = null;
            for (Row row : constraints.inequalities()) {
                if (working.contains(row.id())) {
                    continue;
                }
                double directional = row.dot(step.direction());
                if (directional <= tolerance) {
                    continue;
                }
                double slack = row.rhs() - row.dot(x);
                if (slack < -32.0 * tolerance) {
                    return terminalFailure(
                            SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                            "active-set iterate left feasible region",
                            iteration);
                }
                double candidate = Math.max(0.0, slack) / directional;
                if (candidate < alpha - tolerance
                        || (Math.abs(candidate - alpha) <= tolerance
                                && candidate <= 1.0 + tolerance
                                && (blocker == null || row.id().compareTo(blocker.id()) < 0))) {
                    alpha = candidate;
                    blocker = row;
                }
            }

            alpha = Math.max(0.0, Math.min(1.0, alpha));
            for (int i = 0; i < x.length; i++) {
                x[i] += alpha * step.direction()[i];
            }
            if (blocker != null && alpha < 1.0 - tolerance) {
                working.add(blocker.id());
            }

            double residual = primalResidual(x, constraints);
            if (residual > 64.0 * tolerance) {
                return terminalFailure(
                        SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                        "step produced excessive primal residual",
                        iteration + 1);
            }
        }

        return terminalFailure(
                SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE,
                "active-set iteration limit reached",
                MAX_ITERATIONS);
    }

    private static ScaledProblem scale(SkyIslandHydraulicBoundedQpProblem problem) {
        double[] target = problem.target();
        double[] weight = problem.weight();
        double[] lower = problem.lowerBound();
        double[] upper = problem.upperBound();

        double minimum = Arrays.stream(lower).min().orElseThrow();
        double maximum = Arrays.stream(upper).max().orElseThrow();
        double datum = 0.5 * (minimum + maximum);
        double headScale = Math.max(1.0, maximum - minimum);
        for (SkyIslandHydraulicDifferenceConstraint constraint : problem.differenceConstraints()) {
            headScale = Math.max(headScale, Math.abs(constraint.lower()));
            headScale = Math.max(headScale, Math.abs(constraint.upper()));
        }

        double maximumWeight = Arrays.stream(weight).max().orElseThrow();
        double[] scaledWeight = new double[weight.length];
        double[] scaledTarget = new double[target.length];
        double[] scaledLower = new double[lower.length];
        double[] scaledUpper = new double[upper.length];
        for (int i = 0; i < target.length; i++) {
            scaledTarget[i] = (target[i] - datum) / headScale;
            scaledLower[i] = (lower[i] - datum) / headScale;
            scaledUpper[i] = (upper[i] - datum) / headScale;
            scaledWeight[i] = weight[i] / maximumWeight;
            if (!(scaledWeight[i] > 0.0) || !Double.isFinite(scaledWeight[i])) {
                throw new IllegalArgumentException("weight scaling produced an invalid positive weight");
            }
        }

        double finalHeadScale = headScale;
        List<SkyIslandHydraulicDifferenceConstraint> differences =
                problem.differenceConstraints().stream()
                        .sorted(Comparator
                                .comparing(SkyIslandHydraulicDifferenceConstraint::id)
                                .thenComparingInt(SkyIslandHydraulicDifferenceConstraint::leftIndex)
                                .thenComparingInt(SkyIslandHydraulicDifferenceConstraint::rightIndex))
                        .map(constraint -> new SkyIslandHydraulicDifferenceConstraint(
                                constraint.id(),
                                constraint.leftIndex(),
                                constraint.rightIndex(),
                                constraint.lower() / finalHeadScale,
                                constraint.upper() / finalHeadScale))
                        .toList();

        return new ScaledProblem(
                scaledTarget,
                scaledWeight,
                scaledLower,
                scaledUpper,
                differences,
                datum,
                headScale);
    }

    private static ConstraintSet constraints(ScaledProblem problem) {
        List<Row> equalities = new ArrayList<>();
        List<Row> inequalities = new ArrayList<>();
        for (int i = 0; i < problem.target().length; i++) {
            if (Double.compare(problem.lower()[i], problem.upper()[i]) == 0) {
                equalities.add(Row.single("box:" + i + ":fixed", i, 1.0, problem.lower()[i], true));
            } else {
                inequalities.add(Row.single("box:" + i + ":upper", i, 1.0, problem.upper()[i], false));
                inequalities.add(Row.single("box:" + i + ":lower", i, -1.0, -problem.lower()[i], false));
            }
        }
        for (SkyIslandHydraulicDifferenceConstraint difference : problem.differences()) {
            String prefix = "diff:" + difference.id();
            if (Double.compare(difference.lower(), difference.upper()) == 0) {
                equalities.add(Row.pair(
                        prefix + ":fixed",
                        difference.leftIndex(),
                        1.0,
                        difference.rightIndex(),
                        -1.0,
                        difference.lower(),
                        true));
            } else {
                inequalities.add(Row.pair(
                        prefix + ":upper",
                        difference.leftIndex(),
                        1.0,
                        difference.rightIndex(),
                        -1.0,
                        difference.upper(),
                        false));
                inequalities.add(Row.pair(
                        prefix + ":lower",
                        difference.leftIndex(),
                        -1.0,
                        difference.rightIndex(),
                        1.0,
                        -difference.lower(),
                        false));
            }
        }
        equalities.sort(Comparator.comparing(Row::id));
        inequalities.sort(Comparator.comparing(Row::id));
        return new ConstraintSet(List.copyOf(equalities), List.copyOf(inequalities));
    }

    private static Optional<double[]> feasiblePoint(ScaledProblem problem) {
        int variableCount = problem.target().length;
        int anchor = variableCount;
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < variableCount; i++) {
            edges.add(new Edge(anchor, i, problem.upper()[i], "box:" + i + ":upper"));
            edges.add(new Edge(i, anchor, -problem.lower()[i], "box:" + i + ":lower"));
        }
        for (SkyIslandHydraulicDifferenceConstraint difference : problem.differences()) {
            edges.add(new Edge(
                    difference.rightIndex(),
                    difference.leftIndex(),
                    difference.upper(),
                    "diff:" + difference.id() + ":upper"));
            edges.add(new Edge(
                    difference.leftIndex(),
                    difference.rightIndex(),
                    -difference.lower(),
                    "diff:" + difference.id() + ":lower"));
        }
        edges.sort(Comparator
                .comparingInt(Edge::from)
                .thenComparingInt(Edge::to)
                .thenComparingDouble(Edge::weight)
                .thenComparing(Edge::id));

        double[] distance = new double[variableCount + 1];
        for (int pass = 0; pass < distance.length; pass++) {
            boolean updated = false;
            for (Edge edge : edges) {
                double candidate = distance[edge.from()] + edge.weight();
                if (candidate < distance[edge.to()] - BELLMAN_FORD_TOLERANCE) {
                    distance[edge.to()] = candidate;
                    updated = true;
                    if (pass == distance.length - 1) {
                        return Optional.empty();
                    }
                }
            }
            if (!updated) {
                break;
            }
        }

        double anchorValue = distance[anchor];
        double[] feasible = new double[variableCount];
        for (int i = 0; i < variableCount; i++) {
            feasible[i] = distance[i] - anchorValue;
        }
        return Optional.of(feasible);
    }

    private static List<Row> independentActiveRows(
            List<Row> equalities,
            List<Row> inequalities,
            Set<String> working,
            double[] weight) {
        List<Row> candidates = new ArrayList<>(equalities);
        for (Row row : inequalities) {
            if (working.contains(row.id())) {
                candidates.add(row);
            }
        }
        candidates.sort(Comparator
                .comparing((Row row) -> !row.equality())
                .thenComparing(Row::id));

        List<double[]> orthonormal = new ArrayList<>();
        List<Row> independent = new ArrayList<>();
        for (Row row : candidates) {
            double[] vector = row.metricVector(weight);
            for (double[] basis : orthonormal) {
                double projection = dot(vector, basis);
                for (int i = 0; i < vector.length; i++) {
                    vector[i] -= projection * basis[i];
                }
            }
            // Re-orthogonalize once to reduce deterministic rank loss near dependence.
            for (double[] basis : orthonormal) {
                double projection = dot(vector, basis);
                for (int i = 0; i < vector.length; i++) {
                    vector[i] -= projection * basis[i];
                }
            }
            double norm = euclideanNorm(vector);
            if (norm <= RANK_TOLERANCE) {
                continue;
            }
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
            orthonormal.add(vector);
            independent.add(row);
        }
        return List.copyOf(independent);
    }

    private static KktStep kktStep(double[] gradient, double[] weight, List<Row> active) {
        int n = gradient.length;
        if (active.isEmpty()) {
            double[] direction = new double[n];
            for (int i = 0; i < n; i++) {
                direction[i] = -gradient[i] / weight[i];
            }
            return new KktStep(direction, new double[0]);
        }

        int m = active.size();
        double[][] gram = new double[m][m];
        double[] rhs = new double[m];
        for (int r = 0; r < m; r++) {
            Row rowR = active.get(r);
            for (int i = 0; i < n; i++) {
                rhs[r] -= rowR.coefficient(i) * gradient[i] / weight[i];
            }
            for (int c = 0; c <= r; c++) {
                Row rowC = active.get(c);
                double value = 0.0;
                for (int i = 0; i < n; i++) {
                    value += rowR.coefficient(i) * rowC.coefficient(i) / weight[i];
                }
                gram[r][c] = value;
                gram[c][r] = value;
            }
        }

        double[] multipliers = choleskySolve(gram, rhs);
        if (multipliers == null) {
            return null;
        }
        double[] direction = new double[n];
        for (int i = 0; i < n; i++) {
            double stationarity = gradient[i];
            for (int r = 0; r < m; r++) {
                stationarity += active.get(r).coefficient(i) * multipliers[r];
            }
            direction[i] = -stationarity / weight[i];
        }
        return new KktStep(direction, multipliers);
    }

    private static double[] choleskySolve(double[][] matrix, double[] rhs) {
        int n = rhs.length;
        double[][] lower = new double[n][n];
        double scale = 0.0;
        for (int i = 0; i < n; i++) {
            scale = Math.max(scale, Math.abs(matrix[i][i]));
        }
        double pivotFloor = Math.max(1.0, scale) * RANK_TOLERANCE * RANK_TOLERANCE;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = matrix[i][j];
                for (int k = 0; k < j; k++) {
                    sum -= lower[i][k] * lower[j][k];
                }
                if (i == j) {
                    if (!(sum > pivotFloor) || !Double.isFinite(sum)) {
                        return null;
                    }
                    lower[i][j] = Math.sqrt(sum);
                } else {
                    lower[i][j] = sum / lower[j][j];
                }
            }
        }

        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = rhs[i];
            for (int j = 0; j < i; j++) {
                sum -= lower[i][j] * y[j];
            }
            y[i] = sum / lower[i][i];
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = y[i];
            for (int j = i + 1; j < n; j++) {
                sum -= lower[j][i] * x[j];
            }
            x[i] = sum / lower[i][i];
        }
        return x;
    }

    private static Evidence evidence(
            double[] x,
            ScaledProblem problem,
            ConstraintSet constraints,
            List<Row> active,
            double[] multipliers) {
        double[] gradient = gradient(x, problem.target(), problem.weight());
        double[] stationarity = gradient.clone();
        double complementarity = 0.0;
        double dual = 0.0;
        List<String> activeIds = new ArrayList<>();
        for (int r = 0; r < active.size(); r++) {
            Row row = active.get(r);
            activeIds.add(row.id());
            double multiplier = multipliers[r];
            for (int i = 0; i < stationarity.length; i++) {
                stationarity[i] += row.coefficient(i) * multiplier;
            }
            if (!row.equality()) {
                dual = Math.max(dual, Math.max(0.0, -multiplier));
                complementarity = Math.max(
                        complementarity,
                        Math.abs(multiplier * (row.rhs() - row.dot(x))));
            }
        }
        return new Evidence(
                primalResidual(x, constraints),
                infinityNorm(stationarity),
                complementarity,
                dual,
                Math.max(1.0, infinityNorm(gradient)),
                List.copyOf(activeIds));
    }

    private static SkyIslandHydraulicQpResult solved(
            SkyIslandHydraulicBoundedQpProblem original,
            ScaledProblem scaled,
            double[] scaledSolution,
            int iterations,
            List<Row> active,
            Evidence evidence) {
        double[] solution = new double[scaledSolution.length];
        for (int i = 0; i < solution.length; i++) {
            solution[i] = scaled.datum() + scaled.headScale() * scaledSolution[i];
        }
        double objective = 0.0;
        double[] target = original.target();
        double[] weight = original.weight();
        for (int i = 0; i < solution.length; i++) {
            double residual = solution[i] - target[i];
            objective += 0.5 * weight[i] * residual * residual;
        }
        return new SkyIslandHydraulicQpResult(
                SkyIslandHydraulicQpStatus.SOLVED,
                solution,
                iterations,
                objective,
                evidence.primalResidual() * scaled.headScale(),
                evidence.stationarityResidual(),
                evidence.complementarityResidual(),
                evidence.dualFeasibilityResidual(),
                evidence.activeIds(),
                Optional.empty());
    }

    private static SkyIslandHydraulicQpResult terminalFailure(
            SkyIslandHydraulicQpStatus status,
            String diagnostic,
            int iterations) {
        return new SkyIslandHydraulicQpResult(
                status,
                new double[0],
                iterations,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                List.of(),
                Optional.of(diagnostic));
    }

    private static double[] gradient(double[] x, double[] target, double[] weight) {
        double[] gradient = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            gradient[i] = weight[i] * (x[i] - target[i]);
        }
        return gradient;
    }

    private static double primalResidual(double[] x, ConstraintSet constraints) {
        double residual = 0.0;
        for (Row row : constraints.equalities()) {
            residual = Math.max(residual, Math.abs(row.dot(x) - row.rhs()));
        }
        for (Row row : constraints.inequalities()) {
            residual = Math.max(residual, Math.max(0.0, row.dot(x) - row.rhs()));
        }
        return residual;
    }

    private static double tolerance(double scale) {
        return ABSOLUTE_TOLERANCE + RELATIVE_TOLERANCE * Math.max(1.0, scale);
    }

    private static double infinityNorm(double[] values) {
        double result = 0.0;
        for (double value : values) {
            result = Math.max(result, Math.abs(value));
        }
        return result;
    }

    private static double euclideanNorm(double[] values) {
        return Math.sqrt(dot(values, values));
    }

    private static double dot(double[] a, double[] b) {
        double result = 0.0;
        for (int i = 0; i < a.length; i++) {
            result += a[i] * b[i];
        }
        return result;
    }

    private record ScaledProblem(
            double[] target,
            double[] weight,
            double[] lower,
            double[] upper,
            List<SkyIslandHydraulicDifferenceConstraint> differences,
            double datum,
            double headScale) {}

    private record ConstraintSet(List<Row> equalities, List<Row> inequalities) {}

    private record KktStep(double[] direction, double[] multipliers) {}

    private record Evidence(
            double primalResidual,
            double stationarityResidual,
            double complementarityResidual,
            double dualFeasibilityResidual,
            double problemScale,
            List<String> activeIds) {}

    private record Edge(int from, int to, double weight, String id) {}

    private record Row(
            String id,
            int firstIndex,
            double firstCoefficient,
            int secondIndex,
            double secondCoefficient,
            double rhs,
            boolean equality) {

        static Row single(
                String id,
                int index,
                double coefficient,
                double rhs,
                boolean equality) {
            return new Row(id, index, coefficient, -1, 0.0, rhs, equality);
        }

        static Row pair(
                String id,
                int firstIndex,
                double firstCoefficient,
                int secondIndex,
                double secondCoefficient,
                double rhs,
                boolean equality) {
            return new Row(
                    id,
                    firstIndex,
                    firstCoefficient,
                    secondIndex,
                    secondCoefficient,
                    rhs,
                    equality);
        }

        double dot(double[] values) {
            double result = firstCoefficient * values[firstIndex];
            if (secondIndex >= 0) {
                result += secondCoefficient * values[secondIndex];
            }
            return result;
        }

        double coefficient(int index) {
            double result = 0.0;
            if (firstIndex == index) {
                result += firstCoefficient;
            }
            if (secondIndex == index) {
                result += secondCoefficient;
            }
            return result;
        }

        double[] metricVector(double[] weight) {
            double[] vector = new double[weight.length];
            vector[firstIndex] += firstCoefficient / Math.sqrt(weight[firstIndex]);
            if (secondIndex >= 0) {
                vector[secondIndex] += secondCoefficient / Math.sqrt(weight[secondIndex]);
            }
            return vector;
        }
    }
}
