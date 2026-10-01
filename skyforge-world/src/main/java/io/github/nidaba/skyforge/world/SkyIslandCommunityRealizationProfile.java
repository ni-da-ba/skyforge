package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit structural-realization policy applied downstream of one #1206 community assembly result.
 *
 * <p>The profile is deliberately not inferred from the community archetype. Callers may assign
 * different structural capacities or transforms to the same community without changing upstream
 * ecological meaning.
 */
public record SkyIslandCommunityRealizationProfile(
        SkyIslandCommunityStructureCapacity capacity,
        SkyIslandCommunityRealizationTransform transform) {

    public SkyIslandCommunityRealizationProfile {
        capacity = Objects.requireNonNull(capacity, "capacity");
        transform = Objects.requireNonNull(transform, "transform");
    }

    /** Evaluates this realization profile against one exact community-assembly evaluation. */
    public SkyIslandCommunityRealizationEvaluation evaluate(
            SkyIslandCommunityAssemblyEvaluation assemblyEvaluation) {
        Objects.requireNonNull(assemblyEvaluation, "assemblyEvaluation");
        return new SkyIslandCommunityRealizationEvaluation(
                assemblyEvaluation,
                this,
                Objects.requireNonNull(
                        transform.realize(assemblyEvaluation, capacity),
                        "realization transform result"));
    }
}
