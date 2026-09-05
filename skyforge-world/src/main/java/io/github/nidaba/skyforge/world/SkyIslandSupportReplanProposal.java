package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlan;
import io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0055 reviewable immutable re-plan proposal.
 *
 * <p>Construction never executes the candidate request. A fresh plan produced from the candidate
 * must be re-synthesized and admitted by AUTH-0053 before proof-backed world compilation.
 */
public record SkyIslandSupportReplanProposal(
        SkyIslandArchipelagoRequest originalRequest,
        SkyIslandArchipelagoPlan originalPlan,
        SkyIslandSupportReservationRequirementSynthesis synthesis,
        SkyIslandWorldVerticalReservation originalVerticalReservation,
        SkyIslandSupportReplanMargin authorMargin,
        List<SkyIslandSupportReplanGroupProposal> groupProposals,
        SkyIslandSupportReplanValue belowSuspension,
        SkyIslandSupportReplanValue aboveSuspension,
        Optional<SkyIslandArchipelagoRequest> candidateRequest,
        Optional<SkyIslandWorldVerticalReservation> candidateVerticalReservation,
        long uncertifiedMemberCount) {

    public SkyIslandSupportReplanProposal {
        originalRequest = Objects.requireNonNull(originalRequest, "originalRequest");
        originalPlan = Objects.requireNonNull(originalPlan, "originalPlan");
        synthesis = Objects.requireNonNull(synthesis, "synthesis");
        originalVerticalReservation =
                Objects.requireNonNull(originalVerticalReservation, "originalVerticalReservation");
        authorMargin = Objects.requireNonNull(authorMargin, "authorMargin");
        groupProposals = List.copyOf(Objects.requireNonNull(groupProposals, "groupProposals"));
        belowSuspension = Objects.requireNonNull(belowSuspension, "belowSuspension");
        aboveSuspension = Objects.requireNonNull(aboveSuspension, "aboveSuspension");
        candidateRequest = Objects.requireNonNull(candidateRequest, "candidateRequest");
        candidateVerticalReservation =
                Objects.requireNonNull(
                        candidateVerticalReservation, "candidateVerticalReservation");
        if (uncertifiedMemberCount < 0) {
            throw new IllegalArgumentException("uncertifiedMemberCount must be non-negative");
        }
        boolean complete = uncertifiedMemberCount == 0;
        if (candidateRequest.isPresent() != complete
                || candidateVerticalReservation.isPresent() != complete) {
            throw new IllegalArgumentException(
                    "candidate request/reservation are present exactly for complete proposals");
        }
    }

    public boolean complete() {
        return uncertifiedMemberCount == 0;
    }

    public boolean changesHorizontalPlanning() {
        return groupProposals.stream()
                .anyMatch(SkyIslandSupportReplanGroupProposal::changesPlanningGeometry);
    }

    public boolean changesVerticalReservation() {
        return belowSuspension.raisedByProofOrMargin()
                || aboveSuspension.raisedByProofOrMargin();
    }

    /**
     * A complete proposal still requires deterministic re-planning and fresh AUTH-0054/AUTH-0053
     * evaluation before acceptance whenever horizontal/group planning changes.
     */
    public boolean freshReplanRequired() {
        return complete() && changesHorizontalPlanning();
    }

    public void requireComplete() {
        if (!complete()) {
            throw new IllegalStateException(
                    "AUTH-0055 proposal incomplete: uncertified members="
                            + uncertifiedMemberCount);
        }
    }
}
