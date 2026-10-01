package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Computes fixed-physical-scale, arc-length-weighted diagnostics for a search route. */
public final class SkyIslandRouteFunctionalDiagnosticsPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandRouteFunctionalDiagnosticsPlanner() {}

    public static SkyIslandRouteFunctionalDiagnostics measure(
            SkyIslandGeomorphicCandidateRoute route,
            SkyIslandSemanticField terrain,
            double planningSpacing) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(terrain, "terrain");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }

        List<SkyIslandLocalPosition> points = route.points();
        double probeRadius =
                planningSpacing
                        * SkyIslandTerrainAwareRouteSolver
                                .RIDGE_PROBE_RADIUS_PLANNING_FRACTION;
        double[] elevation = new double[points.size()];
        double[] ridge = new double[points.size()];
        double[] valley = new double[points.size()];

        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition point = points.get(i);
            double z = terrain.sample(point);
            double surrounding = surroundingMean(terrain, point, probeRadius);
            elevation[i] = z;
            ridge[i] = Math.max(0.0, z - surrounding);
            valley[i] = surrounding - z;
        }

        double length = 0.0;
        double positiveVariation = 0.0;
        double maximumUphillGrade = 0.0;
        double ridgeIntegral = 0.0;
        double valleyIntegral = 0.0;

        for (int i = 0; i + 1 < points.size(); i++) {
            double ds = Math.hypot(
                    points.get(i + 1).x() - points.get(i).x(),
                    points.get(i + 1).z() - points.get(i).z());
            if (!(ds > EPSILON)) {
                throw new IllegalStateException(
                        "route diagnostics require strictly positive segment length");
            }
            length += ds;

            double rise = elevation[i + 1] - elevation[i];
            if (rise > 0.0) {
                positiveVariation += rise;
                maximumUphillGrade = Math.max(maximumUphillGrade, rise / ds);
            }

            double ridgeA =
                    ridge[i] > SkyIslandTerrainAwareRouteSolver.RIDGE_DIAGNOSTIC_THRESHOLD
                            ? 1.0
                            : 0.0;
            double ridgeB =
                    ridge[i + 1] > SkyIslandTerrainAwareRouteSolver.RIDGE_DIAGNOSTIC_THRESHOLD
                            ? 1.0
                            : 0.0;
            ridgeIntegral += 0.5 * (ridgeA + ridgeB) * ds;
            valleyIntegral += 0.5 * (valley[i] + valley[i + 1]) * ds;
        }

        if (!(length > EPSILON)) {
            throw new IllegalStateException("route diagnostics require positive path length");
        }
        return new SkyIslandRouteFunctionalDiagnostics(
                route.totalCost(),
                length,
                route.maxGuidanceDeviation(),
                positiveVariation,
                maximumUphillGrade,
                clamp01(ridgeIntegral / length),
                valleyIntegral / length);
    }

    public static double ridgeLengthFraction(
            List<SkyIslandLocalPosition> points,
            SkyIslandSemanticField terrain,
            double planningSpacing) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(terrain, "terrain");
        if (points.size() < 2) {
            throw new IllegalArgumentException("route diagnostics require at least two points");
        }
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }

        double probeRadius =
                planningSpacing
                        * SkyIslandTerrainAwareRouteSolver
                                .RIDGE_PROBE_RADIUS_PLANNING_FRACTION;
        double[] ridge = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition point = Objects.requireNonNull(points.get(i), "point");
            double elevation = terrain.sample(point);
            double surrounding = surroundingMean(terrain, point, probeRadius);
            ridge[i] = Math.max(0.0, elevation - surrounding);
        }

        double length = 0.0;
        double ridgeIntegral = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            double ds = Math.hypot(
                    points.get(i + 1).x() - points.get(i).x(),
                    points.get(i + 1).z() - points.get(i).z());
            if (!(ds > EPSILON)) {
                throw new IllegalStateException(
                        "route diagnostics require strictly positive segment length");
            }
            length += ds;
            double ridgeA =
                    ridge[i] > SkyIslandTerrainAwareRouteSolver.RIDGE_DIAGNOSTIC_THRESHOLD
                            ? 1.0
                            : 0.0;
            double ridgeB =
                    ridge[i + 1] > SkyIslandTerrainAwareRouteSolver.RIDGE_DIAGNOSTIC_THRESHOLD
                            ? 1.0
                            : 0.0;
            ridgeIntegral += 0.5 * (ridgeA + ridgeB) * ds;
        }
        if (!(length > EPSILON)) {
            throw new IllegalStateException("route diagnostics require positive path length");
        }
        return clamp01(ridgeIntegral / length);
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
