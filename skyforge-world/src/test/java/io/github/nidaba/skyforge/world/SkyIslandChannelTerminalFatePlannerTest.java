package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkyIslandChannelTerminalFatePlannerTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void terminalFateIsDeterministicAndCoversEveryNetworkTerminal() {
        for (SkyIslandDescriptor descriptor : List.of(
                descriptor(8L, 81L, 632L),
                descriptor(8L, 81L, 512L),
                descriptor(6L, 61L, 83L))) {
            SkyIslandGeomorphicChannelNetworkPlan network =
                    SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
            List<SkyIslandChannelTerminalFate> first =
                    SkyIslandChannelTerminalFatePlanner.plan(descriptor, network);
            List<SkyIslandChannelTerminalFate> second =
                    SkyIslandChannelTerminalFatePlanner.plan(descriptor, network);

            assertEquals(first, second);
            long terminalCount = network.nodes().stream()
                    .filter(node -> node.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL)
                    .count();
            assertEquals(terminalCount, first.size());
            assertTrue(first.stream().allMatch(fate -> !fate.watershedPath().isEmpty()));
        }
    }

    @Test
    void terminalFateMayContinueBeyondTheSemanticChannelEndpoint() {
        SkyIslandDescriptor descriptor = descriptor(6L, 61L, 83L);
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        List<SkyIslandChannelTerminalFate> fates =
                SkyIslandChannelTerminalFatePlanner.plan(descriptor, network);

        assertTrue(
                fates.stream().anyMatch(fate -> fate.watershedPath().size() > 1),
                "fixture must exercise downstream watershed routing beyond a channel terminal");
        for (SkyIslandChannelTerminalFate fate : fates) {
            assertEquals(
                    fate.channelTerminalCellIndex(),
                    fate.watershedPath().getFirst());
            assertEquals(
                    fate.watershedTerminalCellIndex(),
                    fate.watershedPath().getLast());
            if (fate.watershedPath().size() > 1) {
                assertNotEquals(
                        fate.channelTerminalCellIndex(),
                        fate.watershedTerminalCellIndex());
            }
        }
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
