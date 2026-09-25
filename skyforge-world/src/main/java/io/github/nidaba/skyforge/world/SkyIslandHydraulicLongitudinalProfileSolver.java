package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Solves a bounded ordinary-channel longitudinal bed and water surface without terrain rescue.
 *
 * <p>Vertical shape parameters are supplied in physical Skyforge world-height units and converted
 * internally through the descriptor relief scale. This keeps an authored depth or incision budget
 * physically comparable across islands with different normalized relief ranges.
 *
 * <p>The solver treats hydraulic realization as a feasibility problem. At every route station the
 * bed must stay below the pre-fluvial terrain, within a fixed maximum incision allowance,
 * non-climbing downstream, and below a maximum physical fall slope. Water depth must remain inside
 * explicit physical bounds and the free surface obeys the same downstream monotonicity/maximum-slope
 * discipline.
 *
 * <p>If those intervals become incompatible, the solver fails closed. It never deepens the allowed
 * cut, moves the route, or delegates the conflict to a backend.
 */
public final class SkyIslandHydraulicLongitudinalProfileSolver {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandHydraulicLongitudinalProfileSolver() {}

    public static SkyIslandHydraulicProfileSolveResult solve(
            List<SkyIslandLocalPosition> route,
            SkyIslandSemanticField terrain,
            double verticalReliefScale,
            double targetIncisionWorldUnits,
            double maximumIncisionWorldUnits,
            double targetWaterDepthWorldUnits,
            double minimumWaterDepthWorldUnits,
            double maximumWaterDepthWorldUnits,
            double maximumPhysicalBedSlope,
            double maximumPhysicalWaterSlope) {
        route = List.copyOf(route);
        route.forEach(point -> Objects.requireNonNull(point, "route point"));
        Objects.requireNonNull(terrain, "terrain");
        if (route.size() < 2) {
            throw new IllegalArgumentException("route requires at least two points");
        }

        requirePositive(verticalReliefScale, "verticalReliefScale");
        requireNonNegative(targetIncisionWorldUnits, "targetIncisionWorldUnits");
        requireNonNegative(maximumIncisionWorldUnits, "maximumIncisionWorldUnits");
        if (targetIncisionWorldUnits > maximumIncisionWorldUnits + EPSILON) {
            throw new IllegalArgumentException("target incision cannot exceed maximum incision");
        }

        requirePositive(targetWaterDepthWorldUnits, "targetWaterDepthWorldUnits");
        requirePositive(minimumWaterDepthWorldUnits, "minimumWaterDepthWorldUnits");
        requirePositive(maximumWaterDepthWorldUnits, "maximumWaterDepthWorldUnits");
        if (minimumWaterDepthWorldUnits > targetWaterDepthWorldUnits + EPSILON
                || targetWaterDepthWorldUnits > maximumWaterDepthWorldUnits + EPSILON) {
            throw new IllegalArgumentException("water depth bounds must contain target depth");
        }

        requireNonNegative(maximumPhysicalBedSlope, "maximumPhysicalBedSlope");
        requireNonNegative(maximumPhysicalWaterSlope, "maximumPhysicalWaterSlope");

        double targetIncisionPotential = targetIncisionWorldUnits / verticalReliefScale;
        double maximumIncisionPotential = maximumIncisionWorldUnits / verticalReliefScale;
        double targetWaterDepthPotential = targetWaterDepthWorldUnits / verticalReliefScale;
        double minimumWaterDepthPotential = minimumWaterDepthWorldUnits / verticalReliefScale;
        double maximumWaterDepthPotential = maximumWaterDepthWorldUnits / verticalReliefScale;

        int count = route.size();
        double[] distance = cumulativeDistance(route);
        double[] terrainElevation = new double[count];
        double[] targetBed = new double[count];
        double[] bedLower = new double[count];
        double[] bedUpper = new double[count];

        for (int i = 0; i < count; i++) {
            terrainElevation[i] = clamp01(terrain.sample(route.get(i)));
            bedLower[i] = Math.max(0.0, terrainElevation[i] - maximumIncisionPotential);
            bedUpper[i] = terrainElevation[i];
            targetBed[i] = clamp(
                    terrainElevation[i] - targetIncisionPotential,
                    bedLower[i],
                    bedUpper[i]);
        }

        FeasibleIntervals bedFeasible = propagate(
                bedLower,
                bedUpper,
                distance,
                verticalReliefScale,
                maximumPhysicalBedSlope);
        if (!bedFeasible.feasible()) {
            return SkyIslandHydraulicProfileSolveResult.failure(
                    SkyIslandHydraulicProfileFailureReason.BED_GRADE_INFEASIBLE,
                    bedFeasible.failureStation());
        }

        double[] bed = backtrack(
                targetBed,
                bedFeasible.lower(),
                bedFeasible.upper(),
                distance,
                verticalReliefScale,
                maximumPhysicalBedSlope);

        double[] surfaceLower = new double[count];
        double[] surfaceUpper = new double[count];
        double[] targetSurface = new double[count];

        for (int i = 0; i < count; i++) {
            surfaceLower[i] = bed[i] + minimumWaterDepthPotential;
            surfaceUpper[i] = Math.min(
                    terrainElevation[i],
                    bed[i] + maximumWaterDepthPotential);

            if (surfaceLower[i] > surfaceUpper[i] + EPSILON) {
                return SkyIslandHydraulicProfileSolveResult.failure(
                        SkyIslandHydraulicProfileFailureReason.WATER_SURFACE_INFEASIBLE,
                        i);
            }

            targetSurface[i] = clamp(
                    bed[i] + targetWaterDepthPotential,
                    surfaceLower[i],
                    surfaceUpper[i]);
        }

        FeasibleIntervals surfaceFeasible = propagate(
                surfaceLower,
                surfaceUpper,
                distance,
                verticalReliefScale,
                maximumPhysicalWaterSlope);
        if (!surfaceFeasible.feasible()) {
            return SkyIslandHydraulicProfileSolveResult.failure(
                    SkyIslandHydraulicProfileFailureReason.WATER_SURFACE_INFEASIBLE,
                    surfaceFeasible.failureStation());
        }

        double[] surface = backtrack(
                targetSurface,
                surfaceFeasible.lower(),
                surfaceFeasible.upper(),
                distance,
                verticalReliefScale,
                maximumPhysicalWaterSlope);

        List<SkyIslandHydraulicProfileStation> stations = new ArrayList<>(count);
        double observedBedSlope = 0.0;
        double observedWaterSlope = 0.0;
        double observedIncisionWorldUnits = 0.0;

        for (int i = 0; i < count; i++) {
            SkyIslandHydraulicProfileStation station = new SkyIslandHydraulicProfileStation(
                    route.get(i),
                    distance[i],
                    terrainElevation[i],
                    bed[i],
                    surface[i]);
            stations.add(station);
            observedIncisionWorldUnits = Math.max(
                    observedIncisionWorldUnits,
                    station.incisionWorldUnits(verticalReliefScale));

            if (i > 0) {
                double horizontalDistance = distance[i] - distance[i - 1];
                if (horizontalDistance <= EPSILON) {
                    throw new IllegalArgumentException("route contains coincident consecutive points");
                }

                observedBedSlope = Math.max(
                        observedBedSlope,
                        (bed[i - 1] - bed[i])
                                * verticalReliefScale
                                / horizontalDistance);
                observedWaterSlope = Math.max(
                        observedWaterSlope,
                        (surface[i - 1] - surface[i])
                                * verticalReliefScale
                                / horizontalDistance);
            }
        }

        return SkyIslandHydraulicProfileSolveResult.success(
                new SkyIslandHydraulicLongitudinalProfile(
                        stations,
                        verticalReliefScale,
                        observedBedSlope,
                        observedWaterSlope,
                        observedIncisionWorldUnits));
    }

