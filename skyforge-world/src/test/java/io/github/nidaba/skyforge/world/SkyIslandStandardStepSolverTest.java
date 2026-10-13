package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkyIslandStandardStepSolverTest {
    private static final double DISCHARGE_CUBIC_METRES_PER_SECOND = 20.0;
    private static final double MANNING_ROUGHNESS = 0.035;
    private static final double SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
    private static final double TEST_C_PART_ONE_SLOPE = 0.001;
    private static final double TEST_C_PART_TWO_SLOPE = 0.02;
    private static final double[] TEST_C_PART_ONE_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM = {
        6.012, 5.712, 5.412, 5.112, 4.812, 4.512, 4.212, 3.912, 3.612, 3.312, 3.000
    };
    private static final double[] TEST_C_PART_TWO_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM = {
        4.700, 4.437, 4.118, 3.818, 3.518, 3.218, 2.918, 2.618, 2.319, 2.020, 1.721
    };

    /**
     * Environment Agency Benchmark Test C, Part 1: 11 triangular sections at 300 m spacing,
     * Q=20 m³/s, n=0.035, bed slope 0.001, and downstream stage 3.0 m. The published tables use
     * a datum 10 m above this test's datum; expected levels below are shifted uniformly by 10 m.
     */
    @Test
    void reproducesEnvironmentAgencyTestCPartOneSubcriticalProfile() {
        assertEquals(3.012, normalDepth(TEST_C_PART_ONE_SLOPE), 0.001);

        double[] levels = solveProfile(
                TEST_C_PART_ONE_SLOPE,
                300.0,
                10,
                3.0,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL);
        assertPublishedProfile(levels, TEST_C_PART_ONE_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM, 0.01);
    }

    /**
     * Environment Agency Benchmark Test C, Part 2: 11 triangular sections at 15 m spacing,
     * Q=20 m³/s, n=0.035, bed slope 0.02. Its supercritical profile is controlled upstream;
     * normal depth is therefore used at the upstream boundary and the downstream stage is not
     * imposed on the calculation. The published HEC-RAS steady profile is used with a 0.02 m tolerance.
     */
    @Test
    void reproducesEnvironmentAgencyTestCPartTwoSupercriticalProfile() {
        assertEquals(1.718, normalDepth(TEST_C_PART_TWO_SLOPE), 0.001);

        double[] levels = solveProfile(
                TEST_C_PART_TWO_SLOPE,
                15.0,
                10,
                normalDepth(TEST_C_PART_TWO_SLOPE),
                SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL);
        assertPublishedProfile(levels, TEST_C_PART_TWO_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM, 0.02);
    }

    private static double[] solveProfile(
            double bedSlope,
            double stationIntervalMetres,
            int intervals,
            double boundaryDepthMetres,
            SkyIslandStandardStepSolver.FlowRegime regime) {
        SkyIslandStandardStepSolver.CrossSection section = triangularSection();
        double[] stations = new double[intervals + 1];
        double[] bed = new double[intervals + 1];
        SkyIslandStandardStepSolver.CrossSection[] sections =
                new SkyIslandStandardStepSolver.CrossSection[intervals + 1];
        for (int i = 0; i <= intervals; i++) {
            // The solver takes upstream-to-downstream station order, while Test C reports
            // chainage from its downstream boundary. Bed elevation is relative to a 10 m datum.
            stations[i] = i * stationIntervalMetres;
            bed[i] = intervals * stationIntervalMetres * bedSlope - stations[i] * bedSlope;
            sections[i] = section;
        }

        double[] depths = SkyIslandStandardStepSolver.solve(
                stations,
                bed,
                sections,
                DISCHARGE_CUBIC_METRES_PER_SECOND,
                MANNING_ROUGHNESS,
                boundaryDepthMetres,
                regime);
        double[] levels = new double[depths.length];
        for (int i = 0; i < depths.length; i++) {
            levels[i] = bed[i] + depths[i];
        }
        return levels;
    }

    private static void assertPublishedProfile(double[] actual, double[] expected, double toleranceMetres) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], toleranceMetres, "water level at section " + i);
        }
    }

    private static double normalDepth(double bedSlope) {
        SkyIslandStandardStepSolver.CrossSection section = triangularSection();
        return SkyIslandManningNormalDepthSolver.solve(
                DISCHARGE_CUBIC_METRES_PER_SECOND,
                MANNING_ROUGHNESS,
                bedSlope,
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depthMetres) {
                        return section.area(depthMetres);
                    }

                    @Override
                    public double wettedPerimeter(double depthMetres) {
                        return section.wettedPerimeter(depthMetres);
                    }
                });
    }

    private static SkyIslandStandardStepSolver.CrossSection triangularSection() {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * depthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return 2.0 * depthMetres * Math.sqrt(
                        1.0 + SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * SIDE_SLOPE_HORIZONTAL_TO_VERTICAL);
            }

            @Override
            public double topWidth(double depthMetres) {
                return 2.0 * SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * depthMetres;
            }
        };
    }
}
