package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0090 deterministic regional aggregation of exact AUTH-0089 island opportunity profiles.
 *
 * <p>The publication boundary remains mandatory. Area-weighted ecology math is shared with the
 * AUTH-0103 explicit-association profile so publication and runtime-fixture consumers cannot drift.
 */
public final class SkyIslandRegionalEcologicalOpportunityProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final List<SkyIslandRegionalEcologicalOpportunityEntry> islands;
    private final SkyIslandEcologicalOpportunityAggregation.Result aggregate;

    SkyIslandRegionalEcologicalOpportunityProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            List<SkyIslandRegionalEcologicalOpportunityEntry> islands) {
        this.binding = Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(islands, "islands");
        this.islands = List.copyOf(islands);

        List<SkyIslandAuthoredRealizationAssociation> expected =
                binding.associationCatalog().associations();
        if (this.islands.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "regional ecology profile requires exact AUTH-0087 association coverage");
        }
        for (int index = 0; index < expected.size(); index++) {
            SkyIslandRegionalEcologicalOpportunityEntry entry = this.islands.get(index);
            if (!entry.association().equals(expected.get(index))) {
                throw new IllegalArgumentException(
                        "regional ecology entries must preserve canonical AUTH-0087 association order");
            }
        }
        this.aggregate = SkyIslandEcologicalOpportunityAggregation.aggregate(this.islands);
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
    public List<SkyIslandRegionalEcologicalOpportunityEntry> islands() {
        return islands;
    }

    public double totalHorizontalOwnedAreaEstimate() {
        return aggregate.totalHorizontalOwnedAreaEstimate();
    }

    public double meanVegetationPotential() {
        return aggregate.meanVegetationPotential();
    }

    public double meanSaturationPotential() {
        return aggregate.meanSaturationPotential();
    }

    public double meanThermalSuitability() {
        return aggregate.meanThermalSuitability();
    }

    public Map<SkyIslandEcologyRegime, Double> regimeFractions() {
        return aggregate.regimeFractions();
    }

    public double regimeFraction(SkyIslandEcologyRegime regime) {
        Objects.requireNonNull(regime, "regime");
        return aggregate.regimeFractions().get(regime);
    }
}
