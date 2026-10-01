package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandSemanticChannelReachPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void macroReachDecompositionIsDeterministicAndPreservesEveryAcceptedCoarseSegment() {
        for (long key : new long[] {77L, 118L, 241L, 287L, 512L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            SkyIslandSemanticChannelReachPlan first = SkyIslandSemanticChannelReachPlanner.plan(descriptor);
            SkyIslandSemanticChannelReachPlan second = SkyIslandSemanticChannelReachPlanner.plan(descriptor);
            SkyIslandCoherentChannelPlan coherent = SkyIslandCoherentChannelPlanner.plan(descriptor);

            assertEquals(first, second);
            assertEquals(coherent.retainedReachCount(), first.coarseSegmentCount());

            for (SkyIslandSemanticChannelReach reach : first.reaches()) {
                assertEquals(reach.coarseSegmentCount() + 1, reach.guidancePoints().size());
                assertEquals(
                        reach.profiles().getFirst().segment().start(),
                        reach.guidancePoints().getFirst());
                assertEquals(
                        reach.profiles().getLast().segment().end(),
                        reach.guidancePoints().getLast());
            }
        }
    }

    @Test
    void ordinaryPerCellNodesAreGuidanceRatherThanMandatoryMacroAnchors() {
        SkyIslandSemanticChannelReachPlan plan = SkyIslandSemanticChannelReachPlanner.plan(descriptor(287L));
        assertTrue(
                plan.reaches().stream().anyMatch(reach -> reach.coarseSegmentCount() > 1),
                "the proving ground should contain at least one macro reach spanning ordinary coarse cells");

        Map<Integer, Integer> incoming = new HashMap<>();
        Map<Integer, Integer> outgoing = new HashMap<>();
        for (SkyIslandSemanticChannelReach reach : plan.reaches()) {
            for (SkyIslandChannelProfile profile : reach.profiles()) {
                SkyIslandChannelSegment segment = profile.segment();
                outgoing.merge(segment.sourceCellIndex(), 1, Integer::sum);
                incoming.merge(segment.downstreamCellIndex(), 1, Integer::sum);
                incoming.putIfAbsent(segment.sourceCellIndex(), 0);
                outgoing.putIfAbsent(segment.downstreamCellIndex(), 0);
            }
        }

        for (SkyIslandSemanticChannelReach reach : plan.reaches()) {
            List<SkyIslandChannelProfile> profiles = reach.profiles();
            for (int i = 0; i < profiles.size() - 1; i++) {
                int ordinaryNode = profiles.get(i).segment().downstreamCellIndex();
                assertEquals(1, incoming.get(ordinaryNode));
                assertEquals(1, outgoing.get(ordinaryNode));
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
