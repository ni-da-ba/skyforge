package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives stream order, semantic role, and relative corridor scale from selected channel topology. */
public final class SkyIslandChannelNetworkPlanner {
    private SkyIslandChannelNetworkPlanner() {}

    public static SkyIslandChannelNetworkPlan plan(SkyIslandDescriptor descriptor) {
        SkyIslandHydrologicFeaturePlan featurePlan = SkyIslandHydrologicFeaturePlanner.plan(descriptor);
        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        Map<Integer, SkyIslandHydrologicFeature> channels = new HashMap<>();
        for (SkyIslandHydrologicFeature feature : featurePlan.features()) {
            if (feature.kind() == SkyIslandHydrologicFeatureKind.CHANNEL) {
                channels.put(feature.sourceCellIndex(), feature);
            }
        }

        Map<Integer, List<Integer>> upstream = new HashMap<>();
        for (int source : channels.keySet()) {
            upstream.put(source, new ArrayList<>());
        }
        for (SkyIslandHydrologicFeature feature : channels.values()) {
            List<Integer> inbound = upstream.get(feature.downstreamCellIndex());
            if (inbound != null) {
                inbound.add(feature.sourceCellIndex());
            }
        }
        for (List<Integer> inbound : upstream.values()) {
            inbound.sort(Integer::compareTo);
        }

        Map<Integer, Integer> orders = new HashMap<>();
        for (int source : channels.keySet()) {
            streamOrder(source, upstream, orders, new HashSet<>());
        }
        int maxOrder = orders.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Set<Integer> mainStemCells = mainStemCells(channels, upstream, cells);
        double maxAccumulation = Math.max(1.0e-12, watershed.maxFlowAccumulation());

        List<SkyIslandChannelSegment> segments = new ArrayList<>();
        channels.values().stream()
                .sorted((a, b) -> Integer.compare(a.sourceCellIndex(), b.sourceCellIndex()))
                .forEach(feature -> {
                    SkyIslandWatershedCell source = requireCell(cells, feature.sourceCellIndex());
                    SkyIslandWatershedCell downstream = requireCell(cells, feature.downstreamCellIndex());
                    int order = orders.get(feature.sourceCellIndex());
                    double relativeDischarge = clamp01(source.flowAccumulation() / maxAccumulation);
                    boolean headwater = upstream.get(feature.sourceCellIndex()).isEmpty();
                    SkyIslandChannelRole role;
                    if (headwater) {
                        role = SkyIslandChannelRole.HEADWATER;
                    } else if (mainStemCells.contains(feature.sourceCellIndex())) {
                        role = SkyIslandChannelRole.TRUNK;
                    } else {
                        role = SkyIslandChannelRole.TRIBUTARY;
                    }
                    double orderScale = maxOrder == 0 ? 0.0 : (double) order / maxOrder;
                    double corridorScale = clamp01(0.55 * relativeDischarge + 0.45 * orderScale);
                    segments.add(new SkyIslandChannelSegment(
                            feature.sourceCellIndex(),
                            feature.downstreamCellIndex(),
                            source.position(),
                            downstream.position(),
                            order,
                            role,
                            relativeDischarge,
                            corridorScale));
                });

        return new SkyIslandChannelNetworkPlan(descriptor, segments, maxOrder);
    }

    private static Set<Integer> mainStemCells(
            Map<Integer, SkyIslandHydrologicFeature> channels,
            Map<Integer, List<Integer>> upstream,
            Map<Integer, SkyIslandWatershedCell> cells) {
        List<Integer> terminals = channels.values().stream()
                .filter(feature -> !channels.containsKey(feature.downstreamCellIndex()))
                .map(SkyIslandHydrologicFeature::sourceCellIndex)
                .sorted()
                .toList();
        Set<Integer> mainStems = new HashSet<>();

        for (int terminal : terminals) {
            int current = terminal;
            while (true) {
                List<Integer> inbound = upstream.getOrDefault(current, List.of());
                if (inbound.isEmpty()) {
                    break;
                }
                mainStems.add(current);
                current = strongestUpstream(inbound, cells);
            }
        }
        return Set.copyOf(mainStems);
    }

    private static int strongestUpstream(List<Integer> inbound, Map<Integer, SkyIslandWatershedCell> cells) {
        int strongest = inbound.getFirst();
        double strongestAccumulation = requireCell(cells, strongest).flowAccumulation();
        for (int i = 1; i < inbound.size(); i++) {
            int candidate = inbound.get(i);
            double accumulation = requireCell(cells, candidate).flowAccumulation();
            if (accumulation > strongestAccumulation
                    || (accumulation == strongestAccumulation && candidate < strongest)) {
                strongest = candidate;
                strongestAccumulation = accumulation;
            }
        }
        return strongest;
    }

    private static int streamOrder(
            int cellIndex,
            Map<Integer, List<Integer>> upstream,
            Map<Integer, Integer> memo,
            Set<Integer> visiting) {
        Integer known = memo.get(cellIndex);
        if (known != null) {
            return known;
        }
        if (!visiting.add(cellIndex)) {
            throw new IllegalStateException("channel topology contains a cycle");
        }

        List<Integer> inbound = upstream.getOrDefault(cellIndex, List.of());
        int result;
        if (inbound.isEmpty()) {
            result = 1;
        } else {
            int maximum = 0;
            int maximumCount = 0;
            for (int source : inbound) {
                int order = streamOrder(source, upstream, memo, visiting);
                if (order > maximum) {
                    maximum = order;
                    maximumCount = 1;
                } else if (order == maximum) {
                    maximumCount++;
                }
            }
            result = maximumCount >= 2 ? maximum + 1 : maximum;
        }
        visiting.remove(cellIndex);
        memo.put(cellIndex, result);
        return result;
    }

    private static SkyIslandWatershedCell requireCell(Map<Integer, SkyIslandWatershedCell> cells, int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException("channel references missing watershed cell " + index);
        }
        return cell;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
