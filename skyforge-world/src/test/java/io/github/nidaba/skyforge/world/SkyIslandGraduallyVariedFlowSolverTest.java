package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandGraduallyVariedFlowSolverTest {
    private static final double ROUGHNESS = 0.03;
    private static final double GRAVITY = 9.81;
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(
                    ROUGHNESS, 1.0, GRAVITY, 1.0e-9, 160);

    @Test
    void uniformPrismaticReachPreservesManningNormalDepth() {
        double depth = 1.0;
        double width = 10.0;
        double slope = 0.001;
        double area = width * depth;
        double radius = area / (width + 2.0 * depth);
        double discharge = (1.0 / ROUGHNESS)
                * area * Math.pow(radius, 2.0 / 3.0) * Math.sqrt(slope);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.1, discharge, width),
                section(50.0, 100.05, discharge, width),
                section(100.0, 100.0, discharge, width));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        sections, depth, PARAMETERS);

        assertEquals(3, result.points().size());
        for (SkyIslandGraduallyVariedFlowSolver.ProfilePoint point : result.points()) {
            assertEquals(depth, point.depthMeters(), 1.0e-6);
            assertTrue(point.froudeNumber() < 1.0);
        }
        assertTrue(result.maximumEnergyResidualMeters() < 1.0e-7);

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> shiftedDatum = sections.stream()
                .map(section -> new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        section.chainageMeters(),
                        section.bedElevationMeters() + 1.0e8,
                        section.dischargeCubicMetersPerSecond(),
                        section.bottomWidthMeters(),
                        section.sideSlopeHorizontalToVertical()))
                .toList();
        SkyIslandGraduallyVariedFlowSolver.Result shifted =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        shiftedDatum, depth, PARAMETERS);
        for (int i = 0; i < result.points().size(); i++) {
            assertEquals(result.points().get(i).depthMeters(),
                    shifted.points().get(i).depthMeters(), 1.0e-6);
        }
    }

    @Test
    void elevatedDownstreamControlProducesAnUpstreamBackwaterProfile() {
        double width = 10.0;
        double normalDepth = 1.0;
        double area = width * normalDepth;
        double radius = area / (width + 2.0 * normalDepth);
        double discharge = (1.0 / ROUGHNESS)
                * area * Math.pow(radius, 2.0 / 3.0) * Math.sqrt(0.001);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.2, discharge, width),
                section(50.0, 100.1, discharge, width),
                section(100.0, 100.0, discharge, width));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        sections, 1.5, PARAMETERS);

        assertEquals(1.5, result.points().getLast().depthMeters(), 0.0);
        assertTrue(result.points().getFirst().depthMeters() > 1.5);
        for (SkyIslandGraduallyVariedFlowSolver.ProfilePoint point : result.points()) {
            assertTrue(point.froudeNumber() < 1.0);
            assertTrue(Double.isFinite(point.waterSurfaceElevationMeters()));
        }
    }

    @Test
    void rejectsVariableDischargeAndNonSubcriticalBoundaryInsteadOfSmoothingTransitions() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> changingFlow = List.of(
                section(0.0, 1.0, 2.0, 4.0),
                section(10.0, 0.9, 3.0, 4.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        changingFlow, 1.0, PARAMETERS));

        double discharge = 9.0;
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> ordinaryReach = List.of(
                section(0.0, 1.0, discharge, 10.0),
                section(10.0, 0.9, discharge, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        ordinaryReach, 0.1, PARAMETERS));
    }

    @Test
    void rejectsMalformedSectionsAndNumericalParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> section(0.0, 0.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkyIslandGraduallyVariedFlowSolver.Parameters(
                        0.0, 1.0, GRAVITY, 1.0e-8, 100));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        List.of(section(0.0, 2.0, 1.0, 4.0)), 0.5, PARAMETERS));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double discharge, double width) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, discharge, width, 0.0);
    }
}
