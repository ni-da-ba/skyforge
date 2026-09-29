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

        double semanticCorridorHalfWidth =
                network.planningSpacing()
                        * SkyIslandGeomorphicChannelNetworkPlanner.ROUTE_CORRIDOR_SPACING_FRACTION;
        List<SkyIslandHydraulicReachSkeleton> reaches = new ArrayList<>(network.routes().size());

        for (SkyIslandGeomorphicReachRoute route : network.routes()) {
            double maximumBankfullWidth =
                    2.0
                            * SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                                    descriptor.nominalRadius(),
                                    route.semanticReach().downstreamRelativeDischarge());
            SkyIslandSemanticChannelReach semantic = route.semanticReach();
            double startDischarge = startDischarge(semantic);
            double endDischarge = endDischarge(semantic, startDischarge);
            SkyIslandGeomorphicQualificationPolicy policy =
                    SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
            Optional<SkyIslandChannelProfileKind> ordinaryProfileKind =
                    singleOrdinaryProfileKind(semantic);
            SkyIslandContinuousChannelCenterline centerline =
                    SkyIslandSemanticCorridorCenterlinePlanner.refine(
                            route.route(),
                            semantic.guidancePoints(),
                            terrain,
                            interiority,
                            network.planningSpacing(),
                            semanticCorridorHalfWidth,
                            maximumBankfullWidth,
                            station -> bankfullHalfWidth(
                                    descriptor, startDischarge, endDischarge, station),
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
                                                SkyIslandHydraulicGeometryCalibration
                                                        .waterDepthPotential(
                                                                lerp(startDischarge, endDischarge, station)),
                                                clamp01(terrain.sample(position)),
                                                -tangentZ,
                                                tangentX,
                                                terrain,
                                                policy.limits(qualificationClass));
                                return Math.max(
                                        0.0,
                                        envelope.lowerHead() - envelope.upperHead());
                            });
            reaches.add(sampleReach(descriptor, terrain, route, centerline));
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachSkeleton reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        return new SkyIslandHydraulicGeometrySkeletonPlan(descriptor, network, reaches);
    }

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

    private static double startDischarge(SkyIslandSemanticChannelReach semantic) {
        return Math.max(
                SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                semantic.profiles().getFirst().segment().relativeDischarge());
    }

    private static double endDischarge(
            SkyIslandSemanticChannelReach semantic,
            double startDischarge) {
        return Math.max(
                startDischarge,
                semantic.profiles().getLast().segment().relativeDischarge());
    }

    private static double bankfullHalfWidth(
            SkyIslandDescriptor descriptor,
            double startDischarge,
            double endDischarge,
            double station) {
        double discharge = lerp(startDischarge, endDischarge, station);
        return SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                descriptor.nominalRadius(), discharge);
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
        double startDischarge = startDischarge(semantic);
        double endDischarge = endDischarge(semantic, startDischarge);

        List<SkyIslandHydraulicGeometrySkeletonSample> samples =
                new ArrayList<>(points.size());
        double maximumWidth = 0.0;
        double maximumDepth = 0.0;

        for (int i = 0; i < points.size(); i++) {
            double station = cumulative[i] / pathLength;
            double discharge = lerp(startDischarge, endDischarge, station);
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
