package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.ComposedMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.FamilyAwareMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamily;
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

/** Full-resolution acceptance for family-aware secondary morphology across all primary families. */
final class FamilyAwareMorphologySuspendedVolumeAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;
    private static final double MINIMUM_NONCONTROL_UPPER_DELTA = 1.0;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everyFamilyAwareSpecimenPreservesTopologyFootprintAndAcceptedUnderside(
            MorphologyFamilyReferenceCorpus.Member member) {
        FamilyAwareMorphologySkyIslandVolumeRecipe awareRecipe =
                new FamilyAwareMorphologySkyIslandVolumeRecipe();
        ComposedMorphologySkyIslandVolumeRecipe genericRecipe =
                new ComposedMorphologySkyIslandVolumeRecipe();
        MorphologyFamilySkyIslandVolumeRecipe primaryRecipe =
                new MorphologyFamilySkyIslandVolumeRecipe();

        CompiledSkyIslandVolume aware = awareRecipe.compile(
                ComposedMorphologyReferenceCorpus.descriptor(member), member.family());
        CompiledSkyIslandVolume generic = genericRecipe.compile(
                ComposedMorphologyReferenceCorpus.descriptor(member), member.family());
        CompiledSkyIslandVolume primary = primaryRecipe.compile(
                MorphologyFamilyReferenceCorpus.descriptor(member), member.family());

        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                aware, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());

        assertArrayEquals(
                new CanonicalGraphJson().write(generic.undersideSurfaceGraph()),
                new CanonicalGraphJson().write(aware.undersideSurfaceGraph()),
                member.id() + " changed the accepted SF-IMP-0019 underside");
        assertExactFootprintEnvelope(primary, aware, member);
        assertFamilyAwareUpperSelection(generic, aware, member);
    }

    private static void assertExactFootprintEnvelope(
            CompiledSkyIslandVolume primary,
            CompiledSkyIslandVolume aware,
            MorphologyFamilyReferenceCorpus.Member member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 awareUpper = evaluator.field2(aware.upperSurfaceGraph());
        ScalarField2 awareUnder = evaluator.field2(aware.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                double primaryThickness = primaryUpper.sample(point) - primaryUnder.sample(point);
                double awareThickness = awareUpper.sample(point) - awareUnder.sample(point);
                assertEquals(
                        sign(primaryThickness),
                        sign(awareThickness),
                        () -> member.id() + " changed primary footprint at " + point);
            }
        }
    }

    private static void assertFamilyAwareUpperSelection(
            CompiledSkyIslandVolume generic,
            CompiledSkyIslandVolume aware,
            MorphologyFamilyReferenceCorpus.Member member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 genericUpper = evaluator.field2(generic.upperSurfaceGraph());
        ScalarField2 awareUpper = evaluator.field2(aware.upperSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        double maximumDifference = 0.0;
        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                maximumDifference = Math.max(
                        maximumDifference,
                        Math.abs(genericUpper.sample(point) - awareUpper.sample(point)));
            }
        }
        if (member.family() == MorphologyFamily.MASSIF) {
            assertEquals(0.0, maximumDifference, TOLERANCE, member.id());
        } else {
            assertTrue(
                    maximumDifference >= MINIMUM_NONCONTROL_UPPER_DELTA,
                    member.id() + " family-aware upper delta=" + maximumDifference);
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
