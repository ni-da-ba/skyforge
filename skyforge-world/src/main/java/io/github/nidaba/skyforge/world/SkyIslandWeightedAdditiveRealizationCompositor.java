package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit weighted-additive multi-community structural compositor.
 *
 * <p>Each positively weighted community contributes its realized structural values multiplied by
 * its caller-supplied coexistence weight. Omitted or zero-weight communities do not contribute and
 * do not need to resolve. Any positively weighted unresolved community keeps the aggregate
 * unresolved.
 *
 * <p>This is one named composition policy, not universal ecological law.
 */
public enum SkyIslandWeightedAdditiveRealizationCompositor
        implements SkyIslandMultiCommunityRealizationCompositor {
    INSTANCE;

    @Override
    public Optional<SkyIslandCommunityStructureRealization> compose(
            SkyIslandMultiCommunityRealizationSet realizationSet,
            SkyIslandCommunityCoexistenceWeights coexistenceWeights) {
        Objects.requireNonNull(realizationSet, "realizationSet");
        Objects.requireNonNull(coexistenceWeights, "coexistenceWeights");

        double vegetationDensity = 0.0;
        double canopyCover = 0.0;
        double canopyHeight = 0.0;
        double understoryDensity = 0.0;
        double groundCover = 0.0;
        double biomass = 0.0;
        double patchiness = 0.0;
        double organicSurface = 0.0;
        double deadwood = 0.0;

        for (SkyIslandCommunityRealizationEvaluation evaluation : realizationSet.evaluations()) {
            double weight = coexistenceWeights.weight(evaluation.community());
            if (weight <= 0.0) {
                continue;
            }
            if (evaluation.realization().isEmpty()) {
                return Optional.empty();
            }

            SkyIslandCommunityStructureRealization realized =
                    evaluation.realization().orElseThrow();
            vegetationDensity += weight * realized.vegetationDensity();
            canopyCover += weight * realized.canopyCover();
            canopyHeight += weight * realized.canopyHeightPotential();
            understoryDensity += weight * realized.understoryDensity();
            groundCover += weight * realized.groundCover();
            biomass += weight * realized.biomassPotential();
            patchiness += weight * realized.patchinessPotential();
            organicSurface += weight * realized.organicSurfaceAccumulationPotential();
            deadwood += weight * realized.deadwoodPotential();
        }

        return Optional.of(new SkyIslandCommunityStructureRealization(
                normalized("vegetationDensity", vegetationDensity),
                normalized("canopyCover", canopyCover),
                normalized("canopyHeightPotential", canopyHeight),
                normalized("understoryDensity", understoryDensity),
                normalized("groundCover", groundCover),
                normalized("biomassPotential", biomass),
                normalized("patchinessPotential", patchiness),
                normalized("organicSurfaceAccumulationPotential", organicSurface),
                normalized("deadwoodPotential", deadwood)));
    }

    private static double normalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0 + 1.0e-12) {
            throw new IllegalStateException(name + " aggregate must remain finite and normalized");
        }
        return Math.min(1.0, value);
    }
}
