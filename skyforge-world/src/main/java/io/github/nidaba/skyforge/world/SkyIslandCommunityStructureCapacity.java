package io.github.nidaba.skyforge.world;

/**
 * Explicit backend-neutral structural capacity for one community-realization policy.
 *
 * <p>All dimensions are normalized semantic capacities in {@code [0, 1]}. They are not physical SI
 * measurements; in particular, canopy-height potential is not a value in meters.
 */
public record SkyIslandCommunityStructureCapacity(
        double vegetationDensityCapacity,
        double canopyCoverCapacity,
        double canopyHeightPotential,
        double understoryDensityCapacity,
        double groundCoverCapacity,
        double biomassPotential,
        double patchinessPotential,
        double organicSurfaceAccumulationPotential,
        double deadwoodPotential) {

    public SkyIslandCommunityStructureCapacity {
        requireNormalized("vegetationDensityCapacity", vegetationDensityCapacity);
        requireNormalized("canopyCoverCapacity", canopyCoverCapacity);
        requireNormalized("canopyHeightPotential", canopyHeightPotential);
        requireNormalized("understoryDensityCapacity", understoryDensityCapacity);
        requireNormalized("groundCoverCapacity", groundCoverCapacity);
        requireNormalized("biomassPotential", biomassPotential);
        requireNormalized("patchinessPotential", patchinessPotential);
        requireNormalized(
                "organicSurfaceAccumulationPotential",
                organicSurfaceAccumulationPotential);
        requireNormalized("deadwoodPotential", deadwoodPotential);
    }

    /** Returns a zero-capacity structural profile. */
    public static SkyIslandCommunityStructureCapacity zero() {
        return new SkyIslandCommunityStructureCapacity(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
