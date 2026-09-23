package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkyIslandChannelMacroReachPlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void macroReachPartitionIsDeterministicCompleteAndContiguous() {
        for (long key : new long[] {287L, 649L, 811L}) {
            SkyIslandDescriptor descriptor = descriptor(key);
            List<SkyIslandChannelProfile> profiles =
                    SkyIslandCoherentChannelPlanner.plan(descriptor).profiles();
            var first = SkyIslandChannelMacroReachPlanner.plan(profiles);
            var second = SkyIslandChannelMacroReachPlanner.plan(profiles);
            assertEquals(first, second);

            Set<Integer> visited = new HashSet<>();
            for (SkyIslandChannelMacroReach macro : first) {
                for (SkyIslandChannelProfile profile : macro.profiles()) {
                    assertTrue(visited.add(profile.segment().sourceCellIndex()));
                }
                for (int i = 1; i < macro.profiles().size(); i++) {
                    assertEquals(
                            macro.profiles().get(i - 1).segment().downstreamCellIndex(),
                            macro.profiles().get(i).segment().sourceCellIndex());
                }
            }
            assertEquals(profiles.size(), visited.size());
        }
    }

    @Test
    void onlyDegreeTwoNodesAreAllowedInsideMacroReaches() {
        SkyIslandDescriptor descriptor = descriptor(287L);
        List<SkyIslandChannelProfile> profiles =
                SkyIslandCoherentChannelPlanner.plan(descriptor).profiles();

        Map<Integer, Integer> incoming = new HashMap<>();
        Set<Integer> outgoing = new HashSet<>();
        for (SkyIslandChannelProfile profile : profiles) {
            incoming.merge(profile.segment().downstreamCellIndex(), 1, Integer::sum);
            outgoing.add(profile.segment().sourceCellIndex());
        }

        boolean sawCollapsedChain = false;
        for (SkyIslandChannelMacroReach macro : SkyIslandChannelMacroReachPlanner.plan(profiles)) {
            sawCollapsedChain |= macro.coarseReachCount() > 1;
            for (int i = 1; i < macro.profiles().size(); i++) {
                int internal = macro.profiles().get(i).segment().sourceCellIndex();
                assertEquals(1, incoming.getOrDefault(internal, 0));
                assertTrue(outgoing.contains(internal));
            }
        }
        assertTrue(sawCollapsedChain);
    }

    private static SkyIslandDescriptor descriptor(long key) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, 8L, 81L, key));
    }
}
