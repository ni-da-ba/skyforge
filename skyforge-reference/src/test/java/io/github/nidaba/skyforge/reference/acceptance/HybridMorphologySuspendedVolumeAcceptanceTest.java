package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.HybridMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamilySkyIslandVolumeRecipe;
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

/** Full-resolution midpoint acceptance for every unordered pair of built-in primary families. */
final class HybridMorphologySuspendedVolumeAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;
    private static final double MINIMUM_PARENT_SURFACE_DELTA = 0.25;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyPairwiseMidpointRemainsOneClosedDistinctSuspendedMass(
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
        HybridMorphologySkyIslandVolumeRecipe hybridRecipe =
                new HybridMorphologySkyIslandVolumeRecipe();
        MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
                new MorphologyFamilySkyIslandVolumeRecipe();

        var descriptor = HybridMorphologyReferenceCorpus.descriptor(member.seed());
        CompiledSkyIslandVolume hybrid = hybridRecipe.compile(descriptor, member.blend());
        CompiledSkyIslandVolume first = primaryRecipe.compile(descriptor, member.pair().first());
        CompiledSkyIslandVolume second = primaryRecipe.compile(descriptor, member.pair().second());

        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                hybrid, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());

        assertSharedHybridFootprint(hybrid, member);
        assertDistinctFromBothParents(first, second, hybrid, member);
    }

    private static void assertSharedHybridFootprint(
            CompiledSkyIslandVolume hybrid,
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 upper = evaluator.field2(hybrid.upperSurfaceGraph());
        ScalarField2 underside = evaluator.field2(hybrid.undersideSurfaceGraph());
        double suspension = hybrid.descriptor().suspensionElevation();
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                double upperOffset = upper.sample(point) - suspension;
                double lowerOffset = underside.sample(point) - suspension;
                assertEquals(
                        sign(upperOffset),
                        -sign(lowerOffset),
                        () -> member.id() + " upper/underside footprint mismatch at " + point);
            }
        }
    }

    private static void assertDistinctFromBothParents(
            CompiledSkyIslandVolume first,
            CompiledSkyIslandVolume second,
            CompiledSkyIslandVolume hybrid,
            HybridMorphologyReferenceCorpus.AcceptanceMember member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 firstUpper = evaluator.field2(first.upperSurfaceGraph());
        ScalarField2 secondUpper = evaluator.field2(second.upperSurfaceGraph());
        ScalarField2 hybridUpper = evaluator.field2(hybrid.upperSurfaceGraph());
        ScalarField2 firstUnder = evaluator.field2(first.undersideSurfaceGraph());
        ScalarField2 secondUnder = evaluator.field2(second.undersideSurfaceGraph());
        ScalarField2 hybridUnder = evaluator.field2(hybrid.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        double firstDelta = 0.0;
        double secondDelta = 0.0;
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                firstDelta = Math.max(firstDelta, Math.abs(
                        hybridUpper.sample(point) - firstUpper.sample(point)));
                firstDelta = Math.max(firstDelta, Math.abs(
                        hybridUnder.sample(point) - firstUnder.sample(point)));
                secondDelta = Math.max(secondDelta, Math.abs(
                        hybridUpper.sample(point) - secondUpper.sample(point)));
                secondDelta = Math.max(secondDelta, Math.abs(
                        hybridUnder.sample(point) - secondUnder.sample(point)));
            }
        }

        assertTrue(
                firstDelta >= MINIMUM_PARENT_SURFACE_DELTA,
                member.id() + " collapsed toward first parent; maxDelta=" + firstDelta);
        assertTrue(
                secondDelta >= MINIMUM_PARENT_SURFACE_DELTA,
                member.id() + " collapsed toward second parent; maxDelta=" + secondDelta);
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
