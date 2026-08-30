package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EnrichedProviderHybridMorphologySkyIslandVolumeRecipeTest {
    private static final long SKYFORGE_SEED = 0x534b59464f524745L;
    private static final MorphologyProviderId PRIMARY_ONLY_ID =
            new MorphologyProviderId("example", "primary-only");

    private final EnrichedProviderHybridMorphologySkyIslandVolumeRecipe recipe =
            new EnrichedProviderHybridMorphologySkyIslandVolumeRecipe();
    private final ProviderHybridMorphologySkyIslandVolumeRecipe primaryRecipe =
            new ProviderHybridMorphologySkyIslandVolumeRecipe();
    private final CanonicalGraphJson json = new CanonicalGraphJson();

    @Test
    void enrichmentControlsRejectInvalidValues() {
        MorphologyProviderBlend blend = blend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.5);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderHybridMorphologyEnrichment(blend, -0.01, 0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderHybridMorphologyEnrichment(blend, 0.0, 1.01));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderHybridMorphologyEnrichment(blend, Double.NaN, 0.0));
    }

    @Test
    void zeroEnrichmentPreservesExactProviderPrimaryGraphBytes() {
        SkyIslandVolumeDescriptor descriptor = descriptor(SKYFORGE_SEED);
        MorphologyProviderBlend blend = blend(MorphologyFamily.BASIN, MorphologyFamily.SPINE, 0.37);
        SkyIslandMorphologyProviderRegistry registry = registry(false);
        CompiledSkyIslandVolume expected = primaryRecipe.compile(descriptor, blend, registry);
        CompiledSkyIslandVolume actual = recipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 0.0, 0.0),
                registry);

        assertGraphsEqual(expected, actual);
    }

    @Test
    void canonicalProviderPairSymmetryRemainsGraphExactUnderFullEnrichment() {
        SkyIslandVolumeDescriptor descriptor = descriptor(SKYFORGE_SEED);
        MorphologyProviderId massif = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderId spine = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        MorphologyProviderBlend forward = new MorphologyProviderBlend(massif, spine, 0.30);
        MorphologyProviderBlend reverse = new MorphologyProviderBlend(spine, massif, 0.70);
        assertEquals(forward, reverse);

        SkyIslandMorphologyProviderRegistry registry = registry(false);
        CompiledSkyIslandVolume first = recipe.compile(
                descriptor, ProviderHybridMorphologyEnrichment.full(forward), registry);
        CompiledSkyIslandVolume second = recipe.compile(
                descriptor, ProviderHybridMorphologyEnrichment.full(reverse), registry);
        assertGraphsEqual(first, second);
    }

    @Test
    void providerSecondaryFactorsReplaceAndNeutralizeGenericStructuredRelief() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        MorphologyProviderBlend blend = blend(MorphologyFamily.TABLELAND, MorphologyFamily.BASIN, 0.5);
        CompiledSkyIslandVolume compiled = recipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 0.0, 1.0),
                registry(false));

        ConstantNode genericAmplitude = assertInstanceOf(
                ConstantNode.class,
                compiled.upperSurfaceGraph().requireNode(new NodeId("secondary.descriptor-amplitude")));
        assertEquals(0.0, genericAmplitude.value());
        compiled.upperSurfaceGraph().requireNode(new NodeId("provider-secondary.upper-factor"));
        ConstantNode minimum = assertInstanceOf(
                ConstantNode.class,
                compiled.upperSurfaceGraph().requireNode(new NodeId("provider-secondary.minimum-factor")));
        ConstantNode maximum = assertInstanceOf(
                ConstantNode.class,
                compiled.upperSurfaceGraph().requireNode(new NodeId("provider-secondary.maximum-factor")));
        assertTrue(minimum.value() > 0.0);
        assertTrue(maximum.value() >= minimum.value());
    }

    @Test
    void detailAndSecondaryControlsRemainIndependent() {
        SkyIslandVolumeDescriptor descriptor = descriptor(Long.MIN_VALUE);
        MorphologyProviderBlend blend = blend(MorphologyFamily.MASSIF, MorphologyFamily.LOBED, 0.5);
        SkyIslandMorphologyProviderRegistry registry = registry(false);
        CompiledSkyIslandVolume detailOnly = recipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 1.0, 0.0),
                registry);
        CompiledSkyIslandVolume secondaryOnly = recipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 0.0, 1.0),
                registry);

        assertEquals(1.0, constant(detailOnly, "descriptor.signal-amplitude.upper"));
        assertEquals(1.0, constant(detailOnly, "descriptor.signal-amplitude.underside"));
        assertEquals(1.0, constant(detailOnly, "provider-secondary.minimum-factor"));
        assertEquals(1.0, constant(detailOnly, "provider-secondary.maximum-factor"));

        assertEquals(0.0, constant(secondaryOnly, "descriptor.signal-amplitude.upper"));
        assertEquals(0.0, constant(secondaryOnly, "descriptor.signal-amplitude.underside"));
        assertTrue(constant(secondaryOnly, "provider-secondary.maximum-factor") > 1.0);
        assertNotEquals(
                json.writeString(detailOnly.upperSurfaceGraph()),
                json.writeString(secondaryOnly.upperSurfaceGraph()));
    }

    @Test
    void providerWithoutSecondaryVocabularyUsesNeutralFallback() {
        SkyIslandVolumeDescriptor descriptor = descriptor(SKYFORGE_SEED);
        MorphologyProviderId spine = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        MorphologyProviderBlend blend = new MorphologyProviderBlend(PRIMARY_ONLY_ID, spine, 0.5);
        CompiledSkyIslandVolume compiled = recipe.compile(
                descriptor,
                ProviderHybridMorphologyEnrichment.full(blend),
                registry(true));

        String neutralId = blend.first().equals(PRIMARY_ONLY_ID)
                ? ProviderSecondaryMorphologyComposition.FIRST_FACTOR_PREFIX + "neutral-factor"
                : ProviderSecondaryMorphologyComposition.SECOND_FACTOR_PREFIX + "neutral-factor";
        ConstantNode neutral = assertInstanceOf(
                ConstantNode.class,
                compiled.upperSurfaceGraph().requireNode(new NodeId(neutralId)));
        assertEquals(1.0, neutral.value());
        assertTrue(constant(compiled, "provider-secondary.minimum-factor") > 0.0);
    }

    @Test
    void enrichedEndpointCanUseProviderCanonicalCarrierWhileZeroEndpointStaysExact() {
        SkyIslandVolumeDescriptor descriptor = descriptor(0L);
        MorphologyProviderId massif = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        MorphologyProviderBlend blend = new MorphologyProviderBlend(PRIMARY_ONLY_ID, massif, 0.0);
        SkyIslandMorphologyProviderRegistry registry = registry(true);

        CompiledSkyIslandVolume exactParent = registry.require(PRIMARY_ONLY_ID)
                .compilePrimary(descriptor)
                .volume();
        CompiledSkyIslandVolume zero = recipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 0.0, 0.0),
                registry);
        assertGraphsEqual(exactParent, zero);

        CompiledSkyIslandVolume enriched = recipe.compile(
                descriptor,
                ProviderHybridMorphologyEnrichment.full(blend),
                registry);
        enriched.upperSurfaceGraph().requireNode(new NodeId("provider-canonical.source.profile.remaining"));
        enriched.upperSurfaceGraph().requireNode(new NodeId("provider-secondary.upper-factor"));
    }

    @Test
    void allBuiltInProviderPairsCompileWithFullEnrichmentAndExactDensityIntersection() {
        MorphologyFamily[] families = MorphologyFamily.values();
        SkyIslandMorphologyProviderRegistry registry = registry(false);
        for (int first = 0; first < families.length; first++) {
            for (int second = first + 1; second < families.length; second++) {
                MorphologyProviderBlend blend = blend(families[first], families[second], 0.5);
                CompiledSkyIslandVolume compiled = recipe.compile(
                        descriptor(SKYFORGE_SEED),
                        ProviderHybridMorphologyEnrichment.full(blend),
                        registry);
                IntersectionNode intersection = assertInstanceOf(
                        IntersectionNode.class,
                        compiled.densityGraph().requireNode(compiled.densityGraph().output()));
                assertEquals(new NodeId("density.upper-constraint"), intersection.left());
                assertEquals(new NodeId("density.lower-constraint"), intersection.right());
            }
        }
    }

    @Test
    void proofDescriptorStillRequiresSchemaOneAndZeroEmbeddedSignalAmplitude() {
        MorphologyProviderBlend blend = blend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.5);
        SkyIslandVolumeDescriptor invalidSignal = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                0L,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.1,
                32.0);
        assertThrows(
                IllegalArgumentException.class,
                () -> recipe.compile(
                        invalidSignal,
                        ProviderHybridMorphologyEnrichment.full(blend),
                        registry(false)));
    }

    private static SkyIslandMorphologyProviderRegistry registry(boolean primaryOnly) {
        SkyIslandMorphologyProviderRegistry.Builder builder = SkyIslandMorphologyProviderRegistry.builder();
        builder.registerAll(SkyIslandMorphologyProviders.builtInRegistry().providers());
        if (primaryOnly) {
            builder.register(new PrimaryOnlyProvider());
        }
        return builder.build();
    }

    private static MorphologyProviderBlend blend(
            MorphologyFamily first, MorphologyFamily second, double secondWeight) {
        return new MorphologyProviderBlend(
                SkyIslandMorphologyProviders.builtInId(first),
                SkyIslandMorphologyProviders.builtInId(second),
                secondWeight);
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                256.0,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                0.0,
                32.0);
    }

    private static double constant(CompiledSkyIslandVolume compiled, String id) {
        ConstantNode node = assertInstanceOf(
                ConstantNode.class,
                compiled.upperSurfaceGraph().requireNode(new NodeId(id)));
        return node.value();
    }

    private void assertGraphsEqual(CompiledSkyIslandVolume expected, CompiledSkyIslandVolume actual) {
        assertEquals(
                json.writeString(expected.upperSurfaceGraph()),
                json.writeString(actual.upperSurfaceGraph()));
        assertEquals(
                json.writeString(expected.undersideSurfaceGraph()),
                json.writeString(actual.undersideSurfaceGraph()));
        assertEquals(
                json.writeString(expected.densityGraph()),
                json.writeString(actual.densityGraph()));
    }

    private static final class PrimaryOnlyProvider implements SkyIslandMorphologyProvider {
        private final SkyIslandMorphologyProvider delegate =
                SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);

        @Override
        public MorphologyProviderId id() {
            return PRIMARY_ONLY_ID;
        }

        @Override
        public PrimaryMorphologyContribution compilePrimary(SkyIslandVolumeDescriptor descriptor) {
            return delegate.compilePrimary(descriptor);
        }

        @Override
        public Optional<SecondaryMorphologyContribution> compileSecondaryMorphology(
                SkyIslandVolumeDescriptor descriptor, double amplitude) {
            return SkyIslandMorphologyProvider.super.compileSecondaryMorphology(descriptor, amplitude);
        }
    }
}
