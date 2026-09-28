package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Generic weighted-mean functional-group structural-niche transform.
 *
 * <p>This transform computes the weighted mean of accepted normalized realization dimensions using
 * an explicit caller-supplied structural-affinity profile. It is one named policy, not universal
 * ecological law.
 */
public enum SkyIslandWeightedMeanFunctionalGroupNicheTransform
        implements SkyIslandFunctionalGroupNicheTransform {
    INSTANCE;

    @Override
    public OptionalDouble support(
            Optional<SkyIslandCommunityStructureRealization> aggregateRealization,
            SkyIslandFunctionalGroupStructuralAffinity affinity) {
        Objects.requireNonNull(aggregateRealization, "aggregateRealization");
        Objects.requireNonNull(affinity, "affinity");
        if (aggregateRealization.isEmpty()) {
            return OptionalDouble.empty();
        }

        SkyIslandCommunityStructureRealization structure =
                aggregateRealization.orElseThrow();
        double weighted =
                affinity.vegetationDensityWeight() * structure.vegetationDensity()
                        + affinity.canopyCoverWeight() * structure.canopyCover()
                        + affinity.canopyHeightPotentialWeight()
                                * structure.canopyHeightPotential()
                        + affinity.understoryDensityWeight() * structure.understoryDensity()
                        + affinity.groundCoverWeight() * structure.groundCover()
                        + affinity.biomassPotentialWeight() * structure.biomassPotential()
                        + affinity.patchinessPotentialWeight() * structure.patchinessPotential()
                        + affinity.organicSurfaceAccumulationPotentialWeight()
                                * structure.organicSurfaceAccumulationPotential()
                        + affinity.deadwoodPotentialWeight() * structure.deadwoodPotential();

        double support = weighted / affinity.totalWeight();
        if (!Double.isFinite(support) || support < 0.0 || support > 1.0) {
            throw new IllegalStateException(
                    "functional-group structural niche support must remain finite and normalized");
        }
        return OptionalDouble.of(support);
    }
}
