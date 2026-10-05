package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void failsClosedWhenNoMomentumMatchedJumpConnectsTheControls() {
        List<SkyIslandGraduallyVariedFlowSolver.CrossSection> sections = List.of(
                section(0.0, 4.0, 0.0),
                section(10.0, 3.99, 0.0),
                section(20.0, 3.98, 0.0),
                section(30.0, 3.97, 0.0),
                section(40.0, 3.96, 0.0));
        assertThrows(IllegalStateException.class,
                () -> SkyIslandHydraulicJumpProfileSolver.solve(sections, 0.001, PARAMETERS));
    }

    private static SkyIslandGraduallyVariedFlowSolver.CrossSection section(
            double chainage, double bed, double sideSlope) {
        return new SkyIslandGraduallyVariedFlowSolver.CrossSection(
                chainage, bed, 1.0, 8.0, sideSlope);
    }
}
