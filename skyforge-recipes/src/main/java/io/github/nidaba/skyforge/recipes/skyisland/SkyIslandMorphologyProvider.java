package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Objects;
import java.util.Optional;

/**
 * Public recipe-layer extension contract for one suspended sky-island morphology vocabulary.
 *
 * <p>Providers supply signal-free primary structure and may additionally supply organized secondary
 * geography as a positive multiplicative factor. Provider discovery and application integration are
 * intentionally outside this interface.
 */
public interface SkyIslandMorphologyProvider {
    /** Stable provider identity used for registry lookup, provenance, and deterministic composition. */
    MorphologyProviderId id();

    /** Compiles one signal-free primary morphology contribution. */
    PrimaryMorphologyContribution compilePrimary(SkyIslandVolumeDescriptor descriptor);

    /**
     * Optionally declares a proof-grade conservative support envelope for the signal-free primary.
     *
     * <p>Providers that cannot analytically certify their continuous support return empty. AUTH-0051
     * never derives a certificate from finite sampling.
     */
    default Optional<PrimaryMorphologySupportEnvelope> certifiedPrimarySupportEnvelope(
            SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return Optional.empty();
    }

    /**
     * Compiles organized secondary geography at the requested amplitude.
     *
     * <p>Providers without a secondary vocabulary return {@link Optional#empty()} and can still
     * participate in primary morphology generation and later neutral-secondary composition.
     */
    default Optional<SecondaryMorphologyContribution> compileSecondaryMorphology(
            SkyIslandVolumeDescriptor descriptor, double amplitude) {
        if (!Double.isFinite(amplitude) || amplitude < 0.0 || amplitude > 1.0) {
            throw new IllegalArgumentException("secondary morphology amplitude must be finite and in [0, 1]");
        }
        return Optional.empty();
    }
}
