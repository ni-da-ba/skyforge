package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderHybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderId;
import io.github.nidaba.skyforge.recipes.skyisland.PrimaryMorphologyContribution;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderHybridMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.ProviderHybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProvider;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviders;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.provider.ReferenceCrescentMorphologyProvider;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.HybridMorphologyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Full-resolution acceptance for provider-aware enrichment using the non-built-in reference provider. */
final class EnrichedProviderHybridMorphologyAcceptanceTest {
    private static final long[] SEEDS = {
        Long.MIN_VALUE,
        0L,
        0x534b59464f524745L
    };
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double MINIMUM_ENRICHMENT_SURFACE_DELTA = 0.10;
    private static final double TOLERANCE = 1.0e-10;
    private static final MorphologyProviderId PRIMARY_ONLY_CRESCENT_ID =
            new MorphologyProviderId("reference", "crescent-primary-only");

    private final ReferenceCrescentMorphologyProvider crescent =
            new ReferenceCrescentMorphologyProvider();
    private final ProviderHybridMorphologySkyIslandVolumeRecipe primaryRecipe =
            new ProviderHybridMorphologySkyIslandVolumeRecipe();
    private final EnrichedProviderHybridMorphologySkyIslandVolumeRecipe enrichedRecipe =
            new EnrichedProviderHybridMorphologySkyIslandVolumeRecipe();
    private final SkyIslandMorphologyProviderRegistry registry = registry(false);

    @ParameterizedTest(name = "enriched crescent endpoint seed={0}")
    @MethodSource("seeds")
    @Execution(ExecutionMode.CONCURRENT)
    void standaloneCustomProviderEndpointCanBeCanonicallyEnrichedWithoutChangingItsFootprint(long seed) {
        SkyIslandVolumeDescriptor descriptor = HybridMorphologyReferenceCorpus.descriptor(seed);
        MorphologyProviderBlend blend = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID,
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0);
        CompiledSkyIslandVolume primary = crescent.compilePrimary(descriptor).volume();
        CompiledSkyIslandVolume enriched = enrichedRecipe.compile(
                descriptor,
                ProviderHybridMorphologyEnrichment.full(blend),
                registry);

