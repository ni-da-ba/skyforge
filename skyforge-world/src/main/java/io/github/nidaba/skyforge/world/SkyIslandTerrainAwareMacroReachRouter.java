package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Routes one authored channel macro-reach through a fine terrain-aware corridor.
 *
 * <p>Only macro endpoints are hard geometric controls. Intermediate degree-two watershed nodes
 * remain semantic/profile boundaries but may slide laterally inside the admissible corridor. The
 * returned per-profile paths share exact geometric boundary points, so downstream fluvial geometry
 * can continue to consume one path per accepted profile without reintroducing coarse-grid kinks.
 */
public final class SkyIslandTerrainAwareMacroReachRouter {
    public static final int STATIONS_PER_COARSE_REACH = 6;
    public static final int LANE_COUNT = 17;
    public static final int MAX_LANE_SHIFT_PER_STATION = 4;

    private static final double ASCENT_WEIGHT = 24.0;
    private static final double RIDGE_WEIGHT = 10.0;
    private static final double VALLEY_REWARD = 4.0;
    private static final double TERRAIN_LEVEL_WEIGHT = 1.35;
    private static final double EXTERIOR_WEIGHT = 30.0;
    private static final double LENGTH_WEIGHT = 0.055;
    private static final double LATERAL_CHANGE_WEIGHT = 0.020;
    private static final double CENTERLINE_BIAS_WEIGHT = 0.0008;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandTerrainAwareMacroReachRouter() {}

    public static List<SkyIslandNaturalizedChannelPath> route(
            SkyIslandChannelMacroReach macro,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double maxDeviationSpacingFraction) {
        Objects.requireNonNull(macro, "macro");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        if (!Double.isFinite(maxDeviationSpacingFraction) || maxDeviationSpacingFraction <= 0.0) {
            throw new IllegalArgumentException("maxDeviationSpacingFraction must be finite and positive");
        }

        List<SkyIslandChannelProfile> profiles = macro.profiles();
        int edgeCount = profiles.size();
        int totalStations = edgeCount * STATIONS_PER_COARSE_REACH;
        int centerLane = LANE_COUNT / 2;
        double probeDistance = planningSpacing * 0.22;

        SkyIslandLocalPosition[][] candidates =
                new SkyIslandLocalPosition[totalStations + 1][LANE_COUNT];
        double[][] elevations = new double[totalStations + 1][LANE_COUNT];
        double[][] localCosts = new double[totalStations + 1][LANE_COUNT];
        boolean[][] valid = new boolean[totalStations + 1][LANE_COUNT];

        for (int station = 0; station <= totalStations; station++) {
            StationFrame frame = stationFrame(profiles, station, totalStations);
            double endpointRamp = Math.min(
                    1.0,
                    Math.min(station, totalStations - station)
                            / (double) STATIONS_PER_COARSE_REACH);
            double kindScale = switch (frame.profile().kind()) {
                case ALLUVIAL -> 1.0;
                case INCISED -> 0.82;
                case CASCADE -> 0.62;
            };
            double envelope =
                    planningSpacing * maxDeviationSpacingFraction * endpointRamp * kindScale;

            for (int lane = 0; lane < LANE_COUNT; lane++) {
                if ((station == 0 || station == totalStations) && lane != centerLane) {
                    continue;
                }
                double laneFraction = laneFraction(lane, centerLane);
                SkyIslandLocalPosition point = new SkyIslandLocalPosition(
                        frame.baseline().x() + frame.normal().x() * envelope * laneFraction,
                        frame.baseline().z() + frame.normal().z() * envelope * laneFraction);

                double elevation = terrain.sample(point);
                SkyIslandLocalPosition left = new SkyIslandLocalPosition(
                        point.x() + frame.normal().x() * probeDistance,
                        point.z() + frame.normal().z() * probeDistance);
                SkyIslandLocalPosition right = new SkyIslandLocalPosition(
                        point.x() - frame.normal().x() * probeDistance,
                        point.z() - frame.normal().z() * probeDistance);
                double sideMean = 0.5 * (terrain.sample(left) + terrain.sample(right));
                double ridgePenalty = Math.max(0.0, elevation - sideMean);
                double valleyReward = Math.max(0.0, sideMean - elevation);
                double exteriorPenalty = Math.max(0.0, 0.035 - interiority.sample(point));

                candidates[station][lane] = point;
                elevations[station][lane] = elevation;
                localCosts[station][lane] =
                        TERRAIN_LEVEL_WEIGHT * elevation
                                + RIDGE_WEIGHT * ridgePenalty
                                - VALLEY_REWARD * valleyReward
                                + EXTERIOR_WEIGHT * exteriorPenalty
                                + CENTERLINE_BIAS_WEIGHT * Math.abs(laneFraction);
                valid[station][lane] = true;
            }
        }

        double[][] cost = new double[totalStations + 1][LANE_COUNT];
        int[][] previous = new int[totalStations + 1][LANE_COUNT];
        for (double[] row : cost) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int[] row : previous) {
            Arrays.fill(row, -1);
        }
        cost[0][centerLane] = 0.0;

