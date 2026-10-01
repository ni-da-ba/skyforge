package io.github.nidaba.skyforge.recipes.skyisland;

import java.util.Objects;

/** Independent enrichment controls for one canonical primary-morphology hybrid. */
public record HybridMorphologyEnrichment(
        MorphologyBlend blend,
        double detailAmplitude,
        double secondaryMorphologyAmplitude) {
    /** Validates the canonical blend and normalized enrichment amplitudes. */
    public HybridMorphologyEnrichment {
        Objects.requireNonNull(blend, "blend");
        requireNormalized("detailAmplitude", detailAmplitude);
        requireNormalized("secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);
        detailAmplitude = canonicalZeroOne(detailAmplitude);
        secondaryMorphologyAmplitude = canonicalZeroOne(secondaryMorphologyAmplitude);
    }

    /** Returns full accepted bounded detail and full blended family-aware secondary morphology. */
    public static HybridMorphologyEnrichment full(MorphologyBlend blend) {
        return new HybridMorphologyEnrichment(blend, 1.0, 1.0);
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
