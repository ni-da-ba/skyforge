package io.github.nidaba.skyforge.world;

/**
 * Continuous backend-neutral ecological responses at one island-local position.
 *
 * <p>These values retain the exact normalized AUTH-0003 meanings. They are semantic responses in
 * {@code [0, 1]}, not physical SI quantities and not categorical biome identity.
 */
public record SkyIslandEcologyResponseSample(
        double vegetationPotential,
        double saturationPotential,
        double thermalSuitability) {

    public SkyIslandEcologyResponseSample {
        requireNormalized("vegetationPotential", vegetationPotential);
        requireNormalized("saturationPotential", saturationPotential);
        requireNormalized("thermalSuitability", thermalSuitability);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
