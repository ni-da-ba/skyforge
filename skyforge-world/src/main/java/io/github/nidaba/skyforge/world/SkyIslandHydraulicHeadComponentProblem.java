package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Deterministically composes bounded hydraulic-head observations and difference constraints into
 * one QP. Reusing a key makes occurrences across ordinary spans one shared head variable.
 *
 * <p>Each observation contributes its own positive quadratic weight and hard D2 interval. Repeated
 * keys intersect their intervals and combine their weighted targets; an empty intersection fails
 * closed before the QP solver is called. No envelope or difference bound is widened.
 */
public final class SkyIslandHydraulicHeadComponentProblem {
    private SkyIslandHydraulicHeadComponentProblem() {}

    public static Builder builder() {
        return new Builder();
    }

    public record Outcome(
            SkyIslandHydraulicQpStatus status,
            Optional<SkyIslandHydraulicQpResult> solve,
            Map<String, Double> heads,
            Optional<String> diagnostic) {
        public Outcome {
            status = Objects.requireNonNull(status, "status");
            solve = Objects.requireNonNull(solve, "solve");
            Objects.requireNonNull(heads, "heads");
            TreeMap<String, Double> orderedHeads = new TreeMap<>();
            heads.forEach((key, value) -> {
                if (key == null || key.isBlank() || value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("solved component heads require finite keyed values");
                }
                orderedHeads.put(key, value);
            });
            heads = Collections.unmodifiableMap(new LinkedHashMap<>(orderedHeads));
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
            if (status == SkyIslandHydraulicQpStatus.SOLVED) {
                if (solve.isEmpty()
                        || solve.orElseThrow().status() != SkyIslandHydraulicQpStatus.SOLVED
                        || heads.isEmpty()
                        || diagnostic.isPresent()) {
                    throw new IllegalArgumentException("solved component outcome requires a complete QP result");
                }
            } else if (!heads.isEmpty()) {
                throw new IllegalArgumentException("failed component outcome cannot expose selected heads");
            }
        }
    }

    public static final class Builder {
        private static final Comparator<Observation> OBSERVATION_ORDER =
                Comparator.comparingDouble(Observation::lower)
                        .thenComparingDouble(Observation::upper)
                        .thenComparingDouble(Observation::target)
                        .thenComparingDouble(Observation::weight);

        private final Map<String, List<Observation>> observations = new TreeMap<>();
        private final List<KeyedDifference> differences = new ArrayList<>();

        private Builder() {}

        /**
         * Adds one physical observation of a shared variable.
         *
         * @param key stable identity shared by all occurrences of the same physical boundary
         * @param target quadratic objective target
         * @param weight strictly positive objective weight
         * @param lower hard lower head bound
         * @param upper hard upper head bound
         */
        public Builder addHead(
                String key,
                double target,
                double weight,
                double lower,
                double upper) {
            requireKey(key, "head key");
            if (!Double.isFinite(target)
                    || !Double.isFinite(weight)
                    || weight <= 0.0
                    || !Double.isFinite(lower)
                    || !Double.isFinite(upper)
                    || lower > upper) {
                throw new IllegalArgumentException(
                        "head observations require finite targets, positive weights, and ordered finite bounds");
            }
            observations.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new Observation(target, weight, lower, upper));
            return this;
        }

