package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0042 construction/validation boundary for semantic material resolution decisions.
 *
 * <p>The caller supplies one capability profile per backend candidate. Duplicate profiles are
 * intentionally preserved because they can represent distinct backend materials that are
 * semantically indistinguishable to the world model.
 */
public final class SkyIslandMaterialResolutionDecisionFactory {
    private SkyIslandMaterialResolutionDecisionFactory() {}

    public static SkyIslandMaterialResolutionFrontier frontier(
            SkyIslandMaterialBindingRequest request,
            List<SkyIslandMaterialCapabilityProfile> candidateProfiles) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(candidateProfiles, "candidateProfiles");

        List<SkyIslandMaterialCandidateRank> ranks =
                SkyIslandMaterialCandidateRanker.rankCompatible(request, candidateProfiles);
        if (ranks.isEmpty()) {
            throw new IllegalArgumentException(
                    "material resolution requires at least one AUTH-0040-compatible candidate");
        }
        return new SkyIslandMaterialResolutionFrontier(request, ranks);
    }

    public static SkyIslandMaterialResolutionDecision decide(
            SkyIslandMaterialBindingRequest request,
            List<SkyIslandMaterialCapabilityProfile> candidateProfiles,
            SkyIslandMaterialCapabilityProfile selectedProfile,
            SkyIslandMaterialResolutionSelectionMethod selectionMethod) {
        SkyIslandMaterialResolutionFrontier frontier =
                frontier(request, candidateProfiles);
        return decide(frontier, selectedProfile, selectionMethod);
    }

    public static SkyIslandMaterialResolutionDecision decide(
            SkyIslandMaterialResolutionFrontier frontier,
            SkyIslandMaterialCapabilityProfile selectedProfile,
            SkyIslandMaterialResolutionSelectionMethod selectionMethod) {
        Objects.requireNonNull(frontier, "frontier");
        Objects.requireNonNull(selectedProfile, "selectedProfile");
        Objects.requireNonNull(selectionMethod, "selectionMethod");

        SkyIslandMaterialCompatibilityAssessment compatibility =
                SkyIslandMaterialCompatibilityEvaluator.evaluate(
                        frontier.request(), selectedProfile);
        SkyIslandMaterialCandidateRank rank =
                SkyIslandMaterialCandidateRanker.rank(
                        frontier.request(), selectedProfile);

        return new SkyIslandMaterialResolutionDecision(
                frontier,
                selectedProfile,
                compatibility,
                rank,
                selectionMethod);
    }
}
