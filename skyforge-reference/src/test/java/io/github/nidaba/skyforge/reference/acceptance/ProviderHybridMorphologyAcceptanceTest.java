package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyProviderBlend;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Full-resolution proof that a provider outside the built-in enum can stand alone and hybridize. */
final class ProviderHybridMorphologyAcceptanceTest {
    private static final long[] SEEDS = {
        Long.MIN_VALUE,
        0L,
        0x534b59464f524745L
    };
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;

    private final ProviderHybridMorphologySkyIslandVolumeRecipe hybridRecipe =
            new ProviderHybridMorphologySkyIslandVolumeRecipe();
    private final ReferenceCrescentMorphologyProvider crescent =
            new ReferenceCrescentMorphologyProvider();
    private final SkyIslandMorphologyProviderRegistry registry = registry();

    @ParameterizedTest(name = "crescent seed={0}")
    @MethodSource("seeds")
    @Execution(ExecutionMode.CONCURRENT)
    void genuinelyCustomCrescentRemainsOneClosedSuspendedMass(long seed) {
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(seed);
        CompiledSkyIslandVolume compiled = crescent.compilePrimary(descriptor).volume();
        assertVolumeAcceptance(compiled, "reference:crescent seed=" + seed);
        assertCurvatureIsVisible(compiled);
    }

    @ParameterizedTest(name = "crescent x {0}")
    @MethodSource("families")
    @Execution(ExecutionMode.CONCURRENT)
    void customProviderHybridizesWithEveryBuiltInAtAllCanonicalSeeds(MorphologyFamily family) {
        for (long seed : SEEDS) {
            var descriptor = HybridMorphologyReferenceCorpus.descriptor(seed);
            var builtInId = SkyIslandMorphologyProviders.builtInId(family);
            MorphologyProviderBlend blend = new MorphologyProviderBlend(
                    ReferenceCrescentMorphologyProvider.ID,
                    builtInId,
                    0.5);
            CompiledSkyIslandVolume hybrid = hybridRecipe.compile(descriptor, blend, registry);
            assertVolumeAcceptance(hybrid, blend.pairIdentifier() + " seed=" + seed);
            assertSharedFootprint(hybrid, blend.pairIdentifier());

            CompiledSkyIslandVolume crescentParent = crescent.compilePrimary(descriptor).volume();
            CompiledSkyIslandVolume builtInParent = registry.require(builtInId)
                    .compilePrimary(descriptor)
                    .volume();
            assertDistinctFromParentSurfaces(hybrid, crescentParent, builtInParent, blend.pairIdentifier());
        }
    }

