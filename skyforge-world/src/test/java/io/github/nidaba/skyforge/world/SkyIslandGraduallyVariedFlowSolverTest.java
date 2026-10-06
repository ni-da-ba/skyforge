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
        assertTrue(result.points().getFirst().waterSurfaceElevationMeters()
                > result.points().getLast().waterSurfaceElevationMeters());
        for (SkyIslandGraduallyVariedFlowSolver.ProfilePoint point : result.points()) {
            assertTrue(point.froudeNumber() < 1.0);
            assertTrue(Double.isFinite(point.waterSurfaceElevationMeters()));
        }
    }

    @Test
    void solvesGraduallyDistributedRunoffWithSectionSpecificDischarge() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> graduallyIncreasingFlow = List.of(
                section(0.0, 100.1, 1.0, 5.0),
                section(50.0, 100.05, 1.5, 5.0),
                section(100.0, 100.0, 2.0, 5.0));
        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        graduallyIncreasingFlow, 0.8, PARAMETERS);
        SkyIslandGraduallyVariedFlowSolver.ProfilePoint upstream = result.points().get(1);
        SkyIslandGraduallyVariedFlowSolver.ProfilePoint downstream = result.points().get(2);
        double upstreamConveyance = conveyance(upstream, ROUGHNESS);
        double downstreamConveyance = conveyance(downstream, ROUGHNESS);
        double averageFrictionSlope = Math.pow(
                (upstream.section().dischargeCubicMetersPerSecond()
                                + downstream.section().dischargeCubicMetersPerSecond())
                        / (upstreamConveyance + downstreamConveyance),
                2.0);
        double upstreamEnergy = upstream.waterSurfaceElevationMeters()
                + upstream.velocityMetersPerSecond() * upstream.velocityMetersPerSecond()
                        / (2.0 * GRAVITY);
        double downstreamEnergy = downstream.waterSurfaceElevationMeters()
                + downstream.velocityMetersPerSecond() * downstream.velocityMetersPerSecond()
                        / (2.0 * GRAVITY);
        assertEquals(averageFrictionSlope * 50.0,
                upstreamEnergy - downstreamEnergy, 1.0e-6);
        assertTrue(result.points().getFirst().froudeNumber() < 1.0);
        assertTrue(result.points().get(1).froudeNumber() < 1.0);
        assertTrue(result.points().getLast().froudeNumber() < 1.0);

        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> supercriticalBoundary = List.of(
                section(0.0, 1.0, 9.0, 10.0),
                section(10.0, 0.9, 9.0, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstream(
                        supercriticalBoundary, 0.1, PARAMETERS));
    }

    @Test
    void subcriticalProfileMarchesDownstreamFromUpstreamStageControl() {
        double discharge = 2.0;
        double width = 4.0;
        double bedSlope = 0.001;
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, ROUGHNESS, bedSlope, width, 0.0);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, discharge, width),
                section(50.0, 100.0 - bedSlope * 50.0, discharge, width),
                section(100.0, 100.0 - bedSlope * 100.0, discharge, width));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalDownstream(
                        sections, normalDepth, PARAMETERS);

        assertEquals(sections.size(), result.points().size());
        for (SkyIslandGraduallyVariedFlowSolver.ProfilePoint point : result.points()) {
            assertEquals(normalDepth, point.depthMeters(), 1.0e-6);
            assertTrue(point.froudeNumber() < 1.0);
        }
        assertTrue(result.maximumEnergyResidualMeters() < 1.0e-7);
    }

    @Test
    void subcriticalDownstreamSolveRejectsSupercriticalSourceAndInsufficientHead() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 0.0, 1.0, 2.0),
                section(10.0, 0.0, 1.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalDownstream(
                        List.of(
                                section(0.0, 0.0, 10.0, 3.0),
                                section(10.0, -0.1, 10.0, 3.0)),
                        0.2,
                        PARAMETERS));
        assertThrows(IllegalStateException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalDownstream(
                        List.of(
                                sections.getFirst(),
                                section(10.0, 10.0, 1.0, 2.0)),
                        1.0,
                        PARAMETERS));
    }

    @Test
    void supercriticalProfileMarchesDownstreamFromUpstreamStageControl() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, 10.0, 3.0),
                section(10.0, 99.0, 10.0, 3.0),
                section(20.0, 98.0, 10.0, 3.0));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        sections, 0.4, PARAMETERS);

        assertEquals(3, result.points().size());
        for (SkyIslandGraduallyVariedFlowSolver.ProfilePoint point : result.points()) {
            assertTrue(point.froudeNumber() > 1.0);
        }
        SkyIslandGraduallyVariedFlowSolver.ProfilePoint upstream =
                result.points().getFirst();
        SkyIslandGraduallyVariedFlowSolver.ProfilePoint downstream =
                result.points().getLast();
        double upstreamTotalHead = upstream.waterSurfaceElevationMeters()
                + upstream.velocityMetersPerSecond() * upstream.velocityMetersPerSecond()
                        / (2.0 * GRAVITY);
        double downstreamTotalHead = downstream.waterSurfaceElevationMeters()
                + downstream.velocityMetersPerSecond() * downstream.velocityMetersPerSecond()
                        / (2.0 * GRAVITY);
        assertTrue(upstreamTotalHead > downstreamTotalHead);
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
                SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        shiftedDatum, 0.4, PARAMETERS);
        for (int i = 0; i < result.points().size(); i++) {
            assertEquals(result.points().get(i).depthMeters(),
                    shifted.points().get(i).depthMeters(), 1.0e-6);
        }

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSupercriticalDownstream(
                        sections, 2.0, PARAMETERS));
    }

    @Test
    void mixedRegimeSolveMatchesUpstreamStageAcrossAnInternalCriticalControl() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, 10.0, 3.0),
                section(10.0, 99.99, 10.0, 3.0),
                section(20.0, 99.98, 10.0, 3.0),
                section(30.0, 98.0, 10.0, 3.0),
                section(40.0, 96.0, 10.0, 3.0));
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstreamControl =
                sections.subList(0, 3);
        SkyIslandGraduallyVariedFlowSolver.Result knownSubcritical =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        upstreamControl, PARAMETERS);
        double upstreamDepth = knownSubcritical.points().getFirst().depthMeters();

        assertThrows(IllegalStateException.class,
                () -> SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        sections, PARAMETERS));

        SkyIslandGameScaleHydraulicCalibration calibration =
                new SkyIslandGameScaleHydraulicCalibration(
                        1.0, 1.0, ROUGHNESS, 0.0, 1.0, GRAVITY, 1.0e-9, 160);
        SkyIslandGraduallyVariedFlowSolver.Result mixed =
                SkyIslandOpenChannelOrdinarySpanSolver.solveMixedRegimeAtInternalCriticalControl(
                        sections,
                        upstreamDepth,
                        calibration,
                        new IllegalStateException(
                                "no subcritical standard-step solution before critical depth at section 3"));

        assertEquals(sections.size(), mixed.points().size());
        assertEquals(upstreamDepth, mixed.points().getFirst().depthMeters(), 1.0e-5);
        assertEquals(1.0, mixed.points().get(2).froudeNumber(), 1.0e-7);
        assertTrue(mixed.points().get(3).froudeNumber() > 1.0);
        assertTrue(mixed.points().get(4).froudeNumber() > 1.0);
        assertTrue(mixed.maximumEnergyResidualMeters() < 1.0e-7);
    }

    @Test
    void authorizedCriticalOutfallControlProducesAnUpstreamSubcriticalProfile() {
        double width = 10.0;
        double discharge = 3.0;
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, discharge, width),
                section(50.0, 100.0, discharge, width),
                section(100.0, 100.0, discharge, width));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandGraduallyVariedFlowSolver.solveSubcriticalUpstreamFromCriticalControl(
                        sections, PARAMETERS);

        assertTrue(result.points().getFirst().froudeNumber() < 1.0);
        assertTrue(result.points().get(1).froudeNumber() < 1.0);
        assertEquals(1.0, result.points().getLast().froudeNumber(), 1.0e-7);
        assertTrue(result.maximumEnergyResidualMeters() < 1.0e-7);
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

    private static double conveyance(
            SkyIslandGraduallyVariedFlowSolver.ProfilePoint point, double roughness) {
        double depth = point.depthMeters();
        double bottomWidth = point.section().bottomWidthMeters();
        double sideSlope = point.section().sideSlopeHorizontalToVertical();
        double area = depth * (bottomWidth + sideSlope * depth);
        double perimeter = bottomWidth + 2.0 * depth * Math.hypot(1.0, sideSlope);
        return area * Math.pow(area / perimeter, 2.0 / 3.0) / roughness;
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double discharge, double width) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, discharge, width, 0.0);
    }
}
