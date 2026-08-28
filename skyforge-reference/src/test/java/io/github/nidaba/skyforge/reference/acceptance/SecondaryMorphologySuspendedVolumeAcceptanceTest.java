package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.FixedSeedReferenceCorpus;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.SeededSuspendedVolumeReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import org.junit.jupiter.api.Test;

/** Fixed-seed acceptance for organized upper-surface ridges, spurs, and valleys. */
final class SecondaryMorphologySuspendedVolumeAcceptanceTest {
    private static final double TOLERANCE = 1.0e-10;
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 80.0;
    private static final double MINIMUM_VISIBLE_RELIEF = 8.0;

    private final SecondaryMorphologySkyIslandVolumeRecipe recipe =
            new SecondaryMorphologySkyIslandVolumeRecipe();
    private final SeededSkyIslandVolumeRecipe seededRecipe = new SeededSkyIslandVolumeRecipe();
    private final SuspendedVolumeEvidenceGenerator evidenceGenerator =
            new SuspendedVolumeEvidenceGenerator();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();

    @Test
    void fixedSeedSuitePreservesSuspendedTopologyWhileAddingOrganizedRelief() {
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();

        for (FixedSeedReferenceCorpus.Member member : SeededSuspendedVolumeReferenceCorpus.members()) {
            SkyIslandVolumeDescriptor descriptor =
                    SeededSuspendedVolumeReferenceCorpus.descriptor(member);
            CompiledSkyIslandVolume seeded = seededRecipe.compile(descriptor);
            CompiledSkyIslandVolume structured = recipe.compile(descriptor);
            SuspendedVolumeEvidence evidence = evidenceGenerator.generate(
                    structured, grid, SamplingOrder.FORWARD);
            VolumeMetrics metrics = evidence.metrics();

            assertAll(
                    member.id(),
                    () -> assertTrue(metrics.solidSampleCount() > 0),
                    () -> assertEquals(0, metrics.faceContacts().total()),
                    () -> assertEquals(1, metrics.connectedSolidComponents()),
                    () -> assertTrue(metrics.airClearance().minimum() >= MINIMUM_ACCEPTED_CLEARANCE),
                    () -> assertEquals(-296.0, metrics.bounds().minimumX(), TOLERANCE),
                    () -> assertEquals(296.0, metrics.bounds().maximumX(), TOLERANCE),
                    () -> assertEquals(-236.0, metrics.bounds().minimumZ(), TOLERANCE),
                    () -> assertEquals(236.0, metrics.bounds().maximumZ(), TOLERANCE));

            assertStructuredIdentityEnvelope(descriptor, grid, seeded, structured);
        }
    }

    private void assertStructuredIdentityEnvelope(
            SkyIslandVolumeDescriptor descriptor,
            VolumeGridSpec grid,
            CompiledSkyIslandVolume seeded,
            CompiledSkyIslandVolume structured) {
        ScalarField2 seededUpper = evaluator.field2(seeded.upperSurfaceGraph());
        ScalarField2 seededUnderside = evaluator.field2(seeded.undersideSurfaceGraph());
        ScalarField2 structuredUpper = evaluator.field2(structured.upperSurfaceGraph());
        ScalarField2 structuredUnderside = evaluator.field2(structured.undersideSurfaceGraph());
        double suspension = descriptor.suspensionElevation();
        double maximumRelief = 0.0;

        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                Coordinate2 point = new Coordinate2(grid.xAt(x), grid.zAt(z));
                double seededUpperValue = seededUpper.sample(point);
                double structuredUpperValue = structuredUpper.sample(point);
                double baseOffset = seededUpperValue - suspension;
                double structuredOffset = structuredUpperValue - suspension;

                assertEquals(
                        seededUnderside.sample(point),
                        structuredUnderside.sample(point),
                        TOLERANCE,
                        "secondary morphology must not alter the accepted underside");
                assertEquals(
                        Math.signum(baseOffset),
                        Math.signum(structuredOffset),
                        "secondary morphology must preserve the upper-surface sign envelope");

                if (Math.abs(baseOffset) <= TOLERANCE) {
                    assertEquals(0.0, structuredOffset, TOLERANCE);
                } else {
                    double factor = structuredOffset / baseOffset;
                    assertTrue(
                            factor >= SecondaryMorphologySkyIslandVolumeRecipe.MINIMUM_UPPER_FACTOR
                                            - TOLERANCE
                                    && factor <= SecondaryMorphologySkyIslandVolumeRecipe.MAXIMUM_UPPER_FACTOR
                                            + TOLERANCE,
                            () -> "secondary factor outside analytical envelope: " + factor);
                }
                maximumRelief = Math.max(
                        maximumRelief,
                        Math.abs(structuredUpperValue - seededUpperValue));
            }
        }

        assertTrue(
                maximumRelief >= MINIMUM_VISIBLE_RELIEF,
                () -> "secondary morphology must create visible organized relief; max="
                        + maximumRelief);
    }
}
