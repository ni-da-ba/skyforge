package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import java.util.Objects;

/** One canonical pairwise provider blend plus independent enrichment controls. */
public record ProviderBlendMorphologySpec(
        MorphologyProviderBlend blend,
        double detailAmplitude,
        double secondaryMorphologyAmplitude)
        implements SkyIslandMorphologySpec {

    /** Validates the canonical blend and normalized enrichment amplitudes. */
    public ProviderBlendMorphologySpec {
        Objects.requireNonNull(blend, "blend");
        detailAmplitude = canonicalAmplitude("detailAmplitude", detailAmplitude);
        secondaryMorphologyAmplitude = canonicalAmplitude(
                "secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);
    }

    /** Convenience constructor for a fully enriched provider blend. */
    public static ProviderBlendMorphologySpec full(MorphologyProviderBlend blend) {
        return new ProviderBlendMorphologySpec(blend, 1.0, 1.0);
    }

    @Override
    public String stableIdentifier() {
        return blend.pairIdentifier() + "@" + Double.toString(blend.secondWeight());
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
