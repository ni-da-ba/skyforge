package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One canonical fail-closed transition-ownership classifier for fluvial reaches.
 *
 * <p>Hydraulic candidate solving and terrain realization must consume the same classification so a
 * reach cannot be transition-owned in one layer and ordinary in another.
 */
public final class SkyIslandFluvialTransitionOwnership {
    private SkyIslandFluvialTransitionOwnership() {}

    public static List<SkyIslandQualifiedFluvialDeferralReason> reasons(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, SkyIslandChannelTerminalFate> terminalFates,
            SkyIslandSemanticChannelReach semantic) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(terminalFates, "terminalFates");
        Objects.requireNonNull(semantic, "semantic");

        List<SkyIslandQualifiedFluvialDeferralReason> reasons = new ArrayList<>();
        SkyIslandGeomorphicNetworkNode startNode =
                network.requireNode(semantic.startCellIndex());
        SkyIslandGeomorphicNetworkNode endNode =
                network.requireNode(semantic.endCellIndex());

        if (startNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                || endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CONFLUENCE_TRANSITION_REQUIRED);
        }
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED);
        }
        if (endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
            SkyIslandChannelTerminalFate fate = terminalFates.get(endNode.cellIndex());
            if (fate == null) {
                throw new IllegalStateException(
                        "missing explicit watershed fate for channel terminal "
                                + endNode.cellIndex());
            }
            SkyIslandChannelTerminalFatePolicy.deferralReason(fate.kind())
                    .ifPresent(reasons::add);
        }
        return List.copyOf(reasons);
    }
}
