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
    void closesIncomingBranchesThroughTailwaterControlledOutletAndIsDatumInvariant() {
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE, ROUGHNESS, BED_SLOPE, WIDTH, 0.0);
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)));
        double outletWidth = 2.0 * WIDTH;
        double outletDischarge = 2.0 * DISCHARGE;
        double outletSlope = normalSlopeMetersPerMeter(
                outletDischarge, outletWidth, normalDepth);
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> outlet =
                List.of(section(200.0, 0.0, outletDischarge, outletWidth),
                        section(250.0, -50.0 * outletSlope, outletDischarge, outletWidth),
                        section(300.0, -100.0 * outletSlope, outletDischarge, outletWidth));

        var result = SkyIslandHydraulicEnergyConfluenceComponentSolver.solveWithSubcriticalTailwater(
                incoming, outlet, normalDepth, PARAMETERS, 0.0);

        assertEquals(normalDepth, result.junctionDepthMeters(), 1.0e-6);
        assertEquals(normalDepth,
                result.outletProfile().points().getLast().depthMeters(), 1.0e-6);
        assertTrue(result.maximumEnergyResidualMeters() < 1.0e-7);
        assertEquals(2, result.confluence().incomingProfiles().size());

        double datumShift = 123.45;
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> shiftedIncoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH, datumShift)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH, datumShift)));
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> shiftedOutlet =
                List.of(section(200.0, datumShift, outletDischarge, outletWidth),
                        section(250.0, datumShift - 50.0 * outletSlope,
                                outletDischarge, outletWidth),
                        section(300.0, datumShift - 100.0 * outletSlope,
                                outletDischarge, outletWidth));
        var shifted = SkyIslandHydraulicEnergyConfluenceComponentSolver.solveWithSubcriticalTailwater(
                shiftedIncoming, shiftedOutlet, normalDepth, PARAMETERS, 0.0);

        assertEquals(result.junctionDepthMeters(), shifted.junctionDepthMeters(), 1.0e-6);
        assertEquals(
                result.confluence().confluence().downstreamDepthMeters(),
                shifted.confluence().confluence().downstreamDepthMeters(),
                1.0e-6);
        assertTrue(shifted.maximumEnergyResidualMeters() < 1.0e-7);
    }

    @Test
    void rejectsSupercriticalTailwaterInsteadOfTreatingItAsSubcriticalControl() {
        double normalDepth = SkyIslandManningHydraulics.normalDepthMeters(
                DISCHARGE, ROUGHNESS, BED_SLOPE, WIDTH, 0.0);
        List<SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach> incoming =
                List.of(new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)),
                        new SkyIslandHydraulicEnergyConfluenceComponentSolver.IncomingReach(
                                branchSections(DISCHARGE, WIDTH)));
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> outlet =
                List.of(section(200.0, 0.0, 2.0 * DISCHARGE, 2.0 * WIDTH),
                        section(250.0, -0.05, 2.0 * DISCHARGE, 2.0 * WIDTH));

        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicEnergyConfluenceComponentSolver
                        .solveWithSubcriticalTailwater(
                                incoming, outlet, normalDepth * 0.1, PARAMETERS, 0.0));
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

    private static double normalSlopeMetersPerMeter(
            double discharge, double width, double depth) {
        double area = width * depth;
        double hydraulicRadius = area / (width + 2.0 * depth);
        double conveyance = area * Math.pow(hydraulicRadius, 2.0 / 3.0) / ROUGHNESS;
        return Math.pow(discharge / conveyance, 2.0);
    }

    private static List<SkyIslandGraduallyVariedFlowSolver.CrossSection> branchSections(
            double discharge, double width) {
        return branchSections(discharge, width, 0.0);
    }

    private static List<SkyIslandGraduallyVariedFlowSolver.CrossSection> branchSections(
            double discharge, double width, double datumShift) {
        return List.of(
                section(0.0, datumShift + 0.1, discharge, width),
                section(50.0, datumShift + 0.05, discharge, width),
                section(100.0, datumShift, discharge, width));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double discharge, double width) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, discharge, width, 0.0);
    }
}
