package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.EnrichedHybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.HybridMorphologyEnrichment;
import io.github.nidaba.skyforge.recipes.skyisland.HybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.HybridMorphologyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Full-resolution acceptance for fully enriched pairwise midpoint hybrids. */
final class EnrichedHybridMorphologySuspendedVolumeAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;
    private static final double MINIMUM_ENRICHMENT_SURFACE_DELTA = 0.10;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyFullyEnrichedMidpointPreservesHybridTopologyFootprintAndNontrivialRelief(
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
        var descriptor = HybridMorphologyReferenceCorpus.descriptor(member.seed());
        HybridMorphologySkyIslandVolumeRecipe primaryRecipe =
                new HybridMorphologySkyIslandVolumeRecipe();
        EnrichedHybridMorphologySkyIslandVolumeRecipe enrichedRecipe =
                new EnrichedHybridMorphologySkyIslandVolumeRecipe();

        CompiledSkyIslandVolume primary = primaryRecipe.compile(descriptor, member.blend());
        CompiledSkyIslandVolume enriched = enrichedRecipe.compile(
                descriptor, HybridMorphologyEnrichment.full(member.blend()));

        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                enriched, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());

        assertExactPrimaryFootprint(primary, enriched, member);
        assertEnrichmentIsNontrivial(primary, enriched, member);
    }

    private static void assertExactPrimaryFootprint(
            CompiledSkyIslandVolume primary,
            CompiledSkyIslandVolume enriched,
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
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
                        () -> member.id() + " changed SF-IMP-0022 primary footprint at " + point);
            }
        }
    }

    private static void assertEnrichmentIsNontrivial(
            CompiledSkyIslandVolume primary,
            CompiledSkyIslandVolume enriched,
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
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
                member.id() + " upper enrichment collapsed; maxDelta=" + upperDelta);
        assertTrue(
                undersideDelta >= MINIMUM_ENRICHMENT_SURFACE_DELTA,
                member.id() + " underside detail collapsed; maxDelta=" + undersideDelta);
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static Stream<HybridMorphologyReferenceCorpus.AcceptanceMember> members() {
        return HybridMorphologyReferenceCorpus.acceptanceMembers().stream();
    }
}
