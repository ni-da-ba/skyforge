package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Objects;

/**
 * AUTH-0100 regional petroleum profiler over one exact AUTH-0087 published authored-realization
 * binding.
 *
 * <p>No island subset, eligibility threshold, availability class, guarantee scope, quantity, or
 * backend identity is caller-selectable. Island evidence comes only from accepted AUTH-0098.
 */
public final class SkyIslandRegionalPetroleumSystemOpportunityProfiler {
    private final SkyIslandPetroleumSystemOpportunityProfiler islandProfiler =
            new SkyIslandPetroleumSystemOpportunityProfiler();

    public SkyIslandRegionalPetroleumSystemOpportunityProfile profile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        Objects.requireNonNull(binding, "binding");

        ArrayList<SkyIslandRegionalPetroleumSystemOpportunityEntry> entries =
                new ArrayList<>(binding.volumeCount());
        for (SkyIslandAuthoredRealizationAssociation association :
                binding.associationCatalog().associations()) {
            entries.add(new SkyIslandRegionalPetroleumSystemOpportunityEntry(
                    association,
                    islandProfiler.profile(association.authoredDescriptor())));
        }
        return new SkyIslandRegionalPetroleumSystemOpportunityProfile(binding, entries);
    }
}
