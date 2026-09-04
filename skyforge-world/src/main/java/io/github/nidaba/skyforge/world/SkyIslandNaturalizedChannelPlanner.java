package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces deterministic sub-grid channel centerlines while preserving accepted routing topology.
 *
 * <p>AUTH-0017 never moves graph nodes or changes downstream ownership. Shared node tangents smooth
 * lattice corners, while a small profile-dependent interior bend adds bounded sub-grid variation.
 */
public final class SkyIslandNaturalizedChannelPlanner {
    public static final int SUBDIVISIONS = 8;
    public static final double MAX_CHORD_DEVIATION_SPACING_FRACTION = 0.42;

    private SkyIslandNaturalizedChannelPlanner() {}

    public static SkyIslandNaturalizedChannelPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandChannelProfilePlan profiles = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        double spacing = watershed.spacing();

        Map<Integer, SkyIslandChannelProfile> outgoing = new HashMap<>();
        Map<Integer, List<SkyIslandChannelProfile>> incoming = new HashMap<>();
        Map<Integer, SkyIslandLocalPosition> positions = new HashMap<>();

        for (SkyIslandChannelProfile profile : profiles.profiles()) {
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

        List<SkyIslandNaturalizedChannelPath> paths = new ArrayList<>(profiles.profiles().size());
        for (SkyIslandChannelProfile profile : profiles.profiles()) {
            paths.add(naturalize(descriptor, profile, tangents, spacing));
        }
        return new SkyIslandNaturalizedChannelPlan(descriptor, spacing, paths);
    }

    private static SkyIslandNaturalizedChannelPath naturalize(
            SkyIslandDescriptor descriptor,
            SkyIslandChannelProfile profile,
            Map<Integer, Vector> tangents,
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
        double bendScale = switch (profile.kind()) {
            case ALLUVIAL -> 0.16;
            case INCISED -> 0.08;
            case CASCADE -> 0.035;
        };
        double bendAmplitude = spacing
                * bendScale
                * (0.55 + 0.45 * profile.bankfullWidthPotential())
                * (0.65 + 0.35 * (1.0 - profile.gradientPotential()))
                * signedUnit(hashKey(descriptor, segment));

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

        return new SkyIslandNaturalizedChannelPath(
                profile, points, chordLength, pathLength, maxDeviation);
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

    private static long hashKey(SkyIslandDescriptor descriptor, SkyIslandChannelSegment segment) {
        long value = descriptor.authorshipSeed();
        value ^= Long.rotateLeft((long) segment.sourceCellIndex() * 0x9E3779B97F4A7C15L, 17);
        value ^= Long.rotateLeft((long) segment.downstreamCellIndex() * 0xC2B2AE3D27D4EB4FL, 41);
        return mix64(value);
    }

    private static double signedUnit(long value) {
        double unit = (value >>> 11) * 0x1.0p-53;
        return 2.0 * unit - 1.0;
    }

    private static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
}