    @Test
    void providerBlendCanonicalizationAndGraphIdentityRemainExact() {
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(0x534b59464f524745L);
        var spine = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.SPINE);
        MorphologyProviderBlend forward = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID, spine, 0.30);
        MorphologyProviderBlend reverse = new MorphologyProviderBlend(
                spine, ReferenceCrescentMorphologyProvider.ID, 0.70);
        assertEquals(forward, reverse);

        CanonicalGraphJson json = new CanonicalGraphJson();
        CompiledSkyIslandVolume first = hybridRecipe.compile(descriptor, forward, registry);
        CompiledSkyIslandVolume second = hybridRecipe.compile(descriptor, reverse, registry);
        assertEquals(
                json.writeString(first.upperSurfaceGraph()),
                json.writeString(second.upperSurfaceGraph()));
        assertEquals(
                json.writeString(first.undersideSurfaceGraph()),
                json.writeString(second.undersideSurfaceGraph()));
        assertEquals(
                json.writeString(first.densityGraph()),
                json.writeString(second.densityGraph()));
    }

    @Test
    void providerHybridEndpointsPreserveExactProviderGraphBytes() {
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(0L);
        var massif = SkyIslandMorphologyProviders.builtInId(MorphologyFamily.MASSIF);
        CanonicalGraphJson json = new CanonicalGraphJson();

        MorphologyProviderBlend crescentEndpoint = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID, massif, 0.0);
        MorphologyProviderBlend massifEndpoint = new MorphologyProviderBlend(
                ReferenceCrescentMorphologyProvider.ID, massif, 1.0);
        CompiledSkyIslandVolume expectedCrescent = crescent.compilePrimary(descriptor).volume();
        CompiledSkyIslandVolume expectedMassif = registry.require(massif).compilePrimary(descriptor).volume();
        CompiledSkyIslandVolume actualCrescent = hybridRecipe.compile(descriptor, crescentEndpoint, registry);
        CompiledSkyIslandVolume actualMassif = hybridRecipe.compile(descriptor, massifEndpoint, registry);

        assertEquals(json.writeString(expectedCrescent.upperSurfaceGraph()), json.writeString(actualCrescent.upperSurfaceGraph()));
        assertEquals(json.writeString(expectedCrescent.undersideSurfaceGraph()), json.writeString(actualCrescent.undersideSurfaceGraph()));
        assertEquals(json.writeString(expectedCrescent.densityGraph()), json.writeString(actualCrescent.densityGraph()));
        assertEquals(json.writeString(expectedMassif.upperSurfaceGraph()), json.writeString(actualMassif.upperSurfaceGraph()));
        assertEquals(json.writeString(expectedMassif.undersideSurfaceGraph()), json.writeString(actualMassif.undersideSurfaceGraph()));
        assertEquals(json.writeString(expectedMassif.densityGraph()), json.writeString(actualMassif.densityGraph()));
    }

    private static SkyIslandMorphologyProviderRegistry registry() {
        SkyIslandMorphologyProviderRegistry.Builder builder = SkyIslandMorphologyProviderRegistry.builder();
        for (SkyIslandMorphologyProvider provider : SkyIslandMorphologyProviders.builtInRegistry().providers()) {
            builder.register(provider);
        }
        return builder.register(new ReferenceCrescentMorphologyProvider()).build();
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

    private static void assertSharedFootprint(CompiledSkyIslandVolume hybrid, String label) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 upper = evaluator.field2(hybrid.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(hybrid.undersideSurfaceGraph());
        double suspension = hybrid.descriptor().suspensionElevation();
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                int upperSign = sign(upper.sample(point) - suspension);
                int lowerSign = sign(underside.sample(point) - suspension);
                assertEquals(upperSign, -lowerSign, label + " footprint mismatch at " + point);
            }
        }
    }

    private static void assertDistinctFromParentSurfaces(
            CompiledSkyIslandVolume hybrid,
            CompiledSkyIslandVolume first,
            CompiledSkyIslandVolume second,
            String label) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 hybridUpper = evaluator.field2(hybrid.upperSurfaceGraph());
        ScalarField2 firstUpper = evaluator.field2(first.upperSurfaceGraph());
        ScalarField2 secondUpper = evaluator.field2(second.upperSurfaceGraph());
        double firstDelta = 0.0;
        double secondDelta = 0.0;
        for (int z = -320; z <= 320; z += 32) {
            for (int x = -320; x <= 320; x += 32) {
                Coordinate2 point = new Coordinate2(x, z);
                firstDelta = Math.max(firstDelta, Math.abs(hybridUpper.sample(point) - firstUpper.sample(point)));
                secondDelta = Math.max(secondDelta, Math.abs(hybridUpper.sample(point) - secondUpper.sample(point)));
            }
        }
        assertTrue(firstDelta >= 0.25, label + " collapsed to crescent parent");
        assertTrue(secondDelta >= 0.25, label + " collapsed to built-in parent");
    }

    private static void assertCurvatureIsVisible(CompiledSkyIslandVolume crescent) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 upper = evaluator.field2(crescent.upperSurfaceGraph());
        double suspension = crescent.descriptor().suspensionElevation();
        // At equal positive/negative longitudinal offsets, the bent centerline is displaced to the
        // same transverse side. This distinguishes the provider from a straight ellipse/spine.
        double positiveSide = upper.sample(new Coordinate2(192.0, 96.0)) - suspension;
        double mirroredSide = upper.sample(new Coordinate2(192.0, -96.0)) - suspension;
        assertNotEquals(sign(positiveSide), sign(mirroredSide), "crescent curvature should move the occupied ribbon off a straight axis");
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

    private static Stream<MorphologyFamily> families() {
        return Stream.of(MorphologyFamily.values());
    }
}