    private static FeasibleIntervals propagate(
            double[] localLower,
            double[] localUpper,
            double[] distance,
            double verticalReliefScale,
            double maximumPhysicalSlope) {
        int count = localLower.length;
        double[] lower = new double[count];
        double[] upper = new double[count];

        lower[0] = localLower[0];
        upper[0] = localUpper[0];
        if (lower[0] > upper[0] + EPSILON) {
            return FeasibleIntervals.failure(0);
        }

        for (int i = 1; i < count; i++) {
            double horizontalDistance = distance[i] - distance[i - 1];
            if (horizontalDistance <= EPSILON) {
                throw new IllegalArgumentException("route contains coincident consecutive points");
            }

            double maximumFallPotential =
                    maximumPhysicalSlope * horizontalDistance / verticalReliefScale;

            double reachableLower = lower[i - 1] - maximumFallPotential;
            double reachableUpper = upper[i - 1];

            lower[i] = Math.max(localLower[i], reachableLower);
            upper[i] = Math.min(localUpper[i], reachableUpper);

            if (lower[i] > upper[i] + EPSILON) {
                return FeasibleIntervals.failure(i);
            }
        }

        return FeasibleIntervals.success(lower, upper);
    }

    private static double[] backtrack(
            double[] target,
            double[] feasibleLower,
            double[] feasibleUpper,
            double[] distance,
            double verticalReliefScale,
            double maximumPhysicalSlope) {
        int count = target.length;
        double[] result = new double[count];

        result[count - 1] = clamp(
                target[count - 1],
                feasibleLower[count - 1],
                feasibleUpper[count - 1]);

        for (int i = count - 2; i >= 0; i--) {
            double horizontalDistance = distance[i + 1] - distance[i];
            if (horizontalDistance <= EPSILON) {
                throw new IllegalArgumentException("route contains coincident consecutive points");
            }

            double maximumFallPotential =
                    maximumPhysicalSlope * horizontalDistance / verticalReliefScale;

            double lower = Math.max(feasibleLower[i], result[i + 1]);
            double upper = Math.min(
                    feasibleUpper[i],
                    result[i + 1] + maximumFallPotential);

            if (lower > upper + EPSILON) {
                throw new IllegalStateException(
                        "forward feasible intervals failed during backtracking");
            }

            result[i] = clamp(target[i], lower, upper);
        }

        return result;
    }

    private static double[] cumulativeDistance(List<SkyIslandLocalPosition> route) {
        double[] distance = new double[route.size()];
        for (int i = 1; i < route.size(); i++) {
            double step = Math.hypot(
                    route.get(i).x() - route.get(i - 1).x(),
                    route.get(i).z() - route.get(i - 1).z());
            if (step <= EPSILON) {
                throw new IllegalArgumentException("route contains coincident consecutive points");
            }
            distance[i] = distance[i - 1] + step;
        }
        return distance;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private record FeasibleIntervals(
            boolean feasible,
            double[] lower,
            double[] upper,
            int failureStation) {

        private static FeasibleIntervals success(double[] lower, double[] upper) {
            return new FeasibleIntervals(true, lower, upper, -1);
        }

        private static FeasibleIntervals failure(int station) {
            return new FeasibleIntervals(
                    false,
                    new double[0],
                    new double[0],
                    station);
        }
    }
}
