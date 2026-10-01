package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.MorphologyFamilySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.MorphologyFamilyReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import java.util.stream.Stream;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Full-resolution acceptance for descriptor schema-2 semantic morphology controls. */
final class SemanticMorphologyDescriptorAcceptanceTest {
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 48.0;
    private static final double TOLERANCE = 1.0e-10;

    @ParameterizedTest(name = "{0}")
    @MethodSource("members")
    @Execution(ExecutionMode.CONCURRENT)
    void everySemanticControlSpecimenPreservesAcceptedTopologyAndFootprint(
            MorphologyFamilyReferenceCorpus.Member member) {
        SkyIslandVolumeDescriptor semanticDescriptor = semanticDescriptor(member);
        CompiledSkyIslandVolume semantic = new SemanticSkyIslandVolumeRecipe().compile(semanticDescriptor);
        CompiledSkyIslandVolume primary = new MorphologyFamilySkyIslandVolumeRecipe().compile(
                MorphologyFamilyReferenceCorpus.descriptor(member), member.family());

        SuspendedVolumeEvidence evidence = new SuspendedVolumeEvidenceGenerator().generate(
                semantic, SuspendedVolumeReferenceDomain.grid(), SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertEquals(member.family().semanticFamily(), semantic.descriptor().morphologyFamily());
        assertTrue(metrics.solidSampleCount() > 0, member.id());
        assertEquals(1, metrics.connectedSolidComponents(), member.id());
        assertEquals(0, metrics.faceContacts().total(), member.id());
        assertTrue(
                metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE,
                member.id() + " clearance=" + metrics.airClearance().minimum());
        assertExactFootprintEnvelope(primary, semantic, member);
    }

    private static void assertExactFootprintEnvelope(
            CompiledSkyIslandVolume primary,
            CompiledSkyIslandVolume semantic,
            MorphologyFamilyReferenceCorpus.Member member) {
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        ScalarField2 primaryUpper = evaluator.field2(primary.upperSurfaceGraph());
        ScalarField2 primaryUnder = evaluator.field2(primary.undersideSurfaceGraph());
        ScalarField2 semanticUpper = evaluator.field2(semantic.upperSurfaceGraph());
        ScalarField2 semanticUnder = evaluator.field2(semantic.undersideSurfaceGraph());
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        for (int zIndex = 0; zIndex < grid.zSamples(); zIndex++) {
            for (int xIndex = 0; xIndex < grid.xSamples(); xIndex++) {
                Coordinate2 point = new Coordinate2(grid.xAt(xIndex), grid.zAt(zIndex));
                double primaryThickness = primaryUpper.sample(point) - primaryUnder.sample(point);
                double semanticThickness = semanticUpper.sample(point) - semanticUnder.sample(point);
                assertEquals(
                        sign(primaryThickness),
                        sign(semanticThickness),
                        () -> member.id() + " changed primary footprint at " + point);
            }
        }
    }

    private static SkyIslandVolumeDescriptor semanticDescriptor(
            MorphologyFamilyReferenceCorpus.Member member) {
        SkyIslandVolumeDescriptor base = SuspendedVolumeReferenceDomain.descriptor();
        double detail;
        double secondary;
        if (member.seed() == Long.MIN_VALUE) {
            detail = 0.0;
            secondary = 1.0;
        } else if (member.seed() == 0L) {
            detail = 1.0;
            secondary = 0.0;
        } else {
            detail = 0.35;
            secondary = 0.80;
        }
        return SkyIslandVolumeDescriptor.schema2(
                member.seed(),
                base.centerX(),
                base.centerZ(),
                base.suspensionElevation(),
                base.nominalRadius(),
                base.upperElevation(),
                base.undersideDepth(),
                base.coastalFalloff(),
                base.ridgeAzimuth(),
                base.ridgeStrength(),
                base.undersideTaper(),
                base.undersideAsymmetry(),
                member.family().semanticFamily(),
                detail,
                base.signalScale(),
                secondary);
    }

    private static int sign(double value) {
        if (Math.abs(value) <= TOLERANCE) {
            return 0;
        }
        return value > 0.0 ? 1 : -1;
    }

    private static Stream<MorphologyFamilyReferenceCorpus.Member> members() {
        return MorphologyFamilyReferenceCorpus.members().stream();
    }
}
