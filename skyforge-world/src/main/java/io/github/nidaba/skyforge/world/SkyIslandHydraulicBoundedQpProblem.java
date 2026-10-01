package io.github.nidaba.skyforge.world;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Strictly convex diagonal quadratic projection over box and pair-difference constraints.
 *
 * <p>The objective is {@code 0.5 * sum_i weight[i] * (x[i] - target[i])^2}. This is the exact
 * constraint family required by the F2 hydraulic-head solve after shared semantic nodes are
 * represented by shared variables.
 */
public record SkyIslandHydraulicBoundedQpProblem(
        double[] target,
        double[] weight,
        double[] lowerBound,
        double[] upperBound,
        List<SkyIslandHydraulicDifferenceConstraint> differenceConstraints) {

    public SkyIslandHydraulicBoundedQpProblem {
        target = copy(target, "target");
        weight = copy(weight, "weight");
        lowerBound = copy(lowerBound, "lowerBound");
        upperBound = copy(upperBound, "upperBound");
        if (target.length == 0
                || weight.length != target.length
                || lowerBound.length != target.length
                || upperBound.length != target.length) {
            throw new IllegalArgumentException("all variable arrays must have the same positive length");
        }
        for (int i = 0; i < target.length; i++) {
            if (!Double.isFinite(target[i])) {
                throw new IllegalArgumentException("target values must be finite");
            }
            if (!Double.isFinite(weight[i]) || weight[i] <= 0.0) {
                throw new IllegalArgumentException("weights must be finite and strictly positive");
            }
            if (!Double.isFinite(lowerBound[i])
                    || !Double.isFinite(upperBound[i])
                    || lowerBound[i] > upperBound[i]) {
                throw new IllegalArgumentException("box bounds must be finite and ordered");
            }
        }

        differenceConstraints = List.copyOf(
                Objects.requireNonNull(differenceConstraints, "differenceConstraints"));
        Set<String> ids = new HashSet<>();
        for (SkyIslandHydraulicDifferenceConstraint constraint : differenceConstraints) {
            Objects.requireNonNull(constraint, "difference constraint");
            if (constraint.leftIndex() >= target.length || constraint.rightIndex() >= target.length) {
                throw new IllegalArgumentException("difference constraint index exceeds variable count");
            }
            if (!ids.add(constraint.id())) {
                throw new IllegalArgumentException("difference constraint ids must be unique");
            }
        }
    }

    public int variableCount() {
        return target.length;
    }

    @Override
    public double[] target() {
        return target.clone();
    }

    @Override
    public double[] weight() {
        return weight.clone();
    }

    @Override
    public double[] lowerBound() {
        return lowerBound.clone();
    }

    @Override
    public double[] upperBound() {
        return upperBound.clone();
    }

    private static double[] copy(double[] values, String name) {
        Objects.requireNonNull(values, name);
        return Arrays.copyOf(values, values.length);
    }
}
