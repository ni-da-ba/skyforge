package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;

/** Independent enrichment controls for one canonical provider-neutral morphology hybrid. */
public record ProviderHybridMorphologyEnrichment(
        MorphologyProviderBlend blend,
        double detailAmplitude,
        double secondaryMorphologyAmplitude) {

    /** Validates the provider blend and normalized enrichment amplitudes. */
    public ProviderHybridMorphologyEnrichment {
        Objects.requireNonNull(blend, "blend");
        requireNormalized("detailAmplitude", detailAmplitude);
        requireNormalized("secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);
        detailAmplitude = canonicalZeroOne(detailAmplitude);
        secondaryMorphologyAmplitude = canonicalZeroOne(secondaryMorphologyAmplitude);
    }

    /** Returns full accepted bounded detail and full provider-aware secondary morphology. */
    public static ProviderHybridMorphologyEnrichment full(MorphologyProviderBlend blend) {
        return new ProviderHybridMorphologyEnrichment(blend, 1.0, 1.0);
    }

    private static double canonicalZeroOne(double value) {
        if (value == 0.0) {
            return 0.0;
        }
        if (value == 1.0) {
            return 1.0;
        }
        return value;
    }

    private static void requireNormalized(String property, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(property + " must be finite and in [0, 1]");
        }
    }
}
