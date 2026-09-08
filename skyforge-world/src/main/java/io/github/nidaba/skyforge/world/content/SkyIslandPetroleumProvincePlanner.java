package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityEntry;
import io.github.nidaba.skyforge.world.SkyIslandRegionalPetroleumSystemOpportunityProfile;
import java.util.List;
import java.util.Objects;

/**
 * CONTENT C23 province-feasibility planner over accepted AUTH-0100 regional petroleum evidence.
 *
 * <p>This layer decides only whether a published province can satisfy an intentional petroleum
 * strategic-node requirement from accepted geology. It never selects a literal oilfield, refinery,
 * route, settlement, deposit, or Minecraft realization.
 */
public final class SkyIslandPetroleumProvincePlanner {
    public enum ProvinceIntent {
        ORDINARY_PROVINCE,
        PETROLEUM_STRATEGIC_NODE
    }

    public enum Outcome {
        NO_PETROLEUM_REQUIREMENT,
        CANDIDATES_AVAILABLE,
        REPLAN_REQUIRED
    }

    public record Plan(
            SkyIslandRegionalPetroleumSystemOpportunityProfile sourceProfile,
            ProvinceIntent intent,
            Outcome outcome,
            List<SkyIslandRegionalPetroleumSystemOpportunityEntry> canonicalEligibleCandidates,
            List<SkyIslandRegionalPetroleumSystemOpportunityEntry> rankedEligibleCandidates) {
        public Plan {
            sourceProfile = Objects.requireNonNull(sourceProfile, "sourceProfile");
            intent = Objects.requireNonNull(intent, "intent");
            outcome = Objects.requireNonNull(outcome, "outcome");
            canonicalEligibleCandidates =
                    List.copyOf(Objects.requireNonNull(
                            canonicalEligibleCandidates, "canonicalEligibleCandidates"));
            rankedEligibleCandidates =
                    List.copyOf(Objects.requireNonNull(
                            rankedEligibleCandidates, "rankedEligibleCandidates"));

            if (!canonicalEligibleCandidates.equals(sourceProfile.eligibleIslands())) {
                throw new IllegalArgumentException(
                        "C23 canonical candidates must be the exact AUTH-0100 eligible-island view");
            }
            if (!rankedEligibleCandidates.equals(sourceProfile.rankedEligibleIslands())) {
                throw new IllegalArgumentException(
                        "C23 ranked candidates must be the exact AUTH-0100 ranked eligible-island view");
            }
            Outcome expected = outcomeForIntent(intent, canonicalEligibleCandidates.size());
            if (outcome != expected) {
                throw new IllegalArgumentException(
                        "C23 outcome must follow exact intent and AUTH-0100 eligible coverage");
            }
        }

        public boolean requiresReplan() {
            return outcome == Outcome.REPLAN_REQUIRED;
        }
    }

    public Plan plan(
            SkyIslandRegionalPetroleumSystemOpportunityProfile profile,
            ProvinceIntent intent) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(intent, "intent");

        List<SkyIslandRegionalPetroleumSystemOpportunityEntry> canonical =
                profile.eligibleIslands();
        List<SkyIslandRegionalPetroleumSystemOpportunityEntry> ranked =
                profile.rankedEligibleIslands();
        return new Plan(
                profile,
                intent,
                outcomeForIntent(intent, canonical.size()),
                canonical,
                ranked);
    }

    static Outcome outcomeForIntent(ProvinceIntent intent, int eligibleIslandCount) {
        Objects.requireNonNull(intent, "intent");
        if (eligibleIslandCount < 0) {
            throw new IllegalArgumentException("eligibleIslandCount must be non-negative");
        }
        return switch (intent) {
            case ORDINARY_PROVINCE -> Outcome.NO_PETROLEUM_REQUIREMENT;
            case PETROLEUM_STRATEGIC_NODE -> eligibleIslandCount == 0
                    ? Outcome.REPLAN_REQUIRED
                    : Outcome.CANDIDATES_AVAILABLE;
        };
    }
}
