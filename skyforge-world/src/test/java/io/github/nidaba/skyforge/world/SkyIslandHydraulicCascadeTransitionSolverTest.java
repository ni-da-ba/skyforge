package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicCascadeTransitionSolverTest {
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(
                    0.035, 1.0, 9.81, 1.0e-8, 160);

    @Test
    void criticalInletPropagatesAConvergedSupercriticalChuteProfile() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 8.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(20.0, 6.0, 1.0, 2.0, 0.5));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInlet(
                        sections, PARAMETERS);

        assertEquals(1.0, result.points().getFirst().froudeNumber(), 1.0e-6);
        assertTrue(result.points().getLast().froudeNumber() > 1.0);
        assertTrue(result.maximumEnergyResidualMeters() <= 1.0e-6);
    }

    @Test
    void sourceControlledSupercriticalFlowContinuesAcrossItsCascadeLip() {
        var lip = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                20.0, 8.0, 1.0, 2.0, 0.5);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstream = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 9.0, 1.0, 2.0, 0.5),
                lip);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> chute = List.of(
                lip,
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(25.0, 6.5, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(30.0, 5.0, 1.0, 2.0, 0.5));
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                1.0, 0.035, 0.1, 2.0, 0.5);

        var result = SkyIslandHydraulicCascadeTransitionSolver.solveSourceControlledCascade(
                upstream, chute, normalDepth, PARAMETERS);

        assertFalse(result.criticalControlAtCascadeInlet());
        assertEquals(normalDepth, result.upstreamProfile().points().getFirst().depthMeters(), 0.0);
        assertEquals(
                result.upstreamProfile().points().getLast().depthMeters(),
                result.cascadeProfile().points().getFirst().depthMeters(),
                0.0);
        assertTrue(result.upstreamProfile().points().getLast().froudeNumber() > 1.0);
        assertTrue(result.cascadeProfile().points().stream()
                .allMatch(point -> point.froudeNumber() > 1.0));
        assertTrue(result.upstreamProfile().maximumEnergyResidualMeters() <= 1.0e-6);
        assertTrue(result.cascadeProfile().maximumEnergyResidualMeters() <= 1.0e-6);
    }

    @Test
    void sourceControlledCascadeCanonicalizesNumericallyEquivalentLipSections() {
        var sourceLip = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                20.0, 8.0, 1.0, 2.0, 0.5);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstream = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 9.0, 1.0, 2.0, 0.5),
                sourceLip);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> chute = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        20.0 + 1.0e-9, 8.0 + 1.0e-9, 1.0 + 1.0e-9,
                        2.0 + 1.0e-9, 0.5 + 1.0e-9),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        25.0, 6.5, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        30.0, 5.0, 1.0, 2.0, 0.5));
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                1.0, 0.035, 0.1, 2.0, 0.5);

        var result = SkyIslandHydraulicCascadeTransitionSolver.solveSourceControlledCascade(
                upstream, chute, normalDepth, PARAMETERS);

        assertEquals(sourceLip, result.upstreamProfile().points().getLast().section());
        assertEquals(sourceLip, result.cascadeProfile().points().getFirst().section());
        assertEquals(
                result.upstreamProfile().points().getLast().depthMeters(),
                result.cascadeProfile().points().getFirst().depthMeters(),
                0.0);
    }

    @Test
    void sourceControlledCascadeRejectsMateriallyDifferentLipDischarge() {
        var sourceLip = new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                20.0, 8.0, 1.0, 2.0, 0.5);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> upstream = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 9.0, 1.0, 2.0, 0.5),
                sourceLip);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> chute = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        20.0, 8.0, 1.01, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        25.0, 6.5, 1.0, 2.0, 0.5));
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                1.0, 0.035, 0.1, 2.0, 0.5);

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicCascadeTransitionSolver.solveSourceControlledCascade(
                        upstream, chute, normalDepth, PARAMETERS));
    }

    @Test
    void supercriticalInletContinuesWithoutInventingCriticalControl() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 8.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(20.0, 6.0, 1.0, 2.0, 0.5));

        SkyIslandGraduallyVariedFlowSolver.Result result =
                SkyIslandHydraulicCascadeTransitionSolver.solveFromSupercriticalInlet(
                        sections, 0.1, PARAMETERS);

        assertEquals(0.1, result.points().getFirst().depthMeters(), 0.0);
        assertTrue(result.points().stream().allMatch(point -> point.froudeNumber() > 1.0));
        assertTrue(result.maximumEnergyResidualMeters() <= 1.0e-6);
    }

    @Test
    void supercriticalInletContinuationRejectsSubcriticalBoundary() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(0.0, 10.0, 1.0, 2.0, 0.5),
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(10.0, 8.0, 1.0, 2.0, 0.5));

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicCascadeTransitionSolver.solveFromSupercriticalInlet(
                        sections, 1.0, PARAMETERS));
    }

    @Test
    void chuteRequiresAtLeastTwoSections() {
        SkyIslandGraduallyVariedFlowSolver.CrossSection section =
                new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                        0.0, 0.0, 1.0, 2.0, 0.5);

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandHydraulicCascadeTransitionSolver.solveFromCriticalInlet(
                        List.of(section), PARAMETERS));
    }
}
