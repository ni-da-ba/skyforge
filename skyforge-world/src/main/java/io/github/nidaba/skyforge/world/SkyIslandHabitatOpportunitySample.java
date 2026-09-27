package io.github.nidaba.skyforge.world;

/**
 * Overlapping backend-neutral ecological habitat opportunities at one island-local position.
 *
 * <p>Each value is a normalized opportunity in {@code [0, 1]}. Values are not probabilities,
 * occupancy claims, biome IDs, species identities, or mutually exclusive classifications.
 */
public record SkyIslandHabitatOpportunitySample(
        double woodland,
        double openVegetation,
        double saturatedLowland,
        double alpineExposed,
        double xericExposed) {

    public SkyIslandHabitatOpportunitySample {
        requireNormalized("woodland", woodland);
        requireNormalized("openVegetation", openVegetation);
        requireNormalized("saturatedLowland", saturatedLowland);
        requireNormalized("alpineExposed", alpineExposed);
        requireNormalized("xericExposed", xericExposed);
    }

    /** Returns a zero-opportunity sample outside the authored ecological domain. */
    public static SkyIslandHabitatOpportunitySample outside() {
        return new SkyIslandHabitatOpportunitySample(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
