package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.ComposedMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamilySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.ComposedMorphologyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.MorphologyFamilyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Full-resolution acceptance of the accepted detail and structured-relief layers across all families. */
final class ComposedMorphologySuspendedVolumeAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyComposedFamilySpecimenPreservesTopologyAndPrimaryFootprint(
            MorphologyFamilyReferenceCorpus.Member member) {
        ComposedMorphologySkyIslandVolumeRecipe composedRecipe =
                new ComposedMorphologySkyIslandVolumeRecipe();
        MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
                new MorphologyFamilySkyIslandVolumeRecipe();
        CompiledSkyIslandVolume composed = composedRecipe.compile(
                ComposedMorphologyReferenceCorpus.descriptor(member), member.family());
        CompiledSkyIslandVolume primary = primaryRecipe.compile(
                MorphologyFamilyReferenceCorpus.descriptor(member), member.family());

        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                composed, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());

        assertExactFootprintEnvelope(primary, composed, member);
    }

    private static void assertExactFootprintEnvelope(
            CompiledSkyIslandVolume primary,
            CompiledSkyIslandVolume composed,
            MorphologyFamilyReferenceCorpus.Member member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 composedUpper = evaluator.field2(composed.upperSurfaceGraph());
        ScalarField2 composedUnder = evaluator.field2(composed.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                double primaryThickness = primaryUpper.sample(point) - primaryUnder.sample(point);
                double composedThickness = composedUpper.sample(point) - composedUnder.sample(point);
                assertEquals(
                        sign(primaryThickness),
                        sign(composedThickness),
                        () -> member.id() + " changed primary footprint at " + point);
            }
        }
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static Stream<MorphologyFamilyReferenceCorpus.Member> members() {
        return ComposedMorphologyReferenceCorpus.members().stream();
    }
}
