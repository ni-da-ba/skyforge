package io.github.nidaba.skyforge.recipes.skyisland.group;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0052 analytical support certifier for explicit provider morphology specifications.
 *
 * <p>Direct providers can certify when the provider opts in. Exact blend endpoints inherit the
 * selected endpoint provider. True non-endpoint hybrids remain uncertified until their blended
 * structural-coordinate support receives a separate proof.
 */
public final class SkyIslandMorphologySpecSupportEnvelopeCompiler {

    public Optional<CertifiedSkyIslandSupportEnvelope> certify(
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologySpec morphology,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(morphology, "morphology");
        Objects.requireNonNull(registry, "registry");

        return switch (morphology) {
            case ProviderMorphologySpec provider ->
                    certifyProvider(
                            descriptor,
                            provider.providerId(),
                            provider.detailAmplitude(),
                            provider.secondaryMorphologyAmplitude(),
                            "provider-spec-v1:" + provider.providerId(),
                            registry);
            case ProviderBlendMorphologySpec blend -> {
                double secondWeight = blend.blend().secondWeight();
                if (secondWeight != 0.0 && secondWeight != 1.0) {
                    yield Optional.empty();
                }
                MorphologyProviderId selected =
                        secondWeight == 0.0
                                ? blend.blend().first()
                                : blend.blend().second();
                yield certifyProvider(
                        descriptor,
                        selected,
                        blend.detailAmplitude(),
                        blend.secondaryMorphologyAmplitude(),
                        "provider-blend-endpoint-v1:"
                                + blend.blend().pairIdentifier()
                                + "@"
                                + selected,
                        registry);
            }
        };
    }

    private static Optional<CertifiedSkyIslandSupportEnvelope> certifyProvider(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderId providerId,
            double detailAmplitude,
            double secondaryAmplitude,
            String certificateKind,
            SkyIslandMorphologyProviderRegistry registry) {
        SkyIslandMorphologyProvider provider = registry.require(providerId);
        Optional<PrimaryMorphologySupportEnvelope> primary =
                provider.certifiedPrimarySupportEnvelope(descriptor);
        if (primary.isEmpty()) {
            return Optional.empty();
        }

        double detailMaximum =
                Math.nextUp(
                        1.0
                                + SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT
                                        * detailAmplitude);
        Optional<SecondaryMorphologyContribution> secondary =
                provider.compileSecondaryMorphology(descriptor, secondaryAmplitude);
        double secondaryMaximum =
                Math.nextUp(
                        secondary.map(SecondaryMorphologyContribution::maximumFactor)
                                .orElse(1.0));

        PrimaryMorphologySupportEnvelope base = primary.orElseThrow();
        return Optional.of(
                new CertifiedSkyIslandSupportEnvelope(
                        Math.nextUp(base.maximumHorizontalRadius()),
                        outwardMultiply(
                                outwardMultiply(
                                        base.maximumUpperOffset(),
                                        detailMaximum),
                                secondaryMaximum),
                        outwardMultiply(
                                base.maximumUndersideDepth(),
                                detailMaximum),
                        certificateKind));
    }

    private static double outwardMultiply(double first, double second) {
        return Math.nextUp(first * second);
    }
}