        double referenceStep = planningSpacing / STATIONS_PER_COARSE_REACH;
        for (int station = 1; station <= totalStations; station++) {
            for (int lane = 0; lane < LANE_COUNT; lane++) {
                if (!valid[station][lane]) {
                    continue;
                }
                int minPrior = Math.max(0, lane - MAX_LANE_SHIFT_PER_STATION);
                int maxPrior = Math.min(LANE_COUNT - 1, lane + MAX_LANE_SHIFT_PER_STATION);
                for (int prior = minPrior; prior <= maxPrior; prior++) {
                    if (!valid[station - 1][prior]
                            || !Double.isFinite(cost[station - 1][prior])) {
                        continue;
                    }
                    SkyIslandLocalPosition a = candidates[station - 1][prior];
                    SkyIslandLocalPosition b = candidates[station][lane];
                    double stepLength = distance(a, b);
                    double ascent = Math.max(
                            0.0, elevations[station][lane] - elevations[station - 1][prior]);
                    double lateralChange = Math.abs(
                            laneFraction(lane, centerLane) - laneFraction(prior, centerLane));

                    double candidateCost = cost[station - 1][prior]
                            + localCosts[station][lane]
                            + ASCENT_WEIGHT * ascent
                            + LENGTH_WEIGHT * Math.max(0.0, stepLength / referenceStep - 1.0)
                            + LATERAL_CHANGE_WEIGHT * lateralChange;

                    if (candidateCost < cost[station][lane] - EPSILON
                            || (Math.abs(candidateCost - cost[station][lane]) <= EPSILON
                                    && (previous[station][lane] < 0
                                            || prior < previous[station][lane]))) {
                        cost[station][lane] = candidateCost;
                        previous[station][lane] = prior;
                    }
                }
            }
        }

        if (!Double.isFinite(cost[totalStations][centerLane])) {
            throw new IllegalStateException("macro-reach corridor search found no bounded route");
        }

        int[] lanes = new int[totalStations + 1];
        lanes[totalStations] = centerLane;
        for (int station = totalStations; station > 0; station--) {
            int prior = previous[station][lanes[station]];
            if (prior < 0) {
                throw new IllegalStateException("macro-reach route is missing a predecessor");
            }
            lanes[station - 1] = prior;
        }

        List<SkyIslandLocalPosition> global = new ArrayList<>(totalStations + 1);
        for (int station = 0; station <= totalStations; station++) {
            global.add(candidates[station][lanes[station]]);
        }

        List<SkyIslandNaturalizedChannelPath> result = new ArrayList<>(edgeCount);
        for (int edge = 0; edge < edgeCount; edge++) {
            int from = edge * STATIONS_PER_COARSE_REACH;
            int to = (edge + 1) * STATIONS_PER_COARSE_REACH;
            List<SkyIslandLocalPosition> points = List.copyOf(global.subList(from, to + 1));
            SkyIslandChannelProfile profile = profiles.get(edge);

            double chordLength = distance(points.getFirst(), points.getLast());
            double pathLength = 0.0;
            double maxDeviation = 0.0;
            SkyIslandChannelSegment semantic = profile.segment();
            for (int i = 1; i < points.size(); i++) {
                pathLength += distance(points.get(i - 1), points.get(i));
            }
            for (int i = 0; i < points.size(); i++) {
                double t = (double) i / STATIONS_PER_COARSE_REACH;
                double baselineX = semantic.start().x()
                        + (semantic.end().x() - semantic.start().x()) * t;
                double baselineZ = semantic.start().z()
                        + (semantic.end().z() - semantic.start().z()) * t;
                maxDeviation = Math.max(
                        maxDeviation,
                        Math.hypot(
                                points.get(i).x() - baselineX,
                                points.get(i).z() - baselineZ));
            }

            result.add(new SkyIslandNaturalizedChannelPath(
                    profile, points, chordLength, pathLength, maxDeviation));
        }
        return List.copyOf(result);
    }

    private static StationFrame stationFrame(
            List<SkyIslandChannelProfile> profiles,
            int station,
            int totalStations) {
        int edge;
        double localT;
        if (station == totalStations) {
            edge = profiles.size() - 1;
            localT = 1.0;
        } else {
            edge = station / STATIONS_PER_COARSE_REACH;
            localT = (station % STATIONS_PER_COARSE_REACH)
                    / (double) STATIONS_PER_COARSE_REACH;
        }

        SkyIslandChannelProfile profile = profiles.get(edge);
        SkyIslandChannelSegment segment = profile.segment();
        SkyIslandLocalPosition baseline = new SkyIslandLocalPosition(
                segment.start().x() + (segment.end().x() - segment.start().x()) * localT,
                segment.start().z() + (segment.end().z() - segment.start().z()) * localT);

        Vector tangent;
        if (station > 0
                && station < totalStations
                && station % STATIONS_PER_COARSE_REACH == 0) {
            SkyIslandChannelSegment previous = profiles.get(edge - 1).segment();
            SkyIslandChannelSegment next = profiles.get(edge).segment();
            tangent = normalize(
                    next.end().x() - previous.start().x(),
                    next.end().z() - previous.start().z());
        } else {
            tangent = normalize(
                    segment.end().x() - segment.start().x(),
                    segment.end().z() - segment.start().z());
        }
        return new StationFrame(profile, baseline, new Vector(-tangent.z(), tangent.x()));
    }

    private static double laneFraction(int lane, int centerLane) {
        return (double) (lane - centerLane) / centerLane;
    }

    private static Vector normalize(double x, double z) {
        double length = Math.hypot(x, z);
        if (length <= EPSILON) {
            return new Vector(1.0, 0.0);
        }
        return new Vector(x / length, z / length);
    }

    private static double distance(SkyIslandLocalPosition a, SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private record Vector(double x, double z) {}
    private record StationFrame(
            SkyIslandChannelProfile profile,
            SkyIslandLocalPosition baseline,
            Vector normal) {}
}
