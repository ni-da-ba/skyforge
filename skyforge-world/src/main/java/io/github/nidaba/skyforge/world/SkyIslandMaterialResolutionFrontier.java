package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0042 auditable semantic frontier of every AUTH-0040-compatible candidate rank.
 *
 * <p>Candidate multiplicity is preserved so two distinct backend candidates that advertise an
 * identical semantic capability profile remain visible as a semantic tie without importing either
 * backend identity into the world model.
 */
public record SkyIslandMaterialResolutionFrontier(
        SkyIslandMaterialBindingRequest request,
        List<SkyIslandMaterialCandidateRank> compatibleRanks) {

    public SkyIslandMaterialResolutionFrontier {
        request = Objects.requireNonNull(request, "request");
        compatibleRanks = List.copyOf(compatibleRanks);
        if (compatibleRanks.isEmpty()) {
            throw new IllegalArgumentException(
                    "material resolution frontier requires at least one compatible candidate");
        }

        SkyIslandMaterialCandidateRank previous = null;
        for (SkyIslandMaterialCandidateRank rank : compatibleRanks) {
            Objects.requireNonNull(rank, "compatible rank");
            if (!rank.request().equals(request)) {
                throw new IllegalArgumentException(
                        "resolution frontier ranks must retain one AUTH-0039 request");
            }
            if (previous != null
                    && SkyIslandMaterialCandidateRanker.compareBestFirst(previous, rank) > 0) {
                throw new IllegalArgumentException(
                        "resolution frontier ranks must be ordered best-first");
            }
            previous = rank;
        }
    }

    public SkyIslandMaterialCandidateRank topRank() {
        return compatibleRanks.get(0);
    }

    public int compatibleCandidateCount() {
        return compatibleRanks.size();
    }

    public int topSemanticTieCount() {
        SkyIslandMaterialCandidateRank top = topRank();
        int count = 0;
        for (SkyIslandMaterialCandidateRank rank : compatibleRanks) {
            if (top.semanticallyTiedWith(rank)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public boolean requiresBackendStableTieBreak() {
        return topSemanticTieCount() > 1;
    }

    public boolean containsProfile(SkyIslandMaterialCapabilityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return compatibleRanks.stream().anyMatch(rank -> rank.profile().equals(profile));
    }
}
