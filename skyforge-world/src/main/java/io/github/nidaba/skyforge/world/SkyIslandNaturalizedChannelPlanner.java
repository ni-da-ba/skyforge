package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces deterministic sub-grid channel centerlines while preserving accepted routing topology. */
public final class SkyIslandNaturalizedChannelPlanner {
    public static final int SUBDIVISIONS = 8;
    public static final double MAX_CHORD_DEVIATION_SPACING_FRACTION = 0.42;
    private static final double[] TERRAIN_BEND_FRACTIONS =
            {0.0, -0.25, 0.25, -0.50, 0.50, -0.75, 0.75, -1.0, 1.0};

    private SkyIslandNaturalizedChannelPlanner() {}

    /** Historical/raw AUTH-0017 geometry for the complete visible-channel diagnostic. */
    public static SkyIslandNaturalizedChannelPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandChannelProfilePlanner.plan(descriptor).profiles());
    }

    /** Naturalizes one explicit channel-profile subset without changing any graph node. */
    public static SkyIslandNaturalizedChannelPlan plan(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles) {
        Objects.requireNonNull(descriptor, "descriptor");
        profiles = List.copyOf(profiles);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandSemanticFieldSet semanticFields = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandSemanticField terrain = semanticFields.elevationTendency();
        SkyIslandSemanticField interiority = semanticFields.interiority();
        double spacing = watershed.spacing();

        Map<Integer, SkyIslandChannelProfile> outgoing = new HashMap<>();
        Map<Integer, List<SkyIslandChannelProfile>> incoming = new HashMap<>();
        Map<Integer, SkyIslandLocalPosition> positions = new HashMap<>();

        for (SkyIslandChannelProfile profile : profiles) {
            SkyIslandChannelSegment segment = profile.segment();
            SkyIslandChannelProfile previous = outgoing.put(segment.sourceCellIndex(), profile);
            if (previous != null) {
                throw new IllegalStateException("accepted channel graph has multiple downstream reaches from one cell");
            }
            incoming.computeIfAbsent(segment.downstreamCellIndex(), ignored -> new ArrayList<>()).add(profile);
            positions.put(segment.sourceCellIndex(), segment.start());
            positions.put(segment.downstreamCellIndex(), segment.end());
        }
        incoming.values().forEach(list -> list.sort(Comparator
                .comparingDouble((SkyIslandChannelProfile p) -> p.segment().relativeDischarge())
                .reversed()
                .thenComparingInt(p -> p.segment().sourceCellIndex())));

        Map<Integer, Vector> tangents = new HashMap<>();
        for (int cellIndex : positions.keySet()) {
            tangents.put(cellIndex, tangent(cellIndex, positions, outgoing, incoming));
        }

        List<SkyIslandNaturalizedChannelPath> paths = new ArrayList<>(profiles.size());
        for (SkyIslandChannelProfile profile : profiles) {
            paths.add(naturalize(profile, tangents, terrain, interiority, spacing));
        }
        return new SkyIslandNaturalizedChannelPlan(descriptor, spacing, paths);
    }

    private static SkyIslandNaturalizedChannelPath naturalize(
            SkyIslandChannelProfile profile,
            Map<Integer, Vector> tangents,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double spacing) {
        SkyIslandChannelSegment segment = profile.segment();
        SkyIslandLocalPosition start = segment.start();
        SkyIslandLocalPosition end = segment.end();
        double chordX = end.x() - start.x();
        double chordZ = end.z() - start.z();
        double chordLength = Math.hypot(chordX, chordZ);
        if (chordLength <= 0.0) {
            throw new IllegalStateException("accepted channel segment has zero geometric length");
        }

        Vector startTangent = tangents.getOrDefault(
                segment.sourceCellIndex(), normalize(chordX, chordZ));
        Vector endTangent = tangents.getOrDefault(
                segment.downstreamCellIndex(), normalize(chordX, chordZ));

        double controlFraction = switch (profile.kind()) {
            case ALLUVIAL -> 0.42;
            case INCISED -> 0.34;
            case CASCADE -> 0.24;
        };
        double controlLength = Math.min(spacing * controlFraction, chordLength * 0.48);
        Point c1 = new Point(
                start.x() + startTangent.x() * controlLength,
                start.z() + startTangent.z() * controlLength);
        Point c2 = new Point(
                end.x() - endTangent.x() * controlLength,
                end.z() - endTangent.z() * controlLength);

        Vector normal = normalize(-chordZ, chordX);
        double reachBendScale = switch (profile.kind()) {
            case ALLUVIAL -> 0.38;
            case INCISED -> 0.25;
            case CASCADE -> 0.14;
        };
        double maximumAmplitude = spacing
                * reachBendScale
                * (0.58 + 0.42 * profile.bankfullWidthPotential())
                * (0.68 + 0.32 * (1.0 - profile.gradientPotential()));

        Candidate best = null;
        for (double bendFraction : TERRAIN_BEND_FRACTIONS) {
            Candidate candidate = candidate(
                    start,
                    end,
                    chordX,
                    chordZ,
                    chordLength,
                    c1,
                    c2,
                    normal,
                    maximumAmplitude * bendFraction,
                    terrain,
                    interiority,
                    spacing);
            if (best == null || candidate.cost() < best.cost() - 1.0e-12) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalStateException("terrain-aware naturalization produced no candidate");
        }
        return new SkyIslandNaturalizedChannelPath(
                profile,
                best.points(),
                chordLength,
                best.pathLength(),
                best.maxDeviation());
    }

    private static Candidate candidate(
            SkyIslandLocalPosition start,
            SkyIslandLocalPosition end,
            double chordX,
            double chordZ,
            double chordLength,
            Point c1,
            Point c2,
            Vector normal,
            double bendAmplitude,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double spacing) {
        List<SkyIslandLocalPosition> points = new ArrayList<>(SUBDIVISIONS + 1);
        double maxDeviation = 0.0;
        SkyIslandLocalPosition previous = null;
        double pathLength = 0.0;

        for (int i = 0; i <= SUBDIVISIONS; i++) {
            double t = (double) i / SUBDIVISIONS;
            SkyIslandLocalPosition point;
            if (i == 0) {
                point = start;
            } else if (i == SUBDIVISIONS) {
                point = end;
            } else {
                Point curved = cubicBezier(start, c1, c2, end, t);
                double bendEnvelope = Math.pow(Math.sin(Math.PI * t), 2.0);
                double candidateX = curved.x() + normal.x() * bendAmplitude * bendEnvelope;
                double candidateZ = curved.z() + normal.z() * bendAmplitude * bendEnvelope;

                double baselineX = start.x() + chordX * t;
                double baselineZ = start.z() + chordZ * t;
                double deviationX = candidateX - baselineX;
                double deviationZ = candidateZ - baselineZ;
                double deviation = Math.hypot(deviationX, deviationZ);
                double allowed = spacing
                        * MAX_CHORD_DEVIATION_SPACING_FRACTION
                        * Math.sin(Math.PI * t);
                if (deviation > allowed && deviation > 0.0) {
                    double scale = allowed / deviation;
                    candidateX = baselineX + deviationX * scale;
                    candidateZ = baselineZ + deviationZ * scale;
                    deviation = allowed;
                }
                maxDeviation = Math.max(maxDeviation, deviation);
                point = new SkyIslandLocalPosition(candidateX, candidateZ);
            }

            if (previous != null) {
                pathLength += distance(previous, point);
            }
            points.add(point);
            previous = point;
        }

        double cost = terrainAlignmentCost(points, terrain, interiority, spacing)
                + 0.08 * Math.max(0.0, pathLength / chordLength - 1.0);
        return new Candidate(List.copyOf(points), pathLength, maxDeviation, cost);
    }

    /**
     * Scores one candidate centerline against the pre-channel authored terrain.
     *
     * <p>Positive downstream climbs and ridge crossings are expensive. Running through a local
     * valley floor is rewarded. This keeps naturalization deterministic while ensuring curvature is
     * selected by the landform rather than by an unrelated seed hash.
     */
    private static double terrainAlignmentCost(
            List<SkyIslandLocalPosition> points,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority,
            double spacing) {
        double ascent = 0.0;
        double ridge = 0.0;
        double valley = 0.0;
        double exterior = 0.0;
        double probeDistance = spacing * 0.22;

        for (int i = 1; i < points.size(); i++) {
            double previous = terrain.sample(points.get(i - 1));
            double current = terrain.sample(points.get(i));
            ascent += Math.max(0.0, current - previous);
        }

        for (int i = 1; i + 1 < points.size(); i++) {
            SkyIslandLocalPosition previous = points.get(i - 1);
            SkyIslandLocalPosition current = points.get(i);
            SkyIslandLocalPosition next = points.get(i + 1);
            Vector tangent = normalize(next.x() - previous.x(), next.z() - previous.z());
            Vector localNormal = new Vector(-tangent.z(), tangent.x());
            SkyIslandLocalPosition left = new SkyIslandLocalPosition(
                    current.x() + localNormal.x() * probeDistance,
                    current.z() + localNormal.z() * probeDistance);
            SkyIslandLocalPosition right = new SkyIslandLocalPosition(
                    current.x() - localNormal.x() * probeDistance,
                    current.z() - localNormal.z() * probeDistance);

            double centerElevation = terrain.sample(current);
            double sideMean = 0.5 * (terrain.sample(left) + terrain.sample(right));
            ridge += Math.max(0.0, centerElevation - sideMean);
            valley += Math.max(0.0, sideMean - centerElevation);
            exterior += Math.max(0.0, 0.035 - interiority.sample(current));
        }

        return 9.0 * ascent + 4.0 * ridge - 1.5 * valley + 6.0 * exterior;
    }

    private static Vector tangent(
            int cellIndex,
            Map<Integer, SkyIslandLocalPosition> positions,
            Map<Integer, SkyIslandChannelProfile> outgoing,
            Map<Integer, List<SkyIslandChannelProfile>> incoming) {
        SkyIslandLocalPosition current = positions.get(cellIndex);
        if (current == null) {
            throw new IllegalStateException("channel tangent references missing node position");
        }
        SkyIslandChannelProfile out = outgoing.get(cellIndex);
        List<SkyIslandChannelProfile> in = incoming.getOrDefault(cellIndex, List.of());
        SkyIslandChannelProfile strongestIn = in.isEmpty() ? null : in.getFirst();

        if (out != null && strongestIn != null) {
            SkyIslandLocalPosition upstream = strongestIn.segment().start();
            SkyIslandLocalPosition downstream = out.segment().end();
            return normalize(downstream.x() - upstream.x(), downstream.z() - upstream.z());
        }
        if (out != null) {
            SkyIslandLocalPosition downstream = out.segment().end();
            return normalize(downstream.x() - current.x(), downstream.z() - current.z());
        }
        if (strongestIn != null) {
            SkyIslandLocalPosition upstream = strongestIn.segment().start();
            return normalize(current.x() - upstream.x(), current.z() - upstream.z());
        }
        throw new IllegalStateException("isolated node cannot define a channel tangent");
    }

    private static Point cubicBezier(
            SkyIslandLocalPosition start,
            Point c1,
            Point c2,
            SkyIslandLocalPosition end,
            double t) {
        double u = 1.0 - t;
        double b0 = u * u * u;
        double b1 = 3.0 * u * u * t;
        double b2 = 3.0 * u * t * t;
        double b3 = t * t * t;
        return new Point(
                b0 * start.x() + b1 * c1.x() + b2 * c2.x() + b3 * end.x(),
                b0 * start.z() + b1 * c1.z() + b2 * c2.z() + b3 * end.z());
    }

    private static Vector normalize(double x, double z) {
        double length = Math.hypot(x, z);
        if (length <= 1.0e-12) {
            return new Vector(1.0, 0.0);
        }
        return new Vector(x / length, z / length);
    }

    private static double distance(SkyIslandLocalPosition a, SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private record Vector(double x, double z) {}
    private record Point(double x, double z) {}
    private record Candidate(
            List<SkyIslandLocalPosition> points,
            double pathLength,
            double maxDeviation,
            double cost) {}
}
