package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a small explainable semantic cave-system graph from AUTH-0023 geological regions.
 *
 * <p>AUTH-0024 does not author tunnel splines or carved cells. Nodes are chamber-scale anchors
 * selected inside accepted void-prone domains. Links represent topological continuity justified by
 * either the source void domain itself or expressed fracture/aquifer support between domains.
 */
public final class SkyIslandCaveSystemPlanner {
    private static final int MAX_ANCHORS_PER_VOID_REGION = 3;
    private static final int BRIDGE_SAMPLES = 11;
    private static final double MAX_INTER_REGION_LENGTH = 0.82;
    private static final double MIN_BRIDGE_SUPPORT = 0.34;

    private SkyIslandCaveSystemPlanner() {}

    public static SkyIslandCaveSystemPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandGeologicRegionPlan geology = SkyIslandGeologicRegionPlanner.plan(descriptor);
        SkyIslandGeologyFieldSet continuous = SkyIslandGeologyFieldSet.create(descriptor);
        SkyIslandSemanticFieldSet semantic = SkyIslandSemanticFieldSet.create(descriptor);

        List<SkyIslandGeologicRegion> voidRegions = geology.regions().stream()
                .filter(region -> region.kind() == SkyIslandGeologicRegionKind.VOID_PRONE_DOMAIN)
                .sorted(Comparator.comparingInt(SkyIslandGeologicRegion::regionId))
                .toList();
        if (voidRegions.isEmpty()) {
            return new SkyIslandCaveSystemPlan(descriptor, geology, List.of());
        }

        int totalCells = geology.gridSize() * geology.depthSamples() * geology.gridSize();
        double[] fractureMembership = new double[totalCells];
        double[] aquiferMembership = new double[totalCells];
        for (SkyIslandGeologicRegion region : geology.regions()) {
            double[] target = switch (region.kind()) {
                case FRACTURE_CORRIDOR -> fractureMembership;
                case AQUIFER_BODY -> aquiferMembership;
                case VOID_PRONE_DOMAIN -> null;
            };
            if (target == null) {
                continue;
            }
            for (SkyIslandGeologicRegionCell cell : region.cells()) {
                target[cell.index()] = Math.max(target[cell.index()], cell.membership());
            }
        }

        List<SkyIslandCaveNode> nodes = new ArrayList<>();
        Map<Integer, List<SkyIslandCaveNode>> nodesByRegion = new LinkedHashMap<>();
        for (SkyIslandGeologicRegion region : voidRegions) {
            List<SkyIslandGeologicRegionCell> anchors =
                    selectAnchors(region, geology.gridSize(), geology.depthSamples());
            List<SkyIslandCaveNode> regionNodes = new ArrayList<>();
            for (SkyIslandGeologicRegionCell anchor : anchors) {
                SkyIslandGeologySample sample = continuous.sample(anchor.position());
                SkyIslandCaveNode node = new SkyIslandCaveNode(
                        nodes.size(),
                        region.regionId(),
                        anchor.position(),
                        anchor.membership(),
                        sample.groundwaterPotential());
                nodes.add(node);
                regionNodes.add(node);
            }
            nodesByRegion.put(region.regionId(), List.copyOf(regionNodes));
        }

        DisjointSet components = new DisjointSet(nodes.size());
        List<SkyIslandCaveLink> links = new ArrayList<>();

        // One deterministic minimum tree per connected void-prone source region.
        for (List<SkyIslandCaveNode> regionNodes : nodesByRegion.values()) {
            List<NodePair> candidates = allPairs(regionNodes, descriptor.nominalRadius());
            candidates.sort(NodePair.ORDER);
            for (NodePair pair : candidates) {
                if (!components.union(pair.first().nodeId(), pair.second().nodeId())) {
                    continue;
                }
                SegmentSupport support = segmentSupport(
                        pair.first().position(),
                        pair.second().position(),
                        descriptor,
                        geology,
                        semantic,
                        fractureMembership,
                        aquiferMembership);
                links.add(new SkyIslandCaveLink(
                        links.size(),
                        pair.first().nodeId(),
                        pair.second().nodeId(),
                        SkyIslandCaveConnectionKind.VOID_CONTINUITY,
                        pair.length(),
                        support.fracture(),
                        support.aquifer()));
            }
        }

