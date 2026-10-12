package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SkyIslandUnitWidthStandardStepSolverTest {
    private static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private static final double CHANNEL_LENGTH_METRES = 1000.0;
    private static final int BED_REFERENCE_INTERVAL_METRES = 1;
    private static final int SOLVER_INTERVAL_METRES = 10;
    private static final double PROFILE_TOLERANCE_METRES = 0.001;

    @Test
    void recoversSwashesMacdonaldSmoothSubcriticalProfileFromConstructedBed() {
        double unitDischarge = 2.0;
        double roughness = 0.033;
        double[] recovered = solvePublishedMacDonaldProfile(
                unitDischarge,
                roughness,
                0.5,
                16.0,
                SkyIslandUnitWidthStandardStepSolver.FlowRegime.SUBCRITICAL);

        assertProfileMatchesMacDonald(recovered, unitDischarge, 0.5, 16.0);
    }

    @Test
    void recoversSwashesMacdonaldSmoothSupercriticalProfileFromConstructedBed() {
        double unitDischarge = 2.5;
        double roughness = 0.04;
        double[] recovered = solvePublishedMacDonaldProfile(
                unitDischarge,
                roughness,
                -0.2,
                36.0,
                SkyIslandUnitWidthStandardStepSolver.FlowRegime.SUPERCRITICAL);

        assertProfileMatchesMacDonald(recovered, unitDischarge, -0.2, 36.0);
    }

    @Test
    void rejectsCriticalBoundaryAndNonMonotoneStations() {
        double criticalDepth = Math.cbrt(4.0 / GRAVITY_METRES_PER_SECOND_SQUARED);
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandUnitWidthStandardStepSolver.solve(
                        new double[] {0.0, 1.0},
                        new double[] {0.0, 0.0},
                        2.0,
                        0.033,
                        criticalDepth,
                        SkyIslandUnitWidthStandardStepSolver.FlowRegime.SUBCRITICAL));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandUnitWidthStandardStepSolver.solve(
                        new double[] {0.0, 0.0},
                        new double[] {0.0, 0.0},
                        2.0,
                        0.033,
                        1.0,
                        SkyIslandUnitWidthStandardStepSolver.FlowRegime.SUBCRITICAL));
    }

    private static double[] solvePublishedMacDonaldProfile(
            double unitDischarge,
            double roughness,
            double amplitude,
            double gaussianExponent,
            SkyIslandUnitWidthStandardStepSolver.FlowRegime regime) {
        int referenceIntervals = (int) (CHANNEL_LENGTH_METRES / BED_REFERENCE_INTERVAL_METRES);
        int solverIntervals = (int) (CHANNEL_LENGTH_METRES / SOLVER_INTERVAL_METRES);
        double[] fineBed = constructMacDonaldBed(
                referenceIntervals, unitDischarge, roughness, amplitude, gaussianExponent);
        double[] stations = new double[solverIntervals + 1];
        double[] bed = new double[solverIntervals + 1];
        for (int i = 0; i <= solverIntervals; i++) {
            stations[i] = i * (double) SOLVER_INTERVAL_METRES;
            bed[i] = fineBed[i * SOLVER_INTERVAL_METRES / BED_REFERENCE_INTERVAL_METRES];
        }

        double boundaryDepth = macDonaldDepth(
                regime == SkyIslandUnitWidthStandardStepSolver.FlowRegime.SUBCRITICAL
                        ? CHANNEL_LENGTH_METRES
                        : 0.0,
                unitDischarge,
                amplitude,
                gaussianExponent);
        return SkyIslandUnitWidthStandardStepSolver.solve(
                stations, bed, unitDischarge, roughness, boundaryDepth, regime);
    }

    private static double[] constructMacDonaldBed(
            int intervals,
            double unitDischarge,
            double roughness,
            double amplitude,
            double gaussianExponent) {
        double[] bed = new double[intervals + 1];
        for (int i = 0; i < intervals; i++) {
            double x0 = i * BED_REFERENCE_INTERVAL_METRES;
            double x1 = (i + 1.0) * BED_REFERENCE_INTERVAL_METRES;
            double derivative0 = bedDerivative(x0, unitDischarge, roughness, amplitude, gaussianExponent);
            double derivative1 = bedDerivative(x1, unitDischarge, roughness, amplitude, gaussianExponent);
            bed[i + 1] = bed[i]
                    + 0.5 * (derivative0 + derivative1) * BED_REFERENCE_INTERVAL_METRES;
        }
        return bed;
    }

    private static double bedDerivative(
            double x,
            double unitDischarge,
            double roughness,
            double amplitude,
            double gaussianExponent) {
        double depth = macDonaldDepth(x, unitDischarge, amplitude, gaussianExponent);
        double normalizedStation = x / CHANNEL_LENGTH_METRES - 0.5;
        double exponential = Math.exp(-gaussianExponent * normalizedStation * normalizedStation);
        double depthDerivative = Math.cbrt(4.0 / GRAVITY_METRES_PER_SECOND_SQUARED)
                * amplitude
                * exponential
                * (-2.0 * gaussianExponent * normalizedStation / CHANNEL_LENGTH_METRES);
        double froudeSquared = unitDischarge * unitDischarge
                / (GRAVITY_METRES_PER_SECOND_SQUARED * depth * depth * depth);
        double frictionSlope = roughness * roughness * unitDischarge * unitDischarge
                / Math.pow(depth, 10.0 / 3.0);
        return (froudeSquared - 1.0) * depthDerivative - frictionSlope;
    }

    private static double macDonaldDepth(
            double x,
            double unitDischarge,
            double amplitude,
            double gaussianExponent) {
        double normalizedStation = x / CHANNEL_LENGTH_METRES - 0.5;
        return Math.cbrt(4.0 / GRAVITY_METRES_PER_SECOND_SQUARED)
                * (1.0 + amplitude * Math.exp(-gaussianExponent * normalizedStation * normalizedStation));
    }

    private static void assertProfileMatchesMacDonald(
            double[] recovered, double unitDischarge, double amplitude, double gaussianExponent) {
        for (int i = 0; i < recovered.length; i++) {
            double station = i * (double) SOLVER_INTERVAL_METRES;
            assertEquals(
                    macDonaldDepth(station, unitDischarge, amplitude, gaussianExponent),
                    recovered[i],
                    PROFILE_TOLERANCE_METRES,
                    "water depth at x=" + station + " m");
        }
    }
}
