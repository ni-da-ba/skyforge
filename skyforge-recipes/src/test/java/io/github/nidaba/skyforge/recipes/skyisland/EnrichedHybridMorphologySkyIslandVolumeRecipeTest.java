package io.github.nidaba.skyforge.recipes.skyisland;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import org.junit.jupiter.api.Test;

final class EnrichedHybridMorphologySkyIslandVolumeRecipeTest {
    private static final double SUSPENSION = 256.0;
    private static final double TOLERANCE = 1.0e-10;

    private final EnrichedHybridMorphologySkyIslandVolumeRecipe recipe =
            new EnrichedHybridMorphologySkyIslandVolumeRecipe();
    private final HybridMorphologySkyIslandVolumeRecipe primaryRecipe =
            new HybridMorphologySkyIslandVolumeRecipe();
    private final SemanticSkyIslandVolumeRecipe semanticRecipe =
            new SemanticSkyIslandVolumeRecipe();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();
    private final CanonicalGraphJson graphJson = new CanonicalGraphJson();

    @Test
    void enrichmentSpecificationRejectsOutOfRangeAmplitudes() {
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.5);
        assertThrows(
                IllegalArgumentException.class,
                () -> new HybridMorphologyEnrichment(blend, -0.01, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HybridMorphologyEnrichment(blend, 0.5, 1.01));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HybridMorphologyEnrichment(blend, Double.NaN, 0.5));
    }

    @Test
    void proofRequiresZeroSignalSchemaOneDescriptor() {
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.5);
        HybridMorphologyEnrichment enrichment = HybridMorphologyEnrichment.full(blend);
        assertThrows(
                IllegalArgumentException.class,
                () -> recipe.compile(descriptor(0L, 0.25), enrichment));
        assertThrows(
                IllegalArgumentException.class,
                () -> recipe.compile(schema2Descriptor(0L, MorphologyFamily.MASSIF, 0.0, 0.0), enrichment));
    }

    @Test
    void endpointsAreByteIdenticalToAcceptedSemanticFamilyRecipe() {
        SkyIslandVolumeDescriptor base = descriptor(0L, 0.0);
        MorphologyBlend firstEndpoint = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.0);
        MorphologyBlend secondEndpoint = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 1.0);
        assertEndpointIdentity(base, firstEndpoint, MorphologyFamily.MASSIF, 0.35, 0.80);
        assertEndpointIdentity(base, secondEndpoint, MorphologyFamily.SPINE, 0.35, 0.80);
    }

    @Test
    void zeroEnrichmentIsByteIdenticalToAcceptedPrimaryHybrid() {
        SkyIslandVolumeDescriptor base = descriptor(0L, 0.0);
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.TABLELAND, MorphologyFamily.BASIN, 0.5);
        CompiledSkyIslandVolume primary = primaryRecipe.compile(base, blend);
        CompiledSkyIslandVolume actual = recipe.compile(
                base, new HybridMorphologyEnrichment(blend, 0.0, 0.0));
        assertGraphIdentity(primary, actual);
    }

    @Test
    void canonicalPairSymmetryAndDeterminismRemainExact() {
        SkyIslandVolumeDescriptor base = descriptor(0x534b59464f524745L, 0.0);
        HybridMorphologyEnrichment forward = new HybridMorphologyEnrichment(
                new MorphologyBlend(MorphologyFamily.MASSIF, MorphologyFamily.SPINE, 0.30),
                0.45,
                0.75);
        HybridMorphologyEnrichment reversed = new HybridMorphologyEnrichment(
                new MorphologyBlend(MorphologyFamily.SPINE, MorphologyFamily.MASSIF, 0.70),
                0.45,
                0.75);
        CompiledSkyIslandVolume first = recipe.compile(base, forward);
        CompiledSkyIslandVolume second = recipe.compile(base, reversed);
        CompiledSkyIslandVolume repeat = recipe.compile(base, forward);
        assertGraphIdentity(first, second);
        assertGraphIdentity(first, repeat);
    }

    @Test
    void secondaryOnlyPreservesSignalFreeUndersideAndAddsUpperGeography() {
        SkyIslandVolumeDescriptor base = descriptor(0L, 0.0);
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.BASIN, 0.5);
        CompiledSkyIslandVolume primary = primaryRecipe.compile(base, blend);
        CompiledSkyIslandVolume actual = recipe.compile(
                base, new HybridMorphologyEnrichment(blend, 0.0, 1.0));
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 actualUpper = evaluator.field2(actual.upperSurfaceGraph());
        double maximumUpperDifference = 0.0;
        for (int z = -288; z <= 288; z += 32) {
            for (int x = -288; x <= 288; x += 32) {
                Coordinate2 point = new Coordinate2(x, z);
                assertEquals(primaryUnder.sample(point), actualUnder.sample(point), TOLERANCE);
                maximumUpperDifference = Math.max(
                        maximumUpperDifference,
                        Math.abs(primaryUpper.sample(point) - actualUpper.sample(point)));
            }
        }
        assertTrue(maximumUpperDifference >= 1.0);
        assertInternalFieldConstant(actual.upperSurfaceGraph(), "signal.upper.factor", 1.0);
        assertInternalFieldConstant(actual.undersideSurfaceGraph(), "signal.underside.factor", 1.0);
    }

    @Test
    void detailOnlyNeutralizesBlendedSecondaryFactor() {
        SkyIslandVolumeDescriptor base = descriptor(1L, 0.0);
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.TABLELAND, MorphologyFamily.LOBED, 0.5);
        CompiledSkyIslandVolume primary = primaryRecipe.compile(base, blend);
        CompiledSkyIslandVolume actual = recipe.compile(
                base, new HybridMorphologyEnrichment(blend, 1.0, 0.0));
        assertInternalFieldConstant(actual.upperSurfaceGraph(), "hybrid-secondary.upper-factor", 1.0);

        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
        double maximumUndersideDifference = 0.0;
        for (int z = -256; z <= 256; z += 32) {
            for (int x = -256; x <= 256; x += 32) {
                Coordinate2 point = new Coordinate2(x, z);
                maximumUndersideDifference = Math.max(
                        maximumUndersideDifference,
                        Math.abs(primaryUnder.sample(point) - actualUnder.sample(point)));
            }
        }
        assertTrue(maximumUndersideDifference > 0.1);
    }

    @Test
    void fullBlendedSecondaryFactorStaysInsideParentPositiveEnvelope() {
        SkyIslandVolumeDescriptor base = descriptor(0L, 0.0);
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.MASSIF, MorphologyFamily.BASIN, 0.5);
        CompiledSkyIslandVolume actual = recipe.compile(
                base, HybridMorphologyEnrichment.full(blend));
        ScalarField2 factor = evaluator.field2(graphView(
                actual.upperSurfaceGraph(), "hybrid-secondary.upper-factor"));
        double minimum = Math.min(
                FamilyAwareSecondaryMorphologyComposition.minimumUpperFactor(blend.first()),
                FamilyAwareSecondaryMorphologyComposition.minimumUpperFactor(blend.second()));
        double maximum = Math.max(
                FamilyAwareSecondaryMorphologyComposition.maximumUpperFactor(blend.first()),
                FamilyAwareSecondaryMorphologyComposition.maximumUpperFactor(blend.second()));
        for (int z = -320; z <= 320; z += 32) {
            for (int x = -320; x <= 320; x += 32) {
                double value = factor.sample(new Coordinate2(x, z));
                assertTrue(value > 0.0, "secondary factor must remain strictly positive");
                assertTrue(value >= minimum - TOLERANCE, "secondary factor below parent envelope");
                assertTrue(value <= maximum + TOLERANCE, "secondary factor above parent envelope");
            }
        }
    }

    @Test
    void fullEnrichmentPreservesExactPrimaryFootprintSign() {
        SkyIslandVolumeDescriptor base = descriptor(0L, 0.0);
        for (MorphologyFamily first : MorphologyFamily.values()) {
            for (MorphologyFamily second : MorphologyFamily.values()) {
                if (first.identifier().compareTo(second.identifier()) >= 0) {
                    continue;
                }
                MorphologyBlend blend = new MorphologyBlend(first, second, 0.5);
                CompiledSkyIslandVolume primary = primaryRecipe.compile(base, blend);
                CompiledSkyIslandVolume actual = recipe.compile(
                        base, HybridMorphologyEnrichment.full(blend));
                ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
                ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
                ScalarField2 actualUpper = evaluator.field2(actual.upperSurfaceGraph());
                ScalarField2 actualUnder = evaluator.field2(actual.undersideSurfaceGraph());
                for (int z = -384; z <= 384; z += 24) {
                    for (int x = -384; x <= 384; x += 24) {
                        Coordinate2 point = new Coordinate2(x, z);
                        int expected = sign(primaryUpper.sample(point) - primaryUnder.sample(point));
                        int observed = sign(actualUpper.sample(point) - actualUnder.sample(point));
                        assertEquals(expected, observed, blend.pairIdentifier() + " at " + point);
                    }
                }
            }
        }
    }

    @Test
    void densityIsExactIntersectionOfFinalHybridSurfaces() {
        SkyIslandVolumeDescriptor base = descriptor(0x534b59464f524745L, 0.0);
        MorphologyBlend blend = new MorphologyBlend(
                MorphologyFamily.SPINE, MorphologyFamily.LOBED, 0.5);
        CompiledSkyIslandVolume actual = recipe.compile(
                base, new HybridMorphologyEnrichment(blend, 0.60, 0.85));
        ScalarField2 upper = evaluator.field2(actual.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(actual.undersideSurfaceGraph());
        ScalarField3 density = evaluator.field3(actual.densityGraph());
        for (double x : new double[] {-280.0, -128.0, 0.0, 112.0, 280.0}) {
            for (double z : new double[] {-240.0, -64.0, 0.0, 96.0, 240.0}) {
                Coordinate2 horizontal = new Coordinate2(x, z);
                for (double y : new double[] {96.0, 192.0, 256.0, 320.0, 400.0}) {
                    double expected = Math.min(
                            upper.sample(horizontal) - y,
                            y - underside.sample(horizontal));
                    double observed = density.sample(new Coordinate3(x, y, z));
                    assertEquals(
                            Double.doubleToRawLongBits(expected),
                            Double.doubleToRawLongBits(observed));
                }
            }
        }
    }

    private void assertEndpointIdentity(
            SkyIslandVolumeDescriptor base,
            MorphologyBlend blend,
            MorphologyFamily family,
            double detailAmplitude,
            double secondaryAmplitude) {
        CompiledSkyIslandVolume actual = recipe.compile(
                base,
                new HybridMorphologyEnrichment(blend, detailAmplitude, secondaryAmplitude));
        CompiledSkyIslandVolume expected = semanticRecipe.compile(schema2Descriptor(
                base.seed(), family, detailAmplitude, secondaryAmplitude));
        assertGraphIdentity(expected, actual);
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

    private void assertInternalFieldConstant(
            ProceduralGraph graph, String outputIdentifier, double expected) {
        ScalarField2 field = evaluator.field2(graphView(graph, outputIdentifier));
        for (int z = -256; z <= 256; z += 64) {
            for (int x = -256; x <= 256; x += 64) {
                assertEquals(expected, field.sample(new Coordinate2(x, z)), TOLERANCE);
            }
        }
    }

    private static ProceduralGraph graphView(ProceduralGraph graph, String outputIdentifier) {
        return new ProceduralGraph(graph.nodes(), new NodeId(outputIdentifier));
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static SkyIslandVolumeDescriptor descriptor(long seed, double signalAmplitude) {
        return new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION_1,
                seed,
                0.0,
                0.0,
                SUSPENSION,
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

    private static SkyIslandVolumeDescriptor schema2Descriptor(
            long seed,
            MorphologyFamily family,
            double detailAmplitude,
            double secondaryAmplitude) {
        return SkyIslandVolumeDescriptor.schema2(
                seed,
                0.0,
                0.0,
                SUSPENSION,
                256.0,
                96.0,
                128.0,
                64.0,
                Math.PI / 6.0,
                0.65,
                0.60,
                0.25,
                family.semanticFamily(),
                detailAmplitude,
                32.0,
                secondaryAmplitude);
    }
}
