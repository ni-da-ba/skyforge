package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral AUTH-0040 capability advertisement for one prospective concrete material.
 *
 * <p>The backend owns candidate identity. Skyforge only evaluates this semantic capability vector.
 */
public record SkyIslandMaterialCapabilityProfile(
        double hostMatrixSuitability,
        double fabricExpressiveness,
        double alterationOverprintSuitability,
        double hydrologicConditioningSuitability,
        double structuralAccentSuitability) {

    public SkyIslandMaterialCapabilityProfile {
        requireNormalized("hostMatrixSuitability", hostMatrixSuitability);
        requireNormalized("fabricExpressiveness", fabricExpressiveness);
        requireNormalized("alterationOverprintSuitability", alterationOverprintSuitability);
        requireNormalized("hydrologicConditioningSuitability", hydrologicConditioningSuitability);
        requireNormalized("structuralAccentSuitability", structuralAccentSuitability);
    }

    public double capability(SkyIslandMaterialCapability capability) {
        return switch (Objects.requireNonNull(capability, "capability")) {
            case HOST_MATRIX_SUITABILITY -> hostMatrixSuitability;
            case FABRIC_EXPRESSIVENESS -> fabricExpressiveness;
            case ALTERATION_OVERPRINT_SUITABILITY -> alterationOverprintSuitability;
            case HYDROLOGIC_CONDITIONING_SUITABILITY -> hydrologicConditioningSuitability;
            case STRUCTURAL_ACCENT_SUITABILITY -> structuralAccentSuitability;
        };
    }

    public static SkyIslandMaterialCapabilityProfile uniform(double value) {
        return new SkyIslandMaterialCapabilityProfile(value, value, value, value, value);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