        // At most one geological bridge candidate per pair of distinct source void regions.
        List<BridgeCandidate> bridges = new ArrayList<>();
        for (int a = 0; a < voidRegions.size(); a++) {
            for (int b = a + 1; b < voidRegions.size(); b++) {
                int regionA = voidRegions.get(a).regionId();
                int regionB = voidRegions.get(b).regionId();
                BridgeCandidate best = bestBridge(
                        nodesByRegion.get(regionA),
                        nodesByRegion.get(regionB),
                        descriptor,
                        geology,
                        semantic,
                        fractureMembership,
                        aquiferMembership);
                if (best != null) {
                    bridges.add(best);
                }
            }
        }
        bridges.sort(BridgeCandidate.ORDER);

        // Kruskal-style selection prevents redundant inter-domain loops in this first topology.
        for (BridgeCandidate bridge : bridges) {
            int first = bridge.pair().first().nodeId();
            int second = bridge.pair().second().nodeId();
            if (!components.union(first, second)) {
                continue;
            }
            links.add(new SkyIslandCaveLink(
                    links.size(),
                    first,
                    second,
                    bridge.kind(),
                    bridge.pair().length(),
                    bridge.support().fracture(),
                    bridge.support().aquifer()));
        }

        Map<Integer, List<SkyIslandCaveNode>> componentNodes = new HashMap<>();
        for (SkyIslandCaveNode node : nodes) {
            componentNodes
                    .computeIfAbsent(components.find(node.nodeId()), ignored -> new ArrayList<>())
                    .add(node);
        }

        List<List<SkyIslandCaveNode>> orderedComponents = new ArrayList<>(componentNodes.values());
        orderedComponents.sort(Comparator.comparingInt(
                component -> component.stream().mapToInt(SkyIslandCaveNode::nodeId).min().orElseThrow()));

        List<SkyIslandCaveSystem> systems = new ArrayList<>();
        for (List<SkyIslandCaveNode> component : orderedComponents) {
            component.sort(Comparator.comparingInt(SkyIslandCaveNode::nodeId));
            java.util.Set<Integer> nodeIds = component.stream()
                    .map(SkyIslandCaveNode::nodeId)
                    .collect(java.util.stream.Collectors.toSet());
            List<SkyIslandCaveLink> componentLinks = links.stream()
                    .filter(link -> nodeIds.contains(link.firstNodeId())
                            && nodeIds.contains(link.secondNodeId()))
                    .sorted(Comparator.comparingInt(SkyIslandCaveLink::linkId))
                    .toList();
            systems.add(new SkyIslandCaveSystem(systems.size(), component, componentLinks));
        }

