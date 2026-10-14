package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicCandidateBuilderTest {
    private static final double ROUGHNESS = 0.035;
    private static final double[] TEST_C_PART_ONE_LEVELS = {
        6.012, 5.712, 5.412, 5.112, 4.812, 4.512,
        4.212, 3.912, 3.612, 3.312, 3.000
    };
    private static final double[] TEST_C_PART_TWO_LEVELS = {
        4.716, 4.416, 4.116, 3.816, 3.516, 3.216,
        2.916, 2.615, 2.314, 2.010, 1.700
    };

    /** EA Test C, Part 1 exercises the candidate geometry and hydraulic solve together. */
    @Test
    void coupledCandidateReproducesEnvironmentAgencyTestCPartOne() {
        List<SkyIslandLocalPosition> points = line(0.0, 300.0, 10);
        SkyIslandSemanticField bedSurface = position -> 0.5 - position.x() / 6000.0;
        var candidate = SkyIslandHydraulicCandidateBuilder.build(
                route(points, 3000.0),
                bedSurface,
                configuration(0.001, 5.0, 40.0, 20.0));

        var profile = candidate.solve(
                constantDischarge(points.size(), 20.0),
                ROUGHNESS,
                3.0,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL);

        assertArrayEquals(TEST_C_PART_ONE_LEVELS, profile.waterSurfaceElevationsMetres(), 0.01);
        assertEquals(3000.0, candidate.totalLengthMetres(), 1.0e-9);
        assertEquals(3.0, profile.bedElevationsMetres()[0], 1.0e-12);
        assertEquals(0.0, profile.bedElevationsMetres()[10], 1.0e-12);
    }

    /** EA Test C, Part 2 checks the upstream-controlled supercritical marching direction. */
    @Test
    void coupledCandidateReproducesEnvironmentAgencyTestCPartTwo() {
        List<SkyIslandLocalPosition> points = line(0.0, 15.0, 10);
        SkyIslandSemanticField bedSurface = position -> 0.5 - 0.02 * position.x() / 6.0;
        var candidate = SkyIslandHydraulicCandidateBuilder.build(
                route(points, 150.0),
                bedSurface,
                configuration(0.02, 5.0, 40.0, 20.0));

        var profile = candidate.solve(
                constantDischarge(points.size(), 20.0),
                ROUGHNESS,
                normalDepth(0.02),
                SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL);

        assertArrayEquals(TEST_C_PART_TWO_LEVELS, profile.waterSurfaceElevationsMetres(), 0.02);
        assertEquals(3.0, profile.bedElevationsMetres()[0], 1.0e-12);
        assertEquals(0.0, profile.bedElevationsMetres()[10], 1.0e-12);
    }

    @Test
    void candidateCreatesAContinuousBoundedValleyAroundItsChannelBed() {
        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0));
        var candidate = SkyIslandHydraulicCandidateBuilder.build(
                route(points, 10.0),
                ignored -> 0.5,
                new SkyIslandHydraulicCandidateBuilder.Configuration(
                        1.0, 10.0, 0.0, 0.01, 1.0,
                        0.0, 2.0, 5.0, 20.0, 5.0));

        assertEquals(4.0, candidate.sampleTerrainMetres(new SkyIslandLocalPosition(5.0, 0.0)), 1.0e-12);
        assertEquals(5.0, candidate.sampleTerrainMetres(new SkyIslandLocalPosition(5.0, 2.0)), 1.0e-12);
        assertEquals(5.0, candidate.sampleTerrainMetres(new SkyIslandLocalPosition(5.0, 20.0)), 1.0e-12);
        assertEquals(
                5.0,
                candidate.sampleTerrainMetres(new SkyIslandLocalPosition(5.0, 19.999)),
                1.0e-4);
        assertArrayEquals(
                new double[] {4.05, 3.95},
                candidate.bedElevationsMetres(),
                1.0e-12);

        double[] escaped = candidate.bedElevationsMetres();
        escaped[0] = 1000.0;
        assertEquals(4.05, candidate.bedElevationsMetres()[0], 1.0e-12);
    }

    @Test
    void valleyRequiresAPositiveBlendIntervalBeyondTheBankfullEdge() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkyIslandHydraulicCandidateBuilder.Configuration(
                        1.0, 10.0, 0.0, 0.01, 0.0,
                        0.0, 2.0, 5.0, 10.0, 5.0));
    }

    @Test
    void candidateFailsClosedWhenRequiredChannelCutExceedsItsEnvelope() {
        List<SkyIslandLocalPosition> points = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0));
        var configuration = new SkyIslandHydraulicCandidateBuilder.Configuration(
                1.0, 10.0, 0.0, 0.01, 1.0,
                0.0, 2.0, 5.0, 20.0, 0.5);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandHydraulicCandidateBuilder.build(
                        route(points, 10.0), ignored -> 0.5, configuration));
    }

    private static SkyIslandHydraulicCandidateBuilder.Configuration configuration(
            double minimumBedSlope,
            double bankfullDepthMetres,
            double valleyHalfWidthMetres,
            double maximumTerrainEditMetres) {
        return new SkyIslandHydraulicCandidateBuilder.Configuration(
                1.0,
                6.0,
                0.0,
                minimumBedSlope,
                0.0,
                0.0,
                2.0,
                bankfullDepthMetres,
                valleyHalfWidthMetres,
                maximumTerrainEditMetres);
    }

    private static SkyIslandGeomorphicCandidateRoute route(
            List<SkyIslandLocalPosition> points,
            double length) {
        return new SkyIslandGeomorphicCandidateRoute(
                points, 0.0, length, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private static List<SkyIslandLocalPosition> line(double start, double spacing, int intervals) {
        List<SkyIslandLocalPosition> points = new ArrayList<>(intervals + 1);
        for (int i = 0; i <= intervals; i++) {
            points.add(new SkyIslandLocalPosition(start + i * spacing, 0.0));
        }
        return List.copyOf(points);
    }

    private static double[] constantDischarge(int length, double discharge) {
        double[] values = new double[length];
        java.util.Arrays.fill(values, discharge);
        return values;
    }

    private static double normalDepth(double bedSlope) {
        return SkyIslandManningNormalDepthSolver.solve(
                20.0,
                ROUGHNESS,
                bedSlope,
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depthMetres) {
                        return 2.0 * depthMetres * depthMetres;
                    }

                    @Override
                    public double wettedPerimeter(double depthMetres) {
                        return 2.0 * depthMetres * Math.sqrt(5.0);
                    }
                });
    }
}
