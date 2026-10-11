package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression against Environment Agency report W5-105/TR1, Test C (Triangular Channel),
 * Section 4 and Table 4.1. The cited test specifies a 1:2 (V:H) triangular section,
 * Q=20 m^3/s, Manning n=0.035, and bed slopes 0.001 (subcritical) and 0.02
 * (supercritical). Expected normal depths below are independently solved from the
 * published Manning inputs; they are not copied from Skyforge calibration controls.
 *
 * <p>Source: https://assets.publishing.service.gov.uk/media/602ac6a2d3bf7f031e1bdcea/
 * _Benchmarking_of_Hydraulic_River_Modelling_Software_Packages_-_Test_Specifications.pdf
 */
class SkyIslandOpenChannelBenchmarkTest {
    private static final double DISCHARGE_CUBIC_METERS_PER_SECOND = 20.0;
    private static final double MANNING_N = 0.035;
    private static final double SIDE_SLOPE_HORIZONTAL_PER_VERTICAL = 2.0;
    private static final double GRAVITY_METERS_PER_SECOND_SQUARED = 9.81;

    @Test
    void environmentAgencyTestCPartOneMatchesSubcriticalTriangularNormalDepth() {
        assertTestCNormalDepth(0.001, 3.0122899106344763, true);
    }

    @Test
    void environmentAgencyTestCPartTwoMatchesSupercriticalTriangularNormalDepth() {
        assertTestCNormalDepth(0.02, 1.717725025738699, false);
    }

    private static void assertTestCNormalDepth(
            double slope, double expectedDepthMeters, boolean expectSubcritical) {
        double depth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE_CUBIC_METERS_PER_SECOND,
                MANNING_N,
                slope,
                0.0,
                SIDE_SLOPE_HORIZONTAL_PER_VERTICAL);

        assertEquals(expectedDepthMeters, depth, 1.0e-8);
        assertEquals(
                slope,
                SkyIslandManningHydraulics.uniformFlowEnergySlope(
                        DISCHARGE_CUBIC_METERS_PER_SECOND,
                        MANNING_N,
                        depth,
                        0.0,
                        SIDE_SLOPE_HORIZONTAL_PER_VERTICAL),
                1.0e-12);

        double area = SIDE_SLOPE_HORIZONTAL_PER_VERTICAL * depth * depth;
        double topWidth = 2.0 * SIDE_SLOPE_HORIZONTAL_PER_VERTICAL * depth;
        double hydraulicDepth = area / topWidth;
        double velocity = DISCHARGE_CUBIC_METERS_PER_SECOND / area;
        double froude = velocity / Math.sqrt(
                GRAVITY_METERS_PER_SECOND_SQUARED * hydraulicDepth);
        assertTrue(expectSubcritical ? froude < 1.0 : froude > 1.0,
                "published Test C branch must have the expected Froude regime");
    }
}
