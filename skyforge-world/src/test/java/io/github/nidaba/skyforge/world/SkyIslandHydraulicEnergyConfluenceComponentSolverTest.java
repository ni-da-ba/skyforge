package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicEnergyConfluenceComponentSolverTest {
    private static final double ROUGHNESS = 0.035;
    private static final double DISCHARGE = 1.0;
    private static final double WIDTH = 2.0;
    private static final double BED_SLOPE = 0.001;
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(
                    ROUGHNESS, 1.0, 9.81, 1.0e-8, 160);

    @Test
    void closesSourceControlledBranchesAgainstSharedWaterSurfaceAndOutletEnergy() {
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE, ROUGHNESS, BED_SLOPE, WIDTH, 0.0);
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)));
        SkyIslandGraduallyVariedFlowSolver.CrossSection outlet =
                section(100.0, 0.0, 2.0 * DISCHARGE, 2.0 * WIDTH);

        var result = SkyIslandHydraulicEnergyConfluenceComponentSolver.solve(
                incoming, outlet, normalDepth, PARAMETERS, 0.0);

        assertEquals(2, result.incomingProfiles().size());
        assertEquals(2.0 * DISCHARGE,
                result.confluence().totalDischargeCubicMetersPerSecond(), 0.0);
        assertEquals(normalDepth, result.confluence().downstreamDepthMeters(), 1.0e-6);
        assertTrue(result.junctionDepthResidualMeters() < 1.0e-6);
        assertTrue(result.maximumReachEnergyResidualMeters() < 1.0e-7);
        for (var profile : result.incomingProfiles()) {
            assertEquals(normalDepth, profile.points().getFirst().depthMeters(), 1.0e-6);
            assertEquals(normalDepth, profile.points().getLast().depthMeters(), 1.0e-6);
        }
    }

    @Test
    void rejectsOutletDepthIncompatibleWithSourceControls() {
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE, ROUGHNESS, BED_SLOPE, WIDTH, 0.0);
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)));
        SkyIslandGraduallyVariedFlowSolver.CrossSection outlet =
                section(100.0, 0.0, 2.0 * DISCHARGE, 2.0 * WIDTH);

        assertThrows(IllegalStateException.class,
                () -> SkyIslandHydraulicEnergyConfluenceComponentSolver.solve(
                        incoming, outlet, normalDepth + 0.2, PARAMETERS, 0.0));
    }

    @Test
    void rejectsOutletThatViolatesMassContinuity() {
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE, ROUGHNESS, BED_SLOPE, WIDTH, 0.0);
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)));
        SkyIslandGraduallyVariedFlowSolver.CrossSection outlet =
                section(100.0, 0.0, 1.9, 2.0 * WIDTH);

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicEnergyConfluenceComponentSolver.solve(
                        incoming, outlet, normalDepth, PARAMETERS, 0.0));
    }

    private static List<SkyIslandGraduallyVariedFlowSolver.CrossSection> branchSections(
            double discharge, double width) {
        return List.of(
                section(0.0, 0.1, discharge, width),
                section(50.0, 0.05, discharge, width),
                section(100.0, 0.0, discharge, width));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double discharge, double width) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, discharge, width, 0.0);
    }
}
