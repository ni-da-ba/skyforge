package io.github.nidaba.skyforge.world;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit coexistence weights for multi-community structural composition.
 *
 * <p>Weights are composition policy only. They are not abundance, occupancy probability, percent
 * cover, or population fraction. Weights are not renormalized; their total must remain at most 1.
 */
public record SkyIslandCommunityCoexistenceWeights(
        Map<SkyIslandCommunityArchetype, Double> weights) {

    public SkyIslandCommunityCoexistenceWeights {
        Objects.requireNonNull(weights, "weights");
        EnumMap<SkyIslandCommunityArchetype, Double> copy =
                new EnumMap<>(SkyIslandCommunityArchetype.class);
        double total = 0.0;

        for (Map.Entry<SkyIslandCommunityArchetype, Double> entry : weights.entrySet()) {
            SkyIslandCommunityArchetype community =
                    Objects.requireNonNull(entry.getKey(), "community");
            Double boxed = Objects.requireNonNull(entry.getValue(), "weight");
            double weight = boxed;
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException(
                        "coexistence weights must be finite and non-negative");
            }
            total += weight;
            if (!Double.isFinite(total) || total > 1.0 + 1.0e-12) {
                throw new IllegalArgumentException(
                        "total coexistence weight must be finite and <= 1");
            }
            copy.put(community, weight);
        }

        weights = Collections.unmodifiableMap(copy);
    }

    /** Returns the explicit weight for a community, or zero when it is omitted. */
    public double weight(SkyIslandCommunityArchetype community) {
        Objects.requireNonNull(community, "community");
        return weights.getOrDefault(community, 0.0);
    }

    public double totalWeight() {
        return weights.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
