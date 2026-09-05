package io.github.nidaba.skyforge.recipes.skyisland.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CertifiedSkyIslandSupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologySupportEnvelope;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandMorphologySpecSupportEnvelopeCompilerTest {
    private final SkyIslandMorphologyProviderRegistry registry =
            SkyIslandMorphologyProviders.builtInRegistry();
    private final SkyIslandMorphologySpecCompiler compiler =
            new SkyIslandMorphologySpecCompiler();

    @Test
    void directProviderCompilesExactVolumeAndAnalyticalSupport() {
        SkyIslandVolumeDescriptor descriptor = descriptor(52001L);
        MorphologyProviderId id =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        ProviderMorphologySpec spec = ProviderMorphologySpec.full(id);

        var ordinary = compiler.compile(descriptor, spec, registry);
        SkyIslandMorphologySpecCompilation supported =
                compiler.compileWithSupport(descriptor, spec, registry);

        assertEquals(ordinary, supported.volume());
        CertifiedSkyIslandSupportEnvelope envelope =
                supported.supportEnvelope().orElseThrow();
        assertEquals(
                expected(descriptor, registry.require(id), 1.0, 1.0),
                envelope);
        assertEquals("provider-spec-v1:" + id, envelope.certificateKind());
    }

    @Test
    void exactBlendEndpointsInheritOnlyTheSelectedProviderCertificate() {
        SkyIslandVolumeDescriptor descriptor = descriptor(52002L);
        MorphologyProviderId massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderId basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);

        ProviderBlendMorphologySpec first =
                new ProviderBlendMorphologySpec(
                        new MorphologyProviderBlend(massif, basin, 0.0),
                        0.45,
                        0.65);
        ProviderBlendMorphologySpec second =
                new ProviderBlendMorphologySpec(
                        new MorphologyProviderBlend(massif, basin, 1.0),
                        0.45,
                        0.65);

        CertifiedSkyIslandSupportEnvelope firstEnvelope =
                compiler.compileWithSupport(descriptor, first, registry)
                        .supportEnvelope()
                        .orElseThrow();
        CertifiedSkyIslandSupportEnvelope secondEnvelope =
                compiler.compileWithSupport(descriptor, second, registry)
                        .supportEnvelope()
                        .orElseThrow();

        assertEnvelopeExtents(
                expected(descriptor, registry.require(massif), 0.45, 0.65),
                firstEnvelope);
        assertEnvelopeExtents(
                expected(descriptor, registry.require(basin), 0.45, 0.65),
                secondEnvelope);
        assertTrue(firstEnvelope.certificateKind().contains(massif.toString()));
        assertTrue(secondEnvelope.certificateKind().contains(basin.toString()));
    }

    @Test
    void trueNonEndpointBlendRemainsUncertified() {
        SkyIslandVolumeDescriptor descriptor = descriptor(52003L);
        MorphologyProviderId massif =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderId basin =
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.BASIN);
        ProviderBlendMorphologySpec blend =
                ProviderBlendMorphologySpec.full(
                        new MorphologyProviderBlend(massif, basin, 0.35));

        SkyIslandMorphologySpecCompilation result =
                compiler.compileWithSupport(descriptor, blend, registry);

        assertTrue(result.supportEnvelope().isEmpty());
        assertEquals(
                compiler.compile(descriptor, blend, registry),
                result.volume());
    }

    @Test
    void providerWithoutOptInSupportRemainsUncertified() {
        SkyIslandVolumeDescriptor descriptor = descriptor(52004L);
        SkyIslandMorphologyProvider delegate =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.TABLELAND);
        MorphologyProviderId customId =
                new MorphologyProviderId("test", "uncertified");
        SkyIslandMorphologyProvider custom =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return customId;
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor value) {
                        return delegate.compilePrimary(value);
                    }

                    @Override
                    public Optional<SecondaryMorphologyContribution>
                            compileSecondaryMorphology(
                                    SkyIslandVolumeDescriptor value,
                                    double amplitude) {
                        return delegate.compileSecondaryMorphology(
                                value, amplitude);
                    }
                };
        SkyIslandMorphologyProviderRegistry customRegistry =
                SkyIslandMorphologyProviderRegistry.builder()
                        .register(custom)
                        .build();

        SkyIslandMorphologySpecCompilation result =
                compiler.compileWithSupport(
                        descriptor,
                        ProviderMorphologySpec.full(customId),
                        customRegistry);

        assertTrue(result.supportEnvelope().isEmpty());
    }

    private static CertifiedSkyIslandSupportEnvelope expected(
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologyProvider provider,
            double detailAmplitude,
            double secondaryAmplitude) {
        PrimaryMorphologySupportEnvelope primary =
                provider.certifiedPrimarySupportEnvelope(descriptor)
                        .orElseThrow();
        double detail =
                Math.nextUp(
                        1.0
                                + SeededSkyIslandVolumeRecipe
                                                .MAXIMUM_RELATIVE_DISPLACEMENT
                                        * detailAmplitude);
        double secondary =
                Math.nextUp(
                        provider.compileSecondaryMorphology(
                                        descriptor, secondaryAmplitude)
                                .map(SecondaryMorphologyContribution::maximumFactor)
                                .orElse(1.0));
        return new CertifiedSkyIslandSupportEnvelope(
                Math.nextUp(primary.maximumHorizontalRadius()),
                outwardMultiply(
                        outwardMultiply(
                                primary.maximumUpperOffset(),
                                detail),
                        secondary),
                outwardMultiply(
                        primary.maximumUndersideDepth(),
                        detail),
                "ignored");
    }

    private static void assertEnvelopeExtents(
            CertifiedSkyIslandSupportEnvelope expected,
            CertifiedSkyIslandSupportEnvelope actual) {
        assertEquals(
                expected.maximumHorizontalRadius(),
                actual.maximumHorizontalRadius(),
                0.0);
        assertEquals(
                expected.maximumUpperOffset(),
                actual.maximumUpperOffset(),
                0.0);
        assertEquals(
                expected.maximumUndersideDepth(),
                actual.maximumUndersideDepth(),
                0.0);
    }

    private static double outwardMultiply(double first, double second) {
        return Math.nextUp(first * second);
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                256.0,
                192.0,
                76.0,
                100.0,
                48.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                28.0);
    }
}