        assertVolumeAcceptance(enriched, "enriched reference:crescent seed=" + seed);
        assertExactPrimaryFootprint(primary, enriched, "enriched reference:crescent seed=" + seed);
        assertEnrichmentIsNontrivial(primary, enriched, "enriched reference:crescent seed=" + seed);
    }

    @ParameterizedTest(name = "enriched crescent x {0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyCustomToBuiltInMidpointPreservesPrimaryTopologyFootprintAndNontrivialRelief(Member member) {
        SkyIslandVolumeDescriptor descriptor = HybridMorphologyReferenceCorpus.descriptor(member.seed());
        MorphologyProviderBlend blend = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID,
                SkyIslandMorphologyProviders.builtInId(member.family()),
                0.5);
        CompiledSkyIslandVolume primary = primaryRecipe.compile(descriptor, blend, registry);
        CompiledSkyIslandVolume enriched = enrichedRecipe.compile(
                descriptor,
                ProviderHybridMorphologyEnrichment.full(blend),
                registry);

        String label = "reference:crescent+" + member.family().identifier() + " seed=" + member.seed();
        assertVolumeAcceptance(enriched, label);
        assertExactPrimaryFootprint(primary, enriched, label);
        assertEnrichmentIsNontrivial(primary, enriched, label);
    }

    @ParameterizedTest(name = "canonical crescent carrier seed={0}")
    @MethodSource("seeds")
    void canonicalCarrierIsNumericallyEquivalentToExternalPrimaryWhenSecondaryIsNeutral(long seed) {
        SkyIslandVolumeDescriptor descriptor = HybridMorphologyReferenceCorpus.descriptor(seed);
        SkyIslandMorphologyProviderRegistry carrierRegistry = registry(true);
        MorphologyProviderBlend blend = new MorphologyProviderBlend(
                PRIMARY_ONLY_CRESCENT_ID,
                SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF),
                0.0);
        CompiledSkyIslandVolume exact = crescent.compilePrimary(descriptor).volume();
        // Secondary amplitude is deliberately nonzero so the enriched recipe must canonicalize the
        // endpoint. This wrapper has no secondary vocabulary, so the composed factor remains 1.
        CompiledSkyIslandVolume canonicalized = enrichedRecipe.compile(
                descriptor,
                new ProviderHybridMorphologyEnrichment(blend, 0.0, 1.0),
                carrierRegistry);

        assertSurfaceEquivalence(exact, canonicalized, "canonical crescent seed=" + seed);
    }

    private static SkyIslandMorphologyProviderRegistry registry(boolean includePrimaryOnlyCrescent) {
        SkyIslandMorphologyProviderRegistry.Builder builder = SkyIslandMorphologyProviderRegistry.builder();
        builder.registerAll(SkyIslandMorphologyProviders.builtInRegistry().providers());
        builder.register(new ReferenceCrescentMorphologyProvider());
        if (includePrimaryOnlyCrescent) {
            builder.register(new PrimaryOnlyCrescentProvider());
        }
        return builder.build();
    }

    private static void assertVolumeAcceptance(CompiledSkyIslandVolume compiled, String label) {
        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                compiled, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();
        assertTrue(metrics.solidSampleCount() > 0, label);
        assertEquals(1, metrics.connectedSolidComponents(), label);
        assertEquals(0, metrics.faceContacts().total(), label);
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                label + " clearance=" + metrics.airClearance().minimum());
    }

    private static void assertExactPrimaryFootprint(
            CompiledSkyIslandVolume primary, CompiledSkyIslandVolume enriched, String label) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 enrichedUpper = evaluator.field2(enriched.upperSurfaceGraph());
        ScalarField2 enrichedUnder = evaluator.field2(enriched.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                double primaryThickness = primaryUpper.sample(point) - primaryUnder.sample(point);
                double enrichedThickness = enrichedUpper.sample(point) - enrichedUnder.sample(point);
                assertEquals(
                        sign(primaryThickness),
                        sign(enrichedThickness),
                        () -> label + " changed provider-primary footprint at " + point);
            }
        }
    }

    private static void assertEnrichmentIsNontrivial(
            CompiledSkyIslandVolume primary, CompiledSkyIslandVolume enriched, String label) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 enrichedUpper = evaluator.field2(enriched.upperSurfaceGraph());
        ScalarField2 enrichedUnder = evaluator.field2(enriched.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        double upperDelta = 0.0;
        double undersideDelta = 0.0;
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                upperDelta = Math.max(
                        upperDelta,
                        Math.abs(enrichedUpper.sample(point) - primaryUpper.sample(point)));
                undersideDelta = Math.max(
                        undersideDelta,
                        Math.abs(enrichedUnder.sample(point) - primaryUnder.sample(point)));
            }
        }
        assertTrue(
                upperDelta >= MINIMUM_ENRICHMENT_SURFACE_DELTA,
                label + " upper enrichment collapsed; maxDelta=" + upperDelta);
        assertTrue(
                undersideDelta >= MINIMUM_ENRICHMENT_SURFACE_DELTA,
                label + " underside detail collapsed; maxDelta=" + undersideDelta);
    }

    private static void assertSurfaceEquivalence(
            CompiledSkyIslandVolume expected, CompiledSkyIslandVolume actual, String label) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 expectedUpper = evaluator.field2(expected.upperSurfaceGraph());
        ScalarField2 expectedUnder = evaluator.field2(expected.undersideSurfaceGraph());
        ScalarField2 actualUpper = evaluator.field2(actual.upperSurfaceGraph());
        ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                assertEquals(expectedUpper.sample(point), actualUpper.sample(point), TOLERANCE, label + " upper " + point);
                assertEquals(expectedUnder.sample(point), actualUnder.sample(point), TOLERANCE, label + " underside " + point);
            }
        }
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static Stream<Long> seeds() {
        return Stream.of(SEEDS[0], SEEDS[1], SEEDS[2]);
    }

    private static Stream<Member> members() {
        return Stream.of(MorphologyFamily.values())
                .flatMap(family -> Stream.of(SEEDS[0], SEEDS[1], SEEDS[2])
                        .map(seed -> new Member(family, seed)));
    }

    private record Member(MorphologyFamily family, long seed) {}

    private static final class PrimaryOnlyCrescentProvider implements SkyIslandMorphologyProvider {
        private final ReferenceCrescentMorphologyProvider delegate =
                new ReferenceCrescentMorphologyProvider();

        @Override
        public MorphologyProviderId id() {
            return PRIMARY_ONLY_CRESCENT_ID;
        }

        @Override
        public PrimaryMorphologyContribution compilePrimary(SkyIslandVolumeDescriptor descriptor) {
            return delegate.compilePrimary(descriptor);
        }

        @Override
        public Optional<io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologyContribution>
                compileSecondaryMorphology(SkyIslandVolumeDescriptor descriptor, double amplitude) {
            return SkyIslandMorphologyProvider.super.compileSecondaryMorphology(descriptor, amplitude);
        }
    }
}
