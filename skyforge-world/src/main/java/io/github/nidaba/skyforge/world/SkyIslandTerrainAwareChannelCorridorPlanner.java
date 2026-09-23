package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Finds a deterministic fine channel centerline inside one accepted coarse watershed reach.
 *
 * <p>The watershed graph remains authoritative for catchment topology. This planner owns only the
 * physical sub-grid corridor between two accepted graph nodes. It evaluates a small longitudinal
 * lattice against the pre-channel authored terrain, preferring downhill valley-floor routes over
 * decorative curvature. The bounded dynamic program is intentionally cheap enough to run for every
 * retained reach without a whole-island pathfinding pass.
 */
public final class SkyIslandTerrainAwareChannelCorridorPlanner {
    public static final int STATIONS = 12;
    public static final int LANE_COUNT = 15;
    public static final int MAX_LANE_SHIFT_PER_STATION = 3;

    private static final double ASCENT_WEIGHT = 18.0;
    private static final double RIDGE_WEIGHT = 7.0;
    private static final double VALLEY_REWARD = 2.2;
    private static final double EXTERIOR_WEIGHT = 20.0;
    private static final double LENGTH_WEIGHT = 0.10;
    private static final double LATERAL_CHANGE_WEIGHT = 0.055;
    private static final double CENTERLINE_BIAS_WEIGHT = 0.006;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandTerrainAwareChannelCorridorPlanner() {}

    public static SkyIslandNaturalizedChannelPath route(
            SkyIslandChannelProfile profile,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double maxChordDeviationSpacingFraction) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        if (!Double.isFinite(maxChordDeviationSpacingFraction)
                || maxChordDeviationSpacingFraction <= 0.0) {
            throw new IllegalArgumentException(
                    "maxChordDeviationSpacingFraction must be finite and positive");
        }

        SkyIslandChannelSegment segment = profile.segment();
        SkyIslandLocalPosition start = segment.start();
        SkyIslandLocalPosition end = segment.end();
        double chordX = end.x() - start.x();
        double chordZ = end.z() - start.z();
        double chordLength = Math.hypot(chordX, chordZ);
        if (chordLength <= EPSILON) {
            throw new IllegalStateException("accepted channel segment has zero geometric length");
        }

        Vector chord = new Vector(chordX / chordLength, chordZ / chordLength);
        Vector normal = new Vector(-chord.z(), chord.x());
        SkyIslandLocalPosition[][] candidates = new SkyIslandLocalPosition[STATIONS + 1][LANE_COUNT];
        double[][] elevations = new double[STATIONS + 1][LANE_COUNT];
        double[][] localTerrainCosts = new double[STATIONS + 1][LANE_COUNT];
        boolean[][] valid = new boolean[STATIONS + 1][LANE_COUNT];

        int centerLane = LANE_COUNT / 2;
        double maximumDeviation = planningSpacing * maxChordDeviationSpacingFraction;
        double probeDistance = planningSpacing * 0.22;

        for (int station = 0; station <= STATIONS; station++) {
            double t = (double) station / STATIONS;
            double baselineX = start.x() + chordX * t;
            double baselineZ = start.z() + chordZ * t;
            double envelope = maximumDeviation * Math.sin(Math.PI * t);

            for (int lane = 0; lane < LANE_COUNT; lane++) {
                if ((station == 0 || station == STATIONS) && lane != centerLane) {
                    continue;
                }
                double laneFraction = laneFraction(lane, centerLane);
                SkyIslandLocalPosition point;
                if (station == 0) {
                    point = start;
                } else if (station == STATIONS) {
                    point = end;
                } else {
                    point = new SkyIslandLocalPosition(
                            baselineX + normal.x() * envelope * laneFraction,
                            baselineZ + normal.z() * envelope * laneFraction);
                }

                double elevation = terrain.sample(point);
                SkyIslandLocalPosition left = new SkyIslandLocalPosition(
                        point.x() + normal.x() * probeDistance,
                        point.z() + normal.z() * probeDistance);
                SkyIslandLocalPosition right = new SkyIslandLocalPosition(
                        point.x() - normal.x() * probeDistance,
                        point.z() - normal.z() * probeDistance);
                double sideMean = 0.5 * (terrain.sample(left) + terrain.sample(right));
                double ridgePenalty = Math.max(0.0, elevation - sideMean);
                double valleyReward = Math.max(0.0, sideMean - elevation);
                double exteriorPenalty = Math.max(0.0, 0.035 - interiority.sample(point));

                candidates[station][lane] = point;
                elevations[station][lane] = elevation;
                localTerrainCosts[station][lane] =
                        RIDGE_WEIGHT * ridgePenalty
                                - VALLEY_REWARD * valleyReward
                                + EXTERIOR_WEIGHT * exteriorPenalty
                                + CENTERLINE_BIAS_WEIGHT * Math.abs(laneFraction);
                valid[station][lane] = true;
            }
        }

