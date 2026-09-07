package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Objects;

/**
 * AUTH-0090 regional ecology profiler over one exact AUTH-0087 published authored-realization binding.
 *
 * <p>No island list, resolution, weight, resource family, species role, or backend identity is caller
 * selectable. Island ecology comes only from accepted AUTH-0089.
 */
public final class SkyIslandRegionalEcologicalOpportunityProfiler {
    private final SkyIslandEcologicalOpportunityProfiler islandProfiler =
            new SkyIslandEcologicalOpportunityProfiler();

    public SkyIslandRegionalEcologicalOpportunityProfile profile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        Objects.requireNonNull(binding, "binding");

        ArrayList<SkyIslandRegionalEcologicalOpportunityEntry> entries =
                new ArrayList<>(binding.volumeCount());
        for (SkyIslandAuthoredRealizationAssociation association :
                binding.associationCatalog().associations()) {
            entries.add(new SkyIslandRegionalEcologicalOpportunityEntry(
                    association,
                    islandProfiler.profile(association.authoredDescriptor())));
        }
        return new SkyIslandRegionalEcologicalOpportunityProfile(binding, entries);
    }
}
