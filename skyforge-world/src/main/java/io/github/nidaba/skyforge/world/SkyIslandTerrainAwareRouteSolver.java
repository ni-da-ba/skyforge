package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Deterministic bounded least-cost search for a fine hydrologic route over pre-fluvial terrain.
 *
 * <p>The route remains inside a corridor around coarse semantic guidance, but ordinary coarse
 * planning positions are not mandatory waypoints. Cost strongly penalizes raw-terrain ascent and
 * local ridge occupancy, mildly prefers lower terrain, and penalizes deviation from the semantic
 * corridor center. This class does not carve terrain, solve a hydraulic grade, or rasterize blocks.
 */
public final class SkyIslandTerrainAwareRouteSolver {
    public static final int FINE_DIVISIONS_PER_PLANNING_CELL = 4;
    public static final double RIDGE_DIAGNOSTIC_THRESHOLD = 0.002;
    public static final double RIDGE_PROBE_RADIUS_PLANNING_FRACTION = 0.375;

    private static final double REFERENCE_DIVISIONS_PER_PLANNING_CELL = 4.0;

    private static final double BASE_LENGTH_WEIGHT = 1.0;
    private static final double ASCENT_WEIGHT = 40.0;
    private static final double RIDGE_WEIGHT = 24.0;
    private static final double TERRAIN_LEVEL_WEIGHT = 2.5;
    private static final double GUIDANCE_DEVIATION_WEIGHT = 1.75;
    private static final double LOW_INTERIORITY_WEIGHT = 30.0;
    private static final double LOW_INTERIORITY_THRESHOLD = 0.08;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandTerrainAwareRouteSolver() {}

    public static SkyIslandGeomorphicCandidateRoute solve(
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            List<SkyIslandLocalPosition> guidance,
            double planningSpacing,
            double corridorHalfWidth,
            SkyIslandGeomorphicRouteAnchor startAnchor,
            SkyIslandGeomorphicRouteAnchor endAnchor) {
        return solveAtResolution(
                terrain,
                interiority,
                guidance,
                planningSpacing,
                corridorHalfWidth,
                startAnchor,
                endAnchor,
                FINE_DIVISIONS_PER_PLANNING_CELL);
    }

