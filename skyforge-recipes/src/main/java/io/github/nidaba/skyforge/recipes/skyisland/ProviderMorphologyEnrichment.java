package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;

/** Independent enrichment controls for one explicit morphology provider. */
public record ProviderMorphologyEnrichment(
        MorphologyProviderId providerId,
        double detailAmplitude,
        double secondaryMorphologyAmplitude) {

    /** Validates provider identity and normalized amplitudes. */
    public ProviderMorphologyEnrichment {
        Objects.requireNonNull(providerId, "providerId");
        detailAmplitude = canonicalAmplitude("detailAmplitude", detailAmplitude);
        secondaryMorphologyAmplitude = canonicalAmplitude(
                "secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);
    }

    /** Convenience construction for full detail and full provider-aware secondary morphology. */
    public static ProviderMorphologyEnrichment full(MorphologyProviderId providerId) {
        return new ProviderMorphologyEnrichment(providerId, 1.0, 1.0);
    }

    private static double canonicalAmplitude(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be finite and in [0, 1]");
        }
        if (value == 0.0) {
            return 0.0;
        }
        if (value == 1.0) {
            return 1.0;
        }
        return value;
    }
}
