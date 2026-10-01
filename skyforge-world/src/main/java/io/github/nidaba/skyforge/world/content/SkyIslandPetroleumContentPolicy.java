package io.github.nidaba.skyforge.world.content;

import io.github.nidaba.skyforge.world.SkyIslandPetroleumSystemOpportunityProfile;
import java.util.Objects;

/**
 * CONTENT C22 backend-neutral gameplay policy over accepted AUTH-0098 petroleum-system opportunity.
 *
 * <p>This policy owns progression, availability, alternative-access, and selection-evidence semantics
 * only. It must not reinterpret AUTH-0098 normalized opportunity as recoverable reserves, pressure,
 * grade, deposit count/volume, extraction rate, or backend placement policy.
 */
public final class SkyIslandPetroleumContentPolicy {
    public enum AvailabilityClass {
        STRATEGIC_NODE
    }

    public enum ProgressionBand {
        R3_MATURE_INDUSTRY
    }

    public enum GuaranteePolicy {
        NO_ORDINARY_PROVINCE_HARD_GUARANTEE
    }

    public enum AlternativeAccess {
        BOUNDED_TRADE_OR_SALVAGE
    }

    public enum IndustrialSupplyPolicy {
        PRIMARY_EXTRACTION_OR_LOGISTICS_REQUIRED
    }

    public enum RegionalSelectionEvidence {
        CANONICAL_AUTH_0098_REGIONAL_INVENTORY_REQUIRED
    }

    public record Entry(
            AvailabilityClass availabilityClass,
            ProgressionBand progressionBand,
            GuaranteePolicy guaranteePolicy,
            boolean firstFlightCritical,
            AlternativeAccess alternativeAccess,
            IndustrialSupplyPolicy industrialSupplyPolicy,
            RegionalSelectionEvidence regionalSelectionEvidence) {
        public Entry {
            availabilityClass = Objects.requireNonNull(availabilityClass, "availabilityClass");
            progressionBand = Objects.requireNonNull(progressionBand, "progressionBand");
            guaranteePolicy = Objects.requireNonNull(guaranteePolicy, "guaranteePolicy");
            alternativeAccess = Objects.requireNonNull(alternativeAccess, "alternativeAccess");
            industrialSupplyPolicy =
                    Objects.requireNonNull(industrialSupplyPolicy, "industrialSupplyPolicy");
            regionalSelectionEvidence =
                    Objects.requireNonNull(regionalSelectionEvidence, "regionalSelectionEvidence");
        }
    }

    private static final Entry PETROLEUM = new Entry(
            AvailabilityClass.STRATEGIC_NODE,
            ProgressionBand.R3_MATURE_INDUSTRY,
            GuaranteePolicy.NO_ORDINARY_PROVINCE_HARD_GUARANTEE,
            false,
            AlternativeAccess.BOUNDED_TRADE_OR_SALVAGE,
            IndustrialSupplyPolicy.PRIMARY_EXTRACTION_OR_LOGISTICS_REQUIRED,
            RegionalSelectionEvidence.CANONICAL_AUTH_0098_REGIONAL_INVENTORY_REQUIRED);

    private SkyIslandPetroleumContentPolicy() {}

    /** Returns the fixed C22 petroleum gameplay policy. */
    public static Entry policy() {
        return PETROLEUM;
    }

    /**
     * Returns whether AUTH-0098 supplies any accepted petroleum-system geological opportunity.
     *
     * <p>Zero opportunity fails closed. C22 does not create a strategic petroleum node on
     * zero-opportunity geology merely to satisfy progression or regional variety.
     */
    public static boolean geologicallyEligible(SkyIslandPetroleumSystemOpportunityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.peakSystemOpportunity() > 0.0;
    }

    /**
     * Returns unchanged AUTH-0098 mean system opportunity for ordinal ranking among eligible
     * candidates.
     *
     * <p>This score is not a rarity threshold, reserve estimate, deposit scale, grade, pressure,
     * extraction rate, or placement instruction.
     */
    public static double candidateRankScore(SkyIslandPetroleumSystemOpportunityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (!geologicallyEligible(profile)) {
            return 0.0;
        }
        return profile.meanSystemOpportunity();
    }
}
