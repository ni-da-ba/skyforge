package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandTerrainAwareRouteSolverTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void leastCostRouteUsesLowGapInsteadOfCuttingAcrossSyntheticRidge() {
        SkyIslandSemanticField terrain = position -> syntheticRidgeTerrain(position.x(), position.z());
        SkyIslandSemanticField interiority = ignored -> 1.0;
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(20.0, 0.0));

        SkyIslandGeomorphicRouteAnchor start =
                new SkyIslandGeomorphicRouteAnchor(new SkyIslandLocalPosition(0.0, 0.0), 1.25);
        SkyIslandGeomorphicRouteAnchor end =
                new SkyIslandGeomorphicRouteAnchor(new SkyIslandLocalPosition(20.0, 0.0), 1.25);

        SkyIslandGeomorphicCandidateRoute route = SkyIslandTerrainAwareRouteSolver.solve(
                terrain, interiority, guidance, 4.0, 6.0, start, end);
        SkyIslandGeomorphicCandidateRoute repeat = SkyIslandTerrainAwareRouteSolver.solve(
                terrain, interiority, guidance, 4.0, 6.0, start, end);

        assertEquals(route, repeat);
        assertTrue(start.contains(route.points().getFirst(), 0.75));
        assertTrue(end.contains(route.points().getLast(), 0.75));
        assertTrue(route.maxGuidanceDeviation() > 2.0, "route should leave the direct ridge-crossing line");
        assertTrue(route.maxGuidanceDeviation() <= 6.0 + EPSILON);

        SkyIslandLocalPosition nearRidge = route.points().stream()
                .min(Comparator.comparingDouble(point -> Math.abs(point.x() - 10.0)))
                .orElseThrow();
        assertTrue(
                terrain.sample(nearRidge) + 0.08 < terrain.sample(new SkyIslandLocalPosition(10.0, 0.0)),
                "route should find the low ridge gap instead of accepting the direct high crossing");
    }

    @Test
    void linearDownhillCorridorHasResolutionInvariantObjective() {
        double planningSpacing = 4.0;
        SkyIslandSemanticField terrain =
                position -> clamp01(0.70 - 0.01 * position.x());
        SkyIslandSemanticField interiority = ignored -> 1.0;
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(16.0, 0.0));
        SkyIslandGeomorphicRouteAnchor start =
                new SkyIslandGeomorphicRouteAnchor(
                        new SkyIslandLocalPosition(0.0, 0.0), 0.0);
        SkyIslandGeomorphicRouteAnchor end =
                new SkyIslandGeomorphicRouteAnchor(
                        new SkyIslandLocalPosition(16.0, 0.0), 0.0);

        SkyIslandGeomorphicCandidateRoute coarse =
                SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                        terrain,
                        interiority,
                        guidance,
                        planningSpacing,
                        2.0,
                        start,
                        end,
                        4);
        SkyIslandGeomorphicCandidateRoute medium =
                SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                        terrain,
                        interiority,
                        guidance,
                        planningSpacing,
                        2.0,
                        start,
                        end,
                        8);
        SkyIslandGeomorphicCandidateRoute fine =
                SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                        terrain,
                        interiority,
                        guidance,
                        planningSpacing,
                        2.0,
                        start,
                        end,
                        16);

        assertEquals(16.0, coarse.pathLength(), EPSILON);
        assertEquals(16.0, medium.pathLength(), EPSILON);
        assertEquals(16.0, fine.pathLength(), EPSILON);
        assertEquals(0.0, coarse.maxGuidanceDeviation(), EPSILON);
        assertEquals(0.0, medium.maxGuidanceDeviation(), EPSILON);
        assertEquals(0.0, fine.maxGuidanceDeviation(), EPSILON);
        assertEquals(coarse.totalCost(), medium.totalCost(), 1.0e-9);
        assertEquals(medium.totalCost(), fine.totalCost(), 1.0e-9);

        SkyIslandRouteFunctionalDiagnostics coarseDiagnostics =
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(
                        coarse, terrain, planningSpacing);
        SkyIslandRouteFunctionalDiagnostics mediumDiagnostics =
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(
                        medium, terrain, planningSpacing);
        SkyIslandRouteFunctionalDiagnostics fineDiagnostics =
                SkyIslandRouteFunctionalDiagnosticsPlanner.measure(
                        fine, terrain, planningSpacing);

        assertEquals(0.0, coarseDiagnostics.positiveElevationVariation(), EPSILON);
        assertEquals(0.0, mediumDiagnostics.positiveElevationVariation(), EPSILON);
        assertEquals(0.0, fineDiagnostics.positiveElevationVariation(), EPSILON);
        assertEquals(0.0, coarseDiagnostics.maximumUphillGrade(), EPSILON);
        assertEquals(0.0, mediumDiagnostics.maximumUphillGrade(), EPSILON);
        assertEquals(0.0, fineDiagnostics.maximumUphillGrade(), EPSILON);
        assertEquals(
                coarseDiagnostics.meanValleyFloorAdvantage(),
                mediumDiagnostics.meanValleyFloorAdvantage(),
                1.0e-10);
        assertEquals(
                mediumDiagnostics.meanValleyFloorAdvantage(),
                fineDiagnostics.meanValleyFloorAdvantage(),
                1.0e-10);
    }

    @Test
    void ridgeGapChoiceRemainsPhysicalAcrossSearchResolutions() {
        SkyIslandSemanticField terrain =
                position -> syntheticRidgeTerrain(position.x(), position.z());
        SkyIslandSemanticField interiority = ignored -> 1.0;
        List<SkyIslandLocalPosition> guidance = List.of(
                new SkyIslandLocalPosition(0.0, 0.0),
                new SkyIslandLocalPosition(20.0, 0.0));
        SkyIslandGeomorphicRouteAnchor start =
                new SkyIslandGeomorphicRouteAnchor(
                        new SkyIslandLocalPosition(0.0, 0.0), 0.0);
        SkyIslandGeomorphicRouteAnchor end =
                new SkyIslandGeomorphicRouteAnchor(
                        new SkyIslandLocalPosition(20.0, 0.0), 0.0);

        for (int divisions : new int[] {4, 8, 16}) {
            SkyIslandGeomorphicCandidateRoute route =
                    SkyIslandTerrainAwareRouteSolver.solveAtResolution(
                            terrain,
                            interiority,
                            guidance,
                            4.0,
                            6.0,
                            start,
                            end,
                            divisions);
            SkyIslandLocalPosition nearRidge = route.points().stream()
                    .min(Comparator.comparingDouble(
                            point -> Math.abs(point.x() - 10.0)))
                    .orElseThrow();
            assertTrue(
                    nearRidge.z() > 2.0,
                    "all resolutions should use the positive-z physical ridge gap");
            assertTrue(route.maxGuidanceDeviation() <= 6.0 + EPSILON);
        }
    }

    @Test
    void zeroRadiusGlobalLatticeAnchorsArePreservedExactly() {
        double planningSpacing = 4.0;
        double step = planningSpacing / SkyIslandTerrainAwareRouteSolver.FINE_DIVISIONS_PER_PLANNING_CELL;
        SkyIslandLocalPosition startPosition = new SkyIslandLocalPosition(-7 * step, 3 * step);
        SkyIslandLocalPosition endPosition = new SkyIslandLocalPosition(11 * step, -2 * step);
        SkyIslandSemanticField terrain = position -> clamp01(0.65 - 0.006 * position.x());
        SkyIslandSemanticField interiority = ignored -> 1.0;

        SkyIslandGeomorphicCandidateRoute route = SkyIslandTerrainAwareRouteSolver.solve(
                terrain,
                interiority,
                List.of(startPosition, endPosition),
                planningSpacing,
                5.0,
                new SkyIslandGeomorphicRouteAnchor(startPosition, 0.0),
                new SkyIslandGeomorphicRouteAnchor(endPosition, 0.0));

        assertEquals(startPosition, route.points().getFirst());
        assertEquals(endPosition, route.points().getLast());
    }

    @Test
    void routeDiagnosticsRemainFiniteAndBounded() {
        SkyIslandSemanticField terrain = position -> clamp01(0.70 - 0.012 * position.x());
        SkyIslandSemanticField interiority = ignored -> 1.0;
        SkyIslandGeomorphicCandidateRoute route = SkyIslandTerrainAwareRouteSolver.solve(
                terrain,
                interiority,
                List.of(
                        new SkyIslandLocalPosition(0.0, 0.0),
                        new SkyIslandLocalPosition(16.0, 0.0)),
                4.0,
                4.0,
                new SkyIslandGeomorphicRouteAnchor(new SkyIslandLocalPosition(0.0, 0.0), 1.25),
                new SkyIslandGeomorphicRouteAnchor(new SkyIslandLocalPosition(16.0, 0.0), 1.25));

        assertTrue(route.pathLength() > 0.0);
        assertTrue(route.totalCost() >= 0.0);
        assertTrue(route.uphillStepFraction() >= 0.0 && route.uphillStepFraction() <= 1.0);
        assertTrue(route.ridgeSampleFraction() >= 0.0 && route.ridgeSampleFraction() <= 1.0);
        assertTrue(Double.isFinite(route.meanValleyFloorAdvantage()));
        assertTrue(route.maxUphillStep() >= 0.0);
    }

    private static double syntheticRidgeTerrain(double x, double z) {
        double base = 0.68 - 0.008 * x;
        double ridgeAcrossX = Math.exp(-square((x - 10.0) / 1.35));
        double gapAtPositiveZ = Math.exp(-square((z - 4.0) / 1.15));
        double ridge = 0.25 * ridgeAcrossX * (1.0 - 0.94 * gapAtPositiveZ);
        return clamp01(base + ridge);
    }

    private static double square(double value) {
        return value * value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
