package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts a fine-lattice least-cost route into a bounded continuous channel centerline.
 *
 * <p>Chaikin subdivision is used only as a shape regularizer. The fine search path remains the
 * authority corridor. Smoothed and resampled points are projected back to that path whenever they
 * stray too far, climb materially higher pre-hydrologic terrain, or leave the authored island.
 */
public final class SkyIslandContinuousChannelCenterlinePlanner {
    public static final int CHAIKIN_ITERATIONS = 2;
    public static final double MAXIMUM_DEVIATION_SPACING_FRACTION = 0.70;
    public static final double RESAMPLE_SPACING_FRACTION = 0.125;
    public static final double MAXIMUM_SMOOTHING_TERRAIN_RISE = 0.015;
    public static final double MINIMUM_INTERIORITY = 0.025;

    private static final double EPSILON = 1.0e-12;

    private SkyIslandContinuousChannelCenterlinePlanner() {}

    public static SkyIslandContinuousChannelCenterline refine(
            SkyIslandGeomorphicCandidateRoute route,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double planningSpacing) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }

        List<SkyIslandLocalPosition> original = route.points();
        List<SkyIslandLocalPosition> smoothed = original;
        for (int i = 0; i < CHAIKIN_ITERATIONS; i++) {
            smoothed = chaikin(smoothed);
        }

        double maximumDeviation = planningSpacing * MAXIMUM_DEVIATION_SPACING_FRACTION;
        List<SkyIslandLocalPosition> constrained =
                constrain(smoothed, original, terrain, interiority, maximumDeviation);

        double targetSpacing = planningSpacing * RESAMPLE_SPACING_FRACTION;
        List<SkyIslandLocalPosition> resampled = resample(constrained, targetSpacing);
        resampled = constrain(resampled, original, terrain, interiority, maximumDeviation);
        resampled.set(0, original.getFirst());
        resampled.set(resampled.size() - 1, original.getLast());

        double length = length(resampled);
        double measuredMaximumDeviation = 0.0;
        for (SkyIslandLocalPosition point : resampled) {
            measuredMaximumDeviation =
                    Math.max(measuredMaximumDeviation, project(point, original).distance());
        }
        if (measuredMaximumDeviation > maximumDeviation + EPSILON) {
            throw new IllegalStateException("continuous centerline escaped bounded search corridor");
        }

        return new SkyIslandContinuousChannelCenterline(
                route,
                resampled,
                length,
                measuredMaximumDeviation,
                maximumTurnAngle(resampled));
    }

    private static List<SkyIslandLocalPosition> constrain(
            List<SkyIslandLocalPosition> candidates,
            List<SkyIslandLocalPosition> authority,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double maximumDeviation) {
        List<SkyIslandLocalPosition> result = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            if (i == 0) {
                result.add(authority.getFirst());
                continue;
            }
            if (i == candidates.size() - 1) {
                result.add(authority.getLast());
                continue;
            }

            SkyIslandLocalPosition candidate = candidates.get(i);
            Projection projection = project(candidate, authority);
            double candidateRise =
                    terrain.sample(candidate) - terrain.sample(projection.position());
            boolean accepted =
                    projection.distance() <= maximumDeviation + EPSILON
                            && candidateRise <= MAXIMUM_SMOOTHING_TERRAIN_RISE + EPSILON
                            && interiority.sample(candidate) >= MINIMUM_INTERIORITY;
            result.add(accepted ? candidate : projection.position());
        }
        return result;
    }

    private static List<SkyIslandLocalPosition> chaikin(List<SkyIslandLocalPosition> input) {
        if (input.size() <= 2) {
            return input;
        }
        List<SkyIslandLocalPosition> result = new ArrayList<>(input.size() * 2);
        result.add(input.getFirst());
        for (int i = 0; i < input.size() - 1; i++) {
            SkyIslandLocalPosition a = input.get(i);
            SkyIslandLocalPosition b = input.get(i + 1);
            SkyIslandLocalPosition q = lerp(a, b, 0.25);
            SkyIslandLocalPosition r = lerp(a, b, 0.75);
            if (i > 0) {
                result.add(q);
            }
            if (i < input.size() - 2) {
                result.add(r);
            }
        }
        result.add(input.getLast());
        return List.copyOf(result);
    }

    private static List<SkyIslandLocalPosition> resample(
            List<SkyIslandLocalPosition> input,
            double targetSpacing) {
        double total = length(input);
        if (total <= targetSpacing + EPSILON) {
            return new ArrayList<>(input);
        }

        int segments = Math.max(1, (int) Math.ceil(total / targetSpacing));
        double spacing = total / segments;
        List<SkyIslandLocalPosition> result = new ArrayList<>(segments + 1);
        result.add(input.getFirst());

        int sourceSegment = 1;
        double sourceStartDistance = 0.0;
        double sourceEndDistance = distance(input.getFirst(), input.get(1));
        for (int i = 1; i < segments; i++) {
            double target = i * spacing;
            while (sourceSegment < input.size() - 1
                    && target > sourceEndDistance + EPSILON) {
                sourceStartDistance = sourceEndDistance;
                sourceSegment++;
                sourceEndDistance += distance(
                        input.get(sourceSegment - 1),
                        input.get(sourceSegment));
            }
            SkyIslandLocalPosition a = input.get(sourceSegment - 1);
            SkyIslandLocalPosition b = input.get(sourceSegment);
            double span = Math.max(EPSILON, sourceEndDistance - sourceStartDistance);
            double t = Math.max(0.0, Math.min(1.0, (target - sourceStartDistance) / span));
            result.add(lerp(a, b, t));
        }
        result.add(input.getLast());
        return result;
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
            double t;
            if (lengthSquared <= EPSILON) {
                t = 0.0;
            } else {
                t = ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz)
                        / lengthSquared;
                t = Math.max(0.0, Math.min(1.0, t));
            }
            SkyIslandLocalPosition projected =
                    new SkyIslandLocalPosition(a.x() + t * dx, a.z() + t * dz);
            Projection candidate = new Projection(projected, distance(point, projected));
            if (best == null || candidate.distance() < best.distance() - EPSILON) {
                best = candidate;
            }
        }
        return Objects.requireNonNull(best, "polyline projection");
    }

    private static double maximumTurnAngle(List<SkyIslandLocalPosition> points) {
        double maximum = 0.0;
        for (int i = 1; i < points.size() - 1; i++) {
            double ax = points.get(i).x() - points.get(i - 1).x();
            double az = points.get(i).z() - points.get(i - 1).z();
            double bx = points.get(i + 1).x() - points.get(i).x();
            double bz = points.get(i + 1).z() - points.get(i).z();
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

    private static double length(List<SkyIslandLocalPosition> points) {
        double result = 0.0;
        for (int i = 1; i < points.size(); i++) {
            result += distance(points.get(i - 1), points.get(i));
        }
        return result;
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private static SkyIslandLocalPosition lerp(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            double t) {
        return new SkyIslandLocalPosition(
                a.x() + (b.x() - a.x()) * t,
                a.z() + (b.z() - a.z()) * t);
    }

    private record Projection(SkyIslandLocalPosition position, double distance) {}
}
