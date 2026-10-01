package io.github.nidaba.skyforge.reference.evidence;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import io.github.nidaba.skyforge.recipes.skyisland.CompiledSkyIslandVolume;
import io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologySkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.recipes.skyisland.SignalFreeSkyIslandVolumeRecipe;
import io.github.nidaba.skyforge.reference.sampling.SamplingOrder;
import io.github.nidaba.skyforge.reference.sampling.VolumeGridSpec;
import io.github.nidaba.skyforge.reference.volume.SuspendedVolumeReferenceDomain;
import org.junit.jupiter.api.Test;

final class SuspendedVolumeEvidenceGeneratorTest {
    private static final VolumeGridSpec SMALL_GRID =
            new VolumeGridSpec(-384.0, 384.0, 0.0, 512.0, -384.0, 384.0, 49, 33, 49);

    @Test
    void derivesOneFiniteSuspendedMassWithAirClearanceOnEveryFace() {
        SuspendedVolumeEvidence evidence = evidence(SamplingOrder.FORWARD);
        VolumeMetrics metrics = evidence.metrics();

        assertAll(
                () -> assertTrue(metrics.solidSampleCount() > 0),
                () -> assertEquals(1, metrics.connectedSolidComponents()),
                () -> assertEquals(0, metrics.faceContacts().total()),
                () -> assertTrue(metrics.airClearance().minimum() > 0.0),
                () -> assertTrue(metrics.bounds().minimumY() > SMALL_GRID.minimumY()),
                () -> assertTrue(metrics.bounds().maximumY() < SMALL_GRID.maximumY()),
                () -> assertEquals(
                        metrics.solidSampleCount(), evidence.occupancy().solidSampleCount()));
    }

    @Test
    void exactSlicesAndSurfaceGridsUseTheDeclaredCanonicalAxes() {
        SuspendedVolumeEvidence evidence = evidence(SamplingOrder.BATCHED);

        assertAll(
                () -> assertEquals(49, evidence.upperSurface().specification().width()),
                () -> assertEquals(49, evidence.undersideSurface().specification().height()),
                () -> assertEquals(49, evidence.suspensionDensity().specification().width()),
                () -> assertEquals(49, evidence.eastWest().width()),
                () -> assertEquals(33, evidence.eastWest().height()),
                () -> assertEquals(VolumeSlice.Axis.EAST_WEST, evidence.eastWest().axis()),
                () -> assertEquals(VolumeSlice.Axis.NORTH_SOUTH, evidence.northSouth().axis()),
                () -> assertEquals(0.0, evidence.eastWest().fixedCoordinate()),
                () -> assertEquals(0.0, evidence.northSouth().fixedCoordinate()),
                () -> assertTrue(evidence.eastWest().canonicalCsv().startsWith("x,y,density,solid\n")),
                () -> assertTrue(evidence.northSouth().canonicalCsv().startsWith("z,y,density,solid\n")));
    }

    @Test
    void equivalentSchedulesLeaveEveryDerivedGridByteIdentical() {
        SuspendedVolumeEvidence forward = evidence(SamplingOrder.FORWARD);
        SuspendedVolumeEvidence parallel = evidence(SamplingOrder.PARALLEL);

        assertAll(
                () -> assertTrue(forward.density().rawValuesEqual(parallel.density())),
                () -> assertEquals(forward.occupancy().sha256(), parallel.occupancy().sha256()),
                () -> assertTrue(forward.upperSurface().rawValuesEqual(parallel.upperSurface())),
                () -> assertTrue(forward.undersideSurface().rawValuesEqual(parallel.undersideSurface())),
                () -> assertTrue(forward.suspensionDensity().rawValuesEqual(parallel.suspensionDensity())),
                () -> assertEquals(forward.metrics(), parallel.metrics()));
    }

    @Test
    void structuredForwardFactoringMatchesDirectDensityGraphEvaluation() {
        SkyIslandVolumeDescriptor base = SuspendedVolumeReferenceDomain.descriptor();
        SkyIslandVolumeDescriptor descriptor = new SkyIslandVolumeDescriptor(
                SkyIslandVolumeDescriptor.SCHEMA_VERSION,
                0x534b59464f524745L,
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
                1.0,
                base.signalScale());
        CompiledSkyIslandVolume compiled =
                new SecondaryMorphologySkyIslandVolumeRecipe().compile(descriptor);
        SuspendedVolumeEvidenceGenerator generator = new SuspendedVolumeEvidenceGenerator();
        SuspendedVolumeEvidence factored = generator.generate(compiled, SMALL_GRID, SamplingOrder.FORWARD);
        SuspendedVolumeEvidence direct = generator.generate(compiled, SMALL_GRID, SamplingOrder.PARALLEL);

        assertAll(
                () -> assertTrue(factored.density().rawValuesEqual(direct.density())),
                () -> assertEquals(factored.occupancy().sha256(), direct.occupancy().sha256()),
                () -> assertTrue(factored.upperSurface().rawValuesEqual(direct.upperSurface())),
                () -> assertTrue(factored.undersideSurface().rawValuesEqual(direct.undersideSurface())),
                () -> assertEquals(factored.metrics(), direct.metrics()));
    }

    private static SuspendedVolumeEvidence evidence(SamplingOrder order) {
        CompiledSkyIslandVolume compiled = new SignalFreeSkyIslandVolumeRecipe()
                .compile(SuspendedVolumeReferenceDomain.descriptor());
        return new SuspendedVolumeEvidenceGenerator().generate(compiled, SMALL_GRID, order);
    }
}
