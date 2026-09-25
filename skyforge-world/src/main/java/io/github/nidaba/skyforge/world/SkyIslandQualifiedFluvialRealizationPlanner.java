package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the first qualified terrain-mutating reset field from D2-accepted river reaches.
 *
 * <p>D2 rejection and unresolved transition ownership both fail closed. Only reaches that are
 * accepted and require no confluence or cascade transition receive terrain authority.
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

        List<SkyIslandGeomorphicReachQualification> realizedQualifications =
                new ArrayList<>();
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
                            diagnostic.hydraulicReach());
            if (!reasons.isEmpty()) {
                deferredQualifications.add(
                        new SkyIslandQualifiedFluvialDeferral(qualification, reasons));
                continue;
            }

            realizedQualifications.add(qualification);
            realizedReaches.add(diagnostic.hydraulicReach());
        }

        SkyIslandQualifiedFluvialTerrainField realized =
                new SkyIslandQualifiedFluvialTerrainField(original, realizedReaches);

        // Necessary-but-not-sufficient prequalification is followed by realized-field requalification.
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
            SkyIslandHydraulicReachGeometry reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        List<SkyIslandQualifiedFluvialDeferralReason> reasons = new ArrayList<>();

        if (network.requireNode(semantic.startCellIndex()).kind()
                        == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                || network.requireNode(semantic.endCellIndex()).kind()
                        == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CONFLUENCE_TRANSITION_REQUIRED);
        }
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED);
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
