package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Exact AUTH-0100 pairing of one AUTH-0046 association with its AUTH-0098 petroleum profile. */
public record SkyIslandRegionalPetroleumSystemOpportunityEntry(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandPetroleumSystemOpportunityProfile islandProfile) {

    public SkyIslandRegionalPetroleumSystemOpportunityEntry {
        association = Objects.requireNonNull(association, "association");
        islandProfile = Objects.requireNonNull(islandProfile, "islandProfile");
        if (!islandProfile.descriptor().equals(association.authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "regional petroleum entry profile must belong to the exact associated authored descriptor");
        }
    }

    /** Raw AUTH-0098 geological eligibility: any nonzero accepted petroleum-system opportunity. */
    public boolean geologicallyEligible() {
        return islandProfile.peakSystemOpportunity() > 0.0;
    }

    /** Unchanged AUTH-0098 mean system opportunity for descriptive ordinal ranking only. */
    public double meanSystemOpportunity() {
        return islandProfile.meanSystemOpportunity();
    }
}
