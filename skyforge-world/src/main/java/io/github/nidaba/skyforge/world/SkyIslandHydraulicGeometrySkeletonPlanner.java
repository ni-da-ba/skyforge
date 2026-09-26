package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
            SkyIslandContinuousChannelCenterline centerline =
                    SkyIslandSemanticCorridorCenterlinePlanner.refine(
                            route.route(),
                            route.semanticReach().guidancePoints(),
                            terrain,
                            interiority,
                            network.planningSpacing(),
                            semanticCorridorHalfWidth,
                            maximumBankfullWidth);
            reaches.add(sampleReach(descriptor, terrain, route, centerline));
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachSkeleton reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        return new SkyIslandHydraulicGeometrySkeletonPlan(descriptor, network, reaches);
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
        double startDischarge = Math.max(
                SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE,
                semantic.profiles().getFirst().segment().relativeDischarge());
        double endDischarge = Math.max(
                startDischarge,
                semantic.profiles().getLast().segment().relativeDischarge());

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
