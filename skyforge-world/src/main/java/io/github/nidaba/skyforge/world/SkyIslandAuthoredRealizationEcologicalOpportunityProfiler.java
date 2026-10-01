package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Objects;

/** AUTH-0103 catalog-level ecology opportunity over exact explicit AUTH-0046 associations. */
public final class SkyIslandAuthoredRealizationEcologicalOpportunityProfiler {
    private final SkyIslandEcologicalOpportunityProfiler islandProfiler =
            new SkyIslandEcologicalOpportunityProfiler();

    public SkyIslandAuthoredRealizationEcologicalOpportunityProfile profile(
            SkyIslandAuthoredRealizationCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        ArrayList<SkyIslandRegionalEcologicalOpportunityEntry> entries =
                new ArrayList<>(catalog.size());
        for (SkyIslandAuthoredRealizationAssociation association : catalog.associations()) {
            entries.add(new SkyIslandRegionalEcologicalOpportunityEntry(
                    association,
                    islandProfiler.profile(association.authoredDescriptor())));
        }
        return new SkyIslandAuthoredRealizationEcologicalOpportunityProfile(catalog, entries);
    }
}