    public static SkyIslandGeomorphicCandidateRoute solveAtResolution(
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            List<SkyIslandLocalPosition> guidance,
            double planningSpacing,
            double corridorHalfWidth,
            SkyIslandGeomorphicRouteAnchor startAnchor,
            SkyIslandGeomorphicRouteAnchor endAnchor,
            int divisionsPerPlanningCell) {
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        guidance = List.copyOf(guidance);
        guidance.forEach(point -> Objects.requireNonNull(point, "guidance point"));
        startAnchor = Objects.requireNonNull(startAnchor, "startAnchor");
        endAnchor = Objects.requireNonNull(endAnchor, "endAnchor");
        if (guidance.size() < 2) {
            throw new IllegalArgumentException("guidance requires at least two positions");
        }
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        if (!Double.isFinite(corridorHalfWidth) || corridorHalfWidth <= 0.0) {
            throw new IllegalArgumentException("corridorHalfWidth must be finite and positive");
        }
        if (divisionsPerPlanningCell < 2) {
            throw new IllegalArgumentException("divisionsPerPlanningCell must be at least 2");
        }

        double step = planningSpacing / divisionsPerPlanningCell;
        double padding = corridorHalfWidth + Math.max(startAnchor.radius(), endAnchor.radius()) + step;
        Bounds rawBounds = bounds(guidance, startAnchor.center(), endAnchor.center(), padding);
        int minimumGridX = (int) Math.floor(rawBounds.minX() / step);
        int maximumGridX = (int) Math.ceil(rawBounds.maxX() / step);
        int minimumGridZ = (int) Math.floor(rawBounds.minZ() / step);
        int maximumGridZ = (int) Math.ceil(rawBounds.maxZ() / step);
        int width = Math.max(2, Math.addExact(Math.subtractExact(maximumGridX, minimumGridX), 1));
        int height = Math.max(2, Math.addExact(Math.subtractExact(maximumGridZ, minimumGridZ), 1));
        int count = Math.multiplyExact(width, height);

        SkyIslandLocalPosition[] positions = new SkyIslandLocalPosition[count];
        double[] elevations = new double[count];
        double[] localRidge = new double[count];
        double[] valleyAdvantage = new double[count];
        double[] guidanceDeviation = new double[count];
        double[] localCost = new double[count];
        boolean[] valid = new boolean[count];
        boolean[] start = new boolean[count];
        boolean[] goal = new boolean[count];

        double anchorTolerance = 0.75 * step;
        double probeRadius = planningSpacing * RIDGE_PROBE_RADIUS_PLANNING_FRACTION;
        int startCount = 0;
        int goalCount = 0;

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = index(x, z, width);
                int globalGridX = Math.addExact(minimumGridX, x);
                int globalGridZ = Math.addExact(minimumGridZ, z);
                SkyIslandLocalPosition position =
                        new SkyIslandLocalPosition(globalGridX * step, globalGridZ * step);
                double deviation = distanceToPolyline(position, guidance);
                boolean inStart = anchorContains(startAnchor, position, anchorTolerance);
                boolean inGoal = anchorContains(endAnchor, position, anchorTolerance);
                if (deviation > corridorHalfWidth + EPSILON && !inStart && !inGoal) {
                    continue;
                }

                double elevation = terrain.sample(position);
                double surroundingMean = surroundingMean(terrain, position, probeRadius);
                double ridge = Math.max(0.0, elevation - surroundingMean);
                double valley = surroundingMean - elevation;
                double interior = interiority.sample(position);
                double exteriorPenalty =
                        Math.max(0.0, LOW_INTERIORITY_THRESHOLD - interior);

                positions[index] = position;
                elevations[index] = elevation;
                localRidge[index] = ridge;
                valleyAdvantage[index] = valley;
                guidanceDeviation[index] = deviation;
                localCost[index] =
                        RIDGE_WEIGHT * ridge
                                + TERRAIN_LEVEL_WEIGHT * elevation
                                + GUIDANCE_DEVIATION_WEIGHT
                                        * square(deviation / corridorHalfWidth)
                                + LOW_INTERIORITY_WEIGHT * exteriorPenalty;
                valid[index] = true;
                if (inStart) {
                    start[index] = true;
                    startCount++;
                }
                if (inGoal) {
                    goal[index] = true;
                    goalCount++;
                }
            }
        }

        if (startCount == 0 || goalCount == 0) {
            throw new IllegalStateException("route corridor does not contain both endpoint anchor regions");
        }

        double[] best = new double[count];
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        int[] previous = new int[count];
        Arrays.fill(previous, -1);
        PriorityQueue<OpenNode> open = new PriorityQueue<>(Comparator
                .comparingDouble(OpenNode::estimatedTotal)
                .thenComparingDouble(OpenNode::cost)
                .thenComparingInt(OpenNode::index));

        for (int i = 0; i < count; i++) {
            if (!start[i]) {
                continue;
            }
            best[i] = 0.0;
            open.add(new OpenNode(
                    i,
                    0.0,
                    heuristic(positions[i], endAnchor, planningSpacing)));
        }

        int selectedGoal = -1;
        while (!open.isEmpty()) {
            OpenNode current = open.remove();
            if (current.cost() > best[current.index()] + EPSILON) {
                continue;
            }
            if (goal[current.index()]) {
                selectedGoal = current.index();
                break;
            }

            int cx = current.index() % width;
            int cz = current.index() / width;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                        continue;
                    }
                    int next = index(nx, nz, width);
                    if (!valid[next]) {
                        continue;
                    }

                    double stepLength = Math.hypot(dx * step, dz * step);
                    double normalizedLength =
                            REFERENCE_DIVISIONS_PER_PLANNING_CELL
                                    * stepLength
                                    / planningSpacing;
                    double ascent = Math.max(0.0, elevations[next] - elevations[current.index()]);
                    double transitionCost =
                            BASE_LENGTH_WEIGHT * normalizedLength
                                    + 0.5
                                            * (localCost[current.index()] + localCost[next])
                                            * normalizedLength
                                    + ASCENT_WEIGHT * ascent;
                    double candidate = current.cost() + transitionCost;
                    if (candidate < best[next] - EPSILON
                            || (Math.abs(candidate - best[next]) <= EPSILON
                                    && (previous[next] < 0 || current.index() < previous[next]))) {
                        best[next] = candidate;
                        previous[next] = current.index();
                        open.add(new OpenNode(
                                next,
                                candidate,
                                candidate + heuristic(
                                        positions[next], endAnchor, planningSpacing)));
                    }
                }
            }
        }

        if (selectedGoal < 0) {
            throw new IllegalStateException("terrain-aware search found no route inside the semantic corridor");
        }

        List<Integer> reversed = new ArrayList<>();
        int cursor = selectedGoal;
        while (cursor >= 0) {
            reversed.add(cursor);
            if (start[cursor]) {
                break;
            }
            cursor = previous[cursor];
        }
        if (reversed.isEmpty() || !start[reversed.getLast()]) {
            throw new IllegalStateException("terrain-aware route is missing a start predecessor chain");
        }

        List<SkyIslandLocalPosition> points = new ArrayList<>(reversed.size());
        double pathLength = 0.0;
        double maxDeviation = 0.0;
        int uphillSteps = 0;
        int ridgeSamples = 0;
        double valleySum = 0.0;
        double maxUphillStep = 0.0;
        SkyIslandLocalPosition prior = null;
        double priorElevation = 0.0;

        for (int i = reversed.size() - 1; i >= 0; i--) {
            int node = reversed.get(i);
            SkyIslandLocalPosition point = positions[node];
            points.add(point);
            maxDeviation = Math.max(maxDeviation, guidanceDeviation[node]);
            valleySum += valleyAdvantage[node];
            if (localRidge[node] > RIDGE_DIAGNOSTIC_THRESHOLD) {
                ridgeSamples++;
            }
            if (prior != null) {
                pathLength += Math.hypot(point.x() - prior.x(), point.z() - prior.z());
                double rise = elevations[node] - priorElevation;
                if (rise > EPSILON) {
                    uphillSteps++;
                    maxUphillStep = Math.max(maxUphillStep, rise);
                }
            }
            prior = point;
            priorElevation = elevations[node];
        }

        int stepCount = Math.max(1, points.size() - 1);
        return new SkyIslandGeomorphicCandidateRoute(
                points,
                best[selectedGoal],
                pathLength,
                maxDeviation,
                (double) uphillSteps / stepCount,
                (double) ridgeSamples / points.size(),
                valleySum / points.size(),
                maxUphillStep);
    }

    private static boolean anchorContains(
            SkyIslandGeomorphicRouteAnchor anchor,
            SkyIslandLocalPosition position,
            double rasterTolerance) {
        if (anchor.radius() <= EPSILON) {
            return Math.hypot(
                            position.x() - anchor.center().x(),
                            position.z() - anchor.center().z())
                    <= EPSILON;
        }
        return anchor.contains(position, rasterTolerance);
    }

    private static double heuristic(
            SkyIslandLocalPosition position,
            SkyIslandGeomorphicRouteAnchor goal,
            double planningSpacing) {
        double distance = Math.hypot(
                position.x() - goal.center().x(),
                position.z() - goal.center().z());
        return BASE_LENGTH_WEIGHT
                * REFERENCE_DIVISIONS_PER_PLANNING_CELL
                * Math.max(0.0, distance - goal.radius())
                / planningSpacing;
    }

    private static double surroundingMean(
            SkyIslandSemanticField terrain,
            SkyIslandLocalPosition center,
            double radius) {
        double sum = 0.0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            sum += terrain.sample(new SkyIslandLocalPosition(
                    center.x() + Math.cos(angle) * radius,
                    center.z() + Math.sin(angle) * radius));
        }
        return sum / 8.0;
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        double best = Double.POSITIVE_INFINITY;
        for (int i = 1; i < polyline.size(); i++) {
            best = Math.min(best, distanceToSegment(point, polyline.get(i - 1), polyline.get(i)));
        }
        return best;
    }

    private static double distanceToSegment(
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared <= EPSILON) {
            return Math.hypot(point.x() - a.x(), point.z() - a.z());
        }
        double t = ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double x = a.x() + clamped * dx;
        double z = a.z() + clamped * dz;
        return Math.hypot(point.x() - x, point.z() - z);
    }

    private static Bounds bounds(
            List<SkyIslandLocalPosition> guidance,
            SkyIslandLocalPosition start,
            SkyIslandLocalPosition end,
            double padding) {
        double minX = Math.min(start.x(), end.x());
        double maxX = Math.max(start.x(), end.x());
        double minZ = Math.min(start.z(), end.z());
        double maxZ = Math.max(start.z(), end.z());
        for (SkyIslandLocalPosition point : guidance) {
            minX = Math.min(minX, point.x());
            maxX = Math.max(maxX, point.x());
            minZ = Math.min(minZ, point.z());
            maxZ = Math.max(maxZ, point.z());
        }
        return new Bounds(minX - padding, maxX + padding, minZ - padding, maxZ + padding);
    }

    private static int index(int x, int z, int width) {
        return z * width + x;
    }

    private static double square(double value) {
        return value * value;
    }

    private record OpenNode(int index, double cost, double estimatedTotal) {}

    private record Bounds(double minX, double maxX, double minZ, double maxZ) {}
}
