package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Relaxes a C1 centerline inside the broader semantic reach corridor.
 *
 * <p>The fine-lattice route is a deterministic terrain-aware seed, not final geometric authority.
 * Shared endpoints remain exact. Interior points may move away from the lattice path when they remain
 * inside the semantic guidance corridor, do not climb materially above the seeded terrain route, and
 * stay inside the authored island domain.
 */
public final class SkyIslandSemanticCorridorCenterlinePlanner {
    public static final int MAXIMUM_RELAXATION_SWEEPS = 48;
    public static final double RELAXATION_FRACTION = 0.40;
    public static final double MAXIMUM_TERRAIN_RISE_FROM_SEED = 0.015;
    public static final double MINIMUM_INTERIORITY = 0.025;

    private static final double EPSILON = 1.0e-12;

    private SkyIslandSemanticCorridorCenterlinePlanner() {}

    public static SkyIslandContinuousChannelCenterline refine(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius) {
        Objects.requireNonNull(searchRoute, "searchRoute");
        semanticGuidance = List.copyOf(semanticGuidance);
        semanticGuidance.forEach(p -> Objects.requireNonNull(p, "guidance point"));
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        requirePositive(planningSpacing, "planningSpacing");
        requirePositive(semanticCorridorHalfWidth, "semanticCorridorHalfWidth");
        requireNonNegative(minimumBendRadius, "minimumBendRadius");
        if (semanticGuidance.size() < 2) {
            throw new IllegalArgumentException("semantic guidance requires at least two points");
        }

        SkyIslandContinuousChannelCenterline seed =
                SkyIslandContinuousChannelCenterlinePlanner.refine(
                        searchRoute, terrain, interiority, planningSpacing);

        List<SkyIslandLocalPosition> current = new ArrayList<>(seed.points());
        Candidate best = evaluate(searchRoute, current);
        for (int sweep = 0; sweep < MAXIMUM_RELAXATION_SWEEPS; sweep++) {
            List<SkyIslandLocalPosition> next = relaxOnce(
                    searchRoute,
                    semanticGuidance,
                    current,
                    terrain,
                    interiority,
                    semanticCorridorHalfWidth);
            next.set(0, searchRoute.points().getFirst());
            next.set(next.size() - 1, searchRoute.points().getLast());

            Candidate candidate = evaluate(searchRoute, next);
            if (candidate.compareTo(best) < 0) {
                best = candidate;
            }
            current = next;

            if (minimumBendRadius <= EPSILON
                    || candidate.maximumCurvature() * minimumBendRadius <= 1.0 + EPSILON) {
                best = candidate;
                break;
            }
        }

        for (SkyIslandLocalPosition point : best.points()) {
            if (distanceToPolyline(point, semanticGuidance) > semanticCorridorHalfWidth + EPSILON) {
                throw new IllegalStateException("relaxed centerline escaped semantic corridor");
            }
        }

        return new SkyIslandContinuousChannelCenterline(
                searchRoute,
                best.points(),
                best.pathLength(),
                best.maximumSearchDeviation(),
                best.maximumTurnAngle());
    }

    private static List<SkyIslandLocalPosition> relaxOnce(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            List<SkyIslandLocalPosition> current,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(current);
        for (int i = 1; i < current.size() - 1; i++) {
            SkyIslandLocalPosition previous = current.get(i - 1);
            SkyIslandLocalPosition point = current.get(i);
            SkyIslandLocalPosition next = current.get(i + 1);
            SkyIslandLocalPosition midpoint =
                    new SkyIslandLocalPosition(
                            0.5 * (previous.x() + next.x()),
                            0.5 * (previous.z() + next.z()));
            SkyIslandLocalPosition candidate = lerp(point, midpoint, RELAXATION_FRACTION);

            Projection seedProjection = project(candidate, searchRoute.points());
            boolean allowed =
                    distanceToPolyline(candidate, semanticGuidance)
                                    <= semanticCorridorHalfWidth + EPSILON
                            && terrain.sample(candidate)
                                            - terrain.sample(seedProjection.position())
                                    <= MAXIMUM_TERRAIN_RISE_FROM_SEED + EPSILON
                            && interiority.sample(candidate) >= MINIMUM_INTERIORITY;
            if (allowed) {
                result.set(i, candidate);
            }
        }
        return result;
    }

