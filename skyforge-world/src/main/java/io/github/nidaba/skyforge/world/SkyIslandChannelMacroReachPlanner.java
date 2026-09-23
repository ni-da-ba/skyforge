package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collapses degree-two channel chains into macro-reaches for physical centerline routing.
 *
 * <p>This does not change the authored watershed graph or any profile semantics. It only identifies
 * which graph nodes must remain hard geometric controls. A source with zero inbound retained
 * reaches, a confluence with more than one inbound reach, and every terminal remain controls.
 */
public final class SkyIslandChannelMacroReachPlanner {
    private SkyIslandChannelMacroReachPlanner() {}

    public static List<SkyIslandChannelMacroReach> plan(List<SkyIslandChannelProfile> profiles) {
        profiles = List.copyOf(profiles);
        Map<Integer, SkyIslandChannelProfile> outgoing = new HashMap<>();
        Map<Integer, List<SkyIslandChannelProfile>> incoming = new HashMap<>();

        for (SkyIslandChannelProfile profile : profiles) {
            SkyIslandChannelSegment segment = profile.segment();
            SkyIslandChannelProfile previous = outgoing.put(segment.sourceCellIndex(), profile);
            if (previous != null) {
                throw new IllegalStateException(
                        "accepted channel graph has multiple outgoing reaches from one cell");
            }
            incoming.computeIfAbsent(segment.downstreamCellIndex(), ignored -> new ArrayList<>())
                    .add(profile);
        }
        incoming.values().forEach(list -> list.sort(Comparator.comparingInt(
                profile -> profile.segment().sourceCellIndex())));

        List<SkyIslandChannelProfile> starts = profiles.stream()
                .filter(profile -> incoming
                                .getOrDefault(profile.segment().sourceCellIndex(), List.of())
                                .size()
                        != 1)
                .sorted(Comparator.comparingInt(profile -> profile.segment().sourceCellIndex()))
                .toList();

        Set<Integer> visitedSources = new HashSet<>();
        List<SkyIslandChannelMacroReach> result = new ArrayList<>();
        for (SkyIslandChannelProfile start : starts) {
            if (visitedSources.contains(start.segment().sourceCellIndex())) {
                continue;
            }
            List<SkyIslandChannelProfile> chain = new ArrayList<>();
            SkyIslandChannelProfile current = start;
            while (current != null) {
                int source = current.segment().sourceCellIndex();
                if (!visitedSources.add(source)) {
                    throw new IllegalStateException("macro-reach planning encountered a channel cycle");
                }
                chain.add(current);

                int nextNode = current.segment().downstreamCellIndex();
                SkyIslandChannelProfile next = outgoing.get(nextNode);
                if (next == null) {
                    break;
                }
                if (incoming.getOrDefault(nextNode, List.of()).size() != 1) {
                    break;
                }
                current = next;
            }
            result.add(new SkyIslandChannelMacroReach(chain));
        }

        if (visitedSources.size() != profiles.size()) {
            List<Integer> missing = profiles.stream()
                    .map(profile -> profile.segment().sourceCellIndex())
                    .filter(source -> !visitedSources.contains(source))
                    .sorted()
                    .toList();
            throw new IllegalStateException("macro-reach planning left unvisited profiles " + missing);
        }

        result.sort(Comparator.comparingInt(SkyIslandChannelMacroReach::sourceCellIndex));
        return List.copyOf(result);
    }
}
