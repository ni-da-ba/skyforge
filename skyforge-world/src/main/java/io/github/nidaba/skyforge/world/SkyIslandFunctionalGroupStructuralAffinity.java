package io.github.nidaba.skyforge.world;

/**
 * Explicit structural-affinity weights for one vegetation functional-group niche policy.
 *
 * <p>Weights correspond to the accepted neutral realization dimensions and are caller-supplied.
 * They are not inferred from functional-group identity. Values must be finite and non-negative,
 * with at least one positive weight.
 */
public record SkyIslandFunctionalGroupStructuralAffinity(
        double vegetationDensityWeight,
        double canopyCoverWeight,
        double canopyHeightPotentialWeight,
        double understoryDensityWeight,
        double groundCoverWeight,
        double biomassPotentialWeight,
        double patchinessPotentialWeight,
        double organicSurfaceAccumulationPotentialWeight,
        double deadwoodPotentialWeight) {

    public SkyIslandFunctionalGroupStructuralAffinity {
        requireWeight("vegetationDensityWeight", vegetationDensityWeight);
        requireWeight("canopyCoverWeight", canopyCoverWeight);
        requireWeight("canopyHeightPotentialWeight", canopyHeightPotentialWeight);
        requireWeight("understoryDensityWeight", understoryDensityWeight);
        requireWeight("groundCoverWeight", groundCoverWeight);
        requireWeight("biomassPotentialWeight", biomassPotentialWeight);
        requireWeight("patchinessPotentialWeight", patchinessPotentialWeight);
        requireWeight(
                "organicSurfaceAccumulationPotentialWeight",
                organicSurfaceAccumulationPotentialWeight);
        requireWeight("deadwoodPotentialWeight", deadwoodPotentialWeight);

        double total = totalWeight();
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException(
                    "structural-affinity total weight must be finite and positive");
        }
    }

    public double totalWeight() {
        return vegetationDensityWeight
                + canopyCoverWeight
                + canopyHeightPotentialWeight
                + understoryDensityWeight
                + groundCoverWeight
                + biomassPotentialWeight
                + patchinessPotentialWeight
                + organicSurfaceAccumulationPotentialWeight
                + deadwoodPotentialWeight;
    }

    private static void requireWeight(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
