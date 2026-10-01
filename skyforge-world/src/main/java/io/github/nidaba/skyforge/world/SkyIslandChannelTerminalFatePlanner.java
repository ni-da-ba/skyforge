package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves channel terminal fate through the authoritative watershed graph.
 *
 * <p>A channel semantic terminal is not assumed to coincide with a watershed sink. The accepted
 * channel network may stop before a retained depression or edge outlet, so basin/outlet ownership
 * must follow downstream watershed topology rather than coordinate or cell-index coincidence.
 */
public final class SkyIslandChannelTerminalFatePlanner {
    private SkyIslandChannelTerminalFatePlanner() {}

    public static List<SkyIslandChannelTerminalFate> plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicChannelNetworkPlan network) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(network, "network");
        if (!descriptor.equals(network.descriptor())) {
            throw new IllegalArgumentException("network descriptor must match terminal-fate descriptor");
        }

        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandWaterbodyPlan waterbodies = SkyIslandWaterbodyPlanner.plan(descriptor);

        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }
        Map<Integer, SkyIslandWaterbodyCandidate> candidateBySink = new HashMap<>();
        for (SkyIslandWaterbodyCandidate candidate : waterbodies.candidates()) {
            candidateBySink.put(candidate.sinkCellIndex(), candidate);
        }

        List<SkyIslandChannelTerminalFate> result = new ArrayList<>();
        for (SkyIslandGeomorphicNetworkNode node : network.nodes()) {
            if (node.kind() != SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
                continue;
            }
            result.add(resolve(node.cellIndex(), cells, candidateBySink));
        }
        return List.copyOf(result);
    }

    static SkyIslandChannelTerminalFate resolve(
            int channelTerminal,
            Map<Integer, SkyIslandWatershedCell> cells,
            Map<Integer, SkyIslandWaterbodyCandidate> candidateBySink) {
        List<Integer> path = new ArrayList<>();
        Set<Integer> visiting = new HashSet<>();
        int current = channelTerminal;
        while (true) {
            if (!visiting.add(current)) {
                throw new IllegalStateException(
                        "watershed cycle encountered while resolving channel terminal fate");
            }
            SkyIslandWatershedCell cell = cells.get(current);
            if (cell == null) {
                throw new IllegalStateException(
                        "channel terminal references missing watershed cell " + current);
            }
            path.add(current);
            if (cell.downstreamIndex() < 0) {
                SkyIslandWaterbodyCandidate candidate = candidateBySink.get(cell.index());
                if (candidate != null) {
                    SkyIslandChannelTerminalFateKind kind =
                            candidate.kind() == SkyIslandWaterbodyKind.WETLAND
                                    ? SkyIslandChannelTerminalFateKind.RETAINED_WETLAND
                                    : SkyIslandChannelTerminalFateKind.RETAINED_OPEN_WATER;
                    return new SkyIslandChannelTerminalFate(
                            channelTerminal,
                            cell.index(),
                            kind,
                            java.util.Optional.of(candidate.kind()),
                            path);
                }
                SkyIslandChannelTerminalFateKind kind = cell.edgeOutlet()
                        ? SkyIslandChannelTerminalFateKind.EDGE_OUTLET
                        : SkyIslandChannelTerminalFateKind.UNRESOLVED;
                return new SkyIslandChannelTerminalFate(
                        channelTerminal,
                        cell.index(),
                        kind,
                        java.util.Optional.empty(),
                        path);
            }
            current = cell.downstreamIndex();
        }
    }
}
