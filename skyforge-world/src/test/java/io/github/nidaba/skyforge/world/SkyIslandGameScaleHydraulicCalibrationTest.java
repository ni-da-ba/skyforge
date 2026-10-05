package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import org.junit.jupiter.api.Test;

class SkyIslandGameScaleHydraulicCalibrationTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void mapsAuthoredRelativeSectionGeometryIntoExplicitSiInputs() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 77L));
        SkyIslandHydraulicGeometrySkeletonSample sample =
                new SkyIslandHydraulicGeometrySkeletonSample(
                        new SkyIslandLocalPosition(0.0, 0.0),
                        12.0,
                        0.5,
                        0.5,
                        10.0,
                        0.01,
                        0.5);
        SkyIslandGameScaleHydraulicCalibration calibration = calibration(1.0, 4.0, 1.0);

        SkyIslandGraduallyVariedFlowSolver.CrossSection section =
                calibration.crossSection(descriptor, sample);

        double reliefMeters = descriptor.reliefBudget();
        assertEquals(12.0, section.chainageMeters(), 0.0);
        assertEquals(0.5 * 4.0, section.dischargeCubicMetersPerSecond(), 0.0);
        assertEquals(
                (0.5 - 0.01) * reliefMeters,
                section.bedElevationMeters(),
                1.0e-12);
        assertEquals(
                20.0,
                section.bottomWidthMeters()
                        + 2.0 * section.sideSlopeHorizontalToVertical()
                                * 0.01 * reliefMeters,
                1.0e-12);
    }

    @Test
    void appliesExplicitBedIncisionScaleWithoutChangingBankfullGeometry() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 77L));
        SkyIslandHydraulicGeometrySkeletonSample sample =
                new SkyIslandHydraulicGeometrySkeletonSample(
                        new SkyIslandLocalPosition(0.0, 0.0),
                        12.0,
                        0.5,
                        0.5,
                        10.0,
                        0.01,
                        0.5);
        SkyIslandGameScaleHydraulicCalibration calibration =
                new SkyIslandGameScaleHydraulicCalibration(
                        1.0, 4.0, 0.035, 1.0, 1.0, 9.81, 1.0e-8, 160, 2.0);

        SkyIslandGraduallyVariedFlowSolver.CrossSection section =
                calibration.crossSection(descriptor, sample);

        double reliefMeters = descriptor.reliefBudget();
        assertEquals((0.5 - 2.0 * 0.01) * reliefMeters, section.bedElevationMeters(), 1.0e-12);
        assertEquals(
                20.0,
                section.bottomWidthMeters()
                        + 2.0 * section.sideSlopeHorizontalToVertical()
                                * 0.01 * reliefMeters,
                1.0e-12);
    }

    @Test
    void rejectsCrossSectionsWhoseBankfullWidthCannotContainTheSelectedSideSlope() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 77L));
        SkyIslandHydraulicGeometrySkeletonSample sample =
                new SkyIslandHydraulicGeometrySkeletonSample(
                        new SkyIslandLocalPosition(0.0, 0.0),
                        1.0,
                        0.5,
                        0.5,
                        1.0,
                        0.02,
                        0.5);
        SkyIslandGameScaleHydraulicCalibration incompatible = calibration(1.0, 1.0, 20.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> incompatible.crossSection(descriptor, sample));
    }

    @Test
    void requiresExplicitGameScaleParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGameScaleHydraulicCalibration(
                        0.0, 1.0, 0.035, 1.0, 1.0, 9.81, 1.0e-8, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGameScaleHydraulicCalibration(
                        1.0, 1.0, 0.0, 1.0, 1.0, 9.81, 1.0e-8, 100));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandGameScaleHydraulicCalibration(
                        1.0, 1.0, 0.035, 1.0, 1.0, 9.81, 1.0e-8, 100, 0.0));
    }

    private static SkyIslandGameScaleHydraulicCalibration calibration(
            double metersPerWorldUnit,
            double dischargeScale,
            double sideSlope) {
        return new SkyIslandGameScaleHydraulicCalibration(
                metersPerWorldUnit,
                dischargeScale,
                0.035,
                sideSlope,
                1.0,
                9.81,
                1.0e-8,
                160);
    }
}
