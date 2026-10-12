package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkyIslandManningNormalDepthSolverTest {
    private static final double DISCHARGE_CUBIC_METRES_PER_SECOND = 20.0;
    private static final double MANNING_ROUGHNESS = 0.035;
    private static final double SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
    private static final double REFERENCE_TOLERANCE_METRES = 0.01;

    /**
     * Environment Agency Benchmark Test C: triangular channel, side slope 1V:2H, Q=20 m³/s,
     * Manning n=0.035. Parts 1 and 2 use bed slopes 0.001 and 0.02 respectively. The published
     * results report normal-depth water levels of 16.012 m at the upstream end of Part 1 and
     * 14.718 m at the upstream end of Part 2. The upstream bed elevations are 13.0 m; the EA
     * report states the compared normal depths agree with Manning's equation within 0.01 m.
     *
     * <p>Reference: Environment Agency, Benchmarking Hydraulic River Modelling Software Packages,
     * Results – Test C (Triangular Channels), W5-105/TR2C (2004), sections 2.1, 4.2–4.3,
     * Tables 4.1–4.2. This test is intentionally steady and uniform; it does not claim validation
     * of varied flow or Skyforge's game-scale calibration.
     */
    @Test
    void reproducesEnvironmentAgencyTestCPartOneSubcriticalNormalDepth() {
        double depth = SkyIslandManningNormalDepthSolver.solve(
                DISCHARGE_CUBIC_METRES_PER_SECOND,
                MANNING_ROUGHNESS,
                0.001,
                triangularSection());

        assertEquals(3.01229, depth, REFERENCE_TOLERANCE_METRES);
        assertEquals(16.012, 13.0 + depth, REFERENCE_TOLERANCE_METRES);
    }

    @Test
    void reproducesEnvironmentAgencyTestCPartTwoSupercriticalNormalDepth() {
        double depth = SkyIslandManningNormalDepthSolver.solve(
                DISCHARGE_CUBIC_METRES_PER_SECOND,
                MANNING_ROUGHNESS,
                0.02,
                triangularSection());

        assertEquals(1.71773, depth, REFERENCE_TOLERANCE_METRES);
        assertEquals(14.718, 13.0 + depth, REFERENCE_TOLERANCE_METRES);
    }

    @Test
    void normalDepthIncreasesWithDischargeForSameSectionAndSlope() {
        double shallow = SkyIslandManningNormalDepthSolver.solve(
                10.0, MANNING_ROUGHNESS, 0.001, triangularSection());
        double deep = SkyIslandManningNormalDepthSolver.solve(
                20.0, MANNING_ROUGHNESS, 0.001, triangularSection());

        assertTrue(deep > shallow);
    }

    @Test
    void rejectsNonPhysicalInputsAndSectionProperties() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandManningNormalDepthSolver.solve(
                        0.0, MANNING_ROUGHNESS, 0.001, triangularSection()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandManningNormalDepthSolver.solve(
                        DISCHARGE_CUBIC_METRES_PER_SECOND,
                        MANNING_ROUGHNESS,
                        0.001,
                        new SkyIslandManningNormalDepthSolver.CrossSection() {
                            @Override
                            public double area(double depthMetres) {
                                return Double.NaN;
                            }

                            @Override
                            public double wettedPerimeter(double depthMetres) {
                                return 1.0;
                            }
                        }));
    }

    private static SkyIslandManningNormalDepthSolver.CrossSection triangularSection() {
        return new SkyIslandManningNormalDepthSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * depthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return 2.0
                        * depthMetres
                        * Math.sqrt(1.0 + SIDE_SLOPE_HORIZONTAL_TO_VERTICAL
                                * SIDE_SLOPE_HORIZONTAL_TO_VERTICAL);
            }
        };
    }
}
