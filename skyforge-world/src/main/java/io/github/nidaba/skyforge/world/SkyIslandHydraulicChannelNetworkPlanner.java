package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Solves a deterministic candidate longitudinal hydraulic profile without modifying terrain.
 *
 * <p>Width/depth are discharge-scaled hydraulic-geometry parameters. Shared semantic nodes receive
 * one water-surface datum. Interior samples follow the local pre-fluvial terrain where possible
 * while remaining monotonically downstream and compatible with both endpoint datums.
 *
 * <p>The result exposes required centerline lowering as a diagnostic. A later qualification layer
 * decides whether that modification is acceptable; this planner never excavates to make a route fit.
 *
 * <p><strong>Reset authority:</strong> the topological/clipped head solution in this class is
 * historical staging only. New F2 consumers must obtain C2 centerline/discharge/width/depth state
 * from {@link SkyIslandHydraulicGeometrySkeletonPlanner} and apply the accepted bounded-profile
 * contract rather than treating this planner's water surface as physical authority.
 */
public final class SkyIslandHydraulicChannelNetworkPlanner {
    /** Small strictly-positive grade used only to avoid perfectly flat ordinary channel profiles. */
    public static final double MINIMUM_WATER_SURFACE_GRADE = 1.0e-5;

    /** Downstream hydraulic-geometry width exponent; dimensionless Skyforge calibration. */
    public static final double WIDTH_EXPONENT = SkyIslandHydraulicGeometryCalibration.WIDTH_EXPONENT;

    /** Downstream hydraulic-geometry depth exponent; dimensionless Skyforge calibration. */
    public static final double DEPTH_EXPONENT = SkyIslandHydraulicGeometryCalibration.DEPTH_EXPONENT;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandHydraulicChannelNetworkPlanner() {}

