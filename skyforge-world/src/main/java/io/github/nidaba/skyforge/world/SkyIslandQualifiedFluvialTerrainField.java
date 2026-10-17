package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Backend-neutral continuous terrain response for accepted fluvial reaches only.
 *
 * <p>Rejected reaches are absent from this field and therefore contribute exactly zero terrain
 * delta. Ordinary reaches may meet only through an exact shared semantic node. If two unrelated
 * reach envelopes overlap at a queried position, evaluation fails closed rather than inventing a
 * local priority rule or deepest-cut composition.
 */
public final class SkyIslandQualifiedFluvialTerrainField implements SkyIslandSemanticField {
    private static final double EPSILON = 1.0e-12;
    private static final double BED_ZONE_FRACTION = 0.18;

    private final SkyIslandSemanticField originalTerrain;
    private final List<SkyIslandHydraulicReachGeometry> acceptedReaches;
    private final java.util.Map<Long, SkyIslandHydraulicCandidateBuilder.Candidate>
            coupledCandidates;
    private final double verticalScale;

    public SkyIslandQualifiedFluvialTerrainField(
            SkyIslandSemanticField originalTerrain,
            List<SkyIslandHydraulicReachGeometry> acceptedReaches) {
        this(originalTerrain, acceptedReaches, java.util.Map.of(), 1.0);
    }

    public SkyIslandQualifiedFluvialTerrainField(
            SkyIslandSemanticField originalTerrain,
            List<SkyIslandHydraulicReachGeometry> acceptedReaches,
            java.util.Map<Long, SkyIslandHydraulicCandidateBuilder.Candidate> coupledCandidates,
            double verticalScale) {
        this.originalTerrain = Objects.requireNonNull(originalTerrain, "originalTerrain");
        if (!(verticalScale > 0.0) || !Double.isFinite(verticalScale)) {
            throw new IllegalArgumentException("verticalScale must be finite and positive");
        }
        this.verticalScale = verticalScale;
        this.coupledCandidates = java.util.Map.copyOf(coupledCandidates);
        this.acceptedReaches = acceptedReaches.stream()
                .map(reach -> Objects.requireNonNull(reach, "accepted reach"))
                .sorted(Comparator
                        .comparingInt((SkyIslandHydraulicReachGeometry reach) ->
                                reach.geomorphicRoute().semanticReach().startCellIndex())
                        .thenComparingInt(reach ->
                                reach.geomorphicRoute().semanticReach().endCellIndex()))
                .toList();
    }

    @Override
    public double sample(SkyIslandLocalPosition position) {
        return sampleDetailed(position).targetTerrainPotential();
    }

    public SkyIslandQualifiedFluvialSample sampleDetailed(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double original = clamp01(originalTerrain.sample(position));

        Candidate selected = null;
        for (SkyIslandHydraulicReachGeometry reach : acceptedReaches) {
            long identity = identity(reach.geomorphicRoute().semanticReach().startCellIndex(),
                    reach.geomorphicRoute().semanticReach().endCellIndex());
            Candidate candidate = candidate(
                    reach, position, original, coupledCandidates.get(identity));
            if (candidate == null) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
                continue;
            }
            throw new IllegalStateException(
                    "qualified fluvial reach overlap lacks explicit transition ownership: "
                            + candidate.semanticIdentity()
                            + " and "
                            + selected.semanticIdentity());
        }

