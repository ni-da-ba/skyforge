package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * Constructs one terrain-supported longitudinal bed profile for a directed drainage component.
 *
 * <p>Reach stations sharing a node identity share exactly one bed elevation. The solve keeps every
 * station as high as possible (and therefore minimizes lowering pointwise) subject to the authored
 * terrain/edit lower bound and each reach's minimum downstream bed grade. This is a deterministic
 * longest-path propagation over the acyclic drainage graph, not a hydraulic water-surface solver;
 * standard-step hydraulics must run afterward against the returned joint candidate.
 *
 * <p>When multiple reach descriptions share a physical node, their terrain elevations must agree.
 * The tightest (smallest) edit allowance governs that shared node. Infeasible grade/edit contracts
 * and cyclic input fail closed.
 */
public final class SkyIslandJointNetworkBedSolver {
    private static final double ELEVATION_TOLERANCE_METRES = 1.0e-9;

    private SkyIslandJointNetworkBedSolver() {}

    public static ComponentProfile solve(List<Reach> reaches) {
        Objects.requireNonNull(reaches, "reaches");
        List<Reach> immutableReaches = List.copyOf(reaches);
        if (immutableReaches.isEmpty()) {
            throw new IllegalArgumentException("network bed solve requires at least one reach");
        }

        Map<String, NodeBounds> nodes = new TreeMap<>();
        Map<Edge, Double> requiredDrops = new TreeMap<>(
                Comparator.comparing(Edge::upstreamNodeId).thenComparing(Edge::downstreamNodeId));
        for (Reach reach : immutableReaches) {
            List<Station> stations = reach.stations();
            for (Station station : stations) {
                NodeBounds prior = nodes.get(station.nodeId());
                if (prior == null) {
                    nodes.put(station.nodeId(), new NodeBounds(
                            station.terrainElevationMetres(), station.maximumLoweringMetres()));
                } else {
                    if (Math.abs(prior.terrainElevationMetres()
                                    - station.terrainElevationMetres())
                            > ELEVATION_TOLERANCE_METRES) {
                        throw new IllegalArgumentException(
                                "shared node has inconsistent terrain elevation: " + station.nodeId());
                    }
                    nodes.put(station.nodeId(), new NodeBounds(
                            prior.terrainElevationMetres(),
                            Math.min(prior.maximumLoweringMetres(), station.maximumLoweringMetres())));
                }
            }
            for (int i = 1; i < stations.size(); i++) {
                Station upstream = stations.get(i - 1);
                Station downstream = stations.get(i);
                double requiredDrop = reach.minimumBedSlope()
                        * (downstream.stationMetres() - upstream.stationMetres());
                if (!Double.isFinite(requiredDrop)) {
                    throw new IllegalArgumentException("minimum-grade elevation drop must be finite");
                }
                Edge edge = new Edge(upstream.nodeId(), downstream.nodeId());
                requiredDrops.merge(edge, requiredDrop, Math::max);
            }
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<Map.Entry<Edge, Double>>> outgoing = new HashMap<>();
        nodes.keySet().forEach(node -> indegree.put(node, 0));
        for (Map.Entry<Edge, Double> constraint : requiredDrops.entrySet()) {
            Edge edge = constraint.getKey();
            indegree.merge(edge.downstreamNodeId(), 1, Integer::sum);
            outgoing.computeIfAbsent(edge.upstreamNodeId(), ignored -> new ArrayList<>())
                    .add(constraint);
        }
        outgoing.values().forEach(edges -> edges.sort(
                Comparator.comparing((Map.Entry<Edge, Double> e) -> e.getKey().downstreamNodeId())));

        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((node, degree) -> {
            if (degree == 0) {
                ready.add(node);
            }
        });
        Map<String, Double> maximumFeasibleBed = new TreeMap<>();
        nodes.forEach((node, bounds) -> maximumFeasibleBed.put(node, bounds.terrainElevationMetres()));

        int visited = 0;
        while (!ready.isEmpty()) {
            String upstream = ready.remove();
            visited++;
            double upstreamBed = maximumFeasibleBed.get(upstream);
            for (Map.Entry<Edge, Double> constraint : outgoing.getOrDefault(upstream, List.of())) {
                Edge edge = constraint.getKey();
                double downstreamCeiling = upstreamBed - constraint.getValue();
                maximumFeasibleBed.merge(
                        edge.downstreamNodeId(), downstreamCeiling, Math::min);
                int remaining = indegree.merge(edge.downstreamNodeId(), -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(edge.downstreamNodeId());
                }
            }
        }
        if (visited != nodes.size()) {
            throw new IllegalArgumentException("drainage bed constraints contain a directed cycle");
        }

        double maximumLowering = 0.0;
        for (Map.Entry<String, NodeBounds> entry : nodes.entrySet()) {
            String node = entry.getKey();
            NodeBounds bounds = entry.getValue();
            double bed = maximumFeasibleBed.get(node);
            double minimumAllowedBed = bounds.terrainElevationMetres() - bounds.maximumLoweringMetres();
            if (!Double.isFinite(bed) || bed < minimumAllowedBed - ELEVATION_TOLERANCE_METRES) {
                throw new IllegalArgumentException(
                        "joint minimum-grade bed exceeds terrain edit allowance at node " + node
                                + "; required lowering="
                                + (bounds.terrainElevationMetres() - bed)
                                + " m, allowed="
                                + bounds.maximumLoweringMetres()
                                + " m");
            }
            maximumLowering = Math.max(
                    maximumLowering, bounds.terrainElevationMetres() - bed);
        }

        List<ReachProfile> profiles = new ArrayList<>(immutableReaches.size());
        for (Reach reach : immutableReaches) {
            List<Station> stations = reach.stations();
            List<String> nodeIds = new ArrayList<>(stations.size());
            double[] stationMetres = new double[stations.size()];
            double[] bedElevationsMetres = new double[stations.size()];
            for (int i = 0; i < stations.size(); i++) {
                Station station = stations.get(i);
                nodeIds.add(station.nodeId());
                stationMetres[i] = station.stationMetres();
                bedElevationsMetres[i] = maximumFeasibleBed.get(station.nodeId());
            }
            profiles.add(new ReachProfile(nodeIds, stationMetres, bedElevationsMetres));
        }
        Map<String, Double> immutableBed = Collections.unmodifiableMap(
                new LinkedHashMap<>(maximumFeasibleBed));
        return new ComponentProfile(profiles, immutableBed, maximumLowering);
    }

    /** One sample on a reach, with an exact component-wide physical node identity. */
    public record Station(
            String nodeId,
            double stationMetres,
            double terrainElevationMetres,
            double maximumLoweringMetres) {
        public Station {
            nodeId = Objects.requireNonNull(nodeId, "nodeId");
            if (nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId must not be blank");
            }
            requireFinite(stationMetres, "stationMetres");
            requireFinite(terrainElevationMetres, "terrainElevationMetres");
            if (!(maximumLoweringMetres >= 0.0) || !Double.isFinite(maximumLoweringMetres)) {
                throw new IllegalArgumentException(
                        "maximumLoweringMetres must be finite and nonnegative");
            }
        }
    }

    /** A directed upstream-to-downstream bed path with a strictly positive minimum grade. */
    public record Reach(List<Station> stations, double minimumBedSlope) {
        public Reach {
            stations = List.copyOf(Objects.requireNonNull(stations, "stations"));
            if (stations.size() < 2) {
                throw new IllegalArgumentException("reach requires at least two bed stations");
            }
            if (!(minimumBedSlope > 0.0) || !Double.isFinite(minimumBedSlope)) {
                throw new IllegalArgumentException("minimumBedSlope must be finite and positive");
            }
            Set<String> seenNodes = new LinkedHashSet<>();
            for (int i = 0; i < stations.size(); i++) {
                Station station = Objects.requireNonNull(stations.get(i), "station");
                if (!seenNodes.add(station.nodeId())) {
                    throw new IllegalArgumentException(
                            "a reach cannot visit the same bed node more than once: "
                                    + station.nodeId());
                }
                if (i > 0 && !(station.stationMetres() > stations.get(i - 1).stationMetres())) {
                    throw new IllegalArgumentException(
                            "reach stations must increase strictly downstream");
                }
            }
        }
    }

    /** Solved station arrays for one input reach; array accessors are defensive. */
    public record ReachProfile(
            List<String> nodeIds,
            double[] stationMetres,
            double[] bedElevationsMetres) {
        public ReachProfile {
            nodeIds = List.copyOf(nodeIds);
            stationMetres = stationMetres.clone();
            bedElevationsMetres = bedElevationsMetres.clone();
            if (nodeIds.size() != stationMetres.length
                    || stationMetres.length != bedElevationsMetres.length) {
                throw new IllegalArgumentException("reach profile arrays must have equal lengths");
            }
        }

        @Override
        public double[] stationMetres() {
            return stationMetres.clone();
        }

        @Override
        public double[] bedElevationsMetres() {
            return bedElevationsMetres.clone();
        }
    }

    /** Component bed profiles and the shared node-elevation solution. */
    public record ComponentProfile(
            List<ReachProfile> reaches,
            Map<String, Double> bedElevationsByNodeMetres,
            double maximumTerrainLoweringMetres) {
        public ComponentProfile {
            reaches = List.copyOf(reaches);
            bedElevationsByNodeMetres = Collections.unmodifiableMap(
                    new LinkedHashMap<>(bedElevationsByNodeMetres));
            if (reaches.isEmpty()
                    || !(maximumTerrainLoweringMetres >= 0.0)
                    || !Double.isFinite(maximumTerrainLoweringMetres)) {
                throw new IllegalArgumentException("invalid component bed profile");
            }
        }
    }

    private record NodeBounds(double terrainElevationMetres, double maximumLoweringMetres) {}

    private record Edge(String upstreamNodeId, String downstreamNodeId) {}

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
