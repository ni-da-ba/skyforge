package io.github.nidaba.skyforge.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyIslandFluvialTransitionOwnershipTest {
    private static final long SEED = 0x534B59464F524745L;

    @Test
    void confluenceFixtureExposesConfluenceOwnership() {
        Fixture fixture = fixture(descriptor(8L, 81L, 632L));
        assertTrue(fixture.network().routes().stream()
                .map(route -> reasons(fixture, route.semanticReach()))
                .anyMatch(reasons -> reasons.contains(
                        SkyIslandQualifiedFluvialDeferralReason
                                .CONFLUENCE_TRANSITION_REQUIRED)));
    }

    @Test
    void stressFixtureExposesCascadeAndUnresolvedTerminalOwnership() {
        Fixture fixture = fixture(descriptor(6L, 61L, 512L));
        List<List<SkyIslandQualifiedFluvialDeferralReason>> reasons =
                fixture.network().routes().stream()
                        .map(route -> reasons(fixture, route.semanticReach()))
                        .toList();

        assertTrue(reasons.stream().anyMatch(list -> list.contains(
                SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED)));
        assertTrue(reasons.stream().anyMatch(list -> list.contains(
                SkyIslandQualifiedFluvialDeferralReason.UNRESOLVED_TERMINAL_FATE)));
    }

    @Test
    void onlyExplicitEdgeOutletCanBeOrdinaryFreeTerminal() {
        for (SkyIslandDescriptor descriptor : List.of(
                descriptor(8L, 81L, 77L),
                descriptor(8L, 81L, 118L),
                descriptor(8L, 81L, 512L),
                descriptor(6L, 61L, 512L))) {
            Fixture fixture = fixture(descriptor);
            for (SkyIslandGeomorphicReachRoute route : fixture.network().routes()) {
                SkyIslandSemanticChannelReach semantic = route.semanticReach();
                SkyIslandGeomorphicNetworkNode end =
                        fixture.network().requireNode(semantic.endCellIndex());
                if (end.kind() != SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
                    continue;
                }

                List<SkyIslandQualifiedFluvialDeferralReason> reasons =
                        reasons(fixture, semantic);
                SkyIslandChannelTerminalFate fate =
                        fixture.terminalFates().get(end.cellIndex());
                assertFalse(fate == null);

                boolean terminalFateDeferred = reasons.stream().anyMatch(reason ->
                        reason == SkyIslandQualifiedFluvialDeferralReason
                                        .RETAINED_WATER_TRANSITION_REQUIRED
                                || reason == SkyIslandQualifiedFluvialDeferralReason
                                        .WETLAND_TRANSITION_REQUIRED
                                || reason == SkyIslandQualifiedFluvialDeferralReason
                                        .UNRESOLVED_TERMINAL_FATE);
                assertEquals(
                        fate.kind() != SkyIslandChannelTerminalFateKind.EDGE_OUTLET,
                        terminalFateDeferred);
                if (!terminalFateDeferred) {
                    assertEquals(
                            SkyIslandChannelTerminalFateKind.EDGE_OUTLET,
                            fate.kind());
                }
            }
        }
    }

    private static List<SkyIslandQualifiedFluvialDeferralReason> reasons(
            Fixture fixture,
            SkyIslandSemanticChannelReach semantic) {
        return SkyIslandFluvialTransitionOwnership.reasons(
                fixture.network(), fixture.terminalFates(), semantic);
    }

    private static Fixture fixture(SkyIslandDescriptor descriptor) {
        SkyIslandGeomorphicChannelNetworkPlan network =
                SkyIslandGeomorphicChannelNetworkPlanner.plan(descriptor);
        Map<Integer, SkyIslandChannelTerminalFate> terminalFates = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(descriptor, network)) {
            terminalFates.put(fate.channelTerminalCellIndex(), fate);
        }
        return new Fixture(network, Map.copyOf(terminalFates));
    }

    private static SkyIslandDescriptor descriptor(long province, long cluster, long island) {
        return SkyIslandDescriptorGenerator.derive(
                SkyIslandIdentity.of(SEED, province, cluster, island));
    }

    private record Fixture(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, SkyIslandChannelTerminalFate> terminalFates) {}
}
