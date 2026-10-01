package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0056 immutable terminal report for one and only one candidate planning attempt.
 *
 * <p>Fresh artifacts are present only after the stage that produced them actually ran.
 */
public record SkyIslandSupportConvergenceReport(
        SkyIslandSupportReplanProposal proposal,
        SkyIslandSupportConvergenceOutcome outcome,
        Optional<SkyIslandSupportPlannerFailure> plannerFailure,
        Optional<SkyIslandArchipelagoPlan> freshPlan,
        Optional<SkyIslandSupportReservationRequirementSynthesis> freshSynthesis,
        Optional<SkyIslandSupportReservationPreflightReport> freshPreflight) {

    public SkyIslandSupportConvergenceReport {
        proposal = Objects.requireNonNull(proposal, "proposal");
        outcome = Objects.requireNonNull(outcome, "outcome");
        plannerFailure = Objects.requireNonNull(plannerFailure, "plannerFailure");
        freshPlan = Objects.requireNonNull(freshPlan, "freshPlan");
        freshSynthesis = Objects.requireNonNull(freshSynthesis, "freshSynthesis");
        freshPreflight = Objects.requireNonNull(freshPreflight, "freshPreflight");

        if (!proposal.complete()) {
            throw new IllegalArgumentException(
                    "AUTH-0056 convergence report requires a complete AUTH-0055 proposal");
        }

        switch (outcome) {
            case PLANNER_REJECTED -> {
                requirePresent("plannerFailure", plannerFailure);
                requireEmpty("freshPlan", freshPlan);
                requireEmpty("freshSynthesis", freshSynthesis);
                requireEmpty("freshPreflight", freshPreflight);
            }
            case FRESH_SYNTHESIS_INCOMPLETE -> {
                requireEmpty("plannerFailure", plannerFailure);
                requirePresent("freshPlan", freshPlan);
                requirePresent("freshSynthesis", freshSynthesis);
                requireEmpty("freshPreflight", freshPreflight);
                if (freshSynthesis.orElseThrow().fullySynthesized()) {
                    throw new IllegalArgumentException(
                            "incomplete outcome requires incomplete fresh synthesis");
                }
            }
            case FRESH_RESERVATION_REJECTED -> {
                requireEmpty("plannerFailure", plannerFailure);
                requirePresent("freshPlan", freshPlan);
                requirePresent("freshSynthesis", freshSynthesis);
                requirePresent("freshPreflight", freshPreflight);
                if (!freshSynthesis.orElseThrow().fullySynthesized()
                        || freshPreflight.orElseThrow().admitted()) {
                    throw new IllegalArgumentException(
                            "reservation-rejected outcome requires complete synthesis and rejected preflight");
                }
            }
            case ACCEPTED_ONE_PASS -> {
                requireEmpty("plannerFailure", plannerFailure);
                requirePresent("freshPlan", freshPlan);
                requirePresent("freshSynthesis", freshSynthesis);
                requirePresent("freshPreflight", freshPreflight);
                if (!freshSynthesis.orElseThrow().fullySynthesized()
                        || !freshPreflight.orElseThrow().admitted()) {
                    throw new IllegalArgumentException(
                            "accepted outcome requires complete synthesis and admitted preflight");
                }
            }
        }
    }

    public boolean accepted() {
        return outcome == SkyIslandSupportConvergenceOutcome.ACCEPTED_ONE_PASS;
    }

    /** The executor contract is exactly one candidate planning invocation per report. */
    public int plannerAttemptCount() {
        return 1;
    }

    private static void requirePresent(String property, Optional<?> value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(property + " must be present for outcome");
        }
    }

    private static void requireEmpty(String property, Optional<?> value) {
        if (value.isPresent()) {
            throw new IllegalArgumentException(property + " must be absent for outcome");
        }
    }
}
