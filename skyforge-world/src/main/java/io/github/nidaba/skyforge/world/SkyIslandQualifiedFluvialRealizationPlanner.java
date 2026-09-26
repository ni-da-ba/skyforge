package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the first qualified terrain-mutating reset field from D2-accepted river reaches.
 *
 * <p>D2 rejection and unresolved transition ownership both fail closed. Only reaches that are
 * accepted and require no confluence, cascade, retained-basin, wetland, or unresolved terminal
 * transition receive terrain authority.
 */
public final class SkyIslandQualifiedFluvialRealizationPlanner {
    private SkyIslandQualifiedFluvialRealizationPlanner() {}

    public static SkyIslandQualifiedFluvialRealizationPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    public static SkyIslandQualifiedFluvialRealizationPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(policy, "policy");

        SkyIslandPreHydrologicTerrainField original =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandHydraulicChannelNetworkPlan hydraulic =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        List<SkyIslandGeomorphicReachDiagnostics> diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor, hydraulic, original);

        Map<Integer, SkyIslandChannelTerminalFate> terminalFates = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(
                        descriptor, hydraulic.geomorphicNetwork())) {
            terminalFates.put(fate.channelTerminalCellIndex(), fate);
        }

        List<SkyIslandQualifiedFluvialDeferral> deferredQualifications =
                new ArrayList<>();
        List<SkyIslandGeomorphicReachQualification> rejectedQualifications =
                new ArrayList<>();
        List<SkyIslandHydraulicReachGeometry> realizedReaches = new ArrayList<>();

        for (SkyIslandGeomorphicReachDiagnostics diagnostic : diagnostics) {
            SkyIslandGeomorphicReachQualification qualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            if (!qualification.accepted()) {
                rejectedQualifications.add(qualification);
                continue;
            }

            List<SkyIslandQualifiedFluvialDeferralReason> reasons =
                    deferralReasons(
                            hydraulic.geomorphicNetwork(),
                            terminalFates,
                            diagnostic.hydraulicReach());
            if (!reasons.isEmpty()) {
                deferredQualifications.add(
                        new SkyIslandQualifiedFluvialDeferral(qualification, reasons));
                continue;
            }

            realizedReaches.add(diagnostic.hydraulicReach());
        }

        SkyIslandQualifiedFluvialTerrainField realized =
                new SkyIslandQualifiedFluvialTerrainField(original, realizedReaches);

        // Necessary-but-not-sufficient prequalification is followed by realized-field requalification.
        // Only the post-realization qualification is published as terrain authority.
        List<SkyIslandGeomorphicReachQualification> realizedQualifications = new ArrayList<>();
        List<SkyIslandGeomorphicReachDiagnostics> realizedDiagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(
                        descriptor, hydraulic, realized);
        for (SkyIslandGeomorphicReachDiagnostics diagnostic : realizedDiagnostics) {
            if (!containsReach(realizedReaches, diagnostic.hydraulicReach())) {
                continue;
            }
            SkyIslandGeomorphicReachQualification realizedQualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            if (!realizedQualification.accepted()) {
                throw new IllegalStateException(
                        "realized reach violates D2 after continuous cross-section realization: "
                                + realizedQualification.violations());
            }
            realizedQualifications.add(realizedQualification);
        }
        if (realizedQualifications.size() != realizedReaches.size()) {
            throw new IllegalStateException(
                    "every terrain-authorized reach must publish one post-realization qualification");
        }

        return new SkyIslandQualifiedFluvialRealizationPlan(
                descriptor,
                hydraulic,
                realizedQualifications,
                deferredQualifications,
                rejectedQualifications,
                realized);
    }

    private static List<SkyIslandQualifiedFluvialDeferralReason> deferralReasons(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, SkyIslandChannelTerminalFate> terminalFates,
            SkyIslandHydraulicReachGeometry reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
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
                        "missing explicit watershed fate for channel terminal " + endNode.cellIndex());
            }
            SkyIslandChannelTerminalFatePolicy.deferralReason(fate.kind())
                    .ifPresent(reasons::add);
        }
        return List.copyOf(reasons);
    }

    private static boolean containsReach(
            List<SkyIslandHydraulicReachGeometry> reaches,
            SkyIslandHydraulicReachGeometry candidate) {
        SkyIslandSemanticChannelReach semantic =
                candidate.geomorphicRoute().semanticReach();
        for (SkyIslandHydraulicReachGeometry reach : reaches) {
            SkyIslandSemanticChannelReach other =
                    reach.geomorphicRoute().semanticReach();
            if (semantic.startCellIndex() == other.startCellIndex()
                    && semantic.endCellIndex() == other.endCellIndex()) {
                return true;
            }
        }
        return false;
    }
}