        if (selected == null) {
            return SkyIslandQualifiedFluvialSample.unaffected(original);
        }
        return selected.sample();
    }

    public List<SkyIslandHydraulicReachGeometry> acceptedReaches() {
        return acceptedReaches;
    }

    private Candidate candidate(
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandLocalPosition position,
            double originalTerrain,
            SkyIslandHydraulicCandidateBuilder.Candidate coupled) {
        Projection projection = nearestProjection(reach, position);
        if (projection == null) {
            return null;
        }

        SkyIslandHydraulicGeometrySample a = reach.samples().get(projection.segmentIndex());
        SkyIslandHydraulicGeometrySample b = reach.samples().get(projection.segmentIndex() + 1);
        double fraction = projection.segmentFraction();
        double bankfull = lerp(a.bankfullHalfWidth(), b.bankfullHalfWidth(), fraction);
        double waterSurface = lerp(a.waterSurfacePotential(), b.waterSurfacePotential(), fraction);
        double bed = lerp(a.bedElevationPotential(), b.bedElevationPotential(), fraction);
        double discharge = lerp(a.relativeDischarge(), b.relativeDischarge(), fraction);
        double station = lerp(a.stationFraction(), b.stationFraction(), fraction);

        SkyIslandChannelProfileKind kind =
                profileKind(reach.geomorphicRoute().semanticReach().profiles(), station);
        double valleyHalfWidth = bankfull * valleyMultiplier(kind);
        double distance = projection.distance();
        if (distance > valleyHalfWidth + EPSILON) {
            return null;
        }

        SkyIslandQualifiedFluvialZone zone;
        double target;
        boolean wet = false;
        if (distance <= bankfull + EPSILON) {
            double normalized = bankfull <= EPSILON ? 0.0 : clamp01(distance / bankfull);
            double section =
                    bed
                            + Math.max(0.0, waterSurface - bed)
                                    * Math.pow(normalized, crossSectionExponent(kind));
            target = Math.min(originalTerrain, section);
            wet = target < waterSurface - EPSILON;
            zone = normalized <= BED_ZONE_FRACTION
                    ? SkyIslandQualifiedFluvialZone.CHANNEL_BED
                    : SkyIslandQualifiedFluvialZone.BANKFULL_CORRIDOR;
        } else {
            double recovery =
                    clamp01((distance - bankfull) / Math.max(EPSILON, valleyHalfWidth - bankfull));
            double blend = smoothstep(recovery);
            double recoveryTarget =
                    waterSurface + (originalTerrain - waterSurface) * blend;
            target = Math.min(originalTerrain, recoveryTarget);
            zone = SkyIslandQualifiedFluvialZone.VALLEY_RECOVERY;
        }

        if (coupled != null) {
            double coupledTarget = coupled.sampleTerrainMetres(position) / verticalScale;
            if (!Double.isFinite(coupledTarget) || coupledTarget < -EPSILON || coupledTarget > 1.0 + EPSILON) {
                throw new IllegalStateException("coupled candidate terrain exceeds normalized relief bounds");
            }
            if (coupledTarget > originalTerrain + EPSILON) {
                throw new IllegalStateException("coupled candidate terrain may not raise authored terrain");
            }
            target = Math.max(0.0, Math.min(originalTerrain, coupledTarget));
            wet = zone != SkyIslandQualifiedFluvialZone.VALLEY_RECOVERY
                    && target < waterSurface - EPSILON;
        }

        target = clamp01(target);
        SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
        SkyIslandQualifiedFluvialProvenance provenance =
                new SkyIslandQualifiedFluvialProvenance(
                        semantic.startCellIndex(), semantic.endCellIndex(), kind);
        SkyIslandQualifiedFluvialSample sample =
                new SkyIslandQualifiedFluvialSample(
                        originalTerrain,
                        target,
                        target - originalTerrain,
                        wet,
                        clamp01(waterSurface),
                        zone,
                        Optional.of(provenance));

        double normalizedInfluence =
                distance
                        / Math.max(
                                EPSILON,
                                zone == SkyIslandQualifiedFluvialZone.VALLEY_RECOVERY
                                        ? valleyHalfWidth
                                        : bankfull);
        return new Candidate(
                sample,
                normalizedInfluence,
                discharge,
                semantic.startCellIndex(),
                semantic.endCellIndex());
    }

    private static Projection nearestProjection(
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandLocalPosition position) {
        List<SkyIslandLocalPosition> points = reach.centerline().points();
        List<SkyIslandHydraulicGeometrySample> samples = reach.samples();
        if (points.size() < 2 || points.size() != samples.size()) {
            return null;
        }

        Projection nearest = null;
        for (int i = 0; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i);
            SkyIslandLocalPosition b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double fraction = lengthSquared <= EPSILON ? 0.0 : clamp01(
                    ((position.x() - a.x()) * dx + (position.z() - a.z()) * dz)
                            / lengthSquared);
            double px = a.x() + fraction * dx;
            double pz = a.z() + fraction * dz;
            double distance = Math.hypot(position.x() - px, position.z() - pz);
            if (nearest == null || distance < nearest.distance() - EPSILON
                    || (Math.abs(distance - nearest.distance()) <= EPSILON
                            && i < nearest.segmentIndex())) {
                nearest = new Projection(i, fraction, distance);
            }
        }

        // Map points inside the connected valley corridor through the ruled cross-section mesh.
        // A complete cross-section is assigned the station of its centerline vertex on both
        // sides, avoiding nearest-segment station switching at bends.
        double mappedStation = Double.NaN;
        double mappedDistance = Double.POSITIVE_INFINITY;
        int firstLocalSegment = Math.max(0, nearest.segmentIndex() - 1);
        int lastLocalSegment = Math.min(points.size() - 2, nearest.segmentIndex() + 1);
        for (int i = firstLocalSegment; i <= lastLocalSegment; i++) {
            SkyIslandLocalPosition leftA = crossSectionEndpoint(points, samples, i, 1.0);
            SkyIslandLocalPosition rightA = crossSectionEndpoint(points, samples, i, -1.0);
            SkyIslandLocalPosition leftB = crossSectionEndpoint(points, samples, i + 1, 1.0);
            SkyIslandLocalPosition rightB = crossSectionEndpoint(points, samples, i + 1, -1.0);
            double stationA = samples.get(i).stationFraction();
            double stationB = samples.get(i + 1).stationFraction();
            double[] first = triangleStation(position, leftA, leftB, rightB,
                    stationA, stationB, stationB);
            double[] second = triangleStation(position, leftA, rightB, rightA,
                    stationA, stationB, stationA);
            double candidateStation = Double.isFinite(first[0]) ? first[0] : second[0];
            if (Double.isFinite(candidateStation)) {
                double segmentDistance = distanceToSegment(position, points.get(i), points.get(i + 1));
                if (segmentDistance < mappedDistance) {
                    mappedStation = candidateStation;
                    mappedDistance = segmentDistance;
                }
            }
        }
        if (!Double.isFinite(mappedStation)) {
            return nearest;
        }

        for (int i = 0; i < samples.size() - 1; i++) {
            double a = samples.get(i).stationFraction();
            double b = samples.get(i + 1).stationFraction();
            if (mappedStation <= b + EPSILON && b > a + EPSILON) {
                return new Projection(i, clamp01((mappedStation - a) / (b - a)), nearest.distance());
            }
        }
        return mappedStation <= samples.getFirst().stationFraction()
                ? new Projection(0, 0.0, nearest.distance())
                : new Projection(samples.size() - 2, 1.0, nearest.distance());
    }

    private static SkyIslandLocalPosition crossSectionEndpoint(
            List<SkyIslandLocalPosition> points,
            List<SkyIslandHydraulicGeometrySample> samples,
            int index,
            double side) {
        SkyIslandLocalPosition point = points.get(index);
        SkyIslandLocalPosition before = points.get(Math.max(0, index - 1));
        SkyIslandLocalPosition after = points.get(Math.min(points.size() - 1, index + 1));
        double tx = after.x() - before.x();
        double tz = after.z() - before.z();
        double length = Math.hypot(tx, tz);
        if (length <= EPSILON) {
            return point;
        }
        // Water is only qualified inside bankfull; valley recovery is deliberately dry.
        // Keeping the station mesh to this wet envelope avoids folded, overlapping valley ribbons.
        double halfWidth = samples.get(index).bankfullHalfWidth();
        return new SkyIslandLocalPosition(
                point.x() - side * tz / length * halfWidth,
                point.z() + side * tx / length * halfWidth);
    }

    private static double[] triangleStation(
            SkyIslandLocalPosition p,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            SkyIslandLocalPosition c,
            double stationA,
            double stationB,
            double stationC) {
        double denominator = (b.z() - c.z()) * (a.x() - c.x())
                + (c.x() - b.x()) * (a.z() - c.z());
        if (Math.abs(denominator) <= EPSILON) {
            return new double[] {Double.NaN};
        }
        double u = ((b.z() - c.z()) * (p.x() - c.x())
                + (c.x() - b.x()) * (p.z() - c.z())) / denominator;
        double v = ((c.z() - a.z()) * (p.x() - c.x())
                + (a.x() - c.x()) * (p.z() - c.z())) / denominator;
        double w = 1.0 - u - v;
        if (u < -EPSILON || v < -EPSILON || w < -EPSILON) {
            return new double[] {Double.NaN};
        }
        return new double[] {u * stationA + v * stationB + w * stationC};
    }

    private static double distanceToSegment(
            SkyIslandLocalPosition point,
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double lengthSquared = dx * dx + dz * dz;
        double fraction = lengthSquared <= EPSILON ? 0.0 : clamp01(
                ((point.x() - a.x()) * dx + (point.z() - a.z()) * dz) / lengthSquared);
        return Math.hypot(point.x() - a.x() - fraction * dx,
                point.z() - a.z() - fraction * dz);
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction) {
        int index =
                Math.min(
                        profiles.size() - 1,
                        (int)
                                Math.floor(
                                        Math.max(0.0, Math.min(0.999999999, stationFraction))
                                                * profiles.size()));
        return profiles.get(index).kind();
    }

    private static double valleyMultiplier(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> 3.5;
            case INCISED -> 2.5;
            case CASCADE -> 1.8;
        };
    }

    private static double crossSectionExponent(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> 2.0;
            case INCISED -> 1.45;
            case CASCADE -> 1.20;
        };
    }

    private static double smoothstep(double value) {
        double x = clamp01(value);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Projection(int segmentIndex, double segmentFraction, double distance) {}

    private record Candidate(
            SkyIslandQualifiedFluvialSample sample,
            double normalizedInfluence,
            double discharge,
            int startCellIndex,
            int endCellIndex) {

        boolean sharesSemanticNodeWith(Candidate other) {
            return startCellIndex == other.startCellIndex
                    || startCellIndex == other.endCellIndex
                    || endCellIndex == other.startCellIndex
                    || endCellIndex == other.endCellIndex;
        }

        String semanticIdentity() {
            return startCellIndex + "->" + endCellIndex;
        }

        boolean precedes(Candidate other) {
            if (normalizedInfluence < other.normalizedInfluence - EPSILON) {
                return true;
            }
            if (Math.abs(normalizedInfluence - other.normalizedInfluence) > EPSILON) {
                return false;
            }
            if (discharge > other.discharge + EPSILON) {
                return true;
            }
            if (Math.abs(discharge - other.discharge) > EPSILON) {
                return false;
            }
            if (startCellIndex != other.startCellIndex) {
                return startCellIndex < other.startCellIndex;
            }
            return endCellIndex < other.endCellIndex;
        }
    }
}
