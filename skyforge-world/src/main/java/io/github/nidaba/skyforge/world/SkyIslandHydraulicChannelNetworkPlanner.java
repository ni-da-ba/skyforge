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
 */
public final class SkyIslandHydraulicChannelNetworkPlanner {
    /** Small strictly-positive grade used only to avoid perfectly flat ordinary channel profiles. */
    public static final double MINIMUM_WATER_SURFACE_GRADE = 1.0e-5;

    /** Downstream hydraulic-geometry width exponent; dimensionless Skyforge calibration. */
    public static final double WIDTH_EXPONENT = 0.50;

    /** Downstream hydraulic-geometry depth exponent; dimensionless Skyforge calibration. */
    public static final double DEPTH_EXPONENT = 0.32;

    private static final double MINIMUM_DISCHARGE = 0.015;
    private static final double BASE_WIDTH_RADIUS_FRACTION = 0.0035;
    private static final double WIDTH_RADIUS_FRACTION = 0.020;
    private static final double BASE_DEPTH_POTENTIAL = 0.0035;
    private static final double DEPTH_POTENTIAL_RANGE = 0.017;
    private static final double BASE_FREEBOARD_POTENTIAL = 0.0030;
    private static final double DEPTH_FREEBOARD_FRACTION = 0.45;
    private static final double EPSILON = 1.0e-12;

    private SkyIslandHydraulicChannelNetworkPlanner() {}

