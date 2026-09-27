package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Generic weighted linear interpretation of residual disturbance and recovery progress.
 *
 * <p>The two non-negative weights are normalized internally. A disturbance-favoring profile can
 * assign more weight to residual disturbance; a recovery-favoring profile can assign more weight to
 * recovery progress. This is a reusable response curve, not a built-in species or community policy.
 */
public record SkyIslandWeightedSuccessionAffinityProfile(
        double residualDisturbanceWeight,
        double recoveryProgressWeight)
        implements SkyIslandCommunitySuccessionAffinityProfile {

    public SkyIslandWeightedSuccessionAffinityProfile {
        requireFiniteNonNegative("residualDisturbanceWeight", residualDisturbanceWeight);
        requireFiniteNonNegative("recoveryProgressWeight", recoveryProgressWeight);
        if (residualDisturbanceWeight + recoveryProgressWeight <= 0.0) {
            throw new IllegalArgumentException(
                    "at least one succession-affinity weight must be positive");
        }
    }

    @Override
    public OptionalDouble affinity(SkyIslandCommunitySuccessionAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        if (assessment.state().isEmpty()) {
            return OptionalDouble.empty();
        }
        SkyIslandCommunitySuccessionState state = assessment.state().orElseThrow();
        double totalWeight = residualDisturbanceWeight + recoveryProgressWeight;
        double value =
                (residualDisturbanceWeight * state.residualDisturbance()
                                + recoveryProgressWeight * state.recoveryProgress())
                        / totalWeight;
        return OptionalDouble.of(requireNormalized("succession affinity", value));
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static double requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalStateException(name + " must be finite and in [0, 1]");
        }
        return value;
    }
}