    public static SkyIslandHydraulicChannelNetworkPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandPreHydrologicTerrainField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        return plan(descriptor, network, terrain, interiority);
    }

    static SkyIslandHydraulicChannelNetworkPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticField terrain) {
        return plan(
                descriptor,
                network,
                terrain,
                SkyIslandSemanticFieldSet.create(descriptor).interiority());
    }

    static SkyIslandHydraulicChannelNetworkPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!descriptor.equals(network.descriptor())) {
            throw new IllegalArgumentException("geomorphic network descriptor must match hydraulic descriptor");
        }

        SkyIslandHydraulicGeometrySkeletonPlan skeleton =
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(
                        descriptor, network, terrain, interiority);
        Map<SkyIslandGeomorphicReachRoute, SkyIslandHydraulicReachSkeleton> skeletons =
                new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            skeletons.put(reach.geomorphicRoute(), reach);
        }

        Map<Integer, List<SkyIslandGeomorphicReachRoute>> incoming = new HashMap<>();
        Map<Integer, List<SkyIslandGeomorphicReachRoute>> outgoing = new HashMap<>();
        for (SkyIslandGeomorphicReachRoute route : network.routes()) {
            int start = route.semanticReach().startCellIndex();
            int end = route.semanticReach().endCellIndex();
            outgoing.computeIfAbsent(start, ignored -> new ArrayList<>()).add(route);
            incoming.computeIfAbsent(end, ignored -> new ArrayList<>()).add(route);
            incoming.putIfAbsent(start, new ArrayList<>());
            outgoing.putIfAbsent(end, new ArrayList<>());
        }
        incoming.values().forEach(list -> list.sort(routeComparator()));
        outgoing.values().forEach(list -> list.sort(routeComparator()));

        Map<Integer, Double> nodeDischarge = nodeDischarge(network, incoming, outgoing);
        Map<Integer, Double> rawNodeSurface = new HashMap<>();
        for (SkyIslandGeomorphicNetworkNode node : network.nodes()) {
            double discharge = nodeDischarge.getOrDefault(
                    node.cellIndex(), SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE);
            double depth = SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            double freeboard =
                    SkyIslandHydraulicGeometryCalibration.freeboardFromDepthPotential(depth);
            rawNodeSurface.put(
                    node.cellIndex(),
                    clamp01(Math.max(depth + EPSILON, node.terrainElevation() - freeboard)));
        }

        List<Integer> topological = topologicalNodeOrder(network, incoming, outgoing);
        Map<Integer, Double> nodeSurface = new HashMap<>();
        for (int node : topological) {
            double solved = rawNodeSurface.get(node);
            for (SkyIslandGeomorphicReachRoute inbound : incoming.getOrDefault(node, List.of())) {
                int upstream = inbound.semanticReach().startCellIndex();
                Double upstreamSurface = nodeSurface.get(upstream);
                if (upstreamSurface == null) {
                    throw new IllegalStateException("topological hydraulic solve visited downstream before upstream");
                }
                solved = Math.min(
                        solved,
                        upstreamSurface
                                - MINIMUM_WATER_SURFACE_GRADE
                                        * requireSkeleton(skeletons, inbound).pathLength());
            }
            if (!(solved > EPSILON)) {
                throw new IllegalStateException("candidate hydraulic network requires non-positive water datum");
            }
            nodeSurface.put(node, solved);
        }

        List<SkyIslandHydraulicReachGeometry> reaches = new ArrayList<>(network.routes().size());
        double totalLowering = 0.0;
        int totalSamples = 0;
        double maximumLowering = 0.0;
        double maximumSlope = 0.0;

        for (SkyIslandGeomorphicReachRoute routed : network.routes()) {
            SkyIslandSemanticChannelReach semantic = routed.semanticReach();
            double startSurface = nodeSurface.get(semantic.startCellIndex());
            double endSurface = nodeSurface.get(semantic.endCellIndex());
            SkyIslandHydraulicReachSkeleton reachSkeleton =
                    requireSkeleton(skeletons, routed);
            double minimumDrop =
                    MINIMUM_WATER_SURFACE_GRADE * reachSkeleton.pathLength();
            if (endSurface > startSurface - minimumDrop + EPSILON) {
                throw new IllegalStateException("shared node hydraulic datums violate minimum downstream grade");
            }

            SkyIslandHydraulicReachGeometry reach =
                    solveReach(reachSkeleton, startSurface, endSurface);
            reaches.add(reach);
            maximumLowering = Math.max(maximumLowering, reach.maximumRequiredLowering());
            maximumSlope = Math.max(maximumSlope, reach.maximumWaterSurfaceSlope());
            for (SkyIslandHydraulicGeometrySample sample : reach.samples()) {
                totalLowering += sample.requiredCenterlineLowering();
                totalSamples++;
            }
        }

        reaches.sort(Comparator
                .comparingInt((SkyIslandHydraulicReachGeometry reach) ->
                        reach.geomorphicRoute().semanticReach().startCellIndex())
                .thenComparingInt(reach ->
                        reach.geomorphicRoute().semanticReach().endCellIndex()));

        return new SkyIslandHydraulicChannelNetworkPlan(
                descriptor,
                network,
                reaches,
                maximumLowering,
                totalSamples == 0 ? 0.0 : totalLowering / totalSamples,
                maximumSlope);
    }

    private static SkyIslandHydraulicReachGeometry solveReach(
            SkyIslandHydraulicReachSkeleton skeleton,
            double startSurface,
            double endSurface) {
        List<SkyIslandHydraulicGeometrySkeletonSample> sourceSamples = skeleton.samples();
        double pathLength = skeleton.pathLength();

        double[] target = new double[sourceSamples.size()];
        double[] surface = new double[sourceSamples.size()];
        for (int i = 0; i < sourceSamples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = sourceSamples.get(i);
            double freeboard =
                    SkyIslandHydraulicGeometryCalibration.freeboardFromDepthPotential(
                            sample.waterDepthPotential());
            target[i] = clamp01(Math.max(
                    sample.waterDepthPotential() + EPSILON,
                    sample.terrainElevation() - freeboard));
        }

        surface[0] = startSurface;
        surface[sourceSamples.size() - 1] = endSurface;
        for (int i = 1; i < sourceSamples.size() - 1; i++) {
            double previousStation = sourceSamples.get(i - 1).arcLength();
            double station = sourceSamples.get(i).arcLength();
            double distanceFromPrevious = station - previousStation;
            double remaining = pathLength - station;
            double upper =
                    surface[i - 1] - MINIMUM_WATER_SURFACE_GRADE * distanceFromPrevious;
            double lower =
                    endSurface + MINIMUM_WATER_SURFACE_GRADE * remaining;
            if (lower > upper + EPSILON) {
                throw new IllegalStateException(
                        "fixed endpoint datums leave no monotone interior hydraulic profile");
            }
            surface[i] = Math.max(lower, Math.min(upper, target[i]));
        }

        List<SkyIslandHydraulicGeometrySample> samples =
                new ArrayList<>(sourceSamples.size());
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;

        for (int i = 0; i < sourceSamples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample source = sourceSamples.get(i);
            double bed = Math.max(0.0, surface[i] - source.waterDepthPotential());
            double requiredLowering =
                    Math.max(0.0, source.terrainElevation() - bed);
            maximumLowering = Math.max(maximumLowering, requiredLowering);
            totalLowering += requiredLowering;
            if (i > 0) {
                double ds =
                        source.arcLength() - sourceSamples.get(i - 1).arcLength();
                if (ds > EPSILON) {
                    maximumSlope = Math.max(
                            maximumSlope,
                            Math.abs(surface[i] - surface[i - 1]) / ds);
                }
            }
            samples.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    source.bankfullHalfWidth(),
                    source.waterDepthPotential(),
                    source.terrainElevation(),
                    clamp01(surface[i]),
                    clamp01(bed),
                    requiredLowering));
        }

        return new SkyIslandHydraulicReachGeometry(
                skeleton.geomorphicRoute(),
                skeleton.centerline(),
                samples,
                pathLength,
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlope,
                skeleton.maximumBankfullHalfWidth(),
                skeleton.maximumWaterDepthPotential());
    }

    private static Map<Integer, Double> nodeDischarge(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> incoming,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> outgoing) {
        Map<Integer, Double> result = new HashMap<>();
        for (SkyIslandGeomorphicNetworkNode node : network.nodes()) {
            double discharge = SkyIslandHydraulicGeometryCalibration.MINIMUM_DISCHARGE;
            for (SkyIslandGeomorphicReachRoute route : incoming.getOrDefault(node.cellIndex(), List.of())) {
                discharge = Math.max(
                        discharge,
                        route.semanticReach().downstreamRelativeDischarge());
            }
            for (SkyIslandGeomorphicReachRoute route : outgoing.getOrDefault(node.cellIndex(), List.of())) {
                discharge = Math.max(
                        discharge,
                        route.semanticReach().profiles().getFirst().segment().relativeDischarge());
            }
            result.put(node.cellIndex(), discharge);
        }
        return result;
    }

    private static List<Integer> topologicalNodeOrder(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> incoming,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> outgoing) {
        Map<Integer, Integer> indegree = new HashMap<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (SkyIslandGeomorphicNetworkNode node : network.nodes()) {
            int count = incoming.getOrDefault(node.cellIndex(), List.of()).size();
            indegree.put(node.cellIndex(), count);
            if (count == 0) {
                queue.addLast(node.cellIndex());
            }
        }

        List<Integer> result = new ArrayList<>(network.nodes().size());
        while (!queue.isEmpty()) {
            int node = queue.removeFirst();
            result.add(node);
            for (SkyIslandGeomorphicReachRoute route : outgoing.getOrDefault(node, List.of())) {
                int downstream = route.semanticReach().endCellIndex();
                int remaining = indegree.merge(downstream, -1, Integer::sum);
                if (remaining == 0) {
                    queue.addLast(downstream);
                }
            }
        }
        if (result.size() != network.nodes().size()) {
            throw new IllegalStateException("candidate geomorphic channel network contains a cycle");
        }
        return List.copyOf(result);
    }

    private static SkyIslandHydraulicReachSkeleton requireSkeleton(
            Map<SkyIslandGeomorphicReachRoute, SkyIslandHydraulicReachSkeleton> skeletons,
            SkyIslandGeomorphicReachRoute route) {
        SkyIslandHydraulicReachSkeleton skeleton = skeletons.get(route);
        if (skeleton == null) {
            throw new IllegalStateException("missing hydraulic skeleton for geomorphic reach");
        }
        return skeleton;
    }

    private static Comparator<SkyIslandGeomorphicReachRoute> routeComparator() {
        return Comparator
                .comparingInt((SkyIslandGeomorphicReachRoute route) ->
                        route.semanticReach().startCellIndex())
                .thenComparingInt(route -> route.semanticReach().endCellIndex());
    }

    static double bankfullHalfWidth(double nominalRadius, double relativeDischarge) {
        return SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                nominalRadius, relativeDischarge);
    }

    static double waterDepthPotential(double relativeDischarge) {
        return SkyIslandHydraulicGeometryCalibration.waterDepthPotential(relativeDischarge);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
