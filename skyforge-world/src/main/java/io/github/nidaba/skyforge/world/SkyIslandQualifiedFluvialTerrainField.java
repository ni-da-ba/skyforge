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
 * delta. Overlapping accepted reaches use deterministic semantic dominance rather than independent
 * deepest-cut composition.
 */
public final class SkyIslandQualifiedFluvialTerrainField implements SkyIslandSemanticField {
    private static final double EPSILON = 1.0e-12;
    private static final double BED_ZONE_FRACTION = 0.18;

    private final SkyIslandSemanticField originalTerrain;
    private final List<SkyIslandHydraulicReachGeometry> acceptedReaches;

    public SkyIslandQualifiedFluvialTerrainField(
            SkyIslandSemanticField originalTerrain,
            List<SkyIslandHydraulicReachGeometry> acceptedReaches) {
        this.originalTerrain = Objects.requireNonNull(originalTerrain, "originalTerrain");
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
            Candidate candidate = candidate(reach, position, original);
            if (candidate == null) {
                continue;
            }
            if (selected == null || candidate.precedes(selected)) {
                selected = candidate;
            }
        }

        if (selected == null) {
            return SkyIslandQualifiedFluvialSample.unaffected(original);
        }
        return selected.sample();
    }

    public List<SkyIslandHydraulicReachGeometry> acceptedReaches() {
        return acceptedReaches;
    }

    private static Candidate candidate(
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandLocalPosition position,
            double originalTerrain) {
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
        if (points.size() < 2) {
            return null;
        }
        Projection best = null;
        for (int i = 0; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i);
            SkyIslandLocalPosition b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double fraction;
            if (lengthSquared <= EPSILON) {
                fraction = 0.0;
            } else {
                fraction =
                        clamp01(
                                ((position.x() - a.x()) * dx + (position.z() - a.z()) * dz)
                                        / lengthSquared);
            }
            double px = a.x() + fraction * dx;
            double pz = a.z() + fraction * dz;
            double distance = Math.hypot(position.x() - px, position.z() - pz);
            Projection candidate = new Projection(i, fraction, distance);
            if (best == null
                    || candidate.distance() < best.distance() - EPSILON
                    || (Math.abs(candidate.distance() - best.distance()) <= EPSILON
                            && candidate.segmentIndex() < best.segmentIndex())) {
                best = candidate;
            }
        }
        return best;
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
