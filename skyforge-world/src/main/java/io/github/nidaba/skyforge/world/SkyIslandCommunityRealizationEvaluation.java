package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Transparent result of applying one explicit structural-realization profile to one assembly result.
 *
 * <p>The exact #1206 assembly evaluation is retained so downstream consumers can inspect provenance
 * rather than treating realized structure as an unexplained backend decoration decision.
 */
public record SkyIslandCommunityRealizationEvaluation(
        SkyIslandCommunityAssemblyEvaluation assemblyEvaluation,
        SkyIslandCommunityRealizationProfile realizationProfile,
        Optional<SkyIslandCommunityStructureRealization> realization) {

    public SkyIslandCommunityRealizationEvaluation {
        assemblyEvaluation =
                Objects.requireNonNull(assemblyEvaluation, "assemblyEvaluation");
        realizationProfile =
                Objects.requireNonNull(realizationProfile, "realizationProfile");
        realization = Objects.requireNonNull(realization, "realization");
    }

    public boolean resolved() {
        return realization.isPresent();
    }

    public SkyIslandCommunityArchetype community() {
        return assemblyEvaluation.profile().community();
    }

    public SkyIslandLocalPosition position() {
        return assemblyEvaluation.position();
    }
}
