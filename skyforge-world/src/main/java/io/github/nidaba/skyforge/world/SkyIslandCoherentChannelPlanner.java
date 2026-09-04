package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Suppresses spatially redundant visible drainage components while preserving accepted routing.
 *
 * <p>The watershed remains authoritative. This planner operates only on already accepted channel
 * components and decides which disconnected components deserve to remain distinct visible rivers.
 */
public final class SkyIslandCoherentChannelPlanner {
    /** Minimum terminal separation, measured in AUTH-0005 planning-cell spacings. */
    public static final double MIN_TERMINAL_SEPARATION_CELLS = 4.0;

    private SkyIslandCoherentChannelPlanner() {}

    public static SkyIslandCoherentChannelPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandChannelProfilePlan profilePlan = SkyIslandChannelProfilePlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        List<SkyIslandCoherentChannelComponent> components = components(profilePlan);

        List<SkyIslandCoherentChannelComponent> ordered = new ArrayList<>(components);
        ordered.sort(Comparator
                .comparingDouble(SkyIslandCoherentChannelComponent::terminalRelativeDischarge)
                .reversed()
                .thenComparing(Comparator.comparingInt(SkyIslandCoherentChannelComponent::maxStreamOrder).reversed())
                .thenComparing(Comparator.comparingInt(SkyIslandCoherentChannelComponent::reachCount).reversed())
                .thenComparingInt(SkyIslandCoherentChannelComponent::terminalCellIndex));

        double minimumDistance = watershed.spacing() * MIN_TERMINAL_SEPARATION_CELLS;
        List<SkyIslandCoherentChannelComponent> retained = new ArrayList<>();
        for (SkyIslandCoherentChannelComponent candidate : ordered) {
            if (retained.stream().allMatch(existing ->
                    distance(candidate.terminalPosition(), existing.terminalPosition()) + 1.0e-12
                            >= minimumDistance)) {
                retained.add(candidate);
            }
        }

        retained.sort(Comparator.comparingInt(SkyIslandCoherentChannelComponent::terminalCellIndex));
        return new SkyIslandCoherentChannelPlan(
                descriptor,
                watershed.spacing(),
                components.size(),
                retained);
    }

    private static List<SkyIslandCoherentChannelComponent> components(
            SkyIslandChannelProfilePlan profilePlan) {
        Map<Integer, List<Integer>> adjacency = new HashMap<>();
        Map<Integer, SkyIslandChannelProfile> bySource = new HashMap<>();
        Map<Integer, SkyIslandLocalPosition> nodePositions = new HashMap<>();

        for (SkyIslandChannelProfile profile : profilePlan.profiles()) {
            SkyIslandChannelSegment segment = profile.segment();
            SkyIslandChannelProfile previous = bySource.put(segment.sourceCellIndex(), profile);
            if (previous != null) {
                throw new IllegalStateException("accepted channel graph has multiple outgoing reaches from one cell");
            }
            adjacency.computeIfAbsent(segment.sourceCellIndex(), ignored -> new ArrayList<>())
                    .add(segment.downstreamCellIndex());
            adjacency.computeIfAbsent(segment.downstreamCellIndex(), ignored -> new ArrayList<>())
                    .add(segment.sourceCellIndex());
            nodePositions.put(segment.sourceCellIndex(), segment.start());
            nodePositions.put(segment.downstreamCellIndex(), segment.end());
        }

        List<Integer> roots = adjacency.keySet().stream().sorted().toList();
        Set<Integer> visited = new HashSet<>();
        List<SkyIslandCoherentChannelComponent> result = new ArrayList<>();

        for (int root : roots) {
            if (!visited.add(root)) {
                continue;
            }
            Set<Integer> nodes = new HashSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(root);
            nodes.add(root);

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                for (int neighbor : adjacency.getOrDefault(current, List.of())) {
                    if (nodes.add(neighbor)) {
                        visited.add(neighbor);
                        queue.addLast(neighbor);
                    }
                }
            }

            List<SkyIslandChannelProfile> profiles = bySource.values().stream()
                    .filter(profile -> nodes.contains(profile.segment().sourceCellIndex()))
                    .sorted(Comparator.comparingInt(profile -> profile.segment().sourceCellIndex()))
                    .toList();
            if (profiles.isEmpty()) {
                continue;
            }

            Set<Integer> sourceCells = profiles.stream()
                    .map(profile -> profile.segment().sourceCellIndex())
                    .collect(java.util.stream.Collectors.toSet());
            Set<Integer> terminalNodes = profiles.stream()
                    .map(profile -> profile.segment().downstreamCellIndex())
                    .filter(index -> !sourceCells.contains(index))
                    .collect(java.util.stream.Collectors.toSet());
            if (terminalNodes.size() != 1) {
                throw new IllegalStateException(
                        "accepted channel component must terminate at exactly one downstream node");
            }
            int terminalCell = terminalNodes.iterator().next();
            SkyIslandChannelProfile representative = profiles.stream()
                    .filter(profile -> profile.segment().downstreamCellIndex() == terminalCell)
                    .max(Comparator
                            .comparingDouble((SkyIslandChannelProfile profile) ->
                                    profile.segment().relativeDischarge())
                            .thenComparingInt(profile -> -profile.segment().sourceCellIndex()))
                    .orElseThrow();
            double terminalDischarge = profiles.stream()
                    .filter(profile -> profile.segment().downstreamCellIndex() == terminalCell)
                    .mapToDouble(profile -> profile.segment().relativeDischarge())
                    .max()
                    .orElseThrow();
            int maxOrder = profiles.stream()
                    .mapToInt(profile -> profile.segment().streamOrder())
                    .max()
                    .orElseThrow();
            SkyIslandLocalPosition terminalPosition = nodePositions.get(terminalCell);
            if (terminalPosition == null) {
                throw new IllegalStateException("missing terminal node position");
            }

            result.add(new SkyIslandCoherentChannelComponent(
                    terminalCell,
                    terminalPosition,
                    terminalDischarge,
                    maxOrder,
                    profiles));
        }

        return List.copyOf(result);
    }

    private static double distance(SkyIslandLocalPosition a, SkyIslandLocalPosition b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
