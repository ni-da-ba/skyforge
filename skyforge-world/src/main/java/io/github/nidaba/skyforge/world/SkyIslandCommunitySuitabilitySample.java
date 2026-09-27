package io.github.nidaba.skyforge.world;

/**
 * Overlapping backend-neutral ecological community suitability at one island-local position.
 *
 * <p>Each value is a normalized suitability in {@code [0, 1]}. Suitability means that the current
 * accepted environment can support the structural community archetype; it does not assert occupancy,
 * persistence, species composition, population size, or backend biome identity.
 */
public record SkyIslandCommunitySuitabilitySample(
        double closedWoodland,
        double openHerbaceous,
        double saturatedWetland,
        double alpineTundra,
        double xericScrub) {

    public SkyIslandCommunitySuitabilitySample {
        requireNormalized("closedWoodland", closedWoodland);
        requireNormalized("openHerbaceous", openHerbaceous);
        requireNormalized("saturatedWetland", saturatedWetland);
        requireNormalized("alpineTundra", alpineTundra);
        requireNormalized("xericScrub", xericScrub);
    }

    public static SkyIslandCommunitySuitabilitySample outside() {
        return new SkyIslandCommunitySuitabilitySample(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