        return new SkyIslandCaveSystemPlan(descriptor, geology, systems);
    }

    private static List<SkyIslandGeologicRegionCell> selectAnchors(
            SkyIslandGeologicRegion region,
            int gridSize,
            int depthSamples) {
        int targetCount = region.cellCount() >= 60
                ? 3
                : region.cellCount() >= 18 ? 2 : 1;
        targetCount = Math.min(targetCount, MAX_ANCHORS_PER_VOID_REGION);

        List<SkyIslandGeologicRegionCell> candidates = new ArrayList<>(region.cells());
        candidates.sort(Comparator
                .comparingDouble(SkyIslandGeologicRegionCell::membership)
                .reversed()
                .thenComparingInt(SkyIslandGeologicRegionCell::index));

        List<SkyIslandGeologicRegionCell> selected = new ArrayList<>();
        selected.add(candidates.getFirst());
        while (selected.size() < targetCount) {
            SkyIslandGeologicRegionCell best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (SkyIslandGeologicRegionCell candidate : candidates) {
                if (selected.contains(candidate)) {
                    continue;
                }
                double separation = selected.stream()
                        .mapToDouble(existing -> gridDistance(
                                candidate,
                                existing,
                                gridSize,
                                depthSamples))
                        .min()
                        .orElse(0.0);
                double score = 0.66 * candidate.membership() + 0.34 * separation;
                if (score > bestScore + 1.0e-12
                        || (Math.abs(score - bestScore) <= 1.0e-12
                                && (best == null || candidate.index() < best.index()))) {
                    best = candidate;
                    bestScore = score;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
        }
        selected.sort(Comparator.comparingInt(SkyIslandGeologicRegionCell::index));
        return List.copyOf(selected);
    }

    private static List<NodePair> allPairs(
            List<SkyIslandCaveNode> nodes,
            double radius) {
        List<NodePair> result = new ArrayList<>();
        for (int a = 0; a < nodes.size(); a++) {
            for (int b = a + 1; b < nodes.size(); b++) {
                SkyIslandCaveNode first = nodes.get(a);
                SkyIslandCaveNode second = nodes.get(b);
                result.add(new NodePair(first, second, normalizedDistance(
                        first.position(),
                        second.position(),
                        radius)));
            }
        }
        return result;
    }

    private static BridgeCandidate bestBridge(
            List<SkyIslandCaveNode> firstNodes,
            List<SkyIslandCaveNode> secondNodes,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan geology,
            SkyIslandSemanticFieldSet semantic,
            double[] fractureMembership,
            double[] aquiferMembership) {
        BridgeCandidate best = null;
        for (SkyIslandCaveNode first : firstNodes) {
            for (SkyIslandCaveNode second : secondNodes) {
                double length = normalizedDistance(
                        first.position(),
                        second.position(),
                        descriptor.nominalRadius());
                if (length > MAX_INTER_REGION_LENGTH) {
                    continue;
                }
                SegmentSupport support = segmentSupport(
                        first.position(),
                        second.position(),
                        descriptor,
                        geology,
                        semantic,
                        fractureMembership,
                        aquiferMembership);
                if (!support.inside()
                        || Math.max(support.fracture(), support.aquifer()) < MIN_BRIDGE_SUPPORT) {
                    continue;
                }
                SkyIslandCaveConnectionKind kind = connectionKind(support);
                double cost = length
                        - 0.18 * support.fracture()
                        - 0.18 * support.aquifer();
                BridgeCandidate candidate = new BridgeCandidate(
                        new NodePair(first, second, length),
                        support,
                        kind,
                        cost);
                if (best == null || BridgeCandidate.ORDER.compare(candidate, best) < 0) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static SkyIslandCaveConnectionKind connectionKind(SegmentSupport support) {
        boolean fracture = support.fracture() >= MIN_BRIDGE_SUPPORT;
        boolean aquifer = support.aquifer() >= MIN_BRIDGE_SUPPORT;
        if (fracture && aquifer) {
            return SkyIslandCaveConnectionKind.MIXED_GEOLOGIC_BRIDGE;
        }
        return fracture
                ? SkyIslandCaveConnectionKind.FRACTURE_BRIDGE
                : SkyIslandCaveConnectionKind.AQUIFER_BRIDGE;
    }

    private static SegmentSupport segmentSupport(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second,
            SkyIslandDescriptor descriptor,
            SkyIslandGeologicRegionPlan geology,
            SkyIslandSemanticFieldSet semantic,
            double[] fractureMembership,
            double[] aquiferMembership) {
        double fractureMax = 0.0;
        double aquiferMax = 0.0;
        int fracturePositive = 0;
        int aquiferPositive = 0;

        for (int sample = 0; sample < BRIDGE_SAMPLES; sample++) {
            double t = sample / (BRIDGE_SAMPLES - 1.0);
            double x = lerp(first.x(), second.x(), t);
            double z = lerp(first.z(), second.z(), t);
            double depth = lerp(first.depthFraction(), second.depthFraction(), t);
            SkyIslandLocalPosition surface = new SkyIslandLocalPosition(x, z);
            if (semantic.interiority().sample(surface) <= 0.0) {
                return new SegmentSupport(false, 0.0, 0.0);
            }

            int index = nearestCellIndex(
                    x,
                    z,
                    depth,
                    descriptor.nominalRadius(),
                    geology);
            double fracture = fractureMembership[index];
            double aquifer = aquiferMembership[index];
            fractureMax = Math.max(fractureMax, fracture);
            aquiferMax = Math.max(aquiferMax, aquifer);
            if (fracture > 0.0) {
                fracturePositive++;
            }
            if (aquifer > 0.0) {
                aquiferPositive++;
            }
        }

        double fractureCoverage = fracturePositive / (double) BRIDGE_SAMPLES;
        double aquiferCoverage = aquiferPositive / (double) BRIDGE_SAMPLES;
        return new SegmentSupport(
                true,
                clamp01(0.58 * fractureMax + 0.42 * fractureCoverage),
                clamp01(0.58 * aquiferMax + 0.42 * aquiferCoverage));
    }

    private static int nearestCellIndex(
            double x,
            double z,
            double depth,
            double radius,
            SkyIslandGeologicRegionPlan geology) {
        int ix = clampIndex(
                (int) Math.round((x + radius) / geology.horizontalSpacing()),
                geology.gridSize());
        int iz = clampIndex(
                (int) Math.round((z + radius) / geology.horizontalSpacing()),
                geology.gridSize());
        int id = clampIndex(
                (int) Math.round(depth / geology.depthSpacing()),
                geology.depthSamples());
        return (iz * geology.depthSamples() + id) * geology.gridSize() + ix;
    }

    private static int clampIndex(int value, int size) {
        return Math.max(0, Math.min(size - 1, value));
    }

    private static double gridDistance(
            SkyIslandGeologicRegionCell first,
            SkyIslandGeologicRegionCell second,
            int gridSize,
            int depthSamples) {
        double dx = (first.xIndex() - second.xIndex()) / (double) (gridSize - 1);
        double dz = (first.zIndex() - second.zIndex()) / (double) (gridSize - 1);
        double dd = (first.depthIndex() - second.depthIndex()) / (double) (depthSamples - 1);
        return Math.sqrt(dx * dx + dz * dz + 1.35 * dd * dd);
    }

    private static double normalizedDistance(
            SkyIslandSubsurfacePosition first,
            SkyIslandSubsurfacePosition second,
            double radius) {
        double dx = (first.x() - second.x()) / radius;
        double dz = (first.z() - second.z()) / radius;
        double dd = first.depthFraction() - second.depthFraction();
        return Math.sqrt(dx * dx + dz * dz + 1.35 * dd * dd);
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record NodePair(
            SkyIslandCaveNode first,
            SkyIslandCaveNode second,
            double length) {
        private static final Comparator<NodePair> ORDER = Comparator
                .comparingDouble(NodePair::length)
                .thenComparingInt(pair -> pair.first().nodeId())
                .thenComparingInt(pair -> pair.second().nodeId());
    }

    private record SegmentSupport(
            boolean inside,
            double fracture,
            double aquifer) {}

    private record BridgeCandidate(
            NodePair pair,
            SegmentSupport support,
            SkyIslandCaveConnectionKind kind,
            double cost) {
        private static final Comparator<BridgeCandidate> ORDER = Comparator
                .comparingDouble(BridgeCandidate::cost)
                .thenComparingInt(candidate -> candidate.pair().first().nodeId())
                .thenComparingInt(candidate -> candidate.pair().second().nodeId());
    }

    private static final class DisjointSet {
        private final int[] parent;
        private final int[] rank;

        private DisjointSet(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private boolean union(int first, int second) {
            int rootA = find(first);
            int rootB = find(second);
            if (rootA == rootB) {
                return false;
            }
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
            return true;
        }
    }
}
