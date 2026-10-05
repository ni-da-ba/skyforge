package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
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
    void projectsOneIncisionOnlyDownhillBedAcrossParentReachAndSubspans() {
        SkyIslandDescriptor descriptor = SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, 77L));
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = List.of(
                sample(0.0, 0.0, 0.70),
                sample(10.0, 1.0 / 3.0, 0.60),
                sample(20.0, 2.0 / 3.0, 0.80),
                sample(30.0, 1.0, 0.55));
        SkyIslandGameScaleHydraulicCalibration calibration =
                new SkyIslandGameScaleHydraulicCalibration(
                        1.0, 4.0, 0.035, 0.25, 1.0, 9.81, 1.0e-8, 160, 1.0);

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> full =
                calibration.crossSections(descriptor, samples);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> subspan =
                calibration.crossSections(descriptor, samples.subList(1, 3), samples);

        for (int i = 0; i < full.size(); i++) {
            double rawBed = calibration.crossSection(descriptor, samples.get(i)).bedElevationMeters();
            assertTrue(full.get(i).bedElevationMeters() <= rawBed + 1.0e-12);
            if (i > 0) {
                assertTrue(full.get(i).bedElevationMeters()
                        <= full.get(i - 1).bedElevationMeters() + 1.0e-12);
            }
        }
        assertEquals(full.get(1).bedElevationMeters(), subspan.getFirst().bedElevationMeters(), 1.0e-12);
        assertEquals(full.get(2).bedElevationMeters(), subspan.getLast().bedElevationMeters(), 1.0e-12);
    }

    private static SkyIslandHydraulicGeometrySkeletonSample sample(
            double arcLength, double stationFraction, double terrainElevation) {
        return new SkyIslandHydraulicGeometrySkeletonSample(
                new SkyIslandLocalPosition(arcLength, 0.0),
                arcLength,
                stationFraction,
                0.2 + 0.6 * stationFraction,
                0.5,
                0.01,
                terrainElevation);
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
