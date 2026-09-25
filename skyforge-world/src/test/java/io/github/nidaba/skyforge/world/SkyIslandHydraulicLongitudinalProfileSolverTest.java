package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandHydraulicLongitudinalProfileSolverTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void descendingTerrainProducesBoundedNonClimbingBedAndFreeSurface() {
        List<SkyIslandLocalPosition> route = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(5.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0),
                new SkyIslandLocalPosition(15.0, 0.0),
                new SkyIslandLocalPosition(20.0, 0.0));
        SkyIslandSemanticField terrain = position -> 0.75 - 0.006 * position.x();

        SkyIslandHydraulicProfileSolveResult result =
                SkyIslandHydraulicLongitudinalProfileSolver.solve(
                        route,
                        terrain,
                        100.0,
                        2.5,
                        5.0,
                        1.0,
                        0.6,
                        1.6,
                        0.80,
                        0.80);

        assertTrue(result.feasible());
        SkyIslandHydraulicLongitudinalProfile profile = result.profile().orElseThrow();
        assertEquals(route.size(), profile.stations().size());
        assertTrue(profile.maximumIncisionWorldUnits() <= 5.0 + EPSILON);
        assertTrue(profile.maximumPhysicalBedSlope() <= 0.80 + EPSILON);
        assertTrue(profile.maximumPhysicalWaterSlope() <= 0.80 + EPSILON);

        for (int i = 1; i < profile.stations().size(); i++) {
            SkyIslandHydraulicProfileStation previous = profile.stations().get(i - 1);
            SkyIslandHydraulicProfileStation current = profile.stations().get(i);
            assertTrue(current.bedElevation() <= previous.bedElevation() + EPSILON);
            assertTrue(current.waterSurfaceElevation() <= previous.waterSurfaceElevation() + EPSILON);
            assertTrue(current.waterDepthWorldUnits(profile.verticalReliefScale()) >= 0.6 - EPSILON);
            assertTrue(current.waterDepthWorldUnits(profile.verticalReliefScale()) <= 1.6 + EPSILON);
        }
    }

    @Test
    void incompatibleTerrainFailsClosedInsteadOfIncreasingIncision() {
        List<SkyIslandLocalPosition> route = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(5.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0),
                new SkyIslandLocalPosition(15.0, 0.0));
        SkyIslandSemanticField terrain = position -> {
            if (position.x() < 9.0) {
                return 0.55 - 0.002 * position.x();
            }
            return 0.78;
        };

        SkyIslandHydraulicProfileSolveResult result =
                SkyIslandHydraulicLongitudinalProfileSolver.solve(
                        route,
                        terrain,
                        100.0,
                        2.0,
                        4.0,
                        0.8,
                        0.5,
                        1.2,
                        0.35,
                        0.35);

        assertTrue(!result.feasible());
        assertEquals(
                SkyIslandHydraulicProfileFailureReason.BED_GRADE_INFEASIBLE,
                result.failureReason().orElseThrow());
        assertTrue(result.failureStation() >= 2);
    }

    @Test
    void resultIsDeterministic() {
        List<SkyIslandLocalPosition> route = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(4.0, 1.0),
                new SkyIslandLocalPosition(8.0, 1.0),
                new SkyIslandLocalPosition(12.0, 0.0));
        SkyIslandSemanticField terrain = position ->
                0.68 - 0.004 * position.x() + 0.002 * Math.abs(position.z());

        SkyIslandHydraulicProfileSolveResult first =
                SkyIslandHydraulicLongitudinalProfileSolver.solve(
                        route, terrain, 80.0, 1.44, 3.6, 0.64, 0.32, 1.12, 0.60, 0.60);
        SkyIslandHydraulicProfileSolveResult second =
                SkyIslandHydraulicLongitudinalProfileSolver.solve(
                        route, terrain, 80.0, 0.018, 0.045, 0.008, 0.004, 0.014, 0.60, 0.60);

        assertEquals(first, second);
    }

    @Test
    void shallowIncisionCanFailWaterDepthWithoutDeepeningBed() {
        List<SkyIslandLocalPosition> route = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(5.0, 0.0),
                new SkyIslandLocalPosition(10.0, 0.0));
        SkyIslandSemanticField terrain = position -> 0.60 - 0.002 * position.x();

        SkyIslandHydraulicProfileSolveResult result =
                SkyIslandHydraulicLongitudinalProfileSolver.solve(
                        route,
                        terrain,
                        100.0,
                        0.3,
                        0.4,
                        1.0,
                        0.8,
                        1.5,
                        1.0,
                        1.0);

        assertTrue(!result.feasible());
        assertEquals(
                SkyIslandHydraulicProfileFailureReason.WATER_SURFACE_INFEASIBLE,
                result.failureReason().orElseThrow());
    }
}
