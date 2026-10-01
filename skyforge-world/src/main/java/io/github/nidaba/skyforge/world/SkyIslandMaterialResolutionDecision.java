package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0042 backend-neutral material resolution decision envelope.
 *
 * <p>The selected semantic profile, its AUTH-0040 evidence, AUTH-0041 rank, and final-selection
 * method are retained. Concrete backend candidate identity remains adapter-owned and is absent.
 */
public record SkyIslandMaterialResolutionDecision(
        SkyIslandMaterialResolutionFrontier frontier,
        SkyIslandMaterialCapabilityProfile selectedProfile,
        SkyIslandMaterialCompatibilityAssessment selectedCompatibility,
        SkyIslandMaterialCandidateRank selectedRank,
        SkyIslandMaterialResolutionSelectionMethod selectionMethod) {

    public SkyIslandMaterialResolutionDecision {
        frontier = Objects.requireNonNull(frontier, "frontier");
        selectedProfile = Objects.requireNonNull(selectedProfile, "selectedProfile");
        selectedCompatibility =
                Objects.requireNonNull(selectedCompatibility, "selectedCompatibility");
        selectedRank = Objects.requireNonNull(selectedRank, "selectedRank");
        selectionMethod = Objects.requireNonNull(selectionMethod, "selectionMethod");

        SkyIslandMaterialBindingRequest request = frontier.request();
        if (!frontier.containsProfile(selectedProfile)) {
            throw new IllegalArgumentException(
                    "selected semantic profile must occur in the compatible frontier");
        }

        SkyIslandMaterialCompatibilityAssessment expectedCompatibility =
                SkyIslandMaterialCompatibilityEvaluator.evaluate(request, selectedProfile);
        if (!expectedCompatibility.compatible()
                || !selectedCompatibility.equals(expectedCompatibility)) {
            throw new IllegalArgumentException(
                    "resolution decision must retain exact AUTH-0040 compatibility evidence");
        }

        SkyIslandMaterialCandidateRank expectedRank =
                SkyIslandMaterialCandidateRanker.rank(request, selectedProfile);
        if (!selectedRank.equals(expectedRank)) {
            throw new IllegalArgumentException(
                    "resolution decision must retain exact AUTH-0041 semantic rank");
        }
        if (!frontier.topRank().semanticallyTiedWith(selectedRank)) {
            throw new IllegalArgumentException(
                    "selected semantic profile must lie on the top AUTH-0041 frontier");
        }

        boolean tieBreakRequired = frontier.requiresBackendStableTieBreak();
        if (tieBreakRequired
                && selectionMethod
                        != SkyIslandMaterialResolutionSelectionMethod
                                .BACKEND_STABLE_IDENTITY_TIE_BREAK) {
            throw new IllegalArgumentException(
                    "top semantic tie requires backend-stable identity tie-break provenance");
        }
        if (!tieBreakRequired
                && selectionMethod
                        != SkyIslandMaterialResolutionSelectionMethod.SEMANTIC_RANK_WINNER) {
            throw new IllegalArgumentException(
                    "unique semantic winner must use semantic-rank selection provenance");
        }
    }

    public SkyIslandMaterialBindingRequest request() {
        return frontier.request();
    }

    public int compatibleCandidateCount() {
        return frontier.compatibleCandidateCount();
    }

    public int topSemanticTieCount() {
        return frontier.topSemanticTieCount();
    }

    public boolean backendStableTieBreakApplied() {
        return selectionMethod
                == SkyIslandMaterialResolutionSelectionMethod
                        .BACKEND_STABLE_IDENTITY_TIE_BREAK;
    }
}
