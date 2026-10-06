package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandOpenChannelSourceTailwaterSolverTest {
    private static final double ROUGHNESS = 0.035;
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(
                    ROUGHNESS, 1.0, 9.81, 1.0e-8, 160);

    @Test
    void propagatesSourceNormalDepthDownstreamInBothSupportedRegimes() {
        double subcriticalDischarge = 2.0;
        double subcriticalWidth = 4.0;
        double mildSlope = 0.001;
        double subcriticalNormalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                subcriticalDischarge, ROUGHNESS, mildSlope, subcriticalWidth, 0.0);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> mildReach = List.of(
                section(0.0, 100.0, subcriticalDischarge, subcriticalWidth),
                section(50.0, 100.0 - mildSlope * 50.0, subcriticalDischarge, subcriticalWidth),
                section(100.0, 100.0 - mildSlope * 100.0, subcriticalDischarge, subcriticalWidth));

        var subcritical = SkyIslandOpenChannelOrdinarySpanSolver
                .solveSourceNormalDepthDownstream(mildReach, PARAMETERS);

        assertTrue(subcritical.points().stream().allMatch(point -> point.froudeNumber() < 1.0));
        assertTrue(subcritical.points().stream().allMatch(
                point -> Math.abs(point.depthMeters() - subcriticalNormalDepth) < 1.0e-6));
        assertTrue(subcritical.maximumEnergyResidualMeters() < 1.0e-7);

        double supercriticalDischarge = 10.0;
        double supercriticalWidth = 3.0;
        double steepSlope = 0.04;
        double supercriticalNormalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                supercriticalDischarge, ROUGHNESS, steepSlope, supercriticalWidth, 0.0);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> steepReach = List.of(
                section(0.0, 20.0, supercriticalDischarge, supercriticalWidth),
                section(10.0, 20.0 - steepSlope * 10.0, supercriticalDischarge, supercriticalWidth),
                section(20.0, 20.0 - steepSlope * 20.0, supercriticalDischarge, supercriticalWidth));

        var supercritical = SkyIslandOpenChannelOrdinarySpanSolver
                .solveSourceNormalDepthDownstream(steepReach, PARAMETERS);

        assertTrue(supercritical.points().stream().allMatch(point -> point.froudeNumber() > 1.0));
        assertTrue(supercritical.points().stream().allMatch(
                point -> Math.abs(point.depthMeters() - supercriticalNormalDepth) < 1.0e-6));
        assertTrue(supercritical.maximumEnergyResidualMeters() < 1.0e-7);
    }

    @Test
    void checksSubcriticalTailwaterProfileAgainstSourceNormalDepth() {
        double discharge = 2.0;
        double width = 4.0;
        double bedSlope = 0.001;
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, ROUGHNESS, bedSlope, width, 0.0);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, discharge, width),
                section(50.0, 100.0 - bedSlope * 50.0, discharge, width),
                section(100.0, 100.0 - bedSlope * 100.0, discharge, width));

        var profile =
                SkyIslandOpenChannelOrdinarySpanSolver.solveSourceNormalDepthToTailwater(
                        sections, normalDepth, PARAMETERS);

        assertEquals(normalDepth, profile.points().getFirst().depthMeters(), 1.0e-6);
        assertEquals(normalDepth, profile.points().getLast().depthMeters(), 1.0e-6);
        assertTrue(profile.maximumEnergyResidualMeters() < 1.0e-7);
    }

    @Test
    void rejectsSupercriticalSourceWhenTailwaterCannotSupportAMatchedJump() {
        double discharge = 10.0;
        double width = 3.0;
        double bedSlope = 0.04;
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 20.0, discharge, width),
                section(10.0, 20.0 - bedSlope * 10.0, discharge, width),
                section(20.0, 20.0 - bedSlope * 20.0, discharge, width),
                section(30.0, 20.0 - bedSlope * 30.0, discharge, width),
                section(40.0, 20.0 - bedSlope * 40.0, discharge, width));
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, ROUGHNESS, bedSlope, width, 0.0);
        assertTrue(SkyIslandGraduallyVariedFlowSolver.froudeNumber(
                sections.getFirst(), normalDepth, PARAMETERS) > 1.0);

        assertThrows(IllegalStateException.class,
                () -> SkyIslandOpenChannelOrdinarySpanSolver
                        .solveSourceNormalDepthToTailwater(sections, 0.01, PARAMETERS));
    }

    @Test
    void rejectsIncompatibleSourceAndTailwaterControls() {
        double discharge = 2.0;
        double width = 4.0;
        double bedSlope = 0.001;
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                discharge, ROUGHNESS, bedSlope, width, 0.0);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 100.0, discharge, width),
                section(50.0, 100.0 - bedSlope * 50.0, discharge, width),
                section(100.0, 100.0 - bedSlope * 100.0, discharge, width));

        assertThrows(IllegalStateException.class,
                () -> SkyIslandOpenChannelOrdinarySpanSolver.solveSourceNormalDepthToTailwater(
                        sections, normalDepth + 0.2, PARAMETERS));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double discharge, double width) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, discharge, width, 0.0);
    }
}
