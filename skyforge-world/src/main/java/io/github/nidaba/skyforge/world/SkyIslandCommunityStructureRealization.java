package io.github.nidaba.skyforge.world;

/**
 * Normalized backend-neutral ecological structure realized for one community profile.
 *
 * <p>These values describe semantic visible structure only. They do not identify species, backend
 * blocks/features, physical biomass, physical canopy height, or population counts.
 */
public record SkyIslandCommunityStructureRealization(
        double vegetationDensity,
        double canopyCover,
        double canopyHeightPotential,
        double understoryDensity,
        double groundCover,
        double biomassPotential,
        double patchinessPotential,
        double organicSurfaceAccumulationPotential,
        double deadwoodPotential) {

    public SkyIslandCommunityStructureRealization {
        requireNormalized("vegetationDensity", vegetationDensity);
        requireNormalized("canopyCover", canopyCover);
        requireNormalized("canopyHeightPotential", canopyHeightPotential);
        requireNormalized("understoryDensity", understoryDensity);
        requireNormalized("groundCover", groundCover);
        requireNormalized("biomassPotential", biomassPotential);
        requireNormalized("patchinessPotential", patchinessPotential);
        requireNormalized(
                "organicSurfaceAccumulationPotential",
                organicSurfaceAccumulationPotential);
        requireNormalized("deadwoodPotential", deadwoodPotential);
    }

    /** Returns zero realized structure. */
    public static SkyIslandCommunityStructureRealization zero() {
        return new SkyIslandCommunityStructureRealization(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
