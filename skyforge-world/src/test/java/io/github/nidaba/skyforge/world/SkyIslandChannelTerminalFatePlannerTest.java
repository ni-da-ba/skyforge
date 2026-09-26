package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.List;
import java.util.Map;
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
        Map<Integer, SkyIslandWatershedCell> cells = Map.of(
                10,
                cell(10, 11, false, false),
                11,
                cell(11, 12, false, false),
                12,
                cell(12, -1, false, true));

        SkyIslandChannelTerminalFate fate =
                SkyIslandChannelTerminalFatePlanner.resolve(10, cells, Map.of());

        assertEquals(10, fate.channelTerminalCellIndex());
        assertEquals(12, fate.watershedTerminalCellIndex());
        assertEquals(SkyIslandChannelTerminalFateKind.EDGE_OUTLET, fate.kind());
        assertEquals(List.of(10, 11, 12), fate.watershedPath());
        assertTrue(fate.waterbodyKind().isEmpty());
    }

    private static SkyIslandWatershedCell cell(
            int index,
            int downstream,
            boolean retainedSink,
            boolean edgeOutlet) {
        double surface = 0.5;
        return new SkyIslandWatershedCell(
                index,
                new SkyIslandLocalPosition(index, 0.0),
                surface,
                surface,
                0.0,
                0.1,
                0.1,
                downstream,
                retainedSink,
                edgeOutlet);
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }
}
