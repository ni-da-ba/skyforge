package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Exact AUTH-0090 provenance entry pairing one AUTH-0046 association with its AUTH-0089 profile. */
public record SkyIslandRegionalEcologicalOpportunityEntry(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandEcologicalOpportunityProfile islandProfile) {

    public SkyIslandRegionalEcologicalOpportunityEntry {
        association = Objects.requireNonNull(association, "association");
        islandProfile = Objects.requireNonNull(islandProfile, "islandProfile");
        if (!islandProfile.descriptor().equals(association.authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "regional ecology entry profile must belong to the exact associated authored descriptor");
        }
    }
}
