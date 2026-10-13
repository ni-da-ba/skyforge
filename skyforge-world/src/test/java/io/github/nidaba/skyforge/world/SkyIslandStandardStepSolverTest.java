package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkyIslandStandardStepSolverTest {
    private static final double DISCHARGE_CUBIC_METRES_PER_SECOND = 20.0;
    private static final double MANNING_ROUGHNESS = 0.035;
    private static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private static final double SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
    private static final double TEST_C_PART_ONE_SLOPE = 0.001;
    private static final double TEST_C_PART_TWO_SLOPE = 0.02;
    private static final double[] TEST_C_PART_ONE_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM = {
        6.012, 5.712, 5.412, 5.112, 4.812, 4.512, 4.212, 3.912, 3.612, 3.312, 3.000
    };
    private static final double[] TEST_C_PART_TWO_WATER_LEVELS_UPSTREAM_TO_DOWNSTREAM = {
        4.716, 4.416, 4.116, 3.816, 3.516, 3.216, 2.916, 2.615, 2.314, 2.010, 1.700
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

    @Test
    void standardStepUsesDischargeAtEachSectionForSubcriticalEnergy() {
        SkyIslandStandardStepSolver.CrossSection section = rectangularSection(5.0);
        double downstreamDepth = 10.0;
        double[] depths = SkyIslandStandardStepSolver.solve(
                new double[] {0.0, 1.0},
                new double[] {0.0, 0.0},
                new SkyIslandStandardStepSolver.CrossSection[] {section, section},
                new double[] {10.0, 20.0},
                1.0e-12,
                downstreamDepth,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL);

        double upstreamEnergy = depths[0]
                + Math.pow(10.0 / section.area(depths[0]), 2.0)
                        / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
        double downstreamEnergy = downstreamDepth
                + Math.pow(20.0 / section.area(downstreamDepth), 2.0)
                        / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
        assertEquals(upstreamEnergy, downstreamEnergy, 1.0e-8);
    }

    @Test
    void standardStepUsesDischargeAtEachSectionForSupercriticalEnergy() {
        SkyIslandStandardStepSolver.CrossSection section = rectangularSection(5.0);
        double upstreamDepth = 0.5;
        double[] depths = SkyIslandStandardStepSolver.solve(
                new double[] {0.0, 1.0},
                new double[] {0.0, 0.0},
                new SkyIslandStandardStepSolver.CrossSection[] {section, section},
                new double[] {10.0, 12.0},
                1.0e-12,
                upstreamDepth,
                SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL);

        double upstreamEnergy = upstreamDepth
                + Math.pow(10.0 / section.area(upstreamDepth), 2.0)
                        / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
        double downstreamEnergy = depths[1]
                + Math.pow(12.0 / section.area(depths[1]), 2.0)
                        / (2.0 * GRAVITY_METRES_PER_SECOND_SQUARED);
        assertEquals(upstreamEnergy, downstreamEnergy, 1.0e-8);
    }

    private static SkyIslandStandardStepSolver.CrossSection rectangularSection(double widthMetres) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return widthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return widthMetres + 2.0 * depthMetres;
            }

            @Override
            public double topWidth(double depthMetres) {
                return widthMetres;
            }
        };
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
