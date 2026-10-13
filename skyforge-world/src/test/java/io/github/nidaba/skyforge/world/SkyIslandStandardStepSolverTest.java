package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkyIslandStandardStepSolverTest {
    private static final double DISCHARGE_CUBIC_METRES_PER_SECOND = 20.0;
    private static final double MANNING_ROUGHNESS = 0.035;
    private static final double SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
    private static final double BED_LENGTH_METRES = 100.0;
    private static final double STATION_INTERVAL_METRES = 10.0;

    /**
     * Extends Environment Agency Test C (triangular section, Q=20 m³/s, n=0.035) from its
     * published normal-depth control to the standard-step energy solver. Test C Part 1 uses
     * slope 0.001 and reports an upstream water level of 16.012 m for a bed elevation of 13 m.
     */
    @Test
    void reproducesEnvironmentAgencyTestCPartOneSubcriticalUniformProfile() {
        assertUniformProfile(0.001, SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL, 16.012);
    }

    /**
     * Environment Agency Test C Part 2 uses slope 0.02 and reports an upstream water level of
     * 14.718 m for a bed elevation of 13 m.
     */
    @Test
    void reproducesEnvironmentAgencyTestCPartTwoSupercriticalUniformProfile() {
        assertUniformProfile(0.02, SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL, 14.718);
    }

    private static void assertUniformProfile(
            double bedSlope,
            SkyIslandStandardStepSolver.FlowRegime regime,
            double publishedUpstreamWaterLevel) {
        SkyIslandStandardStepSolver.CrossSection section = triangularSection();
        double normalDepth = SkyIslandManningNormalDepthSolver.solve(
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

        int intervals = (int) (BED_LENGTH_METRES / STATION_INTERVAL_METRES);
        double[] stations = new double[intervals + 1];
        double[] bed = new double[intervals + 1];
        SkyIslandStandardStepSolver.CrossSection[] sections =
                new SkyIslandStandardStepSolver.CrossSection[intervals + 1];
        for (int i = 0; i <= intervals; i++) {
            stations[i] = i * STATION_INTERVAL_METRES;
            bed[i] = 13.0 - bedSlope * stations[i];
            sections[i] = section;
        }

        double boundaryDepth = normalDepth;
        double[] depths = SkyIslandStandardStepSolver.solve(
                stations,
                bed,
                sections,
                DISCHARGE_CUBIC_METRES_PER_SECOND,
                MANNING_ROUGHNESS,
                boundaryDepth,
                regime);

        for (int i = 0; i < depths.length; i++) {
            assertEquals(normalDepth, depths[i], 1.0e-8, "uniform depth at x=" + stations[i] + " m");
        }
        assertEquals(publishedUpstreamWaterLevel, bed[0] + depths[0], 0.01);
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
