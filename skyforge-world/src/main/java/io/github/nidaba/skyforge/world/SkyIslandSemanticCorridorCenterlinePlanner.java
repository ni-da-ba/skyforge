package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

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
        return refine(
                searchRoute,
                semanticGuidance,
                terrain,
                interiority,
                planningSpacing,
                semanticCorridorHalfWidth,
                minimumBendRadius,
                ignored -> 0.0,
                null);
    }

    static SkyIslandContinuousChannelCenterline refine(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing,
            double semanticCorridorHalfWidth,
            double minimumBendRadius,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        Objects.requireNonNull(searchRoute, "searchRoute");
        Objects.requireNonNull(bankfullHalfWidthAtStation, "bankfullHalfWidthAtStation");
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
        Candidate best = evaluate(
                searchRoute, current, headEnvelopeGap, bankfullHalfWidthAtStation);
        for (int sweep = 0; sweep < MAXIMUM_RELAXATION_SWEEPS; sweep++) {
            List<SkyIslandLocalPosition> next = relaxOnce(
                    searchRoute,
                    semanticGuidance,
                    current,
                    terrain,
                    interiority,
                    semanticCorridorHalfWidth,
                    bankfullHalfWidthAtStation,
                    headEnvelopeGap);
            next.set(0, searchRoute.points().getFirst());
            next.set(next.size() - 1, searchRoute.points().getLast());

            Candidate candidate = evaluate(
                    searchRoute, next, headEnvelopeGap, bankfullHalfWidthAtStation);
            if (candidate.compareTo(best, minimumBendRadius) < 0) {
                best = candidate;
            }
            boolean unchanged = next.equals(current);
            current = next;

            if (headEnvelopeGap == null
                    && (minimumBendRadius <= EPSILON
                            || candidate.maximumCurvature() * minimumBendRadius
                                    <= 1.0 + EPSILON)) {
                best = candidate;
                break;
            }
            if (headEnvelopeGap != null
                    && (unchanged
                            || (candidate.maximumHeadEnvelopeGap() <= EPSILON
                                    && candidate.integratedSquaredHeadEnvelopeGap() <= EPSILON
                                    && (minimumBendRadius <= EPSILON
                                            || candidate.maximumCurvature() * minimumBendRadius
                                                    <= 1.0 + EPSILON))) {
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
            double semanticCorridorHalfWidth,
            DoubleUnaryOperator bankfullHalfWidthAtStation,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(current);
        double[] stations = stations(current);
        for (int i = 1; i < current.size() - 1; i++) {
            SkyIslandLocalPosition previous = current.get(i - 1);
            SkyIslandLocalPosition point = current.get(i);
            SkyIslandLocalPosition next = current.get(i + 1);
            SkyIslandLocalPosition midpoint =
                    new SkyIslandLocalPosition(
                            0.5 * (previous.x() + next.x()),
                            0.5 * (previous.z() + next.z()));
            SkyIslandLocalPosition smoothed = lerp(point, midpoint, RELAXATION_FRACTION);
            List<SkyIslandLocalPosition> options = new ArrayList<>(5);
            options.add(smoothed);
            double station = stations[i];
            Vector tangent = tangent(previous, next);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station);
            if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                throw new IllegalArgumentException(
                        "bankfull half-width must be finite and non-negative");
            }
            if (headEnvelopeGap != null && halfWidth > EPSILON) {
                double currentGap =
                        checkedGap(headEnvelopeGap, point, station, tangent, halfWidth);
                if (currentGap > EPSILON) {
                    for (int step = 1; step <= 2; step++) {
                        double offset = 0.25 * step * halfWidth;
                        options.add(new SkyIslandLocalPosition(
                                point.x() - normal.x() * offset,
                                point.z() - normal.z() * offset));
                        options.add(new SkyIslandLocalPosition(
                                point.x() + normal.x() * offset,
                                point.z() + normal.z() * offset));
                    }
                }
            }

            SkyIslandLocalPosition selected = point;
            for (SkyIslandLocalPosition option : options) {
                if (!allowed(
                        option,
                        searchRoute,
                        semanticGuidance,
                        terrain,
                        interiority,
                        semanticCorridorHalfWidth)) {
                    continue;
                }
                if (headEnvelopeGap == null
                        || compareLocalCandidates(
                                        option,
                                        selected,
                                        previous,
                                        next,
                                        station,
                                        halfWidth,
                                        tangent,
                                        headEnvelopeGap,
                                        searchRoute)
                                < 0) {
                    selected = option;
                }
            }
            result.set(i, selected);
        }
        return result;
    }

    private static Candidate evaluate(
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> points,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            DoubleUnaryOperator bankfullHalfWidthAtStation) {
        double maximumSearchDeviation = 0.0;
        double[] gaps = new double[points.size()];
        double[] station = stations(points);
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition point = points.get(i);
            maximumSearchDeviation =
                    Math.max(
                            maximumSearchDeviation,
                            project(point, searchRoute.points()).distance());
            if (headEnvelopeGap != null) {
                Vector tangent = tangentAt(points, i);
                double halfWidth = bankfullHalfWidthAtStation.applyAsDouble(station[i]);
                if (!Double.isFinite(halfWidth) || halfWidth < 0.0) {
                    throw new IllegalArgumentException(
                            "bankfull half-width must be finite and non-negative");
                }
                gaps[i] = checkedGap(
                        headEnvelopeGap, point, station[i], tangent, halfWidth);
            }
        }
        double pathLength = length(points);
        double integratedSquaredGap = 0.0;
        if (headEnvelopeGap != null && pathLength > EPSILON) {
            for (int i = 0; i + 1 < points.size(); i++) {
                double ds = pathLength * (station[i + 1] - station[i]);
                integratedSquaredGap +=
                        0.5 * (gaps[i] * gaps[i] + gaps[i + 1] * gaps[i + 1]) * ds;
            }
            integratedSquaredGap /= pathLength;
        }
        return new Candidate(
                List.copyOf(points),
                pathLength,
                maximumSearchDeviation,
                maximumTurnAngle(points),
                maximumCurvature(points),
                maximum(gaps),
                integratedSquaredGap);
    }

    private static boolean allowed(
            SkyIslandLocalPosition candidate,
            SkyIslandGeomorphicCandidateRoute searchRoute,
            List<SkyIslandLocalPosition> semanticGuidance,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double semanticCorridorHalfWidth) {
        Projection seedProjection = project(candidate, searchRoute.points());
        return distanceToPolyline(candidate, semanticGuidance)
                        <= semanticCorridorHalfWidth + EPSILON
                && terrain.sample(candidate) - terrain.sample(seedProjection.position())
                        <= MAXIMUM_TERRAIN_RISE_FROM_SEED + EPSILON
                && interiority.sample(candidate) >= MINIMUM_INTERIORITY;
    }

    private static int compareLocalCandidates(
            SkyIslandLocalPosition first,
            SkyIslandLocalPosition second,
            SkyIslandLocalPosition previous,
            SkyIslandLocalPosition next,
            double station,
            double halfWidth,
            Vector tangent,
            SkyIslandCenterlineHeadEnvelopeGap headEnvelopeGap,
            SkyIslandGeomorphicCandidateRoute searchRoute) {
        int gap = Double.compare(
                checkedGap(headEnvelopeGap, first, station, tangent, halfWidth),
                checkedGap(headEnvelopeGap, second, station, tangent, halfWidth));
        if (gap != 0) {
            return gap;
        }
        int curvature = Double.compare(
                localCurvature(previous, first, next),
                localCurvature(previous, second, next));
        if (curvature != 0) {
            return curvature;
        }
        return Double.compare(
                project(first, searchRoute.points()).distance(),
                project(second, searchRoute.points()).distance());
    }

    private static double localCurvature(
            SkyIslandLocalPosition previous,
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition next) {
        double ax = point.x() - previous.x();
        double az = point.z() - previous.z();
        double bx = next.x() - point.x();
        double bz = next.z() - point.z();
        double al = Math.hypot(ax, az);
        double bl = Math.hypot(bx, bz);
        if (al <= EPSILON || bl <= EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (al * bl)));
        return Math.acos(cosine) / (0.5 * (al + bl));
    }

    private static double checkedGap(
            SkyIslandCenterlineHeadEnvelopeGap evaluator,
            SkyIslandLocalPosition position,
            double station,
            Vector tangent,
            double halfWidth) {
        double gap =
                evaluator.evaluate(
                        position, station, tangent.x(), tangent.z(), halfWidth);
        if (!Double.isFinite(gap) || gap < 0.0) {
            throw new IllegalArgumentException(
                    "head-envelope gap evaluator must return a finite non-negative value");
        }
        return gap;
    }

    private static double[] stations(List<SkyIslandLocalPosition> points) {
        double[] result = new double[points.size()];
        double total = length(points);
        if (total <= EPSILON) {
            return result;
        }
        for (int i = 1; i < points.size(); i++) {
            result[i] = result[i - 1]
                    + Math.hypot(
                            points.get(i).x() - points.get(i - 1).x(),
                            points.get(i).z() - points.get(i - 1).z());
        }
        for (int i = 0; i < result.length; i++) {
            result[i] /= total;
        }
        return result;
    }

    private static Vector tangent(SkyIslandLocalPosition previous, SkyIslandLocalPosition next) {
        double dx = next.x() - previous.x();
        double dz = next.z() - previous.z();
        double length = Math.hypot(dx, dz);
        if (length <= EPSILON) {
            throw new IllegalStateException("centerline tangent must have positive length");
        }
        return new Vector(dx / length, dz / length);
    }

    private static Vector tangentAt(List<SkyIslandLocalPosition> points, int index) {
        return index == 0
                ? tangent(points.get(0), points.get(1))
                : index == points.size() - 1
                        ? tangent(points.get(index - 1), points.get(index))
                        : tangent(points.get(index - 1), points.get(index + 1));
    }

    private static double maximum(double[] values) {
        double result = 0.0;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
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

    private record Vector(double x, double z) {}

    private record Projection(SkyIslandLocalPosition position, double distance) {}

    private record Candidate(
            List<SkyIslandLocalPosition> points,
            double pathLength,
            double maximumSearchDeviation,
            double maximumTurnAngle,
            double maximumCurvature,
            double maximumHeadEnvelopeGap,
            double integratedSquaredHeadEnvelopeGap) {
        private int compareTo(Candidate other, double minimumBendRadius) {
            double curvatureExcess =
                    Math.max(0.0, maximumCurvature * minimumBendRadius - 1.0);
            double otherCurvatureExcess =
                    Math.max(0.0, other.maximumCurvature * minimumBendRadius - 1.0);
            int excess = Double.compare(curvatureExcess, otherCurvatureExcess);
            if (excess != 0) {
                return excess;
            }
            int maximumGap = Double.compare(maximumHeadEnvelopeGap, other.maximumHeadEnvelopeGap);
            if (maximumGap != 0) {
                return maximumGap;
            }
            int integratedGap =
                    Double.compare(
                            integratedSquaredHeadEnvelopeGap,
                            other.integratedSquaredHeadEnvelopeGap);
            if (integratedGap != 0) {
                return integratedGap;
            }
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