        double[][] cost = new double[STATIONS + 1][LANE_COUNT];
        int[][] previous = new int[STATIONS + 1][LANE_COUNT];
        for (double[] row : cost) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int[] row : previous) {
            Arrays.fill(row, -1);
        }
        cost[0][centerLane] = 0.0;

        double referenceStep = chordLength / STATIONS;
        for (int station = 1; station <= STATIONS; station++) {
            for (int lane = 0; lane < LANE_COUNT; lane++) {
                if (!valid[station][lane]) {
                    continue;
                }
                int minimumPrevious = Math.max(0, lane - MAX_LANE_SHIFT_PER_STATION);
                int maximumPrevious =
                        Math.min(LANE_COUNT - 1, lane + MAX_LANE_SHIFT_PER_STATION);
                for (int prior = minimumPrevious; prior <= maximumPrevious; prior++) {
                    if (!valid[station - 1][prior]
                            || !Double.isFinite(cost[station - 1][prior])) {
                        continue;
                    }
                    SkyIslandLocalPosition a = candidates[station - 1][prior];
                    SkyIslandLocalPosition b = candidates[station][lane];
                    double stepLength = Math.hypot(b.x() - a.x(), b.z() - a.z());
                    double ascent = Math.max(
                            0.0, elevations[station][lane] - elevations[station - 1][prior]);
                    double lateralChange = Math.abs(
                            laneFraction(lane, centerLane) - laneFraction(prior, centerLane));
                    double candidateCost = cost[station - 1][prior]
                            + localTerrainCosts[station][lane]
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

        if (!Double.isFinite(cost[STATIONS][centerLane])) {
            throw new IllegalStateException("terrain-aware corridor search found no bounded route");
        }

        int[] lanes = new int[STATIONS + 1];
        lanes[STATIONS] = centerLane;
        for (int station = STATIONS; station > 0; station--) {
            int prior = previous[station][lanes[station]];
            if (prior < 0) {
                throw new IllegalStateException(
                        "terrain-aware corridor route is missing a predecessor");
            }
            lanes[station - 1] = prior;
        }

        List<SkyIslandLocalPosition> points = new ArrayList<>(STATIONS + 1);
        double pathLength = 0.0;
        double maxDeviation = 0.0;
        SkyIslandLocalPosition priorPoint = null;
        for (int station = 0; station <= STATIONS; station++) {
            SkyIslandLocalPosition point = candidates[station][lanes[station]];
            points.add(point);
            if (priorPoint != null) {
                pathLength += Math.hypot(
                        point.x() - priorPoint.x(), point.z() - priorPoint.z());
            }
            double t = (double) station / STATIONS;
            double baselineX = start.x() + chordX * t;
            double baselineZ = start.z() + chordZ * t;
            maxDeviation = Math.max(
                    maxDeviation, Math.hypot(point.x() - baselineX, point.z() - baselineZ));
            priorPoint = point;
        }

        return new SkyIslandNaturalizedChannelPath(
                profile, points, chordLength, pathLength, maxDeviation);
    }

    private static double laneFraction(int lane, int centerLane) {
        return (double) (lane - centerLane) / centerLane;
    }

    private record Vector(double x, double z) {}
}
