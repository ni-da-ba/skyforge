package io.github.nidaba.skyforge.world;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable result and numerical evidence for one bounded hydraulic quadratic solve. */
public record SkyIslandHydraulicQpResult(
        SkyIslandHydraulicQpStatus status,
        double[] solution,
        int iterations,
        double objective,
        double primalResidual,
        double stationarityResidual,
        double complementarityResidual,
        double dualFeasibilityResidual,
        List<String> activeConstraintIds,
        Optional<String> diagnostic) {

    public SkyIslandHydraulicQpResult {
        status = Objects.requireNonNull(status, "status");
        solution = solution == null ? new double[0] : Arrays.copyOf(solution, solution.length);
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be non-negative");
        }
        requireFiniteNonNegative(objective, "objective");
        requireFiniteNonNegative(primalResidual, "primalResidual");
        requireFiniteNonNegative(stationarityResidual, "stationarityResidual");
        requireFiniteNonNegative(complementarityResidual, "complementarityResidual");
        requireFiniteNonNegative(dualFeasibilityResidual, "dualFeasibilityResidual");
        activeConstraintIds = List.copyOf(
                Objects.requireNonNull(activeConstraintIds, "activeConstraintIds"));
        diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    @Override
    public double[] solution() {
        return solution.clone();
    }

    private static void requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
