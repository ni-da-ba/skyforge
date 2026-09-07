package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderRegistry;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlanner;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0056 executes one approved AUTH-0055 candidate exactly once and reports proof convergence.
 *
 * <p>No automatic retry, margin adjustment, reservation inflation, or candidate reconstruction
 * occurs here.
 */
public final class SkyIslandSupportConvergenceExecutor {

    public SkyIslandSupportConvergenceReport executeOnce(
            SkyIslandSupportReplanProposal proposal,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(registry, "registry");
        proposal.requireComplete();

        SkyIslandArchipelagoPlan freshPlan;
        try {
            freshPlan =
                    new SkyIslandArchipelagoPlanner()
                            .plan(proposal.candidateRequest().orElseThrow());
        } catch (IllegalArgumentException | IllegalStateException plannerFailure) {
            return new SkyIslandSupportConvergenceReport(
                    proposal,
                    SkyIslandSupportConvergenceOutcome.PLANNER_REJECTED,
                    Optional.of(SkyIslandSupportPlannerFailure.from(plannerFailure)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
        }

        SkyIslandSupportReservationRequirementSynthesis freshSynthesis =
                new SkyIslandSupportReservationRequirementSynthesizer()
                        .synthesize(freshPlan, registry);
        if (!freshSynthesis.fullySynthesized()) {
            return new SkyIslandSupportConvergenceReport(
                    proposal,
                    SkyIslandSupportConvergenceOutcome.FRESH_SYNTHESIS_INCOMPLETE,
                    Optional.empty(),
                    Optional.of(freshPlan),
                    Optional.of(freshSynthesis),
                    Optional.empty());
        }

        SkyIslandSupportReservationPreflightReport freshPreflight =
                new SkyIslandSupportReservationPreflight()
                        .evaluate(
                                freshPlan,
                                registry,
                                proposal.candidateVerticalReservation().orElseThrow());
        SkyIslandSupportConvergenceOutcome outcome =
                freshPreflight.admitted()
                        ? SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS
                        : SkyIslandSupportConvergenceOutcome.FRESH_RESERVATION_REJECTED;

        return new SkyIslandSupportConvergenceReport(
                proposal,
                outcome,
                Optional.empty(),
                Optional.of(freshPlan),
                Optional.of(freshSynthesis),
                Optional.of(freshPreflight));
    }
}
