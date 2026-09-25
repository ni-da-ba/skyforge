package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collapses accepted per-cell channel chains into semantic source/confluence/terminal reaches.
 *
 * <p>This is the first authority correction in the post-H6 hydrology reset. Catchment topology and
 * graph relationships remain authoritative, but ordinary intermediate planning cells no longer
 * become mandatory physical river coordinates. Their positions survive only as soft guidance
 * points for a later terrain-aware continuous solver.
 */
public final class SkyIslandSemanticChannelReachPlanner {
    private SkyIslandSemanticChannelReachPlanner() {}

    public static SkyIslandSemanticChannelReachPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandCoherentChannelPlan coherent = SkyIslandCoherentChannelPlanner.plan(descriptor);

        List<SkyIslandSemanticChannelReach> reaches = new ArrayList<>();
        int expectedProfileCount = 0;
        for (SkyIslandCoherentChannelComponent component : coherent.retainedComponents()) {
            List<SkyIslandChannelProfile> profiles = component.profiles();
            expectedProfileCount += profiles.size();

            Map<Integer, SkyIslandChannelProfile> outgoing = new HashMap<>();
            Map<Integer, Integer> incomingCount = new HashMap<>();
            Set<Integer> nodes = new HashSet<>();

            for (SkyIslandChannelProfile profile : profiles) {
                SkyIslandChannelSegment segment = profile.segment();
                SkyIslandChannelProfile previous = outgoing.put(segment.sourceCellIndex(), profile);
                if (previous != null) {
                    throw new IllegalStateException(
                            "coherent channel component has multiple outgoing profiles from one cell");
                }
                incomingCount.merge(segment.downstreamCellIndex(), 1, Integer::sum);
                incomingCount.putIfAbsent(segment.sourceCellIndex(), 0);
                nodes.add(segment.sourceCellIndex());
                nodes.add(segment.downstreamCellIndex());
            }

            Set<Integer> anchors = new HashSet<>();
            for (int node : nodes) {
                int incoming = incomingCount.getOrDefault(node, 0);
                boolean terminal = !outgoing.containsKey(node);
                if (incoming != 1 || terminal) {
                    anchors.add(node);
                }
            }

            Set<Integer> coveredSources = new HashSet<>();
            List<Integer> starts = anchors.stream()
                    .filter(outgoing::containsKey)
                    .sorted()
                    .toList();

            for (int start : starts) {
                List<SkyIslandChannelProfile> chain = new ArrayList<>();
                Set<Integer> visiting = new HashSet<>();
                int current = start;
                while (true) {
                    if (!visiting.add(current)) {
                        throw new IllegalStateException("semantic channel reach contains a cycle");
                    }
                    SkyIslandChannelProfile profile = outgoing.get(current);
                    if (profile == null) {
                        throw new IllegalStateException("semantic reach terminated without a terminal anchor");
                    }
                    if (!coveredSources.add(current)) {
                        throw new IllegalStateException("coarse channel profile belongs to multiple semantic reaches");
                    }
                    chain.add(profile);
                    int downstream = profile.segment().downstreamCellIndex();
                    if (anchors.contains(downstream)) {
                        reaches.add(new SkyIslandSemanticChannelReach(start, downstream, chain));
                        break;
                    }
                    current = downstream;
                }
            }

            if (coveredSources.size() != profiles.size()) {
                throw new IllegalStateException("semantic reach decomposition did not cover the coherent channel component");
            }
        }

        reaches.sort(Comparator.comparingInt(SkyIslandSemanticChannelReach::startCellIndex)
                .thenComparingInt(SkyIslandSemanticChannelReach::endCellIndex));

        SkyIslandSemanticChannelReachPlan result =
                new SkyIslandSemanticChannelReachPlan(descriptor, coherent.planningSpacing(), reaches);
        if (result.coarseSegmentCount() != expectedProfileCount) {
            throw new IllegalStateException("semantic reach plan changed accepted channel topology");
        }
        return result;
    }
}
