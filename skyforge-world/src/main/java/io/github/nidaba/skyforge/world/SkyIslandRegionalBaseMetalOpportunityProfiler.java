package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Objects;

/**
 * AUTH-0094 regional base-metal profiler over one exact AUTH-0087 published authored-realization binding.
 *
 * <p>No island subset, resolution, resource availability class, guarantee scope, quantity threshold,
 * or backend identity is caller-selectable. Island geology comes only from accepted AUTH-0093.
 */
public final class SkyIslandRegionalBaseMetalOpportunityProfiler {
    private final SkyIslandBaseMetalOpportunityProfiler islandProfiler =
            new SkyIslandBaseMetalOpportunityProfiler();

    public SkyIslandRegionalBaseMetalOpportunityProfile profile(
            SkyIslandPublishedAuthoredRealizationBinding binding) {
        Objects.requireNonNull(binding, "binding");

        ArrayList<SkyIslandRegionalBaseMetalOpportunityEntry> entries =
                new ArrayList<>(binding.volumeCount());
        for (SkyIslandAuthoredRealizationAssociation association :
                binding.associationCatalog().associations()) {
            entries.add(new SkyIslandRegionalBaseMetalOpportunityEntry(
                    association,
                    islandProfiler.profile(association.authoredDescriptor())));
        }
        return new SkyIslandRegionalBaseMetalOpportunityProfile(binding, entries);
    }
}