    private static Candidate evaluate(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> points) {
        double maximumSearchDeviation = 0.0;
        for (SkyIslandLocalPosition point : points) {
            maximumSearchDeviation =
                    Math.max(
                            maximumSearchDeviation,
                            project(point, searchRoute.points()).distance());
        }
        return new Candidate(
                List.copyOf(points),
                length(points),
                maximumSearchDeviation,
                maximumTurnAngle(points),
                maximumCurvature(points));
    }

    private static double maximumCurvature(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double al = Math.hypot(ax, az);
            double bl = Math.hypot(bx, bz);
            if (al <= EPSILON || bl <= EPSILON) {
                continue;
            }
            double cosine =
                    Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
            maximum = Math.max(maximum, Math.acos(cosine) / (0.5 * (al + bl)));
        }
        return maximum;
    }

    private static double maximumTurnAngle(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ax = b.x() - a.x();
            double az = b.z() - a.z();
            double bx = c.x() - b.x();
            double bz = c.z() - b.z();
            double al = Math.hypot(ax, az);
            double bl = Math.hypot(bx, bz);
            if (al <= EPSILON || bl <= EPSILON) {
                continue;
            }
            double cosine =
                    Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
            maximum = Math.max(maximum, Math.acos(cosine));
        }
        return maximum;
    }

    private static double distanceToPolyline(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        return project(point, polyline).distance();
    }

    private static Projection project(
            SkyIslandLocalPosition point,
            List<SkyIslandLocalPosition> polyline) {
        Projection best = null;
        for (int i = 1; i < polyline.size(); i++) {
            SkyIslandLocalPosition a = polyline.get(i - 1);
            SkyIslandLocalPosition b = polyline.get(i);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t =
                    lengthSquared <= EPSILON
                            ? 0.0
                            : Math.max(
                                    0.0,
                                    Math.min(
                                            1.0,
                                            ((point.x() - a.x()) * dx
                                                            + (point.z() - a.z()) * dz)
                                                    / lengthSquared));
            SkyIslandLocalPosition projected =
                    new SkyIslandLocalPosition(a.x() + t * dx, a.z() + t * dz);
            Projection candidate =
                    new Projection(
                            projected,
                            Math.hypot(point.x() - projected.x(), point.z() - projected.z()));
            if (best == null || candidate.distance() < best.distance() - EPSILON) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "polyline projection");
    }

    private static double length(List<SkyIslandLocalPosition> points) {
        double result = 0.0;
        for (int i = 1; i < points.size(); i++) {
            result += Math.hypot(
                    points.get(i).x() - points.get(i - 1).x(),
                    points.get(i).z() - points.get(i - 1).z());
        }
        return result;
    }

    private static SkyIslandLocalPosition lerp(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            double t) {
        return new SkyIslandLocalPosition(
                a.x() + (b.x() - a.x()) * t,
                a.z() + (b.z() - a.z()) * t);
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

    private record Projection(SkyIslandLocalPosition position, double distance) {}

    private record Candidate(
            List<SkyIslandLocalPosition> points,
            double pathLength,
            double maximumSearchDeviation,
            double maximumTurnAngle,
            double maximumCurvature)
            implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate other) {
            int curvature = Double.compare(maximumCurvature, other.maximumCurvature);
            if (curvature != 0) {
                return curvature;
            }
            int deviation = Double.compare(maximumSearchDeviation, other.maximumSearchDeviation);
            if (deviation != 0) {
                return deviation;
            }
            return Double.compare(pathLength, other.pathLength);
        }
    }
}
