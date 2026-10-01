package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import java.util.Objects;

/** One explicit provider plus independent detail and secondary-morphology controls. */
public record ProviderMorphologySpec(
        MorphologyProviderId providerId,
        double detailAmplitude,
        double secondaryMorphologyAmplitude)
        implements SkyIslandMorphologySpec {

    /** Validates provider identity and normalized enrichment amplitudes. */
    public ProviderMorphologySpec {
        Objects.requireNonNull(providerId, "providerId");
        detailAmplitude = canonicalAmplitude("detailAmplitude", detailAmplitude);
        secondaryMorphologyAmplitude = canonicalAmplitude(
                "secondaryMorphologyAmplitude", secondaryMorphologyAmplitude);
    }

    /** Convenience constructor for a fully enriched provider morphology. */
    public static ProviderMorphologySpec full(MorphologyProviderId providerId) {
        return new ProviderMorphologySpec(providerId, 1.0, 1.0);
    }

    @Override
    public String stableIdentifier() {
        return providerId.toString();
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
