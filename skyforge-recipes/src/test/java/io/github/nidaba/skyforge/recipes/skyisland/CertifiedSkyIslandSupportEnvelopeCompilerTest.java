package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandMorphologyFamily;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CertifiedSkyIslandSupportEnvelopeCompilerTest {

    @Test
    void certifiesEveryAcceptedSchema2BuiltInFamily() {
        CertifiedSkyIslandSupportEnvelopeCompiler compiler =
                new CertifiedSkyIslandSupportEnvelopeCompiler();

        for (SkyIslandMorphologyFamily semanticFamily :
                SkyIslandMorphologyFamily.values()) {
            SkyIslandVolumeDescriptor descriptor =
                    SkyIslandVolumeDescriptor.schema2(
                            0x5100000000000000L ^ semanticFamily.ordinal(),
                            120.0,
                            -80.0,
                            260.0,
                            100.0,
                            42.0,
                            58.0,
                            24.0,
                            0.31,
                            0.63,
                            0.57,
                            -0.18,
                            semanticFamily,
                            0.40,
                            36.0,
                            0.55);
            CompiledSkyIslandVolume compiled =
                    new SemanticSkyIslandVolumeRecipe().compile(descriptor);

            CertifiedSkyIslandSupportEnvelope envelope =
                    compiler.certify(compiled).orElseThrow();

            MorphologyFamily family = MorphologyFamily.fromSemantic(semanticFamily);
            SkyIslandMorphologyProvider provider =
                    SkyIslandMorphologyProviders.builtIn(family);
            double detailMaximum =
                    Math.nextUp(
                            1.0
                                    + SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT
                                            * descriptor.detailAmplitude());
            double secondaryMaximum =
                    Math.nextUp(
                            provider.compileSecondaryMorphology(
                                            descriptor,
                                            descriptor.secondaryMorphologyAmplitude())
                                    .orElseThrow()
                                    .maximumFactor());
            double primaryUpper = Math.nextUp(descriptor.upperElevation());
            double primaryUnderside =
                    Math.nextUp(2.0 * descriptor.undersideDepth());

            assertEquals(
                    Math.nextUp(1.65 * descriptor.nominalRadius()),
                    envelope.maximumHorizontalRadius(),
                    0.0);
            assertEquals(
                    outwardMultiply(
                            outwardMultiply(primaryUpper, detailMaximum),
                            secondaryMaximum),
                    envelope.maximumUpperOffset(),
                    0.0);
            assertEquals(
                    outwardMultiply(primaryUnderside, detailMaximum),
                    envelope.maximumUndersideDepth(),
                    0.0);
            assertEquals(
                    "semantic-built-in-v1:" + family.identifier(),
                    envelope.certificateKind());
        }
    }

    @Test
    void unknownRecipePathRemainsUncertified() {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        51002L,
                        0.0,
                        0.0,
                        220.0,
                        100.0,
                        40.0,
                        56.0,
                        24.0,
                        0.0,
                        0.55,
                        0.60,
                        0.10,
                        0.0,
                        32.0);
        CompiledSkyIslandVolume primary =
                new MorphologyFamilySkyIslandVolumeRecipe()
                        .compile(descriptor, MorphologyFamily.MASSIF);

        assertTrue(
                new CertifiedSkyIslandSupportEnvelopeCompiler()
                        .certify(primary)
                        .isEmpty());
    }

    @Test
    void semanticRecipeVersionWithoutSemanticProvenanceRemainsUncertified() {
        SkyIslandVolumeDescriptor descriptor =
                SkyIslandVolumeDescriptor.schema2(
                        51005L,
                        0.0,
                        0.0,
                        220.0,
                        100.0,
                        40.0,
                        56.0,
                        24.0,
                        0.0,
                        0.55,
                        0.60,
                        0.10,
                        SkyIslandMorphologyFamily.MASSIF,
                        0.25,
                        32.0,
                        0.30);
        CompiledSkyIslandVolume accepted =
                new SemanticSkyIslandVolumeRecipe().compile(descriptor);
        CompiledSkyIslandVolume forgedProvenance =
                new CompiledSkyIslandVolume(
                        descriptor,
                        SemanticSkyIslandVolumeRecipe.RECIPE_VERSION,
                        accepted.graphSchemaVersion(),
                        accepted.upperSurfaceGraph(),
                        accepted.undersideSurfaceGraph(),
                        accepted.densityGraph(),
                        Map.of());

        assertTrue(
                new CertifiedSkyIslandSupportEnvelopeCompiler()
                        .certify(forgedProvenance)
                        .isEmpty());
    }

    @Test
    void providerCertificationIsExplicitOptIn() {
        SkyIslandMorphologyProvider uncertified =
                new SkyIslandMorphologyProvider() {
                    @Override
                    public MorphologyProviderId id() {
                        return new MorphologyProviderId("test", "uncertified");
                    }

                    @Override
                    public PrimaryMorphologyContribution compilePrimary(
                            SkyIslandVolumeDescriptor descriptor) {
                        throw new UnsupportedOperationException("not needed");
                    }
                };
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        51003L,
                        0.0,
                        0.0,
                        220.0,
                        100.0,
                        40.0,
                        56.0,
                        24.0,
                        0.0,
                        0.55,
                        0.60,
                        0.10,
                        0.0,
                        32.0);

        Optional<PrimaryMorphologySupportEnvelope> envelope =
                uncertified.certifiedPrimarySupportEnvelope(descriptor);

        assertTrue(envelope.isEmpty());
    }

    @Test
    void builtInPrimaryCertificateKeepsAnalyticalMargin() {
        SkyIslandVolumeDescriptor descriptor =
                new SkyIslandVolumeDescriptor(
                        SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                        51004L,
                        0.0,
                        0.0,
                        220.0,
                        100.0,
                        40.0,
                        56.0,
                        24.0,
                        0.0,
                        1.0,
                        1.0,
                        1.0,
                        0.0,
                        32.0);

        for (MorphologyFamily family : MorphologyFamily.values()) {
            PrimaryMorphologySupportEnvelope envelope =
                    SkyIslandMorphologyProviders.builtIn(family)
                            .certifiedPrimarySupportEnvelope(descriptor)
                            .orElseThrow();
            assertEquals(Math.nextUp(165.0), envelope.maximumHorizontalRadius(), 0.0);
            assertEquals(Math.nextUp(40.0), envelope.maximumUpperOffset(), 0.0);
            assertEquals(Math.nextUp(112.0), envelope.maximumUndersideDepth(), 0.0);
        }
    }

    private static double outwardMultiply(double first, double second) {
        return Math.nextUp(first * second);
    }
}
