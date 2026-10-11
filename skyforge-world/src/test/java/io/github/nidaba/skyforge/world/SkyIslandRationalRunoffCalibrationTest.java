package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandRationalRunoffCalibrationTest {
    @Test
    void convertsEffectiveRunoffAreaAndRainfallIntensityToSiDischarge() {
        SkyIslandRationalRunoffCalibration calibration =
                new SkyIslandRationalRunoffCalibration(36.0, 1.0);

        // One full-runoff cell of 100 m by 100 m under 36 mm/h produces 0.1 m^3/s.
        assertEquals(0.1,
                calibration.peakDischargeCubicMetersPerSecond(1.0, 100.0),
                1.0e-12);
    }

    @Test
    void dischargeScalesWithRunoffAreaRainfallAndWorldUnitConversion() {
        SkyIslandRationalRunoffCalibration base =
                new SkyIslandRationalRunoffCalibration(36.0, 1.0);
        SkyIslandRationalRunoffCalibration wetter =
                new SkyIslandRationalRunoffCalibration(72.0, 1.0);
        SkyIslandRationalRunoffCalibration largerWorldUnit =
                new SkyIslandRationalRunoffCalibration(36.0, 2.0);
        double reference = base.peakDischargeCubicMetersPerSecond(2.0, 10.0);

        assertTrue(wetter.peakDischargeCubicMetersPerSecond(2.0, 10.0) > reference);
        assertTrue(base.peakDischargeCubicMetersPerSecond(4.0, 10.0) > reference);
        assertEquals(4.0 * reference,
                largerWorldUnit.peakDischargeCubicMetersPerSecond(2.0, 10.0),
                1.0e-12);
    }

    @Test
    void rejectsMissingOrNonphysicalEventInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandRationalRunoffCalibration(0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandRationalRunoffCalibration(36.0, 0.0));
        SkyIslandRationalRunoffCalibration calibration =
                new SkyIslandRationalRunoffCalibration(36.0, 1.0);
        assertThrows(IllegalArgumentException.class,
                () -> calibration.peakDischargeCubicMetersPerSecond(-1.0, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> calibration.peakDischargeCubicMetersPerSecond(1.0, 0.0));
    }
}