    public static SkyIslandHydraulicChannelNetworkPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        SkyIslandContinuousHydrologicTerrainField terrain =
                SkyIslandContinuousHydrologicTerrainField.create(descriptor);
        return plan(descriptor, network, terrain);
    }

    static SkyIslandHydraulicChannelNetworkPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network,
            SkyIslandSemanticField terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(terrain, "terrain");
        if (!descriptor.equals(network.descriptor())) {
            throw new IllegalArgumentException("geomorphic network descriptor must match hydraulic descriptor");
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
            double depth = waterDepthPotential(nodeDischarge.getOrDefault(node.cellIndex(), MINIMUM_DISCHARGE));
            double freeboard = BASE_FREEBOARD_POTENTIAL + DEPTH_FREEBOARD_FRACTION * depth;
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
                                - MINIMUM_WATER_SURFACE_GRADE * inbound.route().pathLength());
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
            double minimumDrop =
                    MINIMUM_WATER_SURFACE_GRADE * routed.route().pathLength();
            if (endSurface > startSurface - minimumDrop + EPSILON) {
                throw new IllegalStateException("shared node hydraulic datums violate minimum downstream grade");
            }

            SkyIslandHydraulicReachGeometry reach = solveReach(
                    descriptor, terrain, routed, startSurface, endSurface);
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
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicReachRoute routed,
            double startSurface,
            double endSurface) {
        List<SkyIslandLocalPosition> points = routed.route().points();
        double[] cumulative = cumulativeDistance(points);
        double pathLength = cumulative[cumulative.length - 1];
        if (!(pathLength > 0.0)) {
            throw new IllegalStateException("candidate geomorphic route must have positive length");
        }

        double startDischarge = Math.max(
                MINIMUM_DISCHARGE,
                routed.semanticReach().profiles().getFirst().segment().relativeDischarge());
        double endDischarge = Math.max(
                startDischarge,
                routed.semanticReach().profiles().getLast().segment().relativeDischarge());

        double[] target = new double[points.size()];
        double[] discharge = new double[points.size()];
        double[] widths = new double[points.size()];
        double[] depths = new double[points.size()];
        double[] terrainElevation = new double[points.size()];

        for (int i = 0; i < points.size(); i++) {
            double fraction = cumulative[i] / pathLength;
            discharge[i] = lerp(startDischarge, endDischarge, fraction);
            widths[i] = bankfullHalfWidth(descriptor.nominalRadius(), discharge[i]);
            depths[i] = waterDepthPotential(discharge[i]);
            terrainElevation[i] = clamp01(terrain.sample(points.get(i)));
            double freeboard = BASE_FREEBOARD_POTENTIAL + DEPTH_FREEBOARD_FRACTION * depths[i];
            target[i] = clamp01(Math.max(depths[i] + EPSILON, terrainElevation[i] - freeboard));
        }

        double[] surface = new double[points.size()];
        surface[0] = startSurface;
        surface[points.size() - 1] = endSurface;
        for (int i = 1; i < points.size() - 1; i++) {
            double distanceFromPrevious = cumulative[i] - cumulative[i - 1];
            double remaining = pathLength - cumulative[i];
            double upper = surface[i - 1] - MINIMUM_WATER_SURFACE_GRADE * distanceFromPrevious;
            double lower = endSurface + MINIMUM_WATER_SURFACE_GRADE * remaining;
            if (lower > upper + EPSILON) {
                throw new IllegalStateException("fixed endpoint datums leave no monotone interior hydraulic profile");
            }
            surface[i] = Math.max(lower, Math.min(upper, target[i]));
        }

        List<SkyIslandHydraulicGeometrySample> samples = new ArrayList<>(points.size());
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlope = 0.0;
        double maximumWidth = 0.0;
        double maximumDepth = 0.0;

        for (int i = 0; i < points.size(); i++) {
            double bed = Math.max(0.0, surface[i] - depths[i]);
            double requiredLowering = Math.max(0.0, terrainElevation[i] - bed);
            maximumLowering = Math.max(maximumLowering, requiredLowering);
            totalLowering += requiredLowering;
            maximumWidth = Math.max(maximumWidth, widths[i]);
            maximumDepth = Math.max(maximumDepth, depths[i]);
            if (i > 0) {
                double ds = cumulative[i] - cumulative[i - 1];
                if (ds > EPSILON) {
                    maximumSlope = Math.max(
                            maximumSlope,
                            Math.abs(surface[i] - surface[i - 1]) / ds);
                }
            }
            samples.add(new SkyIslandHydraulicGeometrySample(
                    points.get(i),
                    cumulative[i] / pathLength,
                    discharge[i],
                    widths[i],
                    depths[i],
                    terrainElevation[i],
                    clamp01(surface[i]),
                    clamp01(bed),
                    requiredLowering));
        }

        return new SkyIslandHydraulicReachGeometry(
                routed,
                samples,
                pathLength,
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlope,
                maximumWidth,
                maximumDepth);
    }

    private static Map<Integer, Double> nodeDischarge(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> incoming,
            Map<Integer, List<SkyIslandGeomorphicReachRoute>> outgoing) {
        Map<Integer, Double> result = new HashMap<>();
        for (SkyIslandGeomorphicNetworkNode node : network.nodes()) {
            double discharge = MINIMUM_DISCHARGE;
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

    private static Comparator<SkyIslandGeomorphicReachRoute> routeComparator() {
        return Comparator
                .comparingInt((SkyIslandGeomorphicReachRoute route) ->
                        route.semanticReach().startCellIndex())
                .thenComparingInt(route -> route.semanticReach().endCellIndex());
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

    static double bankfullHalfWidth(double nominalRadius, double relativeDischarge) {
        if (!Double.isFinite(nominalRadius) || nominalRadius <= 0.0) {
            throw new IllegalArgumentException("nominalRadius must be finite and positive");
        }
        double q = Math.max(MINIMUM_DISCHARGE, clamp01(relativeDischarge));
        return nominalRadius
                * (BASE_WIDTH_RADIUS_FRACTION
                        + WIDTH_RADIUS_FRACTION * Math.pow(q, WIDTH_EXPONENT));
    }

    static double waterDepthPotential(double relativeDischarge) {
        double q = Math.max(MINIMUM_DISCHARGE, clamp01(relativeDischarge));
        return BASE_DEPTH_POTENTIAL
                + DEPTH_POTENTIAL_RANGE * Math.pow(q, DEPTH_EXPONENT);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
