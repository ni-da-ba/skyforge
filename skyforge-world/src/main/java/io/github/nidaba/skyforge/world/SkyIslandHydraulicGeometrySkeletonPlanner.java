package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds C2 centerlines and discharge-scaled width/depth geometry without solving water-surface head.
 *
 * <p>This is the shared pre-profile substrate for both the historical clipped hydraulic planner and
 * the F2 bounded longitudinal solver. It deliberately carries no node datum, water surface, bed, or
 * terrain-lowering authority.
 */
public final class SkyIslandHydraulicGeometrySkeletonPlanner {
    private SkyIslandHydraulicGeometrySkeletonPlanner() {}

    public static SkyIslandHydraulicGeometrySkeletonPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        return plan(descriptor, network, terrain, interiority);
    }

    static SkyIslandHydraulicGeometrySkeletonPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!descriptor.equals(network.descriptor())) {
            throw new IllegalArgumentException("geomorphic network descriptor must match skeleton descriptor");
        }

        List<SkyIslandHydraulicReachSkeleton> reaches = new ArrayList<>(network.routes().size());

        for (SkyIslandGeomorphicReachRoute route : network.routes()) {
            CenterlineRefinement refinement =
                    refineCenterline(descriptor, network, route, terrain, interiority);
            reaches.add(sampleReach(descriptor, terrain, route, refinement.centerline()));
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachSkeleton reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        return new SkyIslandHydraulicGeometrySkeletonPlan(descriptor, network, reaches);
    }

    static CenterlineRefinement refineCenterline(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandGeomorphicReachRoute route,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        SkyIslandSemanticChannelReach semantic = route.semanticReach();
        SemanticDischargeProfile dischargeProfile = semanticDischargeProfile(semantic);
        double maximumBankfullWidth =
                2.0 * SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                        descriptor.nominalRadius(), dischargeProfile.maximumDischarge());
        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        Optional<SkyIslandChannelProfileKind> ordinaryProfileKind =
                singleOrdinaryProfileKind(semantic);
        var outcome = SkyIslandSemanticCorridorCenterlinePlanner.refineWithDiagnostics(
                route.route(),
                semantic.guidancePoints(),
                terrain,
                interiority,
                network.planningSpacing(),
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION,
                maximumBankfullWidth,
                station -> SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                        descriptor.nominalRadius(), dischargeProfile.atStation(station)),
                (position, station, tangentX, tangentZ, halfWidth) -> {
                    SkyIslandChannelProfileKind kind =
                            SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                                    semantic.profiles(), station);
                    if (kind == SkyIslandChannelProfileKind.CASCADE
                            || ordinaryProfileKind.isEmpty()) {
                        return 0.0;
                    }
                    SkyIslandChannelProfileKind ordinaryKind =
                            ordinaryProfileKind.orElseThrow();
                    SkyIslandGeomorphicQualificationClass qualificationClass =
                            qualificationClass(ordinaryKind);
                    SkyIslandHydraulicHeadEnvelope envelope =
                            SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                                    descriptor,
                                    ordinaryKind,
                                    position,
                                    halfWidth,
                                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(
                                            dischargeProfile.atStation(station)),
                                    clamp01(terrain.sample(position)),
                                    -tangentZ,
                                    tangentX,
                                    terrain,
                                    policy.limits(qualificationClass));
                    return Math.max(
                            0.0, envelope.lowerHead() - envelope.upperHead());
                });
        return new CenterlineRefinement(outcome.centerline(), outcome.diagnostics());
    }

    record CenterlineRefinement(
            SkyIslandContinuousChannelCenterline centerline,
            SkyIslandSemanticCorridorCenterlinePlanner.SearchDiagnostics diagnostics) {}

    private static Optional<SkyIslandChannelProfileKind> singleOrdinaryProfileKind(
            SkyIslandSemanticChannelReach semantic) {
        List<SkyIslandChannelProfileKind> kinds =
                semantic.profiles().stream()
                        .map(profile -> profile.kind())
                        .filter(kind -> kind != SkyIslandChannelProfileKind.CASCADE)
                        .distinct()
                        .toList();
        return kinds.size() == 1 ? Optional.of(kinds.getFirst()) : Optional.empty();
    }

    private static SkyIslandGeomorphicQualificationClass qualificationClass(
            SkyIslandChannelProfileKind profileKind) {
        return switch (profileKind) {
            case ALLUVIAL -> SkyIslandGeomorphicQualificationClass.ALLUVIAL;
            case INCISED -> SkyIslandGeomorphicQualificationClass.INCISED;
            case CASCADE -> throw new IllegalArgumentException(
                    "CASCADE has no ordinary centerline head-gap objective");
        };
    }

    static double relativeDischargeAtStation(
            SkyIslandSemanticChannelReach semantic, double station) {
        Objects.requireNonNull(semantic, "semantic");
        return semanticDischargeProfile(semantic).atStation(station);
    }

    private static SemanticDischargeProfile semanticDischargeProfile(
            SkyIslandSemanticChannelReach semantic) {
        List<SkyIslandLocalPosition> guidance = semantic.guidancePoints();
        double[] cumulativeDistance = cumulativeDistance(guidance);
        double totalLength = cumulativeDistance[cumulativeDistance.length - 1];
        if (!(totalLength > 0.0)) {
            throw new IllegalArgumentException("semantic reach must have positive physical length");
        }
        double[] discharge = new double[semantic.profiles().size()];
        double previous = SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE;
        for (int i = 0; i < discharge.length; i++) {
            double value = Math.max(
                    SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                    semantic.profiles().get(i).segment().relativeDischarge());
            if (value + 1.0e-12 < previous) {
                throw new IllegalStateException(
                        "semantic reach discharge must not decrease downstream");
            }
            discharge[i] = value;
            previous = value;
            if (!(cumulativeDistance[i + 1] > cumulativeDistance[i])) {
                throw new IllegalStateException(
                        "semantic reach profile segment must have positive physical length");
            }
        }
        return new SemanticDischargeProfile(cumulativeDistance, totalLength, discharge);
    }

    private static SkyIslandHydraulicReachSkeleton sampleReach(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicReachRoute route,
            SkyIslandContinuousChannelCenterline centerline) {
        List<SkyIslandLocalPosition> points = centerline.points();
        double[] cumulative = cumulativeDistance(points);
        double pathLength = centerline.pathLength();
        if (!(pathLength > 0.0)) {
            throw new IllegalStateException("candidate geomorphic route must have positive length");
        }

        SkyIslandSemanticChannelReach semantic = route.semanticReach();
        SemanticDischargeProfile dischargeProfile = semanticDischargeProfile(semantic);

        List<SkyIslandHydraulicGeometrySkeletonSample> samples =
                new ArrayList<>(points.size());
        double maximumWidth = 0.0;
        double maximumDepth = 0.0;

        for (int i = 0; i < points.size(); i++) {
            double station = cumulative[i] / pathLength;
            double discharge = dischargeProfile.atStation(station);
            double width =
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                            descriptor.nominalRadius(), discharge);
            double depth =
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            double terrainElevation = clamp01(terrain.sample(points.get(i)));
            samples.add(new SkyIslandHydraulicGeometrySkeletonSample(
                    points.get(i),
                    cumulative[i],
                    station,
                    discharge,
                    width,
                    depth,
                    terrainElevation));
            maximumWidth = Math.max(maximumWidth, width);
            maximumDepth = Math.max(maximumDepth, depth);
        }

        return new SkyIslandHydraulicReachSkeleton(
                route,
                centerline,
                samples,
                pathLength,
                maximumWidth,
                maximumDepth);
    }

    private record SemanticDischargeProfile(
            double[] cumulativeDistance, double totalLength, double[] dischargeAtSegmentStart) {
        private double maximumDischarge() {
            return dischargeAtSegmentStart[dischargeAtSegmentStart.length - 1];
        }

        private double atStation(double station) {
            if (!Double.isFinite(station) || station < 0.0 || station > 1.0) {
                throw new IllegalArgumentException("station must be finite and in [0, 1]");
            }
            double targetDistance = station * totalLength;
            for (int i = 0; i < dischargeAtSegmentStart.length; i++) {
                double startDistance = cumulativeDistance[i];
                double endDistance = cumulativeDistance[i + 1];
                if (targetDistance <= endDistance || i == dischargeAtSegmentStart.length - 1) {
                    double startDischarge = dischargeAtSegmentStart[i];
                    double endDischarge = i + 1 < dischargeAtSegmentStart.length
                            ? dischargeAtSegmentStart[i + 1]
                            : startDischarge;
                    double fraction = Math.max(
                            0.0,
                            Math.min(
                                    1.0,
                                    (targetDistance - startDistance)
                                            / (endDistance - startDistance)));
                    return lerp(startDischarge, endDischarge, fraction);
                }
            }
            throw new IllegalStateException("station escaped semantic discharge profile");
        }
    }

    private static double[] cumulativeDistance(List<SkyIslandLocalPosition> points) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1]
                    + Math.hypot(
                            points.get(i).x() - points.get(i - 1).x(),
                            points.get(i).z() - points.get(i - 1).z());
        }
        return cumulative;
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
