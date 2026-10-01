package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkyIslandMorphologyProviderContractTest {
    private static final long[] SEEDS = {
        Long.MIN_VALUE,
        0L,
        0x534b59464f524745L
    };

    private final CanonicalGraphJson graphJson = new CanonicalGraphJson();
    private final MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
            new MorphologyFamilySkyIslandVolumeRecipe();
    private final FamilyAwareMorphologySkyIslandVolumeRecipe familyAwareRecipe =
            new FamilyAwareMorphologySkyIslandVolumeRecipe();

    @Test
    void providerIdsAreCanonicalNamespacedAndSortable() {
        MorphologyProviderId parsed = MorphologyProviderId.parse("example:crescent");
        assertEquals("example", parsed.namespace());
        assertEquals("crescent", parsed.path());
        assertEquals("example:crescent", parsed.toString());
        assertTrue(parsed.compareTo(MorphologyProviderId.parse("skyforge:massif")) < 0);

        assertThrows(IllegalArgumentException.class, () -> MorphologyProviderId.parse("missing-colon"));
        assertThrows(IllegalArgumentException.class, () -> MorphologyProviderId.parse("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> new MorphologyProviderId("Skyforge", "massif"));
        assertThrows(IllegalArgumentException.class, () -> new MorphologyProviderId("skyforge", "Massif"));
    }

    @Test
    void registryIsImmutableSortedAndRejectsDuplicateIds() {
        SkyIslandMorphologyProvider massif = SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);
        SkyIslandMorphologyProvider basin = SkyIslandMorphologyProviders.builtIn(MorphologyFamily.BASIN);
        SkyIslandMorphologyProviderRegistry registry = SkyIslandMorphologyProviderRegistry.builder()
                .register(massif)
                .register(basin)
                .build();

        assertEquals(
                List.of(
                        new MorphologyProviderId("skyforge", "basin"),
                        new MorphologyProviderId("skyforge", "massif")),
                registry.ids());
        assertSame(massif, registry.require(new MorphologyProviderId("skyforge", "massif")));
        assertThrows(IllegalArgumentException.class, () -> SkyIslandMorphologyProviderRegistry.builder()
                .register(massif)
                .register(SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF)));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.require(new MorphologyProviderId("example", "unknown")));
    }

    @Test
    void builtInRegistryContainsAllAcceptedFamilies() {
        SkyIslandMorphologyProviderRegistry registry = SkyIslandMorphologyProviders.builtInRegistry();
        assertEquals(MorphologyFamily.values().length, registry.ids().size());
        for (MorphologyFamily family : MorphologyFamily.values()) {
            assertEquals(
                    SkyIslandMorphologyProviders.builtInId(family),
                    registry.require(SkyIslandMorphologyProviders.builtInId(family)).id());
        }
    }

    @Test
    void builtInProviderPrimaryGraphsRemainByteIdenticalAcrossEstablishedSeeds() {
        for (MorphologyFamily family : MorphologyFamily.values()) {
            SkyIslandMorphologyProvider provider = SkyIslandMorphologyProviders.builtIn(family);
            for (long seed : SEEDS) {
                SkyIslandVolumeDescriptor descriptor = descriptor(seed, 0.0);
                CompiledSkyIslandVolume accepted = primaryRecipe.compile(descriptor, family);
                PrimaryMorphologyContribution contribution = provider.compilePrimary(descriptor);
                assertGraphIdentity(accepted, contribution.volume());

                contribution.volume().upperSurfaceGraph().requireNode(contribution.footprintResidual());
                contribution.volume().upperSurfaceGraph().requireNode(contribution.alongNormalized());
                contribution.volume().upperSurfaceGraph().requireNode(contribution.acrossNormalized());
                contribution.volume().upperSurfaceGraph().requireNode(contribution.upperFactor());
                contribution.volume().undersideSurfaceGraph().requireNode(contribution.undersideDepthFactor());
                assertTrue(contribution.lobeDirectional().isPresent());
            }
        }
    }

    @Test
    void builtInSecondaryContributionUsesAcceptedFamilyAwareFactorCarrier() {
        SkyIslandVolumeDescriptor signalFree = descriptor(0x534b59464f524745L, 0.0);
        for (MorphologyFamily family : MorphologyFamily.values()) {
            SkyIslandMorphologyProvider provider = SkyIslandMorphologyProviders.builtIn(family);
            SecondaryMorphologyContribution contribution = provider
                    .compileSecondaryMorphology(signalFree, 1.0)
                    .orElseThrow();

            SkyIslandVolumeDescriptor carrierDescriptor = descriptor(signalFree.seed(), 1.0);
            CompiledSkyIslandVolume accepted = familyAwareRecipe.compile(carrierDescriptor, family);
            NodeId acceptedOutput = family == MorphologyFamily.MASSIF
                    ? new NodeId("secondary.upper-factor")
                    : new NodeId("family-aware.upper-factor");
            ProceduralGraph acceptedFactor =
                    new ProceduralGraph(accepted.upperSurfaceGraph().nodes(), acceptedOutput);

            assertEquals(
                    graphJson.writeString(acceptedFactor),
                    graphJson.writeString(contribution.factorGraph()),
                    family.toString());
            assertEquals(
                    FamilyAwareSecondaryMorphologyComposition.minimumUpperFactor(family),
                    contribution.minimumFactor());
            assertEquals(
                    FamilyAwareSecondaryMorphologyComposition.maximumUpperFactor(family),
                    contribution.maximumFactor());
        }
    }

    @Test
    void providerOutsideBuiltInEnumCanBeRegisteredAndResolved() {
        SkyIslandMorphologyProvider custom = new SkyIslandMorphologyProvider() {
            private final MorphologyProviderId id = new MorphologyProviderId("example", "crescent");
            private final SkyIslandMorphologyProvider delegate =
                    SkyIslandMorphologyProviders.builtIn(MorphologyFamily.MASSIF);

            @Override
            public MorphologyProviderId id() {
                return id;
            }

            @Override
            public PrimaryMorphologyContribution compilePrimary(SkyIslandVolumeDescriptor descriptor) {
                return delegate.compilePrimary(descriptor);
            }
        };

        SkyIslandMorphologyProviderRegistry registry = SkyIslandMorphologyProviderRegistry.builder()
                .registerAll(SkyIslandMorphologyProviders.builtInRegistry().providers())
                .register(custom)
                .build();

        assertSame(custom, registry.require(new MorphologyProviderId("example", "crescent")));
        assertTrue(registry.ids().contains(new MorphologyProviderId("example", "crescent")));
        assertTrue(custom.compileSecondaryMorphology(descriptor(0L, 0.0), 1.0).isEmpty());
    }

    @Test
    void malformedContributionsFailBeforeComposition() {
        PrimaryMorphologyContribution accepted = SkyIslandMorphologyProviders
                .builtIn(MorphologyFamily.MASSIF)
                .compilePrimary(descriptor(0L, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new PrimaryMorphologyContribution(
                accepted.volume(),
                new NodeId("missing.footprint"),
                accepted.alongNormalized(),
                accepted.acrossNormalized(),
                accepted.lobeDirectional(),
                accepted.upperFactor(),
                accepted.undersideDepthFactor()));

        ProceduralGraph constant2 = new ProceduralGraph(
                List.of(new ConstantNode(
                        new NodeId("factor"), GraphValueType.SCALAR_FIELD_2, 1.0)),
                new NodeId("factor"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SecondaryMorphologyContribution(constant2, 0.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SecondaryMorphologyContribution(constant2, 1.2, 1.1));
    }

    private void assertGraphIdentity(
            CompiledSkyIslandVolume expected, CompiledSkyIslandVolume actual) {
        assertEquals(
                graphJson.writeString(expected.upperSurfaceGraph()),
                graphJson.writeString(actual.upperSurfaceGraph()));
        assertEquals(
                graphJson.writeString(expected.undersideSurfaceGraph()),
                graphJson.writeString(actual.undersideSurfaceGraph()));
        assertEquals(
                graphJson.writeString(expected.densityGraph()),
                graphJson.writeString(actual.densityGraph()));
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double signalAmplitude) {
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
                signalAmplitude,
                32.0);
    }
}
