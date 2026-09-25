package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the first qualified terrain-mutating reset field from D2-accepted river reaches only.
 *
 * <p>Rejected reaches are preserved as explicit evidence and contribute no terrain delta.
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

        List<SkyIslandGeomorphicReachQualification> accepted = new ArrayList<>();
        List<SkyIslandGeomorphicReachQualification> rejected = new ArrayList<>();
        List<SkyIslandHydraulicReachGeometry> acceptedReaches = new ArrayList<>();

        for (SkyIslandGeomorphicReachDiagnostics diagnostic : diagnostics) {
            SkyIslandGeomorphicReachQualification qualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            if (qualification.accepted()) {
                accepted.add(qualification);
                acceptedReaches.add(diagnostic.hydraulicReach());
            } else {
                rejected.add(qualification);
            }
        }

        SkyIslandQualifiedFluvialTerrainField realized =
                new SkyIslandQualifiedFluvialTerrainField(original, acceptedReaches);

        // Necessary-but-not-sufficient prequalification is followed by realized-field requalification.
        List<SkyIslandGeomorphicReachDiagnostics> realizedDiagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measure(descriptor, hydraulic, realized);
        for (SkyIslandGeomorphicReachDiagnostics diagnostic : realizedDiagnostics) {
            if (!containsReach(acceptedReaches, diagnostic.hydraulicReach())) {
                continue;
            }
            SkyIslandGeomorphicReachQualification realizedQualification =
                    SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostic, policy);
            if (!realizedQualification.accepted()) {
                throw new IllegalStateException(
                        "accepted reach violates D2 after continuous cross-section realization: "
                                + realizedQualification.violations());
            }
        }

        return new SkyIslandQualifiedFluvialRealizationPlan(
                descriptor, hydraulic, accepted, rejected, realized);
    }

    private static boolean containsReach(
            List<SkyIslandHydraulicReachGeometry> accepted,
            SkyIslandHydraulicReachGeometry candidate) {
        SkyIslandSemanticChannelReach semantic =
                candidate.geomorphicRoute().semanticReach();
        for (SkyIslandHydraulicReachGeometry reach : accepted) {
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
