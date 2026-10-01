package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Transparent result of explicit multi-community structural composition.
 *
 * <p>The full per-community realization set is retained even when an aggregate realization resolves.
 */
public record SkyIslandMultiCommunityRealizationComposition(
        SkyIslandMultiCommunityRealizationSet realizationSet,
        SkyIslandCommunityCoexistenceWeights coexistenceWeights,
        SkyIslandMultiCommunityRealizationCompositor compositor,
        Optional<SkyIslandCommunityStructureRealization> aggregateRealization) {

    public SkyIslandMultiCommunityRealizationComposition {
        realizationSet = Objects.requireNonNull(realizationSet, "realizationSet");
        coexistenceWeights =
                Objects.requireNonNull(coexistenceWeights, "coexistenceWeights");
        compositor = Objects.requireNonNull(compositor, "compositor");
        aggregateRealization =
                Objects.requireNonNull(aggregateRealization, "aggregateRealization");
    }

    public static SkyIslandMultiCommunityRealizationComposition evaluate(
            SkyIslandMultiCommunityRealizationSet realizationSet,
            SkyIslandCommunityCoexistenceWeights coexistenceWeights,
            SkyIslandMultiCommunityRealizationCompositor compositor) {
        Objects.requireNonNull(realizationSet, "realizationSet");
        Objects.requireNonNull(coexistenceWeights, "coexistenceWeights");
        Objects.requireNonNull(compositor, "compositor");

        return new SkyIslandMultiCommunityRealizationComposition(
                realizationSet,
                coexistenceWeights,
                compositor,
                Objects.requireNonNull(
                        compositor.compose(realizationSet, coexistenceWeights),
                        "compositor result"));
    }

    public boolean resolved() {
        return aggregateRealization.isPresent();
    }

    public SkyIslandLocalPosition position() {
        return realizationSet.position();
    }
}
