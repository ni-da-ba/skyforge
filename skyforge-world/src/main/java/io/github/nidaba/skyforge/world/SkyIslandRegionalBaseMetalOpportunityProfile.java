package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * AUTH-0094 exact published-region inventory of accepted AUTH-0093 base-metal opportunity.
 *
 * <p>This profile preserves canonical AUTH-0087 association provenance. It supplies raw regional
 * geological evidence only; availability classes, guarantee satisfaction, trade/salvage policy,
 * deposit quantity, and backend realization remain downstream.
 */
public final class SkyIslandRegionalBaseMetalOpportunityProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final List<SkyIslandRegionalBaseMetalOpportunityEntry> islands;

    SkyIslandRegionalBaseMetalOpportunityProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            List<SkyIslandRegionalBaseMetalOpportunityEntry> islands) {
        this.binding = Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(islands, "islands");
        this.islands = List.copyOf(islands);

        List<SkyIslandAuthoredRealizationAssociation> expected =
                binding.associationCatalog().associations();
        if (this.islands.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "regional base-metal inventory requires exact AUTH-0087 association coverage");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!this.islands.get(index).association().equals(expected.get(index))) {
                throw new IllegalArgumentException(
                        "regional base-metal entries must preserve canonical AUTH-0087 association order");
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
    public List<SkyIslandRegionalBaseMetalOpportunityEntry> islands() {
        return islands;
    }

    /** Number of islands with raw nonzero AUTH-0093 opportunity for one metal. */
    public int eligibleIslandCount(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return (int) islands.stream().filter(entry -> entry.geologicallyEligible(kind)).count();
    }

    /** Eligible islands in canonical AUTH-0087 order. */
    public List<SkyIslandRegionalBaseMetalOpportunityEntry> eligibleIslands(
            SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return islands.stream().filter(entry -> entry.geologicallyEligible(kind)).toList();
    }

    /**
     * Descriptive ranking by unchanged AUTH-0093 mean opportunity, with canonical order as the only
     * tie-breaker. This does not select or guarantee a gameplay resource site.
     */
    public List<SkyIslandRegionalBaseMetalOpportunityEntry> rankedEligibleIslands(
            SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        ArrayList<SkyIslandRegionalBaseMetalOpportunityEntry> ranked =
                new ArrayList<>(eligibleIslands(kind));
        ranked.sort(Comparator.comparingDouble(
                        (SkyIslandRegionalBaseMetalOpportunityEntry entry) ->
                                entry.meanOpportunity(kind))
                .reversed());
        return List.copyOf(ranked);
    }

    public OptionalDouble strongestMeanOpportunity(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return islands.stream()
                .filter(entry -> entry.geologicallyEligible(kind))
                .mapToDouble(entry -> entry.meanOpportunity(kind))
                .max();
    }
}
