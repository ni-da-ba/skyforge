package io.github.nidaba.skyforge.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Identifies F3H joint solutions that can replace exactly one F3B/F3C overlap.
 *
 * <p>A solved local QP is not sufficient by itself: the corresponding independent plans must
 * describe the same confluence and CASCADE, and no second joint candidate may own either site.
 * Ordinary spans and network assembly share this admission decision.
 */
final class SkyIslandJointTransitionAdmissions {
    private final Map<Integer, SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byNode;
    private final Map<SkyIslandHydraulicCascadeTransitionSite,
            SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byCascade;

    private SkyIslandJointTransitionAdmissions(
            Map<Integer, SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byNode,
            Map<SkyIslandHydraulicCascadeTransitionSite,
                    SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byCascade) {
        this.byNode = Map.copyOf(byNode);
        this.byCascade = Map.copyOf(byCascade);
    }

    static SkyIslandJointTransitionAdmissions from(
            SkyIslandConfluenceHeadCompatibilityPlan confluencePlan,
            SkyIslandCascadeHeadCompatibilityPlan cascadePlan,
            SkyIslandConfluenceCascadeHeadCompatibilityPlan jointPlan) {
        Objects.requireNonNull(confluencePlan, "confluencePlan");
        Objects.requireNonNull(cascadePlan, "cascadePlan");
        Objects.requireNonNull(jointPlan, "jointPlan");
        if (!confluencePlan.descriptor().equals(cascadePlan.descriptor())
                || !confluencePlan.descriptor().equals(jointPlan.descriptor())) {
            throw new IllegalArgumentException("joint admission descriptors must match");
        }

        Map<Integer, SkyIslandConfluenceHeadCompatibilityOutcome> confluences =
                new HashMap<>();
        for (SkyIslandConfluenceHeadCompatibilityOutcome outcome
                : confluencePlan.outcomes()) {
            int node = outcome.geometry().transitionSite().nodeCellIndex();
            if (confluences.put(node, outcome) != null) {
                throw new IllegalStateException("duplicate F3B confluence " + node);
            }
        }
        Map<SkyIslandHydraulicCascadeTransitionSite,
                SkyIslandCascadeHeadCompatibilityOutcome> cascades = new HashMap<>();
        for (SkyIslandCascadeHeadCompatibilityOutcome outcome : cascadePlan.outcomes()) {
            SkyIslandHydraulicCascadeTransitionSite site =
                    outcome.geometry().transitionSite();
            if (cascades.put(site, outcome) != null) {
                throw new IllegalStateException("duplicate F3C CASCADE site");
            }
        }

        Map<Integer, Integer> nodeCounts = new HashMap<>();
        Map<SkyIslandHydraulicCascadeTransitionSite, Integer> cascadeCounts =
                new HashMap<>();
        for (SkyIslandConfluenceCascadeHeadCompatibilityOutcome outcome
                : jointPlan.outcomes()) {
            nodeCounts.merge(
                    outcome.confluence().transitionSite().nodeCellIndex(), 1, Integer::sum);
            cascadeCounts.merge(
                    outcome.cascade().transitionSite(), 1, Integer::sum);
        }

        Map<Integer, SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byNode =
                new HashMap<>();
        Map<SkyIslandHydraulicCascadeTransitionSite,
                SkyIslandConfluenceCascadeHeadCompatibilityOutcome> byCascade =
                new HashMap<>();
        for (SkyIslandConfluenceCascadeHeadCompatibilityOutcome outcome
                : jointPlan.outcomes()) {
            int node = outcome.confluence().transitionSite().nodeCellIndex();
            SkyIslandHydraulicCascadeTransitionSite site =
                    outcome.cascade().transitionSite();
            SkyIslandConfluenceHeadCompatibilityOutcome confluence =
                    confluences.get(node);
            SkyIslandCascadeHeadCompatibilityOutcome cascade = cascades.get(site);
            if (outcome.status()
                            != SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED
                    || nodeCounts.get(node) != 1
                    || cascadeCounts.get(site) != 1
                    || confluence == null
                    || confluence.status()
                            != SkyIslandConfluenceHeadCompatibilityStatus.CASCADE_COUPLED
                    || cascade == null
                    || cascade.status()
                            == SkyIslandCascadeHeadCompatibilityStatus.INFEASIBLE
                    || cascade.status()
                            == SkyIslandCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE
                    || !confluence.geometry().equals(outcome.confluence())
                    || !cascade.geometry().equals(outcome.cascade())
                    || !confluence.geometry().legs().contains(outcome.coupledLeg())) {
                continue;
            }
            byNode.put(node, outcome);
            byCascade.put(site, outcome);
        }
        return new SkyIslandJointTransitionAdmissions(byNode, byCascade);
    }

    Optional<SkyIslandConfluenceCascadeHeadCompatibilityOutcome> forNode(int node) {
        return Optional.ofNullable(byNode.get(node));
    }

    Optional<SkyIslandConfluenceCascadeHeadCompatibilityOutcome> forCascade(
            SkyIslandHydraulicCascadeTransitionSite site) {
        return Optional.ofNullable(byCascade.get(site));
    }

    List<SkyIslandConfluenceCascadeHeadCompatibilityOutcome> outcomes() {
        return List.copyOf(byNode.values());
    }
}
