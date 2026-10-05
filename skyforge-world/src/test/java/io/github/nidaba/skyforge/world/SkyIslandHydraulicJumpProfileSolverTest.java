package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicJumpProfileSolverTest {
    private static final SkyIslandGraduallyVariedFlowSolver.Parameters PARAMETERS =
            new SkyIslandGraduallyVariedFlowSolver.Parameters(0.035, 1.0, 9.81, 1.0e-8, 160);

    @Test
    void refusesSubcriticalSourceWithoutInventingAJump() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 10.0, 0.0),
                section(10.0, 9.99, 0.0),
                section(20.0, 9.98, 0.0),
                section(30.0, 9.97, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> SkyIslandHydraulicJumpProfileSolver.solve(sections, 1.0, PARAMETERS));
    }

    @Test
    void joinsSupercriticalSourceToCriticalTailwaterThroughAdmissibleJump() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 4.0, 0.0),
                section(10.0, 3.99, 0.0),
                section(20.0, 3.98, 0.0),
                section(30.0, 3.97, 0.0),
                section(40.0, 3.96, 0.0));

        var profile = SkyIslandHydraulicJumpProfileSolver.solve(sections, 0.001, PARAMETERS);

        assertEquals(sections, profile.points().stream()
                .map(SkyIslandGraduallyVariedFlowSolver.ProfilePoint::section)
                .toList());
        assertTrue(profile.points().getFirst().froudeNumber() > 1.0);
        assertTrue(Math.abs(profile.points().getLast().froudeNumber() - 1.0) < 1.0e-6);
        int firstSubcritical = -1;
        for (int i = 1; i < profile.points().size() - 1; i++) {
            double froude = profile.points().get(i).froudeNumber();
            if (froude < 1.0) {
                if (firstSubcritical < 0) {
                    firstSubcritical = i;
                }
            } else {
                assertTrue(firstSubcritical < 0,
                        "the joined profile must not return to the supercritical branch");
            }
        }
        assertTrue(firstSubcritical > 0,
                "the momentum-matched jump must leave at least one subcritical interior section");
        assertTrue(profile.maximumEnergyResidualMeters() < 1.0e-4);
    }

    @Test
    void joinsToAnExplicitSubcriticalTailwaterDepth() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 4.0, 0.0),
                section(10.0, 3.99, 0.0),
                section(20.0, 3.98, 0.0),
                section(30.0, 3.97, 0.0),
                section(40.0, 3.96, 0.0));
        var criticalProfile =
                SkyIslandHydraulicJumpProfileSolver.solve(sections, 0.001, PARAMETERS);
        double tailwaterDepth =
                criticalProfile.points().getLast().depthMeters() + 1.0e-4;

        var profile = SkyIslandHydraulicJumpProfileSolver.solveToTailwater(
                sections, 0.001, tailwaterDepth, PARAMETERS);

        assertEquals(sections, profile.points().stream()
                .map(SkyIslandGraduallyVariedFlowSolver.ProfilePoint::section)
                .toList());
        assertEquals(tailwaterDepth, profile.points().getLast().depthMeters(), 1.0e-12);
        assertTrue(profile.points().getFirst().froudeNumber() > 1.0);
        assertTrue(profile.points().getLast().froudeNumber() < 1.0);
        assertTrue(profile.maximumEnergyResidualMeters() < 1.0e-4);
    }

    @Test
    void findsAJumpInTheLastInteriorIntervalBeforeTheCriticalControl() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 4.0, 0.0),
                section(10.0, 3.99, 0.0),
                section(20.0, 3.98, 0.0),
                section(30.0, 3.78, 0.0));

        var profile = SkyIslandHydraulicJumpProfileSolver.solve(sections, 0.001, PARAMETERS);

        assertEquals(sections, profile.points().stream()
                .map(SkyIslandGraduallyVariedFlowSolver.ProfilePoint::section)
                .toList());
        assertTrue(profile.points().get(0).froudeNumber() > 1.0);
        assertTrue(profile.points().get(2).froudeNumber() > 1.0,
                "the jump must be located downstream of the penultimate cross section");
        assertTrue(Math.abs(profile.points().getLast().froudeNumber() - 1.0) < 1.0e-6);
        assertTrue(profile.maximumEnergyResidualMeters() < 1.0e-4);
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double sideSlope) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, 1.0, 8.0, sideSlope);
    }
}
