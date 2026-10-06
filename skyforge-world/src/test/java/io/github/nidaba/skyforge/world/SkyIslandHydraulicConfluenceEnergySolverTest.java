package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicConfluenceEnergySolverTest {
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(0.035, 1.0, 9.81, 1.0e-8, 160);

    @Test
    void closesTwoIncomingBranchesByDischargeWeightedEnergyAndExplicitLoss() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0));

        var result = SkyIslandHydraulicConfluenceEnergySolver.solve(
                incoming, section(2.0, 0.0, 2.0), PARAMETERS, 0.1);

        assertEquals(2.0, result.totalDischargeCubicMetersPerSecond(), 0.0);
        assertTrue(result.downstreamDepthMeters() > 0.0);
        assertTrue(result.downstreamFroudeNumber() < 1.0);
        assertTrue(result.junctionLossMeters() > 0.0);
        assertTrue(result.energyResidualMeters() < 1.0e-6);

        double downstreamTotalHead = result.downstreamWaterSurfaceElevationMeters()
                + PARAMETERS.energyCoefficient()
                        * result.downstreamVelocityMetersPerSecond()
                        * result.downstreamVelocityMetersPerSecond()
                        / (2.0 * PARAMETERS.gravityMetersPerSecondSquared());
        assertEquals(
                result.dischargeWeightedIncomingTotalHeadMeters(),
                downstreamTotalHead + result.junctionLossMeters(),
                1.0e-6);
    }

    @Test
    void weightsIncomingTotalHeadByBranchDischarge() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 0.8),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.4, 2.0), 1.2));

        var result = SkyIslandHydraulicConfluenceEnergySolver.solve(
                incoming, section(2.0, 0.0, 3.0), PARAMETERS, 0.1);

        double firstHead = totalHead(incoming.get(0));
        double secondHead = totalHead(incoming.get(1));
        double dischargeWeightedHead = (firstHead + 2.0 * secondHead) / 3.0;
        assertEquals(
                dischargeWeightedHead,
                result.dischargeWeightedIncomingTotalHeadMeters(),
                1.0e-12);
        assertTrue(
                Math.abs(result.dischargeWeightedIncomingTotalHeadMeters()
                                - 0.5 * (firstHead + secondHead))
                        > 1.0e-3);
        assertTrue(result.energyResidualMeters() < 1.0e-6);
    }

    @Test
    void zeroLossCoefficientProducesNoJunctionLoss() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0));

        var result = SkyIslandHydraulicConfluenceEnergySolver.solve(
                incoming, section(2.0, 0.0, 2.0), PARAMETERS, 0.0);

        assertEquals(0.0, result.junctionLossMeters(), 0.0);
        assertTrue(result.energyResidualMeters() < 1.0e-6);
    }

    @Test
    void isInvariantToUniformVerticalDatumTranslation() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0));
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> shiftedIncoming =
                incoming.stream()
                        .map(state -> new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                                section(
                                        state.section().chainageMeters(),
                                        state.section().bedElevationMeters() + 17.5,
                                        state.section().dischargeCubicMetersPerSecond()),
                                state.depthMeters()))
                        .toList();

        var baseline = SkyIslandHydraulicConfluenceEnergySolver.solve(
                incoming, section(2.0, 0.0, 2.0), PARAMETERS, 0.1);
        var shifted = SkyIslandHydraulicConfluenceEnergySolver.solve(
                shiftedIncoming, section(2.0, 17.5, 2.0), PARAMETERS, 0.1);

        assertEquals(baseline.downstreamDepthMeters(), shifted.downstreamDepthMeters(), 1.0e-9);
        assertEquals(
                baseline.downstreamWaterSurfaceElevationMeters() + 17.5,
                shifted.downstreamWaterSurfaceElevationMeters(),
                1.0e-9);
        assertEquals(
                baseline.dischargeWeightedIncomingTotalHeadMeters() + 17.5,
                shifted.dischargeWeightedIncomingTotalHeadMeters(),
                1.0e-9);
        assertEquals(baseline.junctionLossMeters(), shifted.junctionLossMeters(), 1.0e-9);
        assertEquals(baseline.energyResidualMeters(), shifted.energyResidualMeters(), 1.0e-9);
    }

    @Test
    void rejectsNonConservingJunctionDischarge() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandHydraulicConfluenceEnergySolver.solve(
                        incoming, section(2.0, 0.0, 1.9), PARAMETERS, 0.1));
    }

    @Test
    void rejectsSupercriticalIncomingBranchWithoutMomentumMatchedJunctionTreatment() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 0.05),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 1.0), 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandHydraulicConfluenceEnergySolver.solve(
                        incoming, section(2.0, 0.0, 2.0), PARAMETERS, 0.1));
    }

    @Test
    void rejectsJunctionWithoutASubcriticalEnergyRoot() {
        List<SkyIslandHydraulicConfluenceEnergySolver.IncomingState> incoming = List.of(
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 0.1), 0.5),
                new SkyIslandHydraulicConfluenceEnergySolver.IncomingState(
                        section(0.0, 0.0, 0.1), 0.5));

        assertThrows(
                IllegalStateException.class,
                () -> SkyIslandHydraulicConfluenceEnergySolver.solve(
                        incoming, section(2.0, 1.0, 0.2), PARAMETERS, 0.1));
    }

    private static double totalHead(
            SkyIslandHydraulicConfluenceEnergySolver.IncomingState state) {
        var section = state.section();
        double area = state.depthMeters()
                * (section.bottomWidthMeters()
                        + section.sideSlopeHorizontalToVertical() * state.depthMeters());
        double velocity = section.dischargeCubicMetersPerSecond() / area;
        return section.bedElevationMeters() + state.depthMeters()
                + PARAMETERS.energyCoefficient() * velocity * velocity
                        / (2.0 * PARAMETERS.gravityMetersPerSecondSquared());
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bedElevation, double discharge) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bedElevation, discharge, 2.0, 0.0);
    }
}
