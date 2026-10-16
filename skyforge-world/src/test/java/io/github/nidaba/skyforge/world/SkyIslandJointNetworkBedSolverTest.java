package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandJointNetworkBedSolverTest {
    @Test
    void sharesOneTerrainSupportedBedAtTwoTributaryConfluence() {
        var upper = reach(
                0.10,
                station("upper-source", 0.0, 10.0, 0.0),
                station("join", 10.0, 9.5, 2.0));
        var tributary = reach(
                0.21,
                station("tributary-source", 0.0, 9.8, 0.0),
                station("join", 10.0, 9.5, 2.5));
        var receiving = reach(
                0.10,
                station("join", 0.0, 9.5, 2.0),
                station("outlet", 10.0, 6.5, 0.0));

        var result = SkyIslandJointNetworkBedSolver.solve(
                List.of(upper, tributary, receiving));

        assertEquals(
                List.of("join", "outlet", "tributary-source", "upper-source"),
                List.copyOf(result.bedElevationsByNodeMetres().keySet()));
        double sharedBed = result.bedElevationsByNodeMetres().get("join");
        assertEquals(7.7, sharedBed, 1.0e-12);
        assertEquals(
                sharedBed,
                result.reaches().get(0).bedElevationsMetres()[1],
                0.0);
        assertEquals(
                sharedBed,
                result.reaches().get(1).bedElevationsMetres()[1],
                0.0);
        assertEquals(
                sharedBed,
                result.reaches().get(2).bedElevationsMetres()[0],
                0.0);
        assertEquals(1.8, result.maximumTerrainLoweringMetres(), 1.0e-12);
        assertMinimumGrade(result.reaches().get(0), 0.10);
        assertMinimumGrade(result.reaches().get(1), 0.21);
        assertMinimumGrade(result.reaches().get(2), 0.10);
    }

    @Test
    void failsClosedWhenTerrainEditEnvelopeCannotSupportRequiredGrade() {
        var impossible = reach(
                0.10,
                station("source", 0.0, 5.0, 0.0),
                station("outlet", 10.0, 5.0, 0.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandJointNetworkBedSolver.solve(List.of(impossible)));
    }

    @Test
    void rejectsDifferentTerrainElevationsForOneSharedPhysicalNode() {
        var first = reach(
                0.01,
                station("a", 0.0, 5.0, 0.0),
                station("join", 1.0, 4.0, 1.0));
        var second = reach(
                0.01,
                station("b", 0.0, 6.0, 0.0),
                station("join", 1.0, 4.01, 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandJointNetworkBedSolver.solve(List.of(first, second)));
    }

    @Test
    void rejectsCyclicDrainageBedConstraints() {
        var first = reach(
                0.01,
                station("a", 0.0, 5.0, 1.0),
                station("b", 1.0, 4.0, 1.0));
        var second = reach(
                0.01,
                station("b", 0.0, 4.0, 1.0),
                station("a", 1.0, 5.0, 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> SkyIslandJointNetworkBedSolver.solve(List.of(first, second)));
    }

    private static SkyIslandJointNetworkBedSolver.Reach reach(
            double minimumGrade,
            SkyIslandJointNetworkBedSolver.Station first,
            SkyIslandJointNetworkBedSolver.Station second) {
        return new SkyIslandJointNetworkBedSolver.Reach(
                List.of(first, second), minimumGrade);
    }

    private static SkyIslandJointNetworkBedSolver.Station station(
            String id, double station, double terrain, double lowering) {
        return new SkyIslandJointNetworkBedSolver.Station(id, station, terrain, lowering);
    }

    private static void assertMinimumGrade(
            SkyIslandJointNetworkBedSolver.ReachProfile profile,
            double minimumGrade) {
        double[] stations = profile.stationMetres();
        double[] beds = profile.bedElevationsMetres();
        assertTrue(
                beds[0] - beds[1] >= minimumGrade * (stations[1] - stations[0]) - 1.0e-12);
    }
}
