package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Explicit limiting-factor community assembly combiner.
 *
 * <p>Support is the product of local suitability, dispersal accessibility, and succession affinity.
 * If either assembly factor is unresolved, composite support remains unresolved.
 */
public enum SkyIslandMultiplicativeAssemblyCombiner
        implements SkyIslandCommunityAssemblyCombiner {
    INSTANCE;

    @Override
    public OptionalDouble support(
            double localSuitability,
            OptionalDouble dispersalAccessibility,
            OptionalDouble successionAffinity) {
        requireNormalized("localSuitability", localSuitability);
        Objects.requireNonNull(dispersalAccessibility, "dispersalAccessibility");
        Objects.requireNonNull(successionAffinity, "successionAffinity");

        if (dispersalAccessibility.isEmpty() || successionAffinity.isEmpty()) {
            return OptionalDouble.empty();
        }

        double accessibility =
                requireNormalized(
                        "dispersalAccessibility",
                        dispersalAccessibility.orElseThrow());
        double succession =
                requireNormalized(
                        "successionAffinity",
                        successionAffinity.orElseThrow());
        return OptionalDouble.of(localSuitability * accessibility * succession);
    }

    private static double requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
        return value;
    }
}
