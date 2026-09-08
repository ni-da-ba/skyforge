package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0100 exact published-region inventory of accepted AUTH-0098 petroleum-system opportunity.
 *
 * <p>This profile preserves canonical AUTH-0087 association provenance. It supplies geological
 * inventory evidence only; STRATEGIC_NODE policy, guarantees, trade/salvage, progression, deposit
 * quantity, and backend realization remain downstream.
 */
public final class SkyIslandRegionalPetroleumSystemOpportunityProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final List<SkyIslandRegionalPetroleumSystemOpportunityEntry> islands;

    SkyIslandRegionalPetroleumSystemOpportunityProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            List<SkyIslandRegionalPetroleumSystemOpportunityEntry> islands) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.islands = List.copyOf(Objects.requireNonNull(islands, "islands"));

        List<SkyIslandAuthoredRealizationAssociation> expected =
                binding.associationCatalog().associations();
        if (this.islands.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "regional petroleum inventory requires exact AUTH-0087 association coverage");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!this.islands.get(index).association().equals(expected.get(index))) {
                throw new IllegalArgumentException(
                        "regional petroleum entries must preserve canonical AUTH-0087 association order");
            }
        }
    }

    public SkyIslandPublishedAuthoredRealizationBinding binding() {
        return binding;
    }

    public SkyIslandCompiledWorldPublicationId publicationId() {
        return binding.publication().id();
    }

    public long authoredWorldSeed() {
        return binding.authoredWorldSeed();
    }

    public int islandCount() {
        return islands.size();
    }

    /** Canonical AUTH-0087 association order. */
    public List<SkyIslandRegionalPetroleumSystemOpportunityEntry> islands() {
        return islands;
    }

    /** Number of islands with raw nonzero AUTH-0098 petroleum-system opportunity. */
    public int eligibleIslandCount() {
        return (int) islands.stream()
                .filter(SkyIslandRegionalPetroleumSystemOpportunityEntry::geologicallyEligible)
                .count();
    }

    /** Eligible islands in canonical AUTH-0087 order. */
    public List<SkyIslandRegionalPetroleumSystemOpportunityEntry> eligibleIslands() {
        return islands.stream()
                .filter(SkyIslandRegionalPetroleumSystemOpportunityEntry::geologicallyEligible)
                .toList();
    }

    /**
     * Descriptive ranking by unchanged AUTH-0098 mean system opportunity.
     *
     * <p>Java's stable sort preserves canonical association order for exact ties. This is not a
     * strategic-node selection or guarantee decision.
     */
    public List<SkyIslandRegionalPetroleumSystemOpportunityEntry> rankedEligibleIslands() {
        ArrayList<SkyIslandRegionalPetroleumSystemOpportunityEntry> ranked =
                new ArrayList<>(eligibleIslands());
        ranked.sort(Comparator.comparingDouble(
                        SkyIslandRegionalPetroleumSystemOpportunityEntry::meanSystemOpportunity)
                .reversed());
        return List.copyOf(ranked);
    }

    public OptionalDouble strongestMeanSystemOpportunity() {
        return islands.stream()
                .filter(SkyIslandRegionalPetroleumSystemOpportunityEntry::geologicallyEligible)
                .mapToDouble(SkyIslandRegionalPetroleumSystemOpportunityEntry::meanSystemOpportunity)
                .max();
    }
}
