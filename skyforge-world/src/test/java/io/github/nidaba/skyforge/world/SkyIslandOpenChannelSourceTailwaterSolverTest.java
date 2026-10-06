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
    void joinsSupercriticalNormalDepthSourceToExplicitSubcriticalTailwater() {
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

        var criticalTailwaterProfile =
                SkyIslandHydraulicJumpProfileSolver.solve(
                        sections, normalDepth, PARAMETERS);
        double tailwaterDepth =
                criticalTailwaterProfile.points().getLast().depthMeters() + 1.0e-4;

        var profile =
                SkyIslandOpenChannelOrdinarySpanSolver.solveSourceNormalDepthToTailwater(
                        sections, tailwaterDepth, PARAMETERS);

        assertTrue(profile.points().getFirst().froudeNumber() > 1.0);
        assertTrue(profile.points().getLast().froudeNumber() < 1.0);
        assertEquals(tailwaterDepth, profile.points().getLast().depthMeters(), 1.0e-12);
        assertTrue(profile.maximumEnergyResidualMeters() < 1.0e-4);
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
