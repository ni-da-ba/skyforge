package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0103 catalog-level aggregation of exact AUTH-0089 island opportunity profiles.
 *
 * <p>This carries explicit AUTH-0046 association provenance only. It makes no AUTH-0058 publication
 * claim and introduces no species, population, resource, or backend policy.
 */
public final class SkyIslandAuthoredRealizationEcologicalOpportunityProfile {
    private final SkyIslandAuthoredRealizationCatalog catalog;
    private final List<SkyIslandRegionalEcologicalOpportunityEntry> islands;
    private final SkyIslandEcologicalOpportunityAggregation.Result aggregate;

    SkyIslandAuthoredRealizationEcologicalOpportunityProfile(
            SkyIslandAuthoredRealizationCatalog catalog,
            List<SkyIslandRegionalEcologicalOpportunityEntry> islands) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(islands, "islands");
        this.islands = List.copyOf(islands);
        List<SkyIslandAuthoredRealizationAssociation> expected = catalog.associations();
        if (this.islands.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "catalog ecology profile requires exact AUTH-0046 association coverage");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!this.islands.get(index).association().equals(expected.get(index))) {
                throw new IllegalArgumentException(
                        "catalog ecology entries must preserve canonical AUTH-0046 association order");
            }
        }
        this.aggregate = SkyIslandEcologicalOpportunityAggregation.aggregate(this.islands);
    }

    public SkyIslandAuthoredRealizationCatalog catalog() {
        return catalog;
    }

    public long authoredWorldSeed() {
        return catalog.authoredWorldSeed();
    }

    public int islandCount() {
        return islands.size();
    }

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