        /** Adds {@code lower <= head(leftKey) - head(rightKey) <= upper}. */
        public Builder addDifference(
                String id,
                String leftKey,
                String rightKey,
                double lower,
                double upper) {
            requireKey(id, "difference id");
            requireKey(leftKey, "left head key");
            requireKey(rightKey, "right head key");
            if (leftKey.equals(rightKey)) {
                throw new IllegalArgumentException("difference constraint requires distinct head keys");
            }
            if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper) {
                throw new IllegalArgumentException("difference bounds must be finite and ordered");
            }
            differences.add(new KeyedDifference(id, leftKey, rightKey, lower, upper));
            return this;
        }

        public Outcome solve() {
            if (observations.isEmpty()) {
                throw new IllegalStateException("component head problem requires at least one variable");
            }

            List<String> keys = List.copyOf(observations.keySet());
            Map<String, Integer> indexByKey = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                indexByKey.put(keys.get(i), i);
            }

            double[] target = new double[keys.size()];
            double[] weight = new double[keys.size()];
            double[] lower = new double[keys.size()];
            double[] upper = new double[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                List<Observation> contributions = new ArrayList<>(observations.get(key));
                contributions.sort(OBSERVATION_ORDER);
                lower[i] = contributions.stream()
                        .mapToDouble(Observation::lower)
                        .max()
                        .orElseThrow();
                upper[i] = contributions.stream()
                        .mapToDouble(Observation::upper)
                        .min()
                        .orElseThrow();
                if (lower[i] > upper[i]) {
                    double gap = lower[i] - upper[i];
                    String diagnostic = "shared head envelope is empty for "
                            + key
                            + " (lowerHeadWorld="
                            + Double.toString(lower[i])
                            + ", upperHeadWorld="
                            + Double.toString(upper[i])
                            + ", infeasibilityGapWorld="
                            + Double.toString(gap)
                            + ")";
                    return new Outcome(
                            SkyIslandHydraulicQpStatus.INFEASIBLE,
                            Optional.empty(),
                            Map.of(),
                            Optional.of(diagnostic));
                }

                double totalWeight = 0.0;
                double weightedTarget = 0.0;
                for (Observation observation : contributions) {
                    totalWeight += observation.weight();
                    weightedTarget += observation.weight() * observation.target();
                }
                if (!Double.isFinite(totalWeight) || !Double.isFinite(weightedTarget)) {
                    throw new IllegalArgumentException("combined component objective overflowed");
                }
                weight[i] = totalWeight;
                target[i] = weightedTarget / totalWeight;
            }

            List<KeyedDifference> orderedDifferences = differences.stream()
                    .sorted(Comparator.comparing(KeyedDifference::id)
                            .thenComparing(KeyedDifference::leftKey)
                            .thenComparing(KeyedDifference::rightKey)
                            .thenComparingDouble(KeyedDifference::lower)
                            .thenComparingDouble(KeyedDifference::upper))
                    .toList();
            List<SkyIslandHydraulicDifferenceConstraint> indexedDifferences =
                    new ArrayList<>(orderedDifferences.size());
            for (KeyedDifference difference : orderedDifferences) {
                Integer left = indexByKey.get(difference.leftKey());
                Integer right = indexByKey.get(difference.rightKey());
                if (left == null || right == null) {
                    throw new IllegalStateException(
                            "difference constraint references an unregistered component head");
                }
                indexedDifferences.add(new SkyIslandHydraulicDifferenceConstraint(
                        difference.id(),
                        left,
                        right,
                        difference.lower(),
                        difference.upper()));
            }

            SkyIslandHydraulicQpResult result =
                    SkyIslandHydraulicBoundedQpSolver.solve(
                            new SkyIslandHydraulicBoundedQpProblem(
                                    target, weight, lower, upper, indexedDifferences));
            if (result.status() != SkyIslandHydraulicQpStatus.SOLVED) {
                return new Outcome(
                        result.status(),
                        Optional.of(result),
                        Map.of(),
                        result.diagnostic());
            }

            double[] solution = result.solution();
            Map<String, Double> heads = new TreeMap<>();
            for (int i = 0; i < keys.size(); i++) {
                heads.put(keys.get(i), solution[i]);
            }
            return new Outcome(
                    SkyIslandHydraulicQpStatus.SOLVED,
                    Optional.of(result),
                    heads,
                    Optional.empty());
        }

        private static void requireKey(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }

    private record Observation(double target, double weight, double lower, double upper) {}

    private record KeyedDifference(
            String id,
            String leftKey,
            String rightKey,
            double lower,
            double upper) {}
}
