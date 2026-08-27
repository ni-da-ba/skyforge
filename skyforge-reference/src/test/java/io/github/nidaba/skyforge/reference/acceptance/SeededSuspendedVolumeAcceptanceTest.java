package io.github.nidaba.skyforge.reference.acceptance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SignalFreeSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.FixedSeedReferenceCorpus;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidence;
import io.github.nidaba.skyforge.reference.evidence.SuspendedVolumeEvidenceGenerator;
import io.github.nidaba.skyforge.reference.evidence.VolumeMetrics;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.SeededSuspendedVolumeReferenceCorpus;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import org.junit.jupiter.api.Test;

final class SeededSuspendedVolumeAcceptanceTest {
    private static final double TOLERANCE = 1.0e-10;
    private static final double MINIMUM_ACCEPTED_CLEARANCE = 80.0;

    private final SeededSkyIslandVolumeRecipe seededRecipe = new SeededSkyIslandVolumeRecipe();
    private final SignalFreeSkyIslandVolumeRecipe signalFreeRecipe =
            new SignalFreeSkyIslandVolumeRecipe();
    private final SuspendedVolumeEvidenceGenerator evidenceGenerator =
            new SuspendedVolumeEvidenceGenerator();
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();

    @Test
    void sfVol006FixedSeedSuitePreservesClosureTopologyClearanceAndIdentityEnvelope() {
        VolumeGridSpec grid = SuspendedVolumeReferenceDomain.grid();
        SkyIslandVolumeDescriptor signalFreeDescriptor = SuspendedVolumeReferenceDomain.descriptor();
        CompiledSkyIslandVolume signalFree = signalFreeRecipe.compile(signalFreeDescriptor);
        ScalarField2 baseUpper = evaluator.field2(signalFree.upperSurfaceGraph());
        ScalarField2 baseUnderside = evaluator.field2(signalFree.undersideSurfaceGraph());

        for (FixedSeedReferenceCorpus.Member member : SeededSuspendedVolumeReferenceCorpus.members()) {
            SkyIslandVolumeDescriptor descriptor =
                    SeededSuspendedVolumeReferenceCorpus.descriptor(member);
            CompiledSkyIslandVolume compiled = seededRecipe.compile(descriptor);
            SuspendedVolumeEvidence evidence = evidenceGenerator.generate(
                    compiled, grid, SamplingOrder.FORWARD);
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

            ScalarField2 seededUpper = evaluator.field2(compiled.upperSurfaceGraph());
            ScalarField2 seededUnderside = evaluator.field2(compiled.undersideSurfaceGraph());
            assertHorizontalIdentityEnvelope(
                    grid,
                    descriptor.suspensionElevation(),
                    baseUpper,
                    baseUnderside,
                    seededUpper,
                    seededUnderside);
        }
    }

    private static void assertHorizontalIdentityEnvelope(
            VolumeGridSpec grid,
            double suspension,
            ScalarField2 baseUpper,
            ScalarField2 baseUnderside,
            ScalarField2 seededUpper,
            ScalarField2 seededUnderside) {
        for (int z = 0; z < grid.zSamples(); z++) {
            for (int x = 0; x < grid.xSamples(); x++) {
                Coordinate2 point = new Coordinate2(grid.xAt(x), grid.zAt(z));
                double baseUpperOffset = baseUpper.sample(point) - suspension;
                double baseUndersideOffset = suspension - baseUnderside.sample(point);
                double seededUpperOffset = seededUpper.sample(point) - suspension;
                double seededUndersideOffset = suspension - seededUnderside.sample(point);

                assertEquals(Math.signum(baseUpperOffset), Math.signum(seededUpperOffset));
                assertEquals(Math.signum(baseUndersideOffset), Math.signum(seededUndersideOffset));
                assertRelativeEnvelope(baseUpperOffset, seededUpperOffset);
                assertRelativeEnvelope(baseUndersideOffset, seededUndersideOffset);
            }
        }
    }

    private static void assertRelativeEnvelope(double baseOffset, double seededOffset) {
        if (Math.abs(baseOffset) <= TOLERANCE) {
            assertEquals(0.0, seededOffset, TOLERANCE);
            return;
        }
        double ratio = seededOffset / baseOffset;
        assertTrue(
                ratio >= 1.0 - SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT - TOLERANCE
                        && ratio <= 1.0 + SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT + TOLERANCE,
                () -> "seeded/base offset ratio outside accepted envelope: " + ratio);
    }
}
