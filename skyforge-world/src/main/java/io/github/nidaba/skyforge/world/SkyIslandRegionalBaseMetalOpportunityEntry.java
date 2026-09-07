package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Exact AUTH-0094 provenance pairing one AUTH-0046 association with its AUTH-0093 profile. */
public record SkyIslandRegionalBaseMetalOpportunityEntry(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandBaseMetalOpportunityProfile islandProfile) {

    public SkyIslandRegionalBaseMetalOpportunityEntry {
        association = Objects.requireNonNull(association, "association");
        islandProfile = Objects.requireNonNull(islandProfile, "islandProfile");
        if (!islandProfile.descriptor().equals(association.authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "regional base-metal entry profile must belong to the exact associated authored descriptor");
        }
    }

    /** Raw AUTH-0093 geological eligibility: any nonzero accepted opportunity. */
    public boolean geologicallyEligible(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return islandProfile.peakOpportunity(kind) > 0.0;
    }

    /** Unchanged AUTH-0093 mean opportunity; downstream Content may use it for ordinal ranking. */
    public double meanOpportunity(SkyIslandBaseMetalKind kind) {
        Objects.requireNonNull(kind, "kind");
        return islandProfile.meanOpportunity(kind);
    }
}
